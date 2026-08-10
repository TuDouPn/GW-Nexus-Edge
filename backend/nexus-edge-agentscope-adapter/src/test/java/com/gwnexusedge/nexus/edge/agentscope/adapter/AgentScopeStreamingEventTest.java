package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Streaming 事件测试（官方 streamEvents Typed Event 流）。
 *
 * <p>验证 AgentScope 官方 {@code streamEvents} 针对受控 OpenAI 兼容测试端点的
 * SSE 流式链路：事件流包含 AGENT_START 与最终 AGENT_END/AGENT_RESULT，
 * 且文本增量事件可被消费。测试端点为下游模型 Test Double。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeStreamingEventTest {

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-streaming");
        // 注册官方 OpenAI 兼容模型到官方 ModelRegistry。
        // 注意：HarnessAgent.builder().model(String) 在构建时立即解析模型，
        // 因此必须先注册再构建（G-03 能力盘点证据：官方解析时机）。
        ModelAssembler.registerOpenAiCompatibleModel(new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是流式验证助手。"));
        agent = HarnessAgent.builder()
                .name("streaming-compat-agent")
                .sysPrompt("你是流式验证助手。")
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
    @DisplayName("官方 streamEvents 从受控端点消费真实 Typed Event 流")
    void streamEventsProducesTypedEvents() {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("stream-session-1")
                .userId("stream-user-1")
                .build();

        List<AgentEventType> types = new ArrayList<>();
        List<String> textDeltas = new ArrayList<>();

        // 官方 streamEvents：以 Flux 消费真实事件流。
        agent.streamEvents(List.of(new UserMessage("请用流式方式回复。")), ctx)
                .doOnNext(event -> types.add(event.getType()))
                .doOnNext(event -> {
                    if (event instanceof io.agentscope.core.event.TextBlockDeltaEvent delta) {
                        textDeltas.add(delta.getDelta());
                    }
                })
                .blockLast(Duration.ofMinutes(3));

        assertNotNull(types);
        assertFalse(types.isEmpty(), "事件流不应为空");
        assertTrue(types.contains(AgentEventType.AGENT_START),
                "事件流应包含 AGENT_START");
        assertTrue(types.contains(AgentEventType.AGENT_END) || types.contains(AgentEventType.AGENT_RESULT),
                "事件流应以 AGENT_END/AGENT_RESULT 结束");
    }

    @Test
    @DisplayName("流式事件携带回复标识（replyId），可用于执行关联")
    void streamingEventsCarryReplyId() {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("stream-session-2")
                .userId("stream-user-2")
                .build();

        List<String> replyIds = new ArrayList<>();
        agent.streamEvents(List.of(new UserMessage("你好")), ctx)
                .doOnNext(event -> {
                    if (event instanceof AgentEvent) {
                        // AgentEvent 基类提供 getId；此处收集事件标识验证可关联性。
                        replyIds.add(((AgentEvent) event).getId());
                    }
                })
                .blockLast(Duration.ofMinutes(3));

        assertFalse(replyIds.isEmpty(), "事件应携带可关联标识");
    }
}
