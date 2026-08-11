package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import java.io.IOException;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第六轮端到端语义测试（第七轮保留项）。
 *
 * <p>验证：
 * <ul>
 *   <li>P1-1：SSE 终态回调内 Task 状态已一致（先状态后事件）。
 *       跨 Workspace Memory 泄露测试已由第七轮 P0-2 重写（Marker 正负断言 +
 *       目录隔离），不再使用 workspaceId 不相等替代内容断言。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeRound6SemanticsTest {

    private CompatEndpoint endpoint;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-r6");
    }

    @AfterAll
    void tearDown() {
        // P2：@BeforeAll 失败时资源为 null，清理必须 null-safe（避免二次 NPE）。
        if (endpoint != null) {
            endpoint.close();
        }
    }

    @Test
    @DisplayName("P1-1：SSE 终态回调内 Task 状态已一致（先状态后事件）")
    void terminalEventObservedAfterStateTransition() throws Exception {
        AgentscopeAdapterConfig config = AgentScopeCompatTestSupport.newConfig(
                endpoint, workspace, "你是终态顺序验证助手。");
        try (AgentscopeAgentExecutionAdapter adapter = new AgentscopeAgentExecutionAdapter(
                config, null, null, new TestSecretResolver())) {
            AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                    "task-r6-order", "user-r6-order", "session-r6-order",
                    "workspace-R6", "tenant-R6", List.of("请回复测试文本")));
            assertNotNull(ref);

            // 在 SSE 终态回调内断言 Task 状态已一致（P1-1）。
            AtomicReference<AgentExecutionReference.ExecutionStatus> statusAtTerminal =
                    new AtomicReference<>();
            CountDownLatch terminalSeen = new CountDownLatch(1);
            Flow.Publisher<AgentEventEnvelope> publisher = adapter.streamExecutionEvents(ref);
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(AgentEventEnvelope item) {
                    // 终态事件回调内，Task 状态必须已推进到一致终态（P1-1）。
                    if (item.type() == AgentEventEnvelope.AgentEventType.COMPLETED
                            || item.type() == AgentEventEnvelope.AgentEventType.CANCELLED
                            || item.type() == AgentEventEnvelope.AgentEventType.FAILED) {
                        statusAtTerminal.set(adapter.statusOf("task-r6-order"));
                        terminalSeen.countDown();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });

            assertTrue(terminalSeen.await(15, TimeUnit.SECONDS),
                    "应观察到终态业务事件");
            AgentExecutionReference.ExecutionStatus status = statusAtTerminal.get();
            assertNotNull(status, "终态事件回调内 Task 状态应已设置");
            // 终态事件与 Task 状态一致：COMPLETED 事件 → 状态 COMPLETED。
            assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED, status,
                    "SSE COMPLETED 回调内 Task 状态应为 COMPLETED（先状态后事件，P1-1）");
        }
    }
}
