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
 * {@link AgentExecutionPort} 的 AgentScope 官方实现（第七轮修正）。
 *
 * <p>安全边界（P0-1）：业务 Agent 构建时显式禁用全部危险能力——
 * {@code disableFilesystemTools / disableShellTool / disableSubagents /
 * disableDynamicSubagents / disableDynamicSkills / disableDefaultWorkspaceSkills /
 * disableMemoryTools / disableMemoryHooks}，并在构建后通过 {@link #toolSurface()}
 * 暴露真实工具面供测试断言。业务 Agent 不得获得 Host execute / 未授权文件系统 /
 * subagent / workspace skill / 自动长期记忆能力。
 *
 * <p>单一执行源（P0-2）：{@code streamEvents()} 是唯一执行源。{@code startExecution}
 * 启动一次 {@code streamEvents} 订阅，把事件写入执行前建立的 {@link EventStreams}（replay 语义，
 * 延迟订阅不丢事件）；{@code streamExecutionEvents} 返回同一 EventStreams，绝不发起第二次执行。
 *
 * <p>Secret 边界（P0-3）：Adapter 只持有 Secret Reference，经 {@link SecretResolver}
 * 在运行期解析临时值；不保存、不记录明文。main 生产装配不提供伪实现。
 *
 * <p>长期 Runtime Identity（P0-1/P0-4）：业务 Tenant/Workspace/User/Session 经
 * {@link AgentRuntimeIdentityMapper} 映射为稳定、无碰撞、路径安全的 AgentScope
 * {@code scopedUserId}/{@code scopedSessionId}（AgentState/Memory 键控隔离）；
 * 原始业务标识保留在 RuntimeContext extras（workspaceId/tenantId/sessionId）。
 * 当前业务 Agent 不允许自动长期记忆：{@code disableMemoryHooks} 显式禁用 Memory
 * Flush/Consolidation Hooks（边界见 ADR-0006），禁止用临时方案掩盖。
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

        // 构建 Agent：显式禁用全部危险能力 + Memory Hooks（P0-1）。
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

        // P0-1/P0-4：业务标识映射为 AgentScope 长期 Runtime Identity（稳定/无碰撞/路径安全），
        // 使 AgentScope AgentState/Memory 按 Tenant/Workspace/User 隔离；
        // 原始业务标识保留在 RuntimeContext extras。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                request.tenantId(), request.workspaceId(),
                request.userId(), request.sessionId());
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(scoped.scopedUserId())
                .sessionId(scoped.scopedSessionId())
                .put("workspaceId", request.workspaceId())
                .put("tenantId", request.tenantId())
                .put("sessionId", request.sessionId())
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
                .doOnNext(event -> onAgentEvent(handle, event))
                .doOnComplete(() -> onStreamComplete(handle))
                .doOnError(error -> onStreamError(handle, error))
                // onStreamError 已完整处理（状态/事件/事件流结束）；onErrorResume 吞掉
                // 继续传播的错误，避免 terminal subscribe 产生 onErrorDropped 噪声。
                .onErrorResume(error -> reactor.core.publisher.Flux.empty())
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
        // P0-3：cancel 的 RuntimeContext 必须携带原 Task 的 scoped Identity + 业务 extras
        // （复用 handle 保存的执行上下文，保证 interrupt 定位同一会话）。
        RuntimeContext ctx = handle.runtimeContext;
        if (ctx == null) {
            // 防御性重建（正常流程不会进入；使用同一 Mapper 保持隔离语义）。
            AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                    handle.request.tenantId(), handle.request.workspaceId(),
                    handle.request.userId(), handle.request.sessionId());
            ctx = RuntimeContext.builder()
                    .userId(scoped.scopedUserId())
                    .sessionId(scoped.scopedSessionId())
                    .put("workspaceId", handle.request.workspaceId())
                    .put("tenantId", handle.request.tenantId())
                    .put("sessionId", handle.request.sessionId())
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

        // P1-2/P1-1：先读取并完整校验持久化上下文，再解析 Secret/注册模型/创建 Agent——
        // 失败路径不遗留未关闭 Agent；引用与恢复内容逐项比较（taskId/userId/sessionId），
        // 内容错绑或篡改时 fail-closed。
        ExecutionContextState restored = stateStore.get(reference.userId(), reference.sessionId(),
                        ExecutionContextState.storeKey(reference.taskId()),
                        ExecutionContextState.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Task " + reference.taskId() + " 恢复失败：State Store 中无执行上下文（fail-closed）"));
        if (restored.getWorkspaceId() == null || restored.getWorkspaceId().isBlank()) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：执行上下文缺少 workspaceId（fail-closed）");
        }
        if (restored.getTenantId() == null || restored.getTenantId().isBlank()) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：执行上下文缺少 tenantId（fail-closed）");
        }
        if (reference.taskId() == null || reference.taskId().isBlank()
                || reference.userId() == null || reference.userId().isBlank()
                || reference.sessionId() == null || reference.sessionId().isBlank()) {
            throw new IllegalStateException(
                    "resume 失败：引用缺少 taskId/userId/sessionId（fail-closed）");
        }
        // P1-1：恢复内容与引用逐项一致（taskId/userId/sessionId），错绑/篡改 fail-closed。
        if (!reference.taskId().equals(restored.getTaskId())) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：恢复上下文 taskId 与引用不一致"
                            + "（restored=" + restored.getTaskId() + "，fail-closed）");
        }
        if (!reference.userId().equals(restored.getUserId())) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：恢复上下文 userId 与引用不一致"
                            + "（restored=" + restored.getUserId() + "，fail-closed）");
        }
        if (!reference.sessionId().equals(restored.getSessionId())) {
            throw new IllegalStateException(
                    "Task " + reference.taskId() + " 恢复失败：恢复上下文 sessionId 与引用不一致"
                            + "（restored=" + restored.getSessionId() + "，fail-closed）");
        }
        String workspaceId = restored.getWorkspaceId();
        String tenantId = restored.getTenantId();

        // P1-2：校验通过后才解析 Secret、注册模型、创建 Agent（失败无遗留）。
        String apiKey = secretResolver.resolve(config.apiKeyReference());
        ModelAssembler.registerOpenAiCompatibleModel(config.modelId(), config.baseUrl(), apiKey);
        HarnessAgent resumed = buildSecureAgent();
        String newAgentId = resumed.getAgentId();
        String newTaskAttemptId = TaskAttemptId.newUuidV7();

        // 业务 Task 标识沿用原引用的 taskId（贯穿全部 Attempt）；上下文由 Store 恢复。
        AgentExecutionRequest resumedRequest = new AgentExecutionRequest(
                reference.taskId(), reference.userId(), reference.sessionId(),
                workspaceId, tenantId, List.of("继续之前的会话"));
        EventStreams<AgentEventEnvelope> eventStreams = EventStreams.replayBounded();
        // P0-1/P0-4：resume 同样使用长期 Runtime Identity（按恢复的 Tenant/Workspace 隔离），
        // 与 startExecution 的 scoped 标识一致，保证同一会话的历史上下文可恢复。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                tenantId, workspaceId, reference.userId(), reference.sessionId());
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(scoped.scopedUserId())
                .sessionId(scoped.scopedSessionId())
                .put("workspaceId", workspaceId)
                .put("tenantId", tenantId)
                .put("sessionId", reference.sessionId())
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
                .doOnNext(event -> onAgentEvent(handle, event))
                .doOnComplete(() -> onStreamComplete(handle))
                .doOnError(error -> onStreamError(handle, error))
                // onStreamError 已完整处理；onErrorResume 避免 terminal onErrorDropped 噪声。
                .onErrorResume(error -> reactor.core.publisher.Flux.empty())
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
     * 构建安全 Agent：显式禁用全部危险能力 + Memory Hooks（P0-1）。
     *
     * <p>Memory 边界（P0-1，ADR-0006）：当前业务 Agent 不允许自动长期记忆——
     * {@code disableMemoryTools} 只移除长期记忆工具，而 Memory Flush/Consolidation
     * Hooks 仍会按 userId 把会话写入共享的 {@code memory/YYYY-MM-DD.md} 台账（跨
     * Workspace 相同 user 共享，与 scopedSessionId 无关），因此必须同时
     * {@code disableMemoryHooks()}。未来若引入自动长期记忆，必须先经 Runtime Identity
     * 评审（scopedUserId 隔离台账路径）并更新 ADR，禁止用临时方案掩盖。
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
                // P0-1：禁用 Memory Flush/Consolidation Hooks（跨 Workspace 共享台账边界）。
                .disableMemoryHooks()
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
     * 暴露执行注入的业务 Workspace/Tenant 上下文（第四轮：从 AgentScope RuntimeContext
     * extras 读取原始业务标识，非 Request 自证）。
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
     * 处理单个 AgentScope 事件：AgentResult 走终态转移（P1-2），其余映射为业务事件。
     */
    private static void onAgentEvent(ExecutionHandle handle, AgentEvent event) {
        if (event instanceof AgentResultEvent result) {
            onAgentResult(handle, result);
            return;
        }
        handle.eventStreams.emit(mapEvent(handle, event));
    }

    /**
     * P1-2：AgentResult 先幂等终态转移，再发布与最终状态一致的终态事件。
     *
     * <p>若任务已是其他终态（例如已 CANCELLED 后迟到的普通结果），不得覆盖终态、
     * 也不得发布冲突的终态事件——保证每个 TaskAttempt 只出现一个与最终状态一致的
     * 终态事件（取消与迟到结果竞态安全）。
     */
    private static void onAgentResult(ExecutionHandle handle, AgentResultEvent result) {
        Msg msg = result.getResult();
        GenerateReason reason = msg == null ? null : msg.getGenerateReason();
        applyResultReason(handle, reason, summarize(result));
    }

    /**
     * 由结果的 {@link GenerateReason} 决定目标终态并推进（package-private 供竞态测试）。
     *
     * @param reason  {@link GenerateReason#INTERRUPTED} → CANCELLED，其他 → COMPLETED
     * @param summary 终态事件的脱敏摘要
     */
    static void applyResultReason(ExecutionHandle handle, GenerateReason reason, String summary) {
        AgentExecutionReference.ExecutionStatus desired =
                reason == GenerateReason.INTERRUPTED
                        ? AgentExecutionReference.ExecutionStatus.CANCELLED
                        : AgentExecutionReference.ExecutionStatus.COMPLETED;
        if (tryTerminal(handle, desired)) {
            handle.eventStreams.emit(terminalEnvelope(handle, desired, summary));
        }
    }

    /**
     * P1-1/P0-3：流正常完成——若尚未终态则兜底转移（CANCEL_REQUESTED → CANCELLED、
     * STARTED → COMPLETED）并发布对应终态事件，然后结束事件流。
     * （package-private 供竞态测试直接验证。）
     */
    static void onStreamComplete(ExecutionHandle handle) {
        AgentExecutionReference.ExecutionStatus status = handle.status;
        if (status == AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED) {
            if (tryTerminal(handle, AgentExecutionReference.ExecutionStatus.CANCELLED)) {
                handle.eventStreams.emit(terminalEnvelope(handle,
                        AgentExecutionReference.ExecutionStatus.CANCELLED, "执行已取消"));
            }
        } else if (status == AgentExecutionReference.ExecutionStatus.STARTED) {
            if (tryTerminal(handle, AgentExecutionReference.ExecutionStatus.COMPLETED)) {
                handle.eventStreams.emit(terminalEnvelope(handle,
                        AgentExecutionReference.ExecutionStatus.COMPLETED, "执行完成"));
            }
        }
        handle.eventStreams.complete();
        log.info("Task {} 事件流结束（taskAttemptId={}, agentId={}）",
                handle.request.taskId(), handle.taskAttemptId, handle.agentId);
    }

    /**
     * P0-3：错误路径——先完成终态转移（FAILED/CANCELLED），再发布脱敏终态事件
     * （满足 Blueprint {@code task.failed} 契约），然后正常结束事件流。
     *
     * <p>脱敏：FAILED 事件摘要只含异常类型名，不含原始异常消息/Secret；
     * 详细异常仅记录到内部诊断字段与日志。
     */
    private static void onStreamError(ExecutionHandle handle, Throwable error) {
        if (isInterrupt(error)) {
            if (tryTerminal(handle, AgentExecutionReference.ExecutionStatus.CANCELLED)) {
                handle.eventStreams.emit(terminalEnvelope(handle,
                        AgentExecutionReference.ExecutionStatus.CANCELLED, "执行已取消"));
            }
            handle.eventStreams.complete();
            return;
        }
        handle.failure = error;
        if (tryTerminal(handle, AgentExecutionReference.ExecutionStatus.FAILED)) {
            handle.eventStreams.emit(terminalEnvelope(handle,
                    AgentExecutionReference.ExecutionStatus.FAILED,
                    "Agent 执行失败（脱敏）：" + error.getClass().getSimpleName()));
        }
        handle.eventStreams.complete();
        log.warn("Task {} 执行失败（taskAttemptId={}）: {}",
                handle.request.taskId(), handle.taskAttemptId, error.toString());
    }

    /**
     * 幂等终态转移（P1-2）：仅当尚未进入终态时完成转移并返回 true。
     *
     * <p>已终态（COMPLETED/FAILED/CANCELLED）时不改变状态、返回 false——
     * 迟到事件不得覆盖终态，也不得重复发布终态事件。
     */
    private static boolean tryTerminal(ExecutionHandle handle,
                                       AgentExecutionReference.ExecutionStatus terminal) {
        synchronized (handle) {
            if (isTerminal(handle.status)) {
                return false;
            }
            handle.status = terminal;
            return true;
        }
    }

    private static boolean isTerminal(AgentExecutionReference.ExecutionStatus status) {
        return status == AgentExecutionReference.ExecutionStatus.COMPLETED
                || status == AgentExecutionReference.ExecutionStatus.FAILED
                || status == AgentExecutionReference.ExecutionStatus.CANCELLED;
    }

    /**
     * 构建终态业务事件信封（eventId 由 Nexus 生成，UUIDv7）。
     */
    private static AgentEventEnvelope terminalEnvelope(
            ExecutionHandle handle, AgentExecutionReference.ExecutionStatus status, String summary) {
        AgentEventEnvelope.AgentEventType type = switch (status) {
            case COMPLETED -> AgentEventEnvelope.AgentEventType.COMPLETED;
            case CANCELLED -> AgentEventEnvelope.AgentEventType.CANCELLED;
            case FAILED -> AgentEventEnvelope.AgentEventType.FAILED;
            default -> throw new IllegalArgumentException("非终态不允许构建终态事件: " + status);
        };
        return new AgentEventEnvelope(
                handle.request.taskId(), type, handle.agentId, summary, nexusEventId());
    }

    /** Nexus 生成的事件标识（UUIDv7；SSE Last-Event-ID 游标；不依赖 AgentScope 事件 id）。 */
    private static String nexusEventId() {
        return TaskAttemptId.newUuidV7();
    }

    private static boolean isInterrupt(Throwable error) {
        return error instanceof InterruptedException
                || error.getMessage() != null
                        && error.getMessage().toLowerCase().contains("interrupt");
    }

    /**
     * 把官方 AgentScope Typed Event 映射为业务安全事件（隐藏思维链、Secret 永不外泄）。
     *
     * <p>终态事件（COMPLETED/CANCELLED/FAILED）不在本方法处理——由
     * {@link #onAgentResult}/{@link #onStreamComplete}/{@link #onStreamError}
     * 在完成终态状态转移后发布（P1-1/P1-2/P0-3），保证"先状态后事件"与单一终态。
     * {@code AGENT_END} 映射 PROGRESS（仅标记流结束，避免双终态事件）。
     *
     * <p>P0-3：{@code eventId} 由 Nexus 生成（UUIDv7），不依赖 AgentScope 事件 id。
     */
    private static AgentEventEnvelope mapEvent(ExecutionHandle handle, AgentEvent event) {
        AgentEventEnvelope.AgentEventType type = switch (event.getType()) {
            case AGENT_START -> AgentEventEnvelope.AgentEventType.STARTED;
            case TOOL_CALL_START -> AgentEventEnvelope.AgentEventType.TOOL_STARTED;
            case TOOL_CALL_END -> AgentEventEnvelope.AgentEventType.TOOL_COMPLETED;
            // AGENT_END 仅标记流结束，不作为业务终态（避免双终态事件）。
            case AGENT_END -> AgentEventEnvelope.AgentEventType.PROGRESS;
            default -> AgentEventEnvelope.AgentEventType.PROGRESS;
        };
        return new AgentEventEnvelope(
                handle.request.taskId(),
                type,
                handle.agentId,
                summarize(event),
                nexusEventId());
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
        if (event instanceof AgentResultEvent) {
            return "执行结果（AgentResultEvent）";
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

    /** 一次执行的生命周期句柄（内部状态，非公共契约；package-private 供竞态测试构造）。 */
    static final class ExecutionHandle {
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
