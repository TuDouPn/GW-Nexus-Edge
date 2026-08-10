package com.gwnexusedge.nexus.edge.domain.agentscope.port;

import java.util.concurrent.Flow;

/**
 * AgentScope 边界的已冻结业务 Port（08_AGENTSCOPE_AND_SKILL.md §2）。
 *
 * <p>领域层只认识以下四种业务语义，永远看不到 AgentScope 类型。Adapter 实现
 * （位于 agentscope 基础设施层）负责将这些语义翻译为官方 AgentScope Harness/Core API。
 * 依赖方向固定为：Port ← Adapter，绝不允许反向依赖。
 *
 * <p>事件契约：{@link #streamExecutionEvents} 返回 JDK 内置
 * {@link java.util.concurrent.Flow.Publisher}（响应式流标准，零第三方依赖），
 * 适合长期异步/SSE 场景；生产 SSE 传输定义于 06_API_AND_EVENT_CONTRACT.md。
 */
public interface AgentExecutionPort {

    /**
     * 启动一次 Agent 执行（映射到 AgentScope 的 {@code call}/{@code stream}）。
     *
     * <p>语义：在长任务完成前立即返回真实可关联的引用；执行在后台异步推进。
     * 引用中的 executionId 必须来自真实执行（AgentScope 官方标识），禁止合成假 ID。
     *
     * @param request 已授权的执行请求（策略决策已由 Nexus Edge 完成）
     * @return 领域可据此关联 Task ↔ Execution ↔ Trace 的引用（异步执行启动后立即返回）
     */
    AgentExecutionReference startExecution(AgentExecutionRequest request);

    /**
     * 请求取消正在运行的执行（映射到 AgentScope 的 interrupt 语义）。
     *
     * <p>语义：调用方必须能在取消后查询执行状态（见 {@link AgentExecutionReference}），
     * 验证在途执行确实被中断；不得以"调用不抛异常"冒充取消成功。
     *
     * @param reference 执行引用
     */
    void cancelExecution(AgentExecutionReference reference);

    /**
     * 在业务允许重试的前提下，恢复此前被中断/失败的执行。
     *
     * <p>语义：必须使用 AgentScope 官方 State Store/恢复能力真正重新建立 Agent
     * 并继续同一会话（同 userId/sessionId + 持久化会话上下文），返回新 Attempt 的
     * 真实引用；禁止生成假 Execution ID。若 AgentScope 2.0.1 不支持该语义，
     * 必须通过 ADR 修订本 Port 与 08 规格（不得静默降级）。
     *
     * @param reference   被恢复尝试的执行引用
     * @param resumeReason 业务恢复该执行的原因
     * @return 恢复后的新尝试引用（真实 Execution 标识）
     */
    AgentExecutionReference resumeExecution(AgentExecutionReference reference, String resumeReason);

    /**
     * 以响应式流输出一次执行的稳定、脱敏业务事件。
     *
     * <p>语义：订阅原 Execution 的真实事件流（不得为读取事件而发起第二次执行）；
     * 事件必须非空、属于同一 Task/Execution、携带可续传标识（事件 id），
     * 供客户端 {@code Last-Event-ID} 断线续传（06 §5）。
     *
     * @param reference 执行引用
     * @return 该执行的真实业务事件流（Flow.Publisher，适合长期异步/SSE）
     */
    Flow.Publisher<AgentEventEnvelope> streamExecutionEvents(AgentExecutionReference reference);
}
