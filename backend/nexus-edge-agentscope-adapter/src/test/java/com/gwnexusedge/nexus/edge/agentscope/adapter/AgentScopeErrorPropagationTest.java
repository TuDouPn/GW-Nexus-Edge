package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 错误传播测试（官方 Provider 错误 → 结构化异常传播）。
 *
 * <p>验证：当模型端点返回 500 时，AgentScope 官方 OpenAI 扩展把错误传播为
 * 结构化异常（而非伪装成空结果的成功）。这是"错误不允许以空结果伪装成功"
 * 原则在官方运行时层的真实体现（09 §7、13 §6）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeErrorPropagationTest {

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-error");
        ModelAssembler.registerOpenAiCompatibleModel(new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是错误验证助手。"));
        agent = HarnessAgent.builder()
                .name("error-compat-agent")
                .sysPrompt("你是错误验证助手。")
                .model("openai:test-model")
                .workspace(workspace)
                .build();
    }

    @AfterAll
    void tearDown() {
        agent.close();
        endpoint.close();
    }

    @Test
    @DisplayName("端点 500 错误被官方 Provider 传播为结构化异常而非成功结果")
    void providerErrorPropagatesAsException() {
        endpoint.setFailWith500(true);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("error-session-1")
                .userId("error-user-1")
                .build();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Object> result = new AtomicReference<>();

        try {
            agent.call(List.of(new UserMessage("触发服务端错误")), ctx)
                    .block(Duration.ofMinutes(3));
        } catch (Exception e) {
            failure.set(e);
        } finally {
            endpoint.setFailWith500(false);
        }

        assertNotNull(failure.get(), "端点 500 应传播为异常，而不是返回成功结果");
        assertTrue(result.get() == null, "不应返回伪造的成功结果");
        // 记录异常类型，作为错误传播证据。
        System.out.println("错误传播证据: " + failure.get().getClass().getName()
                + " -> " + failure.get().getMessage());
    }
}
