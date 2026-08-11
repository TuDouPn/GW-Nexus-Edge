package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第五轮测试工具：通过官方 {@code @Tool} 注解 + {@link RuntimeContext} 参数注入，
 * 在真实 AgentScope Tool 执行链路内读取 workspaceId/tenantId。
 *
 * <p>依据官方 {@code ToolMethodInvoker} 的自动注入规则：{@code @Tool} 方法可声明
 * {@code RuntimeContext} 类型参数，由 AgentScope 在工具执行时注入
 * （评审项 P0-7：真实 Tool 链路读取，非 Request 自证）。
 */
public final class RuntimeContextProbeTool {

    /** 工具名。 */
    public static final String NAME = "probe_execution_context";

    /** 记录每次调用读到的上下文（userId/sessionId → "workspaceId|tenantId"）。 */
    public static final Map<String, String> OBSERVED = new ConcurrentHashMap<>();

    /** 重置观测记录。 */
    public static void reset() {
        OBSERVED.clear();
    }

    /**
     * 读取执行上下文（workspaceId/tenantId）。
     *
     * @param ctx 由 AgentScope 自动注入的当前执行 RuntimeContext
     * @return 上下文读取结果
     */
    @Tool(name = NAME, description = "读取执行上下文（workspaceId/tenantId），用于验证上下文注入")
    public String probe(RuntimeContext ctx) {
        String workspaceId = "";
        String tenantId = "";
        String userId = "?";
        String sessionId = "?";
        if (ctx != null) {
            Object ws = ctx.getExtra().get("workspaceId");
            Object tn = ctx.getExtra().get("tenantId");
            workspaceId = ws == null ? "" : String.valueOf(ws);
            tenantId = tn == null ? "" : String.valueOf(tn);
            userId = ctx.getUserId() == null ? "?" : ctx.getUserId();
            sessionId = ctx.getSessionId() == null ? "?" : ctx.getSessionId();
        }
        String key = userId + "/" + sessionId;
        OBSERVED.put(key, workspaceId + "|" + tenantId);
        return "上下文已读取";
    }
}
