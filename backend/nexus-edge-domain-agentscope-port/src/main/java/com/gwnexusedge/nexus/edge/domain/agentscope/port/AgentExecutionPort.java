package com.gwnexusedge.nexus.edge.domain.agentscope.port;

import java.util.concurrent.Flow;

/**
 * AgentScope 边界的已冻结业务 Port（08_AGENTSCOPE_AND_SKILL.md §2）。
 *
 * <p>领域层只认识以下四种业务语义，永远看不到 AgentScope 类型。Adapter 实现
 * （位于 agentscope 基础设施层）负责将这些语义翻译为官方 AgentScope Harness/Core API。
 * 依赖方向固定为：Port ← Adapter，绝不允许反向依赖。
 *
 * <p>事件契约（P0-2/P1-8 修正）：
 * <ul>
 *   <li>{@code streamEvents} 是 AgentScope 唯一执行源，一次执行只有一个主调用生命周期；
 *       {@link #startExecution} 与 {@link #streamExecutionEvents} 共享同一次执行，
 *       后者绝不再发起第二次执行；</li>
 *   <li>{@link #streamExecutionEvents} 返回 JDK 内置 {@link Flow.Publisher}（零依赖、
 *       适合长期异步/SSE），Publisher 在{@link #startExecution} 时即建立，避免订阅前丢事件；</li>
 *   <li>事件携带 eventId（Nexus 生成，UUIDv7，P0-3）供标识与续传；Last-Event-ID
 *       断线续传属于后续持久化业务事件层职责，本 Port 只保证事件携带可续传标识（06 §5）。</li>
 * </ul>
 */
public interface AgentExecutionPort {

    /**
     * 启动一次 Agent 执行（映射到 AgentScope 的 {@code streamEvents}，唯一执行源）。
     *
     * <p>语义：在长任务完成前立即返回真实可关联引用；同一次执行驱动事件推送、
     * 状态更新、最终结果、失败处理与取消。执行在后台异步推进。
     *
     * @param request 已授权的执行请求（策略决策已由 Nexus Edge 完成）
     * @return 领域可据此关联 TaskAttempt ↔ AgentScope Agent ↔ Trace 的引用
     */
    AgentExecutionReference startExecution(AgentExecutionRequest request);

    /**
     * 请求取消正在运行的执行（映射到 AgentScope 的 interrupt 语义）。
     *
     * <p>语义：仅将状态置为 CANCEL_REQUESTED 并触发官方 interrupt；最终终态
     * （CANCELLED/COMPLETED/FAILED）必须由真实执行事件确认（P1-5），
     * 已完成任务不得再改为 CANCELLED。
     *
     * @param reference 执行引用
     */
    void cancelExecution(AgentExecutionReference reference);

    /**
     * 在业务允许重试的前提下，恢复此前被中断/失败的执行。
     *
     * <p>语义：必须使用 AgentScope 官方 State Store/恢复能力真正重新建立 Agent
     * 并继续同一会话（同 userId/sessionId + 持久化会话上下文），返回新 Attempt 的
     * 真实引用；禁止生成假 Agent 标识。若 AgentScope 2.0.1 不支持该语义，
     * 必须通过 ADR 修订本 Port 与 08 规格（不得静默降级）。
     *
     * @param reference   被恢复尝试的执行引用
     * @param resumeReason 业务恢复该执行的原因
     * @return 恢复后的新尝试引用（真实 Agent 标识）
     */
    AgentExecutionReference resumeExecution(AgentExecutionReference reference, String resumeReason);

    /**
     * 以响应式流输出一次执行的稳定、脱敏业务事件。
     *
     * <p>语义：返回 {@link #startExecution} 时已建立的同一执行事件流（不发起第二次
     * 执行）；事件必须非空、属于同一 Task/Execution、携带可续传事件 id。
     *
     * @param reference 执行引用
     * @return 该执行的真实业务事件流（Flow.Publisher，适合长期异步/SSE）
     */
    Flow.Publisher<AgentEventEnvelope> streamExecutionEvents(AgentExecutionReference reference);
}
