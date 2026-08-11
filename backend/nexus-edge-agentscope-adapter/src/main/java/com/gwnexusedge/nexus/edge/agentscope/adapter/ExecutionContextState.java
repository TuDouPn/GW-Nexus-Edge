package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.state.State;

/**
 * 可持久恢复的执行上下文（第五轮：按 Task/TaskAttempt 隔离）。
 *
 * <p>实现官方 {@link State} 标记接口，可由 {@link io.agentscope.core.state.AgentStateStore}
 * 保存/加载（JSON 序列化）。用于在 start 时写入、resume 时恢复 workspaceId/tenantId。
 *
 * <p>P0-1 隔离：State Store 键按 Task 隔离（{@link #storeKey(String)} 含 taskId），
 * 禁止同一 user/session 下不同 Workspace Task 相互覆盖。
 */
public final class ExecutionContextState implements State {

    /** State Store 中保存执行上下文的键前缀。 */
    public static final String STORE_KEY_PREFIX = "nexus_execution_context:";

    private String taskId;
    private String workspaceId;
    private String tenantId;
    private String userId;
    private String sessionId;

    /** 反序列化用无参构造。 */
    public ExecutionContextState() {
    }

    public ExecutionContextState(String taskId, String workspaceId, String tenantId,
                                 String userId, String sessionId) {
        this.taskId = taskId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    /**
     * 生成按 Task 隔离的 State Store 键（P0-1）。
     *
     * @param taskId 业务 Task 标识
     * @return State Store 键（含 taskId，避免同 user/session 不同 Task 覆盖）
     */
    public static String storeKey(String taskId) {
        return STORE_KEY_PREFIX + taskId;
    }

    public String getTaskId() {
        return taskId;
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

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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
