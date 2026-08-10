package com.gwnexusedge.nexus.edge.domain.agentscope.port;

/**
 * Agent 执行期间发出的稳定业务事件（08 §2、06 §5 SSE 契约）。
 *
 * <p>这是领域的稳定事件信封：Adapter 将 AgentScope 的 Typed Event 映射为这些业务语义。
 * 它刻意只暴露业务安全的字段，确保隐藏思维链、Secret 与未授权内容永远不会越过此边界。
 * SSE 线上格式定义于 06_API_AND_EVENT_CONTRACT.md；本 Port 只承载业务含义。
 *
 * <p>eventId 为官方事件携带的稳定标识，供客户端 {@code Last-Event-ID} 断线续传（06 §5）；
 * 同一 Task/Execution 的全部事件必须携带相同 taskId/executionId。
 *
 * @param taskId      事件所属的业务 Task 标识
 * @param type        业务事件类型（例如 STARTED、TOOL_STARTED、TOOL_COMPLETED、COMPLETED、FAILED）
 * @param executionId AgentScope 执行标识（真实来源，对领域层不透明）
 * @param summary     安全、脱敏后的摘要（绝不携带原始 Prompt / Secret / 思维链）
 * @param eventId     官方事件标识（Last-Event-ID 断线续传）
 */
public record AgentEventEnvelope(
        String taskId,
        AgentEventType type,
        String executionId,
        String summary,
        String eventId) {

    public enum AgentEventType {
        STARTED,
        PROGRESS,
        TOOL_STARTED,
        TOOL_COMPLETED,
        TOOL_FAILED,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    public AgentEventEnvelope {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不允许为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("type 不允许为 null");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId 不允许为空（真实来源）");
        }
        summary = summary == null ? "" : summary;
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 不允许为空（Last-Event-ID 续传需要）");
        }
    }
}
