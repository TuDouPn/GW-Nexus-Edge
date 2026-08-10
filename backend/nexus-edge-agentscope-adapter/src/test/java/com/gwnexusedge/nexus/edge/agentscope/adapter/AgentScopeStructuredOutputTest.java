package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Structured Output 测试（官方结构化输出机制）。
 *
 * <p>通过官方 {@code call(List, Class, RuntimeContext)} 触发结构化输出路径：
 * 官方实现会尝试 native structured output（response_format）或 fallback 到
 * {@code generate_response} 工具。本测试验证该机制真实调用到模型端点，
 * 且返回的 {@link Msg} 可被解析。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeStructuredOutputTest {

    /** 结构化输出目标结构（验证官方 JSON Schema 生成路径）。 */
    public record AnalysisResult(String conclusion, double score, List<String> recommendations) {}

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-structured");
        ModelAssembler.registerOpenAiCompatibleModel(new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是结构化输出验证助手。"));
        agent = HarnessAgent.builder()
                .name("structured-compat-agent")
                .sysPrompt("你是结构化输出验证助手。")
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
    @DisplayName("官方结构化输出调用真实到达模型端点")
    void structuredOutputCallReachesEndpoint() {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("so-session-1")
                .userId("so-user-1")
                .build();

        endpoint.resetRequests();
        Msg result = agent.call(
                List.of(new UserMessage("请分析当前经营状况并给出建议")),
                AnalysisResult.class,
                ctx)
                .block(Duration.ofMinutes(3));

        assertNotNull(result, "结构化输出调用应返回结果");

        // 主调用请求应体现结构化输出路径：native response_format 或 generate_response 工具。
        boolean structuredPathObserved = endpoint.allRequestBodies().stream()
                .filter(body -> body.contains("\"role\":\"user\""))
                .anyMatch(body -> body.contains("response_format") || body.contains("generate_response"));
        assertTrue(structuredPathObserved,
                "结构化输出调用应通过官方 response_format 或 generate_response 路径到达端点");
    }
}
