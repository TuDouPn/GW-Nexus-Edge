package com.gwnexusedge.nexus.edge.domain.agentscope.port;

import java.util.List;

/**
 * 启动一次 Agent 执行的授权输入（08_AGENTSCOPE_AND_SKILL.md §2）。
 *
 * <p>Adapter 收到本请求时，Nexus Edge 已完成授权与策略决策；Adapter 仅负责构造
 * AgentScope 运行时上下文。此处不允许出现任何 AgentScope 类型——领域只认识业务概念。
 *
 * @param taskId      业务 Task 标识
 * @param userId      执行者用户标识
 * @param sessionId   限定本次执行的会话标识
 * @param workspaceId 工作空间标识（用于运行时上下文的信息字段）
 * @param tenantId    租户标识（用于运行时上下文的信息字段）
 * @param messages    按顺序排列的业务消息（文本），作为执行输入
 */
public record AgentExecutionRequest(
        String taskId,
        String userId,
        String sessionId,
        String workspaceId,
        String tenantId,
        List<String> messages) {

    public AgentExecutionRequest {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不允许为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不允许为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不允许为空");
        }
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
