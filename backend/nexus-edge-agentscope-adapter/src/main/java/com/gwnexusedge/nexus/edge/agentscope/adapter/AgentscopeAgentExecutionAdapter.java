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
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
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
 * 启动一次 {@code streamEvents} 订阅，把事件写入执行前建立的 {@link EventStreams}（replay 语义，
 * 延迟订阅不丢事件）；{@code streamExecutionEvents} 返回同一 EventStreams，绝不发起第二次执行。
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

        // P0-1：为本次执行创建独立 OTel span（并发隔离，不 makeCurrent 于调用线程），
        // traceId 在 span 创建时即确定；span 上下文经 Reactor Context 传播到异步执行链。
        TraceSupport.TraceHandle trace = TraceSupport.start();
        String traceId = trace.traceId();

        // P0-2：事件源必须在执行开始前建立（replay 语义，延迟订阅不丢事件）。
        EventStreams<AgentEventEnvelope> eventStreams = EventStreams.replayBounded();

        // P0-1：把 workspaceId/tenantId 作为可持久恢复的执行上下文保存到 State Store，
        // 键按 Task 隔离（含 taskId），禁止同 user/session 不同 Task 覆盖；
        // resume 时从 Store 恢复，不依赖 Request 自证。
        if (stateStore != null) {
            stateStore.save(request.userId(), request.sessionId(),
                    ExecutionContextState.storeKey(request.taskId()),
                    new ExecutionContextState(request.taskId(),
                            request.workspaceId(), request.tenantId(),
                            request.userId(), request.sessionId()));
        }

        // P1-9：RuntimeContext 注入 workspaceId/tenantId（可观测与审计上下文）。
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(request.sessionId())
                .userId(request.userId())
                .put("workspaceId", request.workspaceId())
                .put("tenantId", request.tenantId())
                .build();

        ExecutionHandle handle = new ExecutionHandle(
                request, agent, taskAttemptId, agentId, eventStreams, ctx);
        // handles 以 taskAttemptId 为键；业务 Task 标识 → taskAttemptId 映射供按 Task 查询。
        handlesByAttemptId.put(taskAttemptId, handle);
        attemptIdByTaskId.put(request.taskId(), taskAttemptId);

        List<Msg> messages = request.messages().stream()
                .map(UserMessage::new)
                .map(Msg.class::cast)
                .toList();

        // 单一执行源（P0-2）：唯一一次 streamEvents 订阅，同时完成事件推送/状态/结果/失败/取消。
        handle.disposable = agent.streamEvents(messages, ctx)
                // P0-1：把本次执行的 OTel span 注入 Reactor Context，由 AgentScope
                // ContextPropagationOperator 在异步链中传播（并发 Task 隔离）。
                .contextWrite(reactorCtx ->
                        ContextPropagationOperator.storeOpenTelemetryContext(
                                reactorCtx, io.opentelemetry.context.Context.current().with(trace.span())))
                .doOnNext(event -> {
                    AgentEventEnvelope envelope = mapEvent(handle, event);
                    handle.eventStreams.emit(envelope);
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
                    handle.eventStreams.complete();
                    // P1-元数据误判修正：流结束兜底时区分取消与正常完成。
                    // CANCEL_REQUESTED 未收到中断异常/恢复消息即结束 → CANCELLED（非 COMPLETED）。
                    if (handle.status == AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED) {
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.CANCELLED);
                    } else if (handle.status == AgentExecutionReference.ExecutionStatus.STARTED) {
                        // 正常流结束且未收到结果事件：视为完成（流内无结果事件时兜底）。
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.COMPLETED);
                    }
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
                    handle.eventStreams.completeExceptionally(error);
                })
                // 第四轮：doFinally 幂等结束 span，覆盖 complete/error/cancel/dispose。
                .doFinally(signal -> TraceSupport.end(trace))
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
        // P0-3：cancel 的 RuntimeContext 必须携带原 Task 的 workspaceId/tenantId
        // （复用 handle 保存的执行上下文，保证 interrupt 定位同一会话）。
        RuntimeContext ctx = handle.runtimeContext;
        if (ctx == null) {
            ctx = RuntimeContext.builder()
                    .sessionId(handle.request.sessionId())
                    .userId(handle.request.userId())
                    .build();
        }
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

        // P0-1/P0-2：resume 从 State Store 按 Task 隔离键恢复 workspaceId/tenantId；
        // 恢复不到上下文、Workspace 或 Tenant 时 fail-closed（抛异常），
        // 不得用空字符串继续执行。
        ExecutionContextState restored = null;
        if (stateStore != null) {
            restored = stateStore.get(reference.userId(), reference.sessionId(),
                            ExecutionContextState.storeKey(reference.taskId()),
                            ExecutionContextState.class)
                    .orElse(null);
        }
        if (restored == null) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：State Store 中无执行上下文（fail-closed）");
        }
        if (restored.getWorkspaceId() == null || restored.getWorkspaceId().isBlank()) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：执行上下文缺少 workspaceId（fail-closed）");
        }
        if (restored.getTenantId() == null || restored.getTenantId().isBlank()) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：执行上下文缺少 tenantId（fail-closed）");
        }
        String workspaceId = restored.getWorkspaceId();
        String tenantId = restored.getTenantId();
        // 业务 Task 标识沿用原引用的 taskId（贯穿全部 Attempt）；上下文由 Store 恢复。
        AgentExecutionRequest resumedRequest = new AgentExecutionRequest(
                reference.taskId(), reference.userId(), reference.sessionId(),
                workspaceId, tenantId, List.of("继续之前的会话"));
        EventStreams<AgentEventEnvelope> eventStreams = EventStreams.replayBounded();
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(reference.sessionId())
                .userId(reference.userId())
                .put("workspaceId", workspaceId)
                .put("tenantId", tenantId)
                .build();
        ExecutionHandle handle = new ExecutionHandle(
                resumedRequest, resumed, newTaskAttemptId, newAgentId, eventStreams, ctx);
        handlesByAttemptId.put(newTaskAttemptId, handle);
        attemptIdByTaskId.put(resumedRequest.taskId(), newTaskAttemptId);

        List<Msg> messages = List.of(new UserMessage("继续之前的会话"));
        // P0-1：resume 同样为独立 span，经 Reactor Context 传播。
        TraceSupport.TraceHandle trace = TraceSupport.start();
        handle.disposable = resumed.streamEvents(messages, ctx)
                .contextWrite(reactorCtx ->
                        ContextPropagationOperator.storeOpenTelemetryContext(
                                reactorCtx, io.opentelemetry.context.Context.current().with(trace.span())))
                .doOnNext(event -> {
                    AgentEventEnvelope envelope = mapEvent(handle, event);
                    handle.eventStreams.emit(envelope);
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
                    handle.eventStreams.complete();
                    // P1-元数据误判修正：与 startExecution 一致——取消请求未收到中断确认即结束 → CANCELLED。
                    if (handle.status == AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED) {
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.CANCELLED);
                    } else if (handle.status == AgentExecutionReference.ExecutionStatus.STARTED) {
                        transitionTerminal(handle,
                                AgentExecutionReference.ExecutionStatus.COMPLETED);
                    }
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
                    handle.eventStreams.completeExceptionally(error);
                })
                // 第四轮：doFinally 幂等结束 span，覆盖 complete/error/cancel/dispose。
                .doFinally(signal -> TraceSupport.end(trace))
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
        // P0-2：返回执行前已建立的 replay 事件源（Flow.Publisher 视图），绝不发起第二次执行。
        return handle.eventStreams.asFlowPublisher();
    }

    /**
     * 构建安全 Agent：显式禁用全部危险能力（P0-1/P1-Allowlist）。
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
                // 第四轮：注册官方 OtelTracingMiddleware，使 AgentScope 的执行 span
                // （invoke_agent <name>）成为本 Adapter span 的子 span（父子关联验证）。
                .middleware(new io.agentscope.core.tracing.OtelTracingMiddleware())
                // P0-1：禁用默认文件系统/Shell/subagent/skill 能力。
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                // P1-Allowlist：业务 Agent 不授予默认 memory/session 工具，
                // 工具面仅为显式白名单（toolkit 中注册的工具）。
                .disableMemoryTools()
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
     * 暴露执行注入的 Workspace/Tenant 上下文（第四轮：从 AgentScope RuntimeContext 读取，
     * 非 Request 自证）。
     *
     * @param taskId 业务 Task 标识
     * @return AgentScope RuntimeContext extras 中的 workspaceId/tenantId
     */
    public java.util.Map<String, String> executionContext(String taskId) {
        String attemptId = attemptIdByTaskId.get(taskId);
        ExecutionHandle handle = attemptId == null ? null : handlesByAttemptId.get(attemptId);
        if (handle == null || handle.runtimeContext == null) {
            return java.util.Map.of();
        }
        Object ws = handle.runtimeContext.getExtra().get("workspaceId");
        Object tn = handle.runtimeContext.getExtra().get("tenantId");
        return java.util.Map.of(
                "workspaceId", ws == null ? "" : String.valueOf(ws),
                "tenantId", tn == null ? "" : String.valueOf(tn));
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
     *
     * <p>单一终态（第四轮 + 第五轮 P0-4）：仅 {@code AGENT_RESULT} 映射业务终态事件，
     * 且**根据 GenerateReason 决定类型**——{@code INTERRUPTED} → CANCELLED，
     * 其他 → COMPLETED；{@code AGENT_END} 映射 PROGRESS（仅标记流结束）。
     * 业务事件终态与 Task 状态（transitionTerminal）保持一致。
     */
    private AgentEventEnvelope mapEvent(ExecutionHandle handle, AgentEvent event) {
        AgentEventEnvelope.AgentEventType type;
        if (event instanceof AgentResultEvent result) {
            // P0-4：AGENT_RESULT 根据 GenerateReason 映射 COMPLETED 或 CANCELLED。
            Msg msg = result.getResult();
            boolean interrupted = msg != null && msg.getGenerateReason() == GenerateReason.INTERRUPTED;
            type = interrupted
                    ? AgentEventEnvelope.AgentEventType.CANCELLED
                    : AgentEventEnvelope.AgentEventType.COMPLETED;
        } else {
            type = switch (event.getType()) {
                case AGENT_START -> AgentEventEnvelope.AgentEventType.STARTED;
                case TOOL_CALL_START -> AgentEventEnvelope.AgentEventType.TOOL_STARTED;
                case TOOL_CALL_END -> AgentEventEnvelope.AgentEventType.TOOL_COMPLETED;
                // AGENT_END 仅标记流结束，不作为业务终态（避免双终态事件）。
                case AGENT_END -> AgentEventEnvelope.AgentEventType.PROGRESS;
                default -> AgentEventEnvelope.AgentEventType.PROGRESS;
            };
        }
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
            handle.eventStreams.complete();
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
        final EventStreams<AgentEventEnvelope> eventStreams;
        final RuntimeContext runtimeContext;
        volatile AgentExecutionReference.ExecutionStatus status;
        volatile Throwable failure;
        volatile Disposable disposable;

        ExecutionHandle(AgentExecutionRequest request, HarnessAgent agent,
                        String taskAttemptId, String agentId,
                        EventStreams<AgentEventEnvelope> eventStreams,
                        RuntimeContext runtimeContext) {
            this.request = request;
            this.agent = agent;
            this.taskAttemptId = taskAttemptId;
            this.agentId = agentId;
            this.eventStreams = eventStreams;
            this.runtimeContext = runtimeContext;
            this.status = AgentExecutionReference.ExecutionStatus.STARTED;
        }
    }
}
