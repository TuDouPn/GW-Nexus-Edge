package com.gwnexusedge.nexus.edge.agentscope.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.1.0 内嵌 AgentScope Harness/Core 的装配配置（G-01 验证对象）。
 *
 * <p>按 00 §3 决策：不部署独立 AgentScope Service 或 Runtime Control Plane，
 * 而是将 Harness/Core 内嵌于 Spring 上下文。
 *
 * <p>生产配置 fail-fast（评审 CHANGES_REQUESTED）：以下属性均为必填且无默认值，
 * 缺失即启动失败；不允许在 main 代码中出现 test-key/test-model/localhost 默认值。
 * Secret 只允许 Reference：api-key-reference 指向 Secret Provider 管理的引用（07 §7），
 * 由运行期解析，不写入代码或配置明文。
 */
@Configuration
public class AgentscopeAdapterConfiguration {

    @Bean
    public AgentscopeAdapterConfig agentscopeAdapterConfig(
            @Value("${nexus.edge.agentscope.model-id}") String modelId,
            @Value("${nexus.edge.agentscope.base-url}") String baseUrl,
            @Value("${nexus.edge.agentscope.api-key-reference}") String apiKeyReference,
            @Value("${nexus.edge.agentscope.workspace-path}") String workspacePath,
            @Value("${nexus.edge.agentscope.system-prompt:你是一个测试助手。}") String systemPrompt) {
        return new AgentscopeAdapterConfig(
                modelId, baseUrl, apiKeyReference, workspacePath, systemPrompt);
    }

    @Bean(destroyMethod = "close")
    public AgentscopeAgentExecutionAdapter agentscopeAgentExecutionAdapter(
            AgentscopeAdapterConfig config) {
        return new AgentscopeAgentExecutionAdapter(config);
    }
}
