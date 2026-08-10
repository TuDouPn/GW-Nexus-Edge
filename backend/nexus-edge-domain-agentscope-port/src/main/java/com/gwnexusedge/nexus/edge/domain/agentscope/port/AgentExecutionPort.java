package com.gwnexusedge.nexus.edge.domain.agentscope.port;

/**
 * AgentScope 边界的已冻结业务 Port（08_AGENTSCOPE_AND_SKILL.md §2）。
 *
 * <p>领域层只认识以下四种业务语义，永远看不到 AgentScope 类型。Adapter 实现
 * （位于 agentscope 基础设施层）负责将这些语义翻译为官方 AgentScope Harness/Core API。
 * 依赖方向固定为：Port ← Adapter，绝不允许反向依赖。
 */
public interface AgentExecutionPort {

    /**
     * 启动一次 Agent 执行（映射到 AgentScope 的 {@code call}/{@code stream}）。
     *
     * @param request 已授权的执行请求（策略决策已由 Nexus Edge 完成）
     * @return 领域可据此关联 Task ↔ Execution ↔ Trace 的引用
     */
    AgentExecutionReference startExecution(AgentExecutionRequest request);

    /**
     * 请求取消正在运行的执行（映射到 AgentScope 的 interrupt 语义）。
     * 执行标识对领域层不透明。
     *
     * @param reference 执行引用
     */
    void cancelExecution(AgentExecutionReference reference);

    /**
     * 在业务允许重试的前提下，恢复此前被中断/失败的执行。
     * 范围：业务步骤级恢复（03 §6）；不要求 Token 级续跑。
     *
     * @param reference   被恢复尝试的执行引用
     * @param resumeReason 业务恢复该执行的原因
     * @return 恢复后的新尝试引用
     */
    AgentExecutionReference resumeExecution(AgentExecutionReference reference, String resumeReason);

    /**
     * 以流式方式输出一次执行的稳定、脱敏业务事件。
     * 返回的 {@link java.util.stream.Stream} 是适合测试的有界拉取视图；
     * 生产环境 SSE 传输定义于 06_API_AND_EVENT_CONTRACT.md。
     *
     * @param reference 执行引用
     * @return 按时间顺序排列的有界业务事件流
     */
    java.util.stream.Stream<AgentEventEnvelope> streamExecutionEvents(AgentExecutionReference reference);
}
