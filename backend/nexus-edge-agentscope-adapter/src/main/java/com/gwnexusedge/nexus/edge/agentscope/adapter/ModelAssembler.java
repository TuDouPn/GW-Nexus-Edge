package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.model.ModelRegistry;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.OpenAIModelProvider;

/**
 * 模型装配器：把 {@link AgentscopeAdapterConfig} 翻译为 AgentScope 官方可消费的模型配置。
 *
 * <p>DEV-0001 验证 OpenAI-compatible 私有端点的真实链路（08 §3）：通过官方
 * {@link OpenAIChatModel} 构建指向受控测试端点的模型，并通过官方 {@link ModelRegistry}
 * 注册。该模型是真实 AgentScope 模型实例，仅下游端点为测试 Test Double，
 * 不能用于宣称 DeepSeek 或企业私有模型已通过生产认证。
 */
public final class ModelAssembler {

    private ModelAssembler() {
        // 工具类，禁止实例化
    }

    /**
     * 注册一个 OpenAI 兼容模型到官方 ModelRegistry。
     *
     * @param config Adapter 配置
     * @return 注册使用的模型标识
     */
    public static String registerOpenAiCompatibleModel(AgentscopeAdapterConfig config) {
        // 官方 Provider 声明支持 openai: 前缀；此处显式构建模型并注册到 Registry。
        OpenAIModelProvider provider = new OpenAIModelProvider();
        if (!provider.supports(config.modelId())) {
            throw new IllegalArgumentException("模型标识 " + config.modelId() + " 不被 OpenAI Provider 支持");
        }

        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(stripProviderPrefix(config.modelId()))
                .baseUrl(config.baseUrl())
                .build();

        ModelRegistry.register(config.modelId(), model);
        return config.modelId();
    }

    private static String stripProviderPrefix(String modelId) {
        int colon = modelId.indexOf(':');
        return colon >= 0 ? modelId.substring(colon + 1) : modelId;
    }
}
