package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.model.ModelRegistry;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.OpenAIModelProvider;

/**
 * 模型装配器：把 {@link AgentscopeAdapterConfig} 翻译为 AgentScope 官方可消费的模型配置。
 *
 * <p>DEV-0001 验证 OpenAI-compatible 私有端点的真实链路（08 §3）：通过官方
 * {@link OpenAIChatModel} 构建指向端点的模型，并通过官方 {@link ModelRegistry} 注册。
 * 该模型是真实 AgentScope 模型实例，仅下游端点为测试 Test Double 时用于兼容验证，
 * 不能用于宣称 DeepSeek 或企业私有模型已通过生产认证。
 *
 * <p>Secret 边界：本类不接收明文 apiKey 值本身之外的任何生产 Secret；
 * 生产环境 apiKey 由调用方从 Secret Provider 解析（07 §7），此处仅透传运行期值。
 */
public final class ModelAssembler {

    private ModelAssembler() {
        // 工具类，禁止实例化
    }

    /**
     * 注册一个 OpenAI 兼容模型到官方 ModelRegistry。
     *
     * @param modelId   模型标识（{@code provider:model} 形式）
     * @param baseUrl   端点 base URL
     * @param apiKey    运行期 Secret 值（由调用方从 Secret Provider 解析；测试用 Test Double 凭据）
     * @return 注册使用的模型标识
     */
    public static String registerOpenAiCompatibleModel(String modelId, String baseUrl, String apiKey) {
        OpenAIModelProvider provider = new OpenAIModelProvider();
        if (!provider.supports(modelId)) {
            throw new IllegalArgumentException("模型标识 " + modelId + " 不被 OpenAI Provider 支持");
        }

        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(stripProviderPrefix(modelId))
                .baseUrl(baseUrl)
                .build();

        ModelRegistry.register(modelId, model);
        return modelId;
    }

    private static String stripProviderPrefix(String modelId) {
        int colon = modelId.indexOf(':');
        return colon >= 0 ? modelId.substring(colon + 1) : modelId;
    }
}
