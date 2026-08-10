package com.gwnexusedge.nexus.edge.agentscope.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.1.0 内嵌 AgentScope Harness/Core 的装配配置（G-01 验证对象）。
 *
 * <p>按 00 §3 决策：不部署独立 AgentScope Service 或 Runtime Control Plane，
 * 而是将 Harness/Core 内嵌于 Spring 上下文。DEV-0001 阶段通过属性注入配置，
 * 且不读取任何生产 Secret（07 §7：Secret 一律走 Provider 引用）。
 */
@Configuration
public class AgentscopeAdapterConfiguration {

    @Bean
    public AgentscopeAdapterConfig agentscopeAdapterConfig(
            @Value("${nexus.edge.agentscope.model-id:openai:test-model}") String modelId,
            @Value("${nexus.edge.agentscope.base-url:http://localhost:18443}") String baseUrl,
            @Value("${nexus.edge.agentscope.api-key:test-key}") String apiKey,
            @Value("${nexus.edge.agentscope.workspace-path:./.agentscope/workspace}") String workspacePath,
            @Value("${nexus.edge.agentscope.system-prompt:你是一个测试助手。}") String systemPrompt) {
        return new AgentscopeAdapterConfig(modelId, baseUrl, apiKey, workspacePath, systemPrompt);
    }

    @Bean(destroyMethod = "close")
    public AgentscopeAgentExecutionAdapter agentscopeAgentExecutionAdapter(
            AgentscopeAdapterConfig config) {
        return new AgentscopeAgentExecutionAdapter(config);
    }
}
