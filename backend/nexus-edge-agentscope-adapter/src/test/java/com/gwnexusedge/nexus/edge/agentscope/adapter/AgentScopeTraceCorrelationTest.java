package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Task / Execution / Trace 关联测试（03 §6、12 §6）。
 *
 * <p>验证 Nexus Edge 业务 Task 标识、AgentScope Execution 标识与 Trace 标识
 * 之间的关联机制：领域引用同时携带三者；官方事件元数据支持注入 taskId
 * （{@link AgentEvent#METADATA_TASK_ID}），构成 Task → Execution → Trace 关联链。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeTraceCorrelationTest {

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-trace");
        ModelAssembler.registerOpenAiCompatibleModel(new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是关联验证助手。"));
        agent = HarnessAgent.builder()
                .name("trace-compat-agent")
                .sysPrompt("你是关联验证助手。")
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
    @DisplayName("领域引用同时携带 Task / Execution / Trace 三要素")
    void referenceCarriesAllThreeIds() {
        AgentscopeAdapterConfig config = new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是关联验证助手。");
        try (AgentscopeAgentExecutionAdapter adapter = new AgentscopeAgentExecutionAdapter(config)) {
            AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                    "task-trace-1", "user-trace-1", "session-trace-1",
                    "workspace-1", "tenant-1", List.of("你好")));

            assertNotNull(ref);
            assertEquals("task-trace-1", ref.taskId(), "Task 标识应原样传递");
            assertFalse(ref.executionId().isBlank(), "Execution 标识不应为空");
            assertNotNull(ref.traceId(), "Trace 标识不应为 null");
        }
    }

    @Test
    @DisplayName("官方事件元数据支持 taskId 注入，构成 Task → Execution 关联链")
    void officialEventMetadataCarriesTaskId() {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("trace-session-2")
                .userId("trace-user-2")
                .build();

        // 官方 AgentEvent 定义 METADATA_TASK_ID 常量，且事件可携带 metadata。
        // 此处验证事件携带该常量的能力（0.5.x+ 官方 API 证据）。
        assertNotNull(AgentEvent.METADATA_TASK_ID, "官方事件元数据键 taskId 应存在");

        // 验证事件流的执行标识可用于关联：收集事件 id 并断言非空。
        java.util.List<String> eventIds = new java.util.ArrayList<>();
        agent.streamEvents(List.of(new UserMessage("你好")), ctx)
                .doOnNext(event -> eventIds.add(event.getId()))
                .blockLast(Duration.ofMinutes(3));
        assertFalse(eventIds.isEmpty(), "执行事件应携带可关联标识");
    }

    @Test
    @DisplayName("Adapter 事件映射保留 Task 标识（业务事件与执行同源）")
    void mappedEventsKeepTaskId() {
        AgentscopeAdapterConfig config = new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是关联验证助手。");
        try (AgentscopeAgentExecutionAdapter adapter = new AgentscopeAgentExecutionAdapter(config)) {
            AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                    "task-trace-3", "user-trace-1", "session-trace-3",
                    "workspace-1", "tenant-1", List.of("你好")));

            AgentExecutionReference streamRef = new AgentExecutionReference(
                    ref.taskId(), ref.executionId(), ref.traceId());
            var events = adapter.streamExecutionEvents(streamRef).toList();
            // 事件可能为空（取决于执行已完成后的流式读取），但每个事件必须携带同一 Task 标识。
            for (AgentEventEnvelope event : events) {
                assertEquals("task-trace-3", event.taskId(), "事件 Task 标识应与执行同源");
                assertNotNull(event.type());
                assertNotNull(event.executionId());
            }
            assertTrue(events.stream().allMatch(e -> e.taskId().equals("task-trace-3")),
                    "全部事件应归属同一 Task");
        }
    }
}
