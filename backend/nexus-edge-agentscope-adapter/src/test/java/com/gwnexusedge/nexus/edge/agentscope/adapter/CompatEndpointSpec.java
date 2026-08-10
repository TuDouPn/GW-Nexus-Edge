package com.gwnexusedge.nexus.edge.agentscope.adapter;

/**
 * 受控 OpenAI-compatible 测试端点（下游模型 Test Double）。
 *
 * <p>DEV-0001 用它验证 AgentScope 官方 OpenAI 扩展的真实 HTTP、Streaming、Tool Calling
 * 与错误传播链路。它是"下游模型"的 Test Double，不是 Fake AgentScope——
 * AgentScope Harness/Core、Model、Event 全部使用官方代码，仅下游模型端点受控。
 *
 * <p>重要边界：它只能证明运行时与协议集成，不能用来宣称 DeepSeek 或企业私有模型
 * Provider 已通过生产认证（用户修订要求五）。
 */
public final class CompatEndpointSpec {

    private CompatEndpointSpec() {
        // 工具类，禁止实例化
    }

    /** 默认测试端点端口。 */
    public static final int DEFAULT_PORT = 18443;

    /** 健康/握手路径，测试用。 */
    public static final String HEALTH_PATH = "/health";

    /** 与 OpenAI 官方一致的聊天补全路径。 */
    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
}
