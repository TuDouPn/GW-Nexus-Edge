package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.SecretResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.1.0 内嵌 AgentScope Harness/Core 的装配配置（G-01 验证对象）。
 *
 * <p>按 00 §3 决策：不部署独立 AgentScope Service 或 Runtime Control Plane，
 * 而是将 Harness/Core 内嵌于 Spring 上下文。
 *
 * <p>生产配置 fail-fast（P0-3）：以下属性均为必填且无默认值；systemPrompt 无默认值
 * （不允许"测试助手"类默认 Prompt）。Secret 只允许 Reference：{@code api-key-reference}
 * 经 {@link SecretResolver} 在运行期解析临时值，明文不写入代码/配置/日志。
 *
 * <p>Secret 边界：{@link SecretResolver} 为必填 Bean。DEV-0001 范围外，生产 Secret
 * Provider 实现由平台基础设施层提供（07 §7）；本配置只定义装配契约，不提交伪实现。
 */
@Configuration
public class AgentscopeAdapterConfiguration {

    @Bean
    public AgentscopeAdapterConfig agentscopeAdapterConfig(
            @Value("${nexus.edge.agentscope.model-id}") String modelId,
            @Value("${nexus.edge.agentscope.base-url}") String baseUrl,
            @Value("${nexus.edge.agentscope.api-key-reference}") String apiKeyReference,
            @Value("${nexus.edge.agentscope.workspace-path}") String workspacePath,
            @Value("${nexus.edge.agentscope.system-prompt}") String systemPrompt) {
        return new AgentscopeAdapterConfig(
                modelId, baseUrl, apiKeyReference, workspacePath, systemPrompt);
    }

    @Bean(destroyMethod = "close")
    public AgentscopeAgentExecutionAdapter agentscopeAgentExecutionAdapter(
            AgentscopeAdapterConfig config,
            SecretResolver secretResolver) {
        return new AgentscopeAgentExecutionAdapter(config, null, null, secretResolver);
    }
}
