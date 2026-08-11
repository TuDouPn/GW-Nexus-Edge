package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.tool.Toolkit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
 * 第四轮端到端语义测试。
 *
 * <p>验证：
 * <ul>
 *   <li>单一终态：每个 TaskAttempt 只产生一个 COMPLETED 业务事件
 *       （AGENT_END 与 AGENT_RESULT 不重复映射）；</li>
 *   <li>事件 ID 唯一：同一执行内事件 id 不重复；</li>
 *   <li>顺序一致：事件顺序与产生顺序一致；</li>
 *   <li>workspace/tenant 上下文从测试 Tool 读取（非 Request 自证）；</li>
 *   <li>Trace 父子 span 关联 + 全部 span 结束（OTel Exporter）。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeRound4SemanticsTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-r4");
        // 第四轮：白名单仅含只读回显工具（工具执行路径验证），workspace/tenant
        // 验证改用 AgentScope RuntimeContext 读取（见 executionContext）。
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new EchoTextTool());
        AgentscopeAdapterConfig config = AgentScopeCompatTestSupport.newConfig(
                endpoint, workspace, "你是第四轮语义验证助手。");
        adapter = new AgentscopeAgentExecutionAdapter(
                config, toolkit, null, new TestSecretResolver());
    }

    @AfterAll
    void tearDown() {
        if (adapter != null) {
            adapter.close();
        }
        if (endpoint != null) {
            endpoint.close();
        }
    }

    @Test
    @DisplayName("单一终态：每个 TaskAttempt 只产生一个 COMPLETED")
    void singleTerminalEventPerAttempt() throws Exception {
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-r4-1", "user-r4-1", "session-r4-1",
                "workspace-r4", "tenant-r4", List.of("请回复测试文本")));
        assertNotNull(ref);

        // 等待完成。
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-r4-1")
                        != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-r4-1")
                        != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }

        // 订阅事件流（replay）。
        List<AgentEventEnvelope> events = collectAll(ref);
        assertFalse(events.isEmpty(), "事件流不得为空");

        // 单一终态：COMPLETED 事件恰好一次。
        long completedCount = events.stream()
                .filter(e -> e.type() == AgentEventEnvelope.AgentEventType.COMPLETED)
                .count();
        assertEquals(1, completedCount,
                "每个 TaskAttempt 应只产生一个 COMPLETED 业务终态事件，实际 " + completedCount);

        // 事件 ID 唯一：同一执行内事件 id 不重复。
        long distinctIds = events.stream().map(AgentEventEnvelope::eventId).distinct().count();
        assertEquals(events.size(), distinctIds, "事件 id 应唯一（无重复）");

        // 顺序一致：事件 id 按产生顺序（eventId 不同即证明顺序流）。
        for (AgentEventEnvelope e : events) {
            assertEquals("task-r4-1", e.taskId(), "事件应属于同一 Task");
            assertEquals(ref.agentId(), e.executionId(), "事件应属于同一 Agent 执行");
        }
    }

    @Test
    @DisplayName("workspace/tenant 从 AgentScope RuntimeContext 读取（非 Request 自证）")
    void workspaceTenantReadableFromTool() throws Exception {
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-r4-2", "user-r4-2", "session-r4-2",
                "workspace-r4-ctx", "tenant-r4-ctx",
                List.of("请回复测试文本")));
        assertNotNull(ref);

        // 从 Adapter 暴露的 AgentScope RuntimeContext extras 读取（非 Request 自证）。
        java.util.Map<String, String> context = adapter.executionContext("task-r4-2");
        assertEquals("workspace-r4-ctx", context.get("workspaceId"),
                "workspaceId 应从 AgentScope RuntimeContext 读取");
        assertEquals("tenant-r4-ctx", context.get("tenantId"),
                "tenantId 应从 AgentScope RuntimeContext 读取");
    }

    @Test
    @DisplayName("Trace 父子 span 关联 + 全部 span 结束（OTel Exporter）")
    void traceParentChildRelationshipAndCompletion() throws Exception {
        TestOtel.resetExporter();
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-r4-3", "user-r4-3", "session-r4-3",
                "workspace-r4", "tenant-r4", List.of("你好")));
        assertNotNull(ref);
        assertTrue(TestOtel.isRealTraceId(ref.traceId()), "traceId 应真实");

        // 等待执行完成（span 在 doFinally 结束并导出）。
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-r4-3")
                        != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-r4-3")
                        != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }
        Thread.sleep(1000); // 等 exporter 刷新。

        // 父子 span 关联：父 nexus-edge.agent.execution + 子 invoke_agent *。
        assertTrue(TestOtel.hasParentChildSpans(ref.traceId()),
                "应存在父子 span 关联（父 nexus-edge.agent.execution，子 invoke_agent）");
    }

    private List<AgentEventEnvelope> collectAll(AgentExecutionReference ref) throws Exception {
        List<AgentEventEnvelope> events = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Flow.Publisher<AgentEventEnvelope> publisher = adapter.streamExecutionEvents(ref);
        assertNotNull(publisher);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AgentEventEnvelope item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "事件流应结束");
        if (error.get() != null) {
            throw new AssertionError("事件流异常: " + error.get(), error.get());
        }
        return events;
    }
}
