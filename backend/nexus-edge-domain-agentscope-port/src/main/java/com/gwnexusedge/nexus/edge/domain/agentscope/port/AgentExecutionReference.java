package com.gwnexusedge.nexus.edge.domain.agentscope.port;

/**
 * 从 Nexus Edge 领域视角标识一次逻辑上的 Agent 执行（00_DECISIONS.md A-004）。
 *
 * <p>Nexus Edge 拥有业务 Task 生命周期，仅维护与 AgentScope 执行的映射。executionId
 * 必须来自真实执行（AgentScope 官方标识），领域层对其内容不透明，但禁止空值。
 * traceId 来自 OpenTelemetry 上下文，未采集时显式为空（由 Adapter 保证不产生
 * "unassigned" 这类伪造值）。userId/sessionId 为会话级恢复所需（官方 State Store
 * 按 (userId, sessionId) 键控），因此引用携带二者。
 *
 * @param taskId      业务 Task 标识（API 传输用 UUIDv7 字符串）
 * @param executionId AgentScope 执行/Agent 标识（真实来源，非合成）
 * @param traceId     OpenTelemetry trace id；未采集时为空字符串
 * @param attemptNo   业务尝试序号（TaskAttempt 语义，从 1 开始）
 * @param status      执行状态（供调用方验证取消/完成/失败）
 * @param userId      执行用户标识（会话恢复）
 * @param sessionId   会话标识（会话恢复）
 */
public record AgentExecutionReference(
        String taskId,
        String executionId,
        String traceId,
        int attemptNo,
        ExecutionStatus status,
        String userId,
        String sessionId) {

    /** 执行状态：供调用方验证生命周期，而非仅凭"未抛异常"推断。 */
    public enum ExecutionStatus {
        /** 已启动，后台推进中。 */
        STARTED,
        /** 已请求取消，等待确认。 */
        CANCEL_REQUESTED,
        /** 已确认取消。 */
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
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId 不允许为空（禁止合成假 ID）");
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
            String taskId, String executionId, String traceId,
            ExecutionStatus status, String userId, String sessionId) {
        return new AgentExecutionReference(
                taskId, executionId, traceId, 1, status, userId, sessionId);
    }

    /** 以新状态重建引用。 */
    public AgentExecutionReference withStatus(ExecutionStatus newStatus) {
        return new AgentExecutionReference(
                taskId, executionId, traceId, attemptNo, newStatus, userId, sessionId);
    }

    /** 以新尝试序号与执行标识重建引用（TaskAttempt 语义）。 */
    public AgentExecutionReference nextAttempt(String newExecutionId, ExecutionStatus newStatus) {
        return new AgentExecutionReference(
                taskId, newExecutionId, traceId, attemptNo + 1, newStatus, userId, sessionId);
    }

    /** 以真实 traceId 重建引用（从 OTel 上下文采集后调用）。 */
    public AgentExecutionReference withTraceId(String realTraceId) {
        return new AgentExecutionReference(
                taskId, executionId, realTraceId == null ? "" : realTraceId,
                attemptNo, status, userId, sessionId);
    }
}
