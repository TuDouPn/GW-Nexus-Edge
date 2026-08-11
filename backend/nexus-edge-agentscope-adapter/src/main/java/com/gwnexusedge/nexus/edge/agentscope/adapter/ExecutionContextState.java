package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.state.State;

/**
 * 可持久恢复的执行上下文（第四轮：workspaceId/tenantId 持久化）。
 *
 * <p>实现官方 {@link State} 标记接口，可由 {@link io.agentscope.core.state.AgentStateStore}
 * 保存/加载（JSON 序列化）。用于在 start 时写入、resume 时恢复 workspaceId/tenantId，
 * 保证 start/cancel/resume 全链路上下文一致（评审项 3）。
 */
public final class ExecutionContextState implements State {

    /** State Store 中保存执行上下文的键。 */
    public static final String STORE_KEY = "nexus_execution_context";

    private String workspaceId;
    private String tenantId;
    private String userId;
    private String sessionId;

    /** 反序列化用无参构造。 */
    public ExecutionContextState() {
    }

    public ExecutionContextState(String workspaceId, String tenantId,
                                 String userId, String sessionId) {
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
