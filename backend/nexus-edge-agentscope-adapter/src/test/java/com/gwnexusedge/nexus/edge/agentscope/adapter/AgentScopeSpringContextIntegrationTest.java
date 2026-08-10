package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.SecretResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Spring Boot 4.1.0 内嵌 AgentScope Harness/Core 集成测试（G-01 核心）。
 *
 * <p>验证：Spring Boot 4.1.0 上下文能装配 AgentScope Harness/Core 的 Adapter Bean，
 * 并通过受控 OpenAI 兼容测试端点完成一次真实异步执行（00 §3、A-002）。
 *
 * <p>P0-4：本测试自包含且顺序无关——{@code @BeforeAll} 重置 ModelRegistry 与 OTel
 * 全局状态，独立运行时（{@code -Dtest=AgentScopeSpringContextIntegrationTest}）
 * 不依赖任何其它测试的先执行状态。P0-3：测试上下文显式提供 {@link SecretResolver} Bean。
 */
@SpringBootTest(classes = AgentScopeSpringContextIntegrationTest.AgentScopeTestContext.class)
class AgentScopeSpringContextIntegrationTest {

    /** 自包含 Spring 配置（测试专用值）。 */
    @Configuration
    public static class AgentScopeTestContext {
        @Bean(destroyMethod = "close")
        public CompatEndpoint compatEndpoint() throws java.io.IOException {
            return new CompatEndpoint(0);
        }

        @Bean
        public SecretResolver testSecretResolver() {
            return new TestSecretResolver();
        }

        @Bean
        public AgentscopeAdapterConfig agentscopeAdapterConfig(CompatEndpoint endpoint) {
            return new AgentscopeAdapterConfig(
                    "openai:test-model",
                    endpoint.baseUrl(),
                    TestSecretResolver.TEST_API_KEY,
                    System.getProperty("java.io.tmpdir") + "/nexus-edge-spring-workspace",
                    "你是 Spring 集成验证助手。");
        }

        @Bean(destroyMethod = "close")
        public AgentscopeAgentExecutionAdapter agentscopeAgentExecutionAdapter(
                AgentscopeAdapterConfig config,
                SecretResolver secretResolver) {
            return new AgentscopeAgentExecutionAdapter(config, null, null, secretResolver);
        }
    }

    @Autowired
    private AgentscopeAgentExecutionAdapter adapter;

    @BeforeAll
    static void resetGlobalState() {
        // P0-4：重置全局静态状态，保证独立运行/任意顺序均一致。
        io.agentscope.core.model.ModelRegistry.reset();
        TestOtel.init();
    }

    @Test
    @DisplayName("Spring Boot 4.1.0 上下文内嵌 AgentScope 并异步完成真实执行")
    void springContextRunsEmbeddedAgentScope() throws Exception {
        assertNotNull(adapter, "Spring 上下文应装配 AgentScope Adapter Bean");

        AgentExecutionReference reference = adapter.startExecution(new AgentExecutionRequest(
                "task-spring-1", "user-spring-1", "session-spring-1",
                "workspace-1", "tenant-1", List.of("请回复测试文本")));

        assertNotNull(reference);
        assertFalse(reference.agentId().isBlank(), "agentId 不得为空");
        assertTrue(reference.taskAttemptId().matches("[0-9a-f-]{36}"),
                "taskAttemptId 应为 UUIDv7");
        assertTrue(TestOtel.isRealTraceId(reference.traceId()),
                "Spring 链路应返回真实 traceId");

        // 等待异步执行完成。
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-spring-1")
                        != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-spring-1")
                        != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(200);
        }
        assertTrue(adapter.statusOf("task-spring-1")
                        == AgentExecutionReference.ExecutionStatus.COMPLETED,
                "Spring 上下文内嵌的 AgentScope 执行应真实完成");
    }
}
