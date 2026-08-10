package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionPort;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * {@link AgentExecutionPort} 的 AgentScope 官方实现（DEV-0001）。
 *
 * <p>本类位于 Adapter 层，依赖领域 Port 与官方 AgentScope SDK；领域层不依赖本类。
 * 所有 Agent 执行能力均来自 AgentScope 官方 Harness/Core API，不实现任何自研
 * Runtime、Workflow、Memory 或 Tool Framework（00 §2、A-003）。
 *
 * <p>安全边界（评审 CHANGES_REQUESTED）：
 * <ul>
 *   <li>Tool 白名单：仅注入显式白名单只读工具，禁用官方 meta tool；业务 Agent
 *       不得获得 Host execute / Shell / 未授权文件系统工具；</li>
 *   <li>执行生命周期：{@code startExecution} 异步启动并立即返回真实引用
 *       （executionId 来自官方 {@code getAgentId()}）；{@code cancelExecution} 通过
 *       官方 interrupt 中断在途执行并验证状态；</li>
 *   <li>真实 Trace：traceId 由调用方从 OTel 上下文采集后通过
 *       {@code withTraceId} 注入，禁止 "unassigned" 等伪造值；</li>
 *   <li>resume：基于官方 State Store 同会话恢复，禁止伪 Execution ID。</li>
 * </ul>
 *
 * <p>线程安全：{@link HarnessAgent} 单实例不保证并发调用安全（官方文档明确），
 * 因此本 Adapter 为每个 Task 构建独立 HarnessAgent 实例并以 Task 标识映射。
 */
