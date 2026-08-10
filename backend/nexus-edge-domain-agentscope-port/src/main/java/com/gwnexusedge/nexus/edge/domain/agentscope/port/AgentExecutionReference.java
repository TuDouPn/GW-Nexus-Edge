package com.gwnexusedge.nexus.edge.domain.agentscope.port;

/**
 * 从 Nexus Edge 领域视角标识一次逻辑上的 Agent 执行。
 *
 * <p>这是已冻结的业务语义（00_DECISIONS.md A-004）：Nexus Edge 拥有业务 Task 生命周期，
 * 仅维护与 AgentScope 执行的映射关系。该值对象承载领域所理解的稳定标识符；
 * AgentScope 的执行标识对外保持不透明字符串，因此领域层永远不会依赖 AgentScope 类型。
 *
 * @param taskId      业务 Task 标识（API 传输用 UUIDv7 字符串）
 * @param executionId AgentScope 执行标识，对领域层不透明
 * @param traceId     OpenTelemetry 追踪标识，用于关联 Task → Execution → Trace
 */
public record AgentExecutionReference(String taskId, String executionId, String traceId) {

    public AgentExecutionReference {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不允许为空");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId 不允许为空");
        }
    }

    /** 携带新的执行标识重建引用（用于 Task 重新尝试的场景）。 */
    public AgentExecutionReference withExecutionId(String newExecutionId) {
        return new AgentExecutionReference(taskId, newExecutionId, traceId);
    }
}
