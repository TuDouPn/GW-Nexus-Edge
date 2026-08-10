package com.gwnexusedge.nexus.edge.domain.agentscope.port;

/**
 * 从 Nexus Edge 领域视角标识一次逻辑上的 Agent 执行（00_DECISIONS.md A-004）。
 *
 * <p>标识模型（P1-7 修正）：四个标识各自独立、语义明确——
 * <ul>
 *   <li>{@code taskId}：业务 Task 标识（操作员发起，贯穿全部 Attempt）；</li>
 *   <li>{@code taskAttemptId}：Nexus Edge 业务层生成的 TaskAttempt UUIDv7（业务主键，API 传输）；</li>
 *   <li>{@code agentId}：AgentScope 官方 Agent 实例标识（构建时 UUID.randomUUID()），
 *       不是官方 Execution ID——AgentScope 2.0.1 无独立 Execution ID 概念；</li>
 *   <li>{@code traceId}：OpenTelemetry 真实 Trace ID（32 位 hex），来自执行链路。</li>
 * </ul>
 * 映射由 Nexus Edge 持久化（A-004）。禁止把 agentId 称为"官方 Execution ID"。
 *
 * @param taskId        业务 Task 标识
 * @param taskAttemptId Nexus TaskAttempt UUIDv7（业务主键）
 * @param agentId       AgentScope Agent 实例标识（官方 getAgentId）
 * @param traceId       OpenTelemetry trace id；未采集时为空字符串
 * @param attemptNo     业务尝试序号（TaskAttempt 语义，从 1 开始）
 * @param status        执行状态（互斥终态：COMPLETED/FAILED/CANCELLED）
 * @param userId        执行用户标识（会话恢复）
 * @param sessionId     会话标识（会话恢复）
 */
public record AgentExecutionReference(
        String taskId,
        String taskAttemptId,
        String agentId,
        String traceId,
        int attemptNo,
        ExecutionStatus status,
        String userId,
        String sessionId) {

    /** 执行状态：互斥终态（P1-5），由真实执行事件驱动，禁止自行设置。 */
    public enum ExecutionStatus {
        /** 已启动，后台推进中。 */
        STARTED,
        /** 已请求取消，等待真实中断事件确认。 */
        CANCEL_REQUESTED,
        /** 已确认取消（interrupt 恢复消息/中断异常）。 */
        CANCELLED,
        /** 正常完成。 */
        COMPLETED,
        /** 失败。 */
        FAILED
    }

    public AgentExecutionReference {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不允许为空");
        }
        if (taskAttemptId == null || taskAttemptId.isBlank()) {
            throw new IllegalArgumentException("taskAttemptId 不允许为空");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不允许为空");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo 必须 >= 1");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不允许为 null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不允许为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不允许为空");
        }
        traceId = traceId == null ? "" : traceId;
    }

    /** 首尝试构造（attemptNo=1）。 */
    public static AgentExecutionReference firstAttempt(
            String taskId, String taskAttemptId, String agentId, String traceId,
            ExecutionStatus status, String userId, String sessionId) {
        return new AgentExecutionReference(
                taskId, taskAttemptId, agentId, traceId, 1, status, userId, sessionId);
    }

    /** 以新状态重建引用（终态只允许由执行事件驱动，见 P1-5）。 */
    public AgentExecutionReference withStatus(ExecutionStatus newStatus) {
        return new AgentExecutionReference(
                taskId, taskAttemptId, agentId, traceId, attemptNo, newStatus, userId, sessionId);
    }

    /** 以新尝试序号、新 Attempt 标识与新 Agent 标识重建引用（TaskAttempt 语义）。 */
    public AgentExecutionReference nextAttempt(
            String newTaskAttemptId, String newAgentId, ExecutionStatus newStatus) {
        return new AgentExecutionReference(
                taskId, newTaskAttemptId, newAgentId, traceId, attemptNo + 1,
                newStatus, userId, sessionId);
    }

    /** 以真实 traceId 重建引用（从 OTel 上下文采集后调用）。 */
    public AgentExecutionReference withTraceId(String realTraceId) {
        return new AgentExecutionReference(
                taskId, taskAttemptId, agentId, realTraceId == null ? "" : realTraceId,
                attemptNo, status, userId, sessionId);
    }
}
