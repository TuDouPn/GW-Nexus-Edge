package com.gwnexusedge.nexus.edge.agentscope.adapter;

/**
 * AgentScope Adapter 的配置对象（DEV-0001 兼容验证用）。
 *
 * <p>生产配置必须 fail-fast：本配置不提供任何默认值，缺失必填项时构造失败。
 * Secret 只允许通过 Secret Reference/Provider 提供（07 §7），apiKey 为测试端点
 * 专用 Test Double 凭据，不得在生产注入真实 Secret。
 *
 * @param modelId        模型标识，例如 {@code openai:test-model}
 * @param baseUrl        OpenAI 兼容端点的 base URL（生产为经审核的模型端点）
 * @param apiKeyReference Secret Reference 名称（经 Secret Provider 解析为运行期值）
 * @param workspacePath  AgentScope 工作区路径
 * @param systemPrompt   系统提示词
 */
public record AgentscopeAdapterConfig(
        String modelId,
        String baseUrl,
        String apiKeyReference,
        String workspacePath,
        String systemPrompt) {

    public AgentscopeAdapterConfig {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId 不允许为空（生产配置 fail-fast）");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不允许为空（生产配置 fail-fast）");
        }
        if (apiKeyReference == null || apiKeyReference.isBlank()) {
            throw new IllegalArgumentException("apiKeyReference 不允许为空（Secret 只允许 Reference）");
        }
        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalArgumentException("workspacePath 不允许为空");
        }
    }
}
