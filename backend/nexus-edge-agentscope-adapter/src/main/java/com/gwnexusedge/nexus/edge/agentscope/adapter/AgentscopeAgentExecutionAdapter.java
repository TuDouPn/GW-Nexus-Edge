package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionPort;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.SecretResolver;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.TaskAttemptId;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * {@link AgentExecutionPort} 的 AgentScope 官方实现（DEV-0001 第二轮修正）。
 *
 * <p>安全边界（P0-1）：业务 Agent 构建时显式禁用全部危险能力——
 * {@code disableFilesystemTools / disableShellTool / disableSubagents /
 * disableDynamicSubagents / disableDynamicSkills / disableDefaultWorkspaceSkills}，
 * 并在构建后通过 {@link #toolSurface()} 暴露真实工具面供测试断言。
 * 业务 Agent 不得获得 Host execute / 未授权文件系统 / subagent / workspace skill 能力。
 *
 * <p>单一执行源（P0-2）：{@code streamEvents()} 是唯一执行源。{@code startExecution}
 * 启动一次 {@code streamEvents} 订阅，把事件写入执行前建立的 {@link SubmissionPublisher}；
 * {@code streamExecutionEvents} 返回同一 Publisher，绝不发起第二次执行。
 *
 * <p>Secret 边界（P0-3）：Adapter 只持有 Secret Reference，经 {@link SecretResolver}
 * 在运行期解析临时值；不保存、不记录明文。main 生产装配不提供伪实现。
 *
 * <p>标识模型（P1-7）：taskAttemptId（UUIDv7）/ agentId（AgentScope 实例标识）/
 * traceId（OTel）三者独立，禁止混淆。
 */
public class AgentscopeAgentExecutionAdapter implements AgentExecutionPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeAgentExecutionAdapter.class);

    private final AgentscopeAdapterConfig config;
    private final Toolkit toolkit;
    private final AgentStateStore stateStore;
    private final SecretResolver secretResolver;
    private final Map<String, ExecutionHandle> handlesByAttemptId = new ConcurrentHashMap<>();
    private final Map<String, String> attemptIdByTaskId = new ConcurrentHashMap<>();

    /**
     * @param config         Adapter 配置（生产配置 fail-fast）
     * @param toolkit        显式白名单 Toolkit（业务允许的只读工具；为 null 时使用空 Toolkit）
     * @param stateStore     官方 State Store（会话持久化与 resume；为 null 时无持久化）
     * @param secretResolver Secret 解析端口（apiKeyReference → 临时值；禁止直接传明文）
     */
    public AgentscopeAgentExecutionAdapter(
            AgentscopeAdapterConfig config,
            Toolkit toolkit,
            AgentStateStore stateStore,
            SecretResolver secretResolver) {
        this.config = Objects.requireNonNull(config, "config 不允许为 null");
        this.toolkit = toolkit == null ? new Toolkit() : toolkit;
        this.stateStore = stateStore;
        this.secretResolver = Objects.requireNonNull(secretResolver, "secretResolver 不允许为 null");
    }

    @Override
    public AgentExecutionReference startExecution(AgentExecutionRequest request) {
        // 通过 SecretResolver 解析 Secret Reference 为运行期临时值（P0-3），
        // 解析结果仅本次执行使用，不保存、不记录。
        String apiKey = secretResolver.resolve(config.apiKeyReference());
        ModelAssembler.registerOpenAiCompatibleModel(config.modelId(), config.baseUrl(), apiKey);

        // 构建 Agent：显式禁用全部危险能力（P0-1）。
        HarnessAgent agent = buildSecureAgent();

        // 标识模型（P1-7）：taskAttemptId 为 Nexus 业务 UUIDv7；agentId 为 AgentScope 实例标识。
        String taskAttemptId = TaskAttemptId.newUuidV7();
        String agentId = agent.getAgentId();

        // P1-4：真实执行链路采集 OTel Trace ID（span 在创建时即确定 traceId）。
        // AgentScope core 通过 GlobalOpenTelemetry + OtelTracingMiddleware 传播 span；
        // span 在订阅的执行线程中保持 current 直至完成/失败/取消（见下方 doOnComplete/doOnError）。
        // 未配置 SDK 时返回 no-op（traceId 为全零），由测试与生产门禁拒绝。
        TraceSupport.TraceHandle trace = TraceSupport.startAndCaptureTraceId();
        String traceId = trace.traceId();

        // P1-8：SubmissionPublisher 必须在执行开始前建立，避免订阅前事件丢失。
        SubmissionPublisher<AgentEventEnvelope> publisher = new SubmissionPublisher<>();

        ExecutionHandle handle = new ExecutionHandle(
                request, agent, taskAttemptId, agentId, publisher);
        // handles 以 taskAttemptId 为键；业务 Task 标识 → taskAttemptId 映射供按 Task 查询。
        handlesByAttemptId.put(taskAttemptId, handle);
        attemptIdByTaskId.put(request.taskId(), taskAttemptId);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(request.sessionId())
                .userId(request.userId())
                .build();
        List<Msg> messages = request.messages().stream()
                .map(UserMessage::new)
                .map(Msg.class::cast)
                .toList();

        // 单一执行源（P0-2）：唯一一次 streamEvents 订阅，同时完成事件推送/状态/结果/失败/取消。
        handle.disposable = agent.streamEvents(messages, ctx)
                .doOnNext(event -> {
                    AgentEventEnvelope envelope = mapEvent(handle, event);
                    handle.publisher.submit(envelope);
                    // 终态由真实事件驱动（P1-5）：检测中断恢复消息。
                    if (event instanceof AgentResultEvent result) {
                        Msg msg = result.getResult();
                        if (msg != null && msg.getGenerateReason() == GenerateReason.INTERRUPTED) {
                            transitionTerminal(handle,
                                    AgentExecutionReference.ExecutionStatus.CANCELLED);
                        } else {
                            transitionTerminal(handle,
                                    AgentExecutionReference.ExecutionStatus.COMPLETED);
                        }
                    }
                })
                .doOnComplete(() -> {
                    handle.publisher.close();
                    if (handle.status == AgentExecutionReference.ExecutionStatus.STARTED
                            || handle.status == AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED) {
                        // 正常流结束且未收到结果事件：视为完成（流内无结果事件时兜底）。
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.COMPLETED);
                    }
                    TraceSupport.end(trace);
                    log.info("Task {} 事件流结束（taskAttemptId={}, agentId={}）",
                            request.taskId(), taskAttemptId, agentId);
                })
                .doOnError(error -> {
                    if (isInterrupt(error)) {
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.CANCELLED);
                    } else {
                        handle.failure = error;
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.FAILED);
                        log.warn("Task {} 执行失败: {}", request.taskId(), error.toString());
                    }
                    TraceSupport.end(trace);
                    handle.publisher.closeExceptionally(error);
                })
                .subscribe();

        AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                request.taskId(), taskAttemptId, agentId, traceId,
                AgentExecutionReference.ExecutionStatus.STARTED,
                request.userId(), request.sessionId());
        log.info("Task {} 已异步启动（taskAttemptId={}, agentId={}, traceId={}）",
                request.taskId(), taskAttemptId, agentId, traceId);
        return ref;
    }

    @Override
    public void cancelExecution(AgentExecutionReference reference) {
        ExecutionHandle handle = handlesByAttemptId.get(reference.taskAttemptId());
        if (handle == null) {
            throw new IllegalStateException("Task " + reference.taskId() + " 不存在可取消的执行");
        }
        // P1-5：只置 CANCEL_REQUESTED 并触发官方 interrupt；
        // 终态 CANCELLED 必须由真实中断事件确认，不得在此自行标记。
        if (handle.status == AgentExecutionReference.ExecutionStatus.COMPLETED
                || handle.status == AgentExecutionReference.ExecutionStatus.FAILED
                || handle.status == AgentExecutionReference.ExecutionStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 已进入终态 " + handle.status + "，不可取消");
        }
        handle.status = AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED;
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(handle.request.sessionId())
                .userId(handle.request.userId())
                .build();
        handle.agent.getDelegate().interrupt(ctx);
        log.info("Task {} 已请求取消（等待真实中断事件确认）", reference.taskId());
    }

    @Override
    public AgentExecutionReference resumeExecution(
            AgentExecutionReference reference, String resumeReason) {
        if (stateStore == null) {
            throw new IllegalStateException(
                    "resumeExecution 需要官方 AgentStateStore；当前 Adapter 未配置 State Store");
        }

        // P0-3：resume 前同样经 SecretResolver 解析并重新注册模型
        // （模拟 ModelRegistry 重新初始化后不依赖静态注册残留，P1-6）。
        String apiKey = secretResolver.resolve(config.apiKeyReference());
        ModelAssembler.registerOpenAiCompatibleModel(config.modelId(), config.baseUrl(), apiKey);

        // 同 (userId, sessionId) + 同 store 重建 Agent，恢复持久化会话上下文（官方语义）。
        HarnessAgent resumed = buildSecureAgent();
        String newAgentId = resumed.getAgentId();
        String newTaskAttemptId = TaskAttemptId.newUuidV7();
        // 业务 Task 标识沿用原引用的 taskId（贯穿全部 Attempt）。
        AgentExecutionRequest resumedRequest = new AgentExecutionRequest(
                reference.taskId(), reference.userId(), reference.sessionId(),
                "", "", List.of("继续之前的会话"));
        SubmissionPublisher<AgentEventEnvelope> publisher = new SubmissionPublisher<>();
        ExecutionHandle handle = new ExecutionHandle(
                resumedRequest, resumed, newTaskAttemptId, newAgentId, publisher);
        handlesByAttemptId.put(newTaskAttemptId, handle);
        attemptIdByTaskId.put(resumedRequest.taskId(), newTaskAttemptId);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(reference.sessionId())
                .userId(reference.userId())
                .build();
        List<Msg> messages = List.of(new UserMessage("继续之前的会话"));
        // P1-4：resume 同样采集真实 OTel Trace ID。
        TraceSupport.TraceHandle trace = TraceSupport.startAndCaptureTraceId();
        handle.disposable = resumed.streamEvents(messages, ctx)
                .doOnNext(event -> {
                    AgentEventEnvelope envelope = mapEvent(handle, event);
                    handle.publisher.submit(envelope);
                    if (event instanceof AgentResultEvent result) {
                        Msg msg = result.getResult();
                        if (msg != null && msg.getGenerateReason() == GenerateReason.INTERRUPTED) {
                            transitionTerminal(handle,
                                    AgentExecutionReference.ExecutionStatus.CANCELLED);
                        } else {
                            transitionTerminal(handle,
                                    AgentExecutionReference.ExecutionStatus.COMPLETED);
                        }
                    }
                })
                .doOnComplete(() -> {
                    handle.publisher.close();
                    if (handle.status == AgentExecutionReference.ExecutionStatus.STARTED
                            || handle.status == AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED) {
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.COMPLETED);
                    }
                    TraceSupport.end(trace);
                })
                .doOnError(error -> {
                    if (isInterrupt(error)) {
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.CANCELLED);
                    } else {
                        handle.failure = error;
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.FAILED);
                    }
                    TraceSupport.end(trace);
                    handle.publisher.closeExceptionally(error);
                })
                .subscribe();

        log.info("Task {} 已基于 State Store 恢复（原因={}，新 agentId={}，traceId={}）",
                reference.taskId(), resumeReason, newAgentId, trace.traceId());
        return reference.nextAttempt(newTaskAttemptId, newAgentId,
                        AgentExecutionReference.ExecutionStatus.STARTED)
                .withTraceId(trace.traceId());
    }

    @Override
    public Flow.Publisher<AgentEventEnvelope> streamExecutionEvents(AgentExecutionReference reference) {
        ExecutionHandle handle = handlesByAttemptId.get(reference.taskAttemptId());
        if (handle == null) {
            throw new IllegalStateException("Task " + reference.taskId() + " 不存在可流式读取的执行");
        }
        // P0-2/P1-8：返回执行前已建立的 Publisher，绝不发起第二次执行。
        return handle.publisher;
    }

    /**
     * 构建安全 Agent：显式禁用全部危险能力（P0-1）。
     */
    private HarnessAgent buildSecureAgent() {
        return HarnessAgent.builder()
                .name("nexus-edge-compat-agent")
                .sysPrompt(config.systemPrompt())
                .model(config.modelId())
                .workspace(Path.of(config.workspacePath()))
                .toolkit(toolkit)
                .stateStore(stateStore)
                .enableMetaTool(false)
                // P0-1：禁用默认文件系统/Shell/subagent/skill 能力。
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .build();
    }

    /**
     * 暴露构建后 Agent 的真实工具面（供测试断言，P0-1）。
     *
     * @param taskId 业务 Task 标识
     */
    public Set<String> toolSurface(String taskId) {
        String attemptId = attemptIdByTaskId.get(taskId);
        ExecutionHandle handle = attemptId == null ? null : handlesByAttemptId.get(attemptId);
        if (handle == null) {
            return Set.of();
        }
        return handle.agent.getToolkit().getToolNames();
    }

    /**
     * 互斥终态转移（P1-5）：STARTED/CANCEL_REQUESTED 之外的终态不允许再改变。
     */
    private static void transitionTerminal(
            ExecutionHandle handle, AgentExecutionReference.ExecutionStatus terminal) {
        synchronized (handle) {
            if (handle.status == AgentExecutionReference.ExecutionStatus.COMPLETED
                    || handle.status == AgentExecutionReference.ExecutionStatus.FAILED
                    || handle.status == AgentExecutionReference.ExecutionStatus.CANCELLED) {
                return; // 已进入终态，禁止覆盖。
            }
            handle.status = terminal;
        }
    }

    private static boolean isInterrupt(Throwable error) {
        return error instanceof InterruptedException
                || error.getMessage() != null
                        && error.getMessage().toLowerCase().contains("interrupt");
    }

    /**
     * 把官方 AgentScope Typed Event 映射为业务安全事件（隐藏思维链、Secret 永不外泄）。
     * eventId 为官方事件 id，供标识；Last-Event-ID 续传属后续持久化层（P1-8）。
     */
    private AgentEventEnvelope mapEvent(ExecutionHandle handle, AgentEvent event) {
        AgentEventEnvelope.AgentEventType type = switch (event.getType()) {
            case AGENT_START -> AgentEventEnvelope.AgentEventType.STARTED;
            case TOOL_CALL_START -> AgentEventEnvelope.AgentEventType.TOOL_STARTED;
            case TOOL_CALL_END -> AgentEventEnvelope.AgentEventType.TOOL_COMPLETED;
            case AGENT_END, AGENT_RESULT -> AgentEventEnvelope.AgentEventType.COMPLETED;
            default -> AgentEventEnvelope.AgentEventType.PROGRESS;
        };
        // taskId = 业务 Task 标识（request.taskId）；executionId = AgentScope Agent 标识。
        return new AgentEventEnvelope(
                handle.request.taskId(),
                type,
                handle.agentId,
                summarize(event),
                event.getId());
    }

    private static String summarize(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta) {
            return "文本增量: " + delta.getDelta();
        }
        if (event instanceof ToolCallStartEvent tool) {
            return "工具调用: " + tool.getToolCallName();
        }
        if (event instanceof io.agentscope.core.event.AgentEndEvent end) {
            return "执行结束（replyId=" + end.getReplyId() + "）";
        }
        return "事件: " + event.getType();
    }

    /** 当前执行状态快照（按业务 Task 标识查询）。 */
    public AgentExecutionReference.ExecutionStatus statusOf(String taskId) {
        String attemptId = attemptIdByTaskId.get(taskId);
        ExecutionHandle handle = attemptId == null ? null : handlesByAttemptId.get(attemptId);
        return handle == null ? null : handle.status;
    }

    @Override
    public void close() {
        handlesByAttemptId.values().forEach(handle -> {
            if (handle.disposable != null) {
                handle.disposable.dispose();
            }
            handle.publisher.close();
            handle.agent.close();
        });
        handlesByAttemptId.clear();
        attemptIdByTaskId.clear();
    }

    /** 一次执行的生命周期句柄（内部状态，非公共契约）。 */
    private static final class ExecutionHandle {
        final AgentExecutionRequest request;
        final HarnessAgent agent;
        final String taskAttemptId;
        final String agentId;
        final SubmissionPublisher<AgentEventEnvelope> publisher;
        volatile AgentExecutionReference.ExecutionStatus status;
        volatile Throwable failure;
        volatile Disposable disposable;

        ExecutionHandle(AgentExecutionRequest request, HarnessAgent agent,
                        String taskAttemptId, String agentId,
                        SubmissionPublisher<AgentEventEnvelope> publisher) {
            this.request = request;
            this.agent = agent;
            this.taskAttemptId = taskAttemptId;
            this.agentId = agentId;
            this.publisher = publisher;
            this.status = AgentExecutionReference.ExecutionStatus.STARTED;
        }
    }
}
