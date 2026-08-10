package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Tool Calling 测试（官方 AgentScope Tool 链路）。
 *
 * <p>通过官方 {@code @Tool} 注解 + 官方 {@link Toolkit} 注册工具，注入 HarnessAgent；
 * 受控测试端点返回 tool_call 后，AgentScope 官方 Runtime 会执行 {@code echo_text} 工具。
 * 本测试验证：官方 Tool 定义被真实发送到模型端点，且工具被官方 Runtime 调用。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeToolCallingTest {

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-tool");
        ModelAssembler.registerOpenAiCompatibleModel(new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是一个会调用工具的助手。"));

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new EchoTextTool());

        agent = HarnessAgent.builder()
                .name("tool-compat-agent")
                .sysPrompt("你是一个会调用工具的助手。")
                .model("openai:test-model")
                .workspace(workspace)
                .toolkit(toolkit)
                .build();
    }

    @AfterAll
    void tearDown() {
        agent.close();
        endpoint.close();
    }

    @Test
    @DisplayName("官方 Toolkit 工具定义被真实发送到模型端点")
    void toolSchemaIsSentToEndpoint() {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("tool-session-1")
                .userId("tool-user-1")
                .build();

        endpoint.resetRequests();
        // 触发一次带工具的调用（测试端点检测 tools 后返回 tool_call）。
        agent.call(List.of(new UserMessage("调用 echo_text 工具")), ctx)
                .block(Duration.ofMinutes(3));

        // 主调用请求（含用户消息）应携带工具定义；Memory 中间件的 extraction 请求不含。
        boolean mainRequestHasTool = endpoint.allRequestBodies().stream()
                .filter(body -> body.contains("\"role\":\"user\""))
                .anyMatch(body -> body.contains("echo_text") && body.contains("\"tools\""));
        assertTrue(mainRequestHasTool, "主调用请求应包含注册的工具定义 echo_text 与 tools 数组");
    }

    @Test
    @DisplayName("官方 Runtime 在收到 tool_call 后实际执行注册的工具")
    void toolIsActuallyInvoked() {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("tool-session-2")
                .userId("tool-user-2")
                .build();

        AtomicBoolean toolStartObserved = new AtomicBoolean(false);
        List<AgentEvent> events = new ArrayList<>();

        agent.streamEvents(List.of(new UserMessage("调用 echo_text 工具")), ctx)
                .doOnNext(events::add)
                .blockLast(Duration.ofMinutes(3));

        for (AgentEvent event : events) {
            if (event instanceof ToolCallStartEvent tool
                    && "echo_text".equals(tool.getToolCallName())) {
                toolStartObserved.set(true);
            }
        }
        assertTrue(toolStartObserved.get(), "应观察到官方 TOOL_CALL_START 事件中的 echo_text 工具");
    }
}
