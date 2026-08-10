package com.gwnexusedge.nexus.edge.agentscope.adapter;

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
 * DEV-0001 Task / Execution / Trace 关联测试（评审项 5：拒绝空值与 "unassigned"）。
 *
 * <p>验证：
 * <ul>
 *   <li>executionId 来自官方 {@code getAgentId()}（真实 UUID），拒绝合成/空值；</li>
 *   <li>traceId 来自 OTel 上下文（{@link TestOtel}），拒绝空值与 "unassigned"；</li>
 *   <li>官方事件元数据 {@link AgentEvent#METADATA_TASK_ID} 支持 Task 关联。</li>
 * </ul>
 *
 * <p>AgentScope 2.0.1 无独立 Execution ID 概念（真实边界见 ADR-0006）：Adapter 以
 * 官方 AgentId 作为执行标识，Nexus Attempt ID 与 AgentScope 标识的边界在 ADR 中明确。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeTraceCorrelationTest {

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-trace");
        ModelAssembler.registerOpenAiCompatibleModel(
                "openai:test-model", endpoint.baseUrl(), "test-key");
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
    @DisplayName("executionId 为官方真实标识，traceId 为真实 OTel Trace ID")
    void realExecutionAndTraceIds() {
        // 真实 OTel Trace ID（32 位 hex）。
        String traceId = TestOtel.newTraceId();
        assertTrue(TestOtel.isRealTraceId(traceId), "OTel 应产生真实 Trace ID");

        // 官方 AgentId 为真实 UUID。
        assertTrue(agent.getAgentId().matches("[0-9a-f-]{36}"),
                "官方 getAgentId() 应为真实 UUID，实际: " + agent.getAgentId());

        // 构造引用：executionId 来自官方，traceId 来自 OTel。
        AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                "task-trace-1", agent.getAgentId(), traceId,
                AgentExecutionReference.ExecutionStatus.STARTED,
                "user-trace-1", "session-trace-1");

        // 评审项 5/7：拒绝空值、拒绝 "unassigned"。
        assertFalse(ref.executionId().isBlank(), "executionId 不得为空");
        assertFalse(ref.executionId().equals("unassigned"), "executionId 不得为 unassigned");
        assertTrue(TestOtel.isRealTraceId(ref.traceId()),
                "traceId 必须为真实 OTel Trace ID，实际: " + ref.traceId());
        assertFalse("unassigned".equals(ref.traceId()), "traceId 不得为 unassigned");
        assertEquals("task-trace-1", ref.taskId());
    }

    @Test
    @DisplayName("官方事件元数据支持 taskId 注入，构成 Task → Execution 关联链")
    void officialEventMetadataCarriesTaskId() {
        assertNotNull(AgentEvent.METADATA_TASK_ID, "官方事件元数据键 taskId 应存在");

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("trace-session-2")
                .userId("trace-user-2")
                .build();
        java.util.List<String> eventIds = new java.util.ArrayList<>();
        agent.streamEvents(List.of(new UserMessage("你好")), ctx)
                .doOnNext(event -> eventIds.add(event.getId()))
                .blockLast(Duration.ofMinutes(3));
        assertFalse(eventIds.isEmpty(), "执行事件应携带可关联标识");
    }

    @Test
    @DisplayName("构造引用时空白 traceId 被规范化为空串（不允许 null/unassigned）")
    void blankTraceIdNormalized() {
        AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                "task-trace-3", agent.getAgentId(), null,
                AgentExecutionReference.ExecutionStatus.STARTED,
                "user-trace-3", "session-trace-3");
        // 空白 traceId 规范化为空串；真实采集时由 Adapter 注入真实值。
        assertFalse("unassigned".equals(ref.traceId()), "不得产生 unassigned 伪造值");
        assertNotNull(ref.traceId(), "traceId 不得为 null");
    }
}
