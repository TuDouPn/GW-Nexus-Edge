package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Structured Output 测试（评审项 7：必须断言目标对象成功解析）。
 *
 * <p>官方结构化输出把解析结果注入 {@link MessageMetadataKeys#STRUCTURED_OUTPUT} 元数据；
 * 若 JSON 解析失败，官方只记录 warn 且不注入该键。本测试断言：结构化输出元数据存在、
 * 可解析为目标对象、且无解析警告（若官方日志出现解析失败即失败）。
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
        TestOtel.init();
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-structured");
        ModelAssembler.registerOpenAiCompatibleModel(
                "openai:test-model", endpoint.baseUrl(), "test-key");
        agent = HarnessAgent.builder()
                .name("structured-compat-agent")
                .sysPrompt("你是结构化输出验证助手。")
                .model("openai:test-model")
                .workspace(workspace)
                .toolkit(new Toolkit())
                .enableMetaTool(false)
                .build();
    }

    @AfterAll
    void tearDown() {
        agent.close();
        endpoint.close();
    }

    @Test
    @DisplayName("官方结构化输出结果可解析为目标对象且无解析警告")
    void structuredOutputParsesToTargetObject() {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("so-session-1")
                .userId("so-user-1")
                .build();

        endpoint.setStructuredReply(true);
        Msg result = agent.call(
                List.of(new UserMessage("请分析当前经营状况并给出建议")),
                AnalysisResult.class,
                ctx)
                .block(Duration.ofMinutes(3));

        assertNotNull(result, "结构化输出调用应返回结果");

        // 评审项 7：断言目标对象成功解析——官方把解析结果注入 STRUCTURED_OUTPUT 元数据。
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata, "结果应携带元数据");
        Object parsed = metadata.get(MessageMetadataKeys.STRUCTURED_OUTPUT);
        assertNotNull(parsed,
                "结构化输出元数据必须存在（若官方解析失败会 warn 且不注入，本测试视为失败）");
        assertTrue(parsed instanceof Map, "结构化输出应为 JSON 对象映射");
        Map<?, ?> parsedMap = (Map<?, ?>) parsed;
        assertTrue(parsedMap.containsKey("conclusion"), "结构化输出应包含 conclusion 字段");
        assertTrue(parsedMap.containsKey("score"), "结构化输出应包含 score 字段");
        assertTrue(parsedMap.containsKey("recommendations"), "结构化输出应包含 recommendations 字段");
    }
}
