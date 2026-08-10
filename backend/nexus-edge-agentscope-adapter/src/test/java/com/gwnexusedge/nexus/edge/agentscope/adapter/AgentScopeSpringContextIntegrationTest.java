package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Spring Boot 4.1.0 内嵌 AgentScope Harness/Core 集成测试（G-01 核心）。
 *
 * <p>验证：Spring Boot 4.1.0 上下文能装配 AgentScope Harness/Core 的 Adapter Bean，
 * 并通过受控 OpenAI 兼容测试端点完成一次真实执行——即"Spring Boot 内嵌
 * AgentScope"（00 §3、A-002：不部署独立 AgentScope Service 或 Runtime Control Plane）。
 *
 * <p>测试上下文自包含地声明端点、配置与 Adapter Bean，将 base-url 指向
 * 实际启动的受控测试端点；端点 Test Double 只证明运行时与协议集成，
 * 不构成任何模型 Provider 的生产认证。
 */
@SpringBootTest(classes = AgentScopeSpringContextIntegrationTest.AgentScopeTestContext.class)
class AgentScopeSpringContextIntegrationTest {

    /** 自包含 Spring 配置：端点、配置与 Adapter 全部显式声明。 */
    @Configuration
    public static class AgentScopeTestContext {
        @Bean(destroyMethod = "close")
        public CompatEndpoint compatEndpoint() throws java.io.IOException {
            return new CompatEndpoint(0);
        }

        @Bean
        public AgentscopeAdapterConfig agentscopeAdapterConfig(CompatEndpoint endpoint) {
            return new AgentscopeAdapterConfig(
                    "openai:test-model",
                    endpoint.baseUrl(),
                    "test-key",
                    System.getProperty("java.io.tmpdir") + "/nexus-edge-spring-workspace",
                    "你是 Spring 集成验证助手。");
        }

        @Bean(destroyMethod = "close")
        public AgentscopeAgentExecutionAdapter agentscopeAgentExecutionAdapter(
                AgentscopeAdapterConfig config) {
            return new AgentscopeAgentExecutionAdapter(config);
        }
    }

    @Autowired
    private AgentscopeAgentExecutionAdapter adapter;

    @Test
    @DisplayName("Spring Boot 4.1.0 上下文内嵌 AgentScope 并完成真实执行")
    void springContextRunsEmbeddedAgentScope() {
        assertNotNull(adapter, "Spring 上下文应装配 AgentScope Adapter Bean");

        AgentExecutionReference reference = adapter.startExecution(new AgentExecutionRequest(
                "task-spring-1", "user-spring-1", "session-spring-1",
                "workspace-1", "tenant-1", List.of("请回复测试文本")));

        assertNotNull(reference);
        assertNotNull(reference.executionId());
        assertTrue(reference.taskId().startsWith("task-spring-"));
    }
}
