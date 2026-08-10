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
 * DEV-0001 错误传播测试（评审项 7：修复恒真断言）。
 *
 * <p>验证：当模型端点返回 500 时，AgentScope 官方 OpenAI 扩展把错误传播为结构化异常
 * （而非伪装成空结果的成功）。官方行为：重试 2 次后抛出 {@code RetryExhaustedException}。
 * 断言直接基于捕获的异常本身，不使用从未赋值的占位变量。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeErrorPropagationTest {

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-error");
        ModelAssembler.registerOpenAiCompatibleModel(
                "openai:test-model", endpoint.baseUrl(), "test-key");
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
        try {
            agent.call(List.of(new UserMessage("触发服务端错误")), ctx)
                    .block(Duration.ofMinutes(3));
        } catch (Exception e) {
            failure.set(e);
        } finally {
            endpoint.setFailWith500(false);
        }

        // 评审项 7：直接断言捕获的异常（而非从未赋值的 result 产生恒真断言）。
        Throwable error = failure.get();
        assertNotNull(error, "端点 500 应传播为异常，而不是返回成功结果");

        // 官方内置重试：RetryExhaustedException 为预期传播结果（G-03 能力证据）。
        String errorName = error.getClass().getSimpleName();
        String message = error.getMessage() == null ? "" : error.getMessage();
        assertTrue(errorName.contains("RetryExhausted") || message.contains("Retries exhausted")
                        || errorName.contains("OpenAI") || errorName.contains("Http"),
                "错误应以结构化异常传播，实际类型: " + error.getClass().getName()
                        + "，消息: " + message);
        System.out.println("错误传播证据: " + error.getClass().getName() + " -> " + message);
    }
}