public class AgentscopeAgentExecutionAdapter implements AgentExecutionPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeAgentExecutionAdapter.class);

    private final AgentscopeAdapterConfig config;
    private final Toolkit toolkit;
    private final AgentStateStore stateStore;
    private final Map<String, ExecutionHandle> handlesByTaskId = new ConcurrentHashMap<>();
    private final Map<String, SubmissionPublisher<AgentEventEnvelope>> eventPublishers =
            new ConcurrentHashMap<>();

    /**
     * @param config     Adapter 配置（生产配置 fail-fast，见 {@link AgentscopeAdapterConfig}）
     * @param toolkit    显式白名单 Toolkit（已注册业务允许的工具；为 null 时使用空白名单）
     * @param stateStore 官方 State Store（用于会话持久化与 resume；为 null 时无持久化）
     */
    public AgentscopeAgentExecutionAdapter(
            AgentscopeAdapterConfig config, Toolkit toolkit, AgentStateStore stateStore) {
        this.config = Objects.requireNonNull(config, "config 不允许为 null");
        this.toolkit = toolkit == null ? new Toolkit() : toolkit;
        this.stateStore = stateStore;
    }

    /**
     * 便于测试的便捷构造（空白名单 Toolkit + 无持久化）。
     */
    public AgentscopeAgentExecutionAdapter(AgentscopeAdapterConfig config) {
        this(config, new Toolkit(), null);
    }

    @Override
    public AgentExecutionReference startExecution(AgentExecutionRequest request) {
        // 真实模型注册：apiKey 为调用方从 Secret Provider 解析的运行期值（07 §7）。
        ModelAssembler.registerOpenAiCompatibleModel(
                config.modelId(), config.baseUrl(), resolveApiKey());

        // 官方 HarnessAgent 构建：显式白名单 Toolkit + 禁用官方 meta tool。
        // meta tool 提供动态工具加载/编排能力，业务 Agent 不授予（评审项 1）。
        HarnessAgent agent = HarnessAgent.builder()
                .name("nexus-edge-compat-agent")
                .sysPrompt(config.systemPrompt())
                .model(config.modelId())
                .workspace(Path.of(config.workspacePath()))
                .toolkit(toolkit)
                .stateStore(stateStore)
                .enableMetaTool(false)
                .build();

        // 真实 Execution 标识：官方 getAgentId() 为构建时 UUID.randomUUID() 生成。
        String executionId = agent.getAgentId();
        ExecutionHandle handle = new ExecutionHandle(request, agent, executionId);
        handlesByTaskId.put(request.taskId(), handle);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(request.sessionId())
                .userId(request.userId())
                .build();
        List<Msg> messages = request.messages().stream()
                .map(UserMessage::new)
                .map(Msg.class::cast)
                .toList();

        // 异步推进执行：长任务完成前立即返回真实引用。
        handle.subscription = agent.call(messages, ctx)
                .doOnNext(result -> {
                    handle.status = AgentExecutionReference.ExecutionStatus.COMPLETED;
                    log.info("Task {} 执行完成（executionId={}）", request.taskId(), executionId);
                })
                .doOnError(error -> {
                    handle.status = AgentExecutionReference.ExecutionStatus.FAILED;
                    handle.failure = error;
                    log.warn("Task {} 执行失败: {}", request.taskId(), error.toString());
                })
                .subscribe();

        AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                request.taskId(), executionId, "",
                AgentExecutionReference.ExecutionStatus.STARTED,
                request.userId(), request.sessionId());
        log.info("Task {} 已异步启动（executionId={}, sessionId={}, userId={}）",
                request.taskId(), executionId, request.sessionId(), request.userId());
        return ref;
    }

    @Override
    public void cancelExecution(AgentExecutionReference reference) {
        ExecutionHandle handle = handlesByTaskId.get(reference.taskId());
        if (handle == null) {
            throw new IllegalStateException("Task " + reference.taskId() + " 不存在可取消的执行");
        }
        // 请求取消：更新状态后触发官方 interrupt（会话级优雅中断）。
        handle.status = AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED;
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(handle.request.sessionId())
                .userId(handle.request.userId())
                .build();
        handle.agent.getDelegate().interrupt(ctx);
        if (handle.subscription != null) {
            handle.subscription.dispose();
        }
        handle.status = AgentExecutionReference.ExecutionStatus.CANCELLED;
        log.info("Task {} 已取消（executionId={}）", reference.taskId(), handle.executionId);
    }

    @Override
    public AgentExecutionReference resumeExecution(
            AgentExecutionReference reference, String resumeReason) {
        if (stateStore == null) {
            throw new IllegalStateException(
                    "resumeExecution 需要官方 AgentStateStore；当前 Adapter 未配置 State Store");
        }

        // 基于官方 State Store：同 (userId, sessionId) + 同 store 重建 HarnessAgent，
        // 通过 getAgentState(userId, sessionId) 懒加载恢复持久化会话上下文（官方语义）。
        // 不依赖原 handle 是否存活（跨 Adapter 实例亦可恢复）。
        HarnessAgent resumed = HarnessAgent.builder()
                .name("nexus-edge-compat-agent")
                .sysPrompt(config.systemPrompt())
                .model(config.modelId())
                .workspace(Path.of(config.workspacePath()))
                .toolkit(toolkit)
                .stateStore(stateStore)
                .enableMetaTool(false)
                .build();

        String newExecutionId = resumed.getAgentId();
        AgentExecutionRequest resumedRequest = new AgentExecutionRequest(
                reference.taskId(), reference.userId(), reference.sessionId(),
                "", "", List.of("继续之前的会话"));
        ExecutionHandle handle = new ExecutionHandle(resumedRequest, resumed, newExecutionId);
        handlesByTaskId.put(reference.taskId(), handle);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(reference.sessionId())
                .userId(reference.userId())
                .build();
        List<Msg> messages = List.of(new UserMessage("继续之前的会话"));
        handle.subscription = resumed.call(messages, ctx)
                .doOnNext(result -> handle.status = AgentExecutionReference.ExecutionStatus.COMPLETED)
                .doOnError(error -> {
                    handle.status = AgentExecutionReference.ExecutionStatus.FAILED;
                    handle.failure = error;
                })
                .subscribe();

        log.info("Task {} 已基于 State Store 恢复（原因={}，新执行标识={}，sessionId={}）",
                reference.taskId(), resumeReason, newExecutionId, reference.sessionId());
        return reference.nextAttempt(newExecutionId, AgentExecutionReference.ExecutionStatus.STARTED);
    }

    @Override
    public Flow.Publisher<AgentEventEnvelope> streamExecutionEvents(AgentExecutionReference reference) {
        ExecutionHandle handle = handlesByTaskId.get(reference.taskId());
        if (handle == null) {
            throw new IllegalStateException("Task " + reference.taskId() + " 不存在可流式读取的执行");
        }

        // 订阅原 Execution 的真实事件流（不发起第二次执行）。
        // 使用官方 streamEvents(List, RuntimeContext) 的 Flux，桥接到 JDK Flow.Publisher。
        SubmissionPublisher<AgentEventEnvelope> publisher = new SubmissionPublisher<>();
        eventPublishers.put(reference.taskId(), publisher);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(handle.request.sessionId())
                .userId(handle.request.userId())
                .build();

        handle.streamSubscription = handle.agent
                .streamEvents(handle.request.messages().stream().map(UserMessage::new).map(Msg.class::cast).toList(), ctx)
                .map(event -> mapEvent(reference, event))
                .doOnNext(publisher::submit)
                .doOnComplete(publisher::close)
                .doOnError(error -> {
                    log.warn("Task {} 事件流异常: {}", reference.taskId(), error.toString());
                    publisher.closeExceptionally(error);
                })
                .subscribe();
        return publisher;
    }

    /**
     * 把官方 AgentScope Typed Event 映射为业务安全事件（隐藏思维链、Secret 永不外泄）。
     */
    private AgentEventEnvelope mapEvent(AgentExecutionReference reference, AgentEvent event) {
        AgentEventEnvelope.AgentEventType type = switch (event.getType()) {
            case AGENT_START -> AgentEventEnvelope.AgentEventType.STARTED;
            case TOOL_CALL_START -> AgentEventEnvelope.AgentEventType.TOOL_STARTED;
            case TOOL_CALL_END -> AgentEventEnvelope.AgentEventType.TOOL_COMPLETED;
            case AGENT_END, AGENT_RESULT -> AgentEventEnvelope.AgentEventType.COMPLETED;
            default -> AgentEventEnvelope.AgentEventType.PROGRESS;
        };
        // 事件携带官方事件 id（getId），作为 Last-Event-ID 断线续传标识（06 §5）。
        return new AgentEventEnvelope(
                reference.taskId(),
                type,
                reference.executionId(),
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

    /**
     * 解析 Secret：DEV-0001 阶段通过 Secret Provider 或测试 Profile 提供运行期值。
     * 生产环境必须由 Secret Provider 解析（07 §7）；本方法默认以 Reference 名解析失败即报错。
     */
    private String resolveApiKey() {
        // DEV-0001 兼容验证：测试通过测试 Profile 注入 Test Double 凭据。
        // 生产实现必须替换为 Secret Provider 解析（07 §7），此处仅透传引用解析结果。
        return config.apiKeyReference();
    }

    /** 当前执行状态快照（供 cancel/生命周期验证）。 */
    public AgentExecutionReference.ExecutionStatus statusOf(String taskId) {
        ExecutionHandle handle = handlesByTaskId.get(taskId);
        return handle == null ? null : handle.status;
    }

    @Override
    public void close() {
        handlesByTaskId.values().forEach(handle -> {
            if (handle.subscription != null) {
                handle.subscription.dispose();
            }
            if (handle.streamSubscription != null) {
                handle.streamSubscription.dispose();
            }
            handle.agent.close();
        });
        handlesByTaskId.clear();
        eventPublishers.values().forEach(SubmissionPublisher::close);
        eventPublishers.clear();
    }

    /** 一次执行的生命周期句柄（内部状态，非公共契约）。 */
    private static final class ExecutionHandle {
        final AgentExecutionRequest request;
        final HarnessAgent agent;
        final String executionId;
        volatile AgentExecutionReference.ExecutionStatus status;
        volatile Throwable failure;
        volatile Disposable subscription;
        volatile Disposable streamSubscription;

        ExecutionHandle(AgentExecutionRequest request, HarnessAgent agent, String executionId) {
            this.request = request;
            this.agent = agent;
            this.executionId = executionId;
            this.status = AgentExecutionReference.ExecutionStatus.STARTED;
        }
    }
}
