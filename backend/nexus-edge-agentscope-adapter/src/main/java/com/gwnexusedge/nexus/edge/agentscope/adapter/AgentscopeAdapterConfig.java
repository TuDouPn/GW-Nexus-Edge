package com.gwnexusedge.nexus.edge.agentscope.adapter;

/**
 * AgentScope Adapter 的配置对象（DEV-0001 兼容验证用）。
 *
 * <p>本配置只承载与 AgentScope 模型装配相关的参数；它不包含任何 Secret 值本身，
 * apiKey 仅用于测试端点（Test Double），生产环境 Secret 一律走 Secret Provider 引用
 * （07_SECURITY_AND_PERMISSION.md §7）。DEV-0001 阶段仅用于受控测试端点。
 *
 * @param modelId        模型标识，例如 {@code openai:test-model}
 * @param baseUrl        OpenAI 兼容端点的 base URL（受控测试端点）
 * @param apiKey         测试端点 API Key（仅测试用，非生产 Secret）
 * @param workspacePath  AgentScope 工作区路径（本地临时目录）
 * @param systemPrompt   系统提示词
 */
public record AgentscopeAdapterConfig(
        String modelId,
        String baseUrl,
        String apiKey,
        String workspacePath,
        String systemPrompt) {

    public AgentscopeAdapterConfig {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId 不允许为空");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不允许为空");
        }
        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalArgumentException("workspacePath 不允许为空");
        }
    }
}
