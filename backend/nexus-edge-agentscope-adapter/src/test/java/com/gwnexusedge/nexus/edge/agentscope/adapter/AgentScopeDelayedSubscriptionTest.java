package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
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
 * DEV-0001 延迟订阅完整事件测试（P0-2 关键证据）。
 *
 * <p>验证：事件源（EventSink replay）在执行前建立；即使执行已完成、事件早已产生，
 * 延迟订阅者仍能收到完整事件序列（修复前 SubmissionPublisher 会丢失早期事件）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeDelayedSubscriptionTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-delayed");
        adapter = AgentScopeCompatTestSupport.newAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是延迟订阅验证助手。"));
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
    @DisplayName("P0-2：执行完成后延迟订阅仍收到完整事件序列")
    void delayedSubscriptionReceivesFullEventSequence() throws Exception {
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-delayed-1", "user-delayed-1", "session-delayed-1",
                "workspace-1", "tenant-1", List.of("请回复测试文本")));
        assertNotNull(ref);

        // 等待执行完成（事件早已产生并缓冲）。
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-delayed-1")
                        != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-delayed-1")
                        != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }
        assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                adapter.statusOf("task-delayed-1"), "执行应完成");

        // P0-2 核心：执行完成后才订阅，仍应收到完整事件序列（replay 缓冲）。
        List<AgentEventEnvelope> events = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Flow.Publisher<AgentEventEnvelope> publisher = adapter.streamExecutionEvents(ref);
        assertNotNull(publisher, "应返回事件源");

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

        assertTrue(done.await(10, TimeUnit.SECONDS), "延迟订阅应在限定时间内完成");
        if (error.get() != null) {
            System.out.println("延迟订阅异常（记录）: " + error.get());
        }

        // 延迟订阅必须收到完整事件序列（非空，且至少包含 AGENT_START 与结束事件）。
        assertFalse(events.isEmpty(), "延迟订阅不得丢失事件（replay 应回放完整序列）");
        assertTrue(events.stream().anyMatch(e -> e.type() == AgentEventEnvelope.AgentEventType.STARTED),
                "事件序列应包含 STARTED");
        assertTrue(events.stream().anyMatch(e -> e.type() == AgentEventEnvelope.AgentEventType.COMPLETED),
                "事件序列应包含 COMPLETED");
        // 事件同源。
        for (AgentEventEnvelope event : events) {
            assertEquals("task-delayed-1", event.taskId());
            assertEquals(ref.agentId(), event.executionId());
            assertFalse(event.eventId().isBlank());
        }
    }
}
