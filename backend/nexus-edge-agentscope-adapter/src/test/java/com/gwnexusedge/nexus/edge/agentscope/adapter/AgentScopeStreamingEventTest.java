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
 * DEV-0001 事件契约测试（P0-2/P1-8）。
 *
 * <p>验证：
 * <ul>
 *   <li>{@code streamExecutionEvents} 返回执行前已建立的 Publisher（不发起第二次执行）；</li>
 *   <li>事件非空、属于同一 Task/Execution、携带事件 id（P1-8：仅声明携带标识，
 *       Last-Event-ID 续传属后续持久化层）；</li>
 *   <li>单 Task 单主调用生命周期（P0-2）。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeStreamingEventTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-streaming");
        adapter = AgentScopeCompatTestSupport.newAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是流式验证助手。"));
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
    @DisplayName("P0-2/P1-8：streamExecutionEvents 返回既有执行的事件流，事件非空、同源、携带标识")
    void streamEventsAreRealNonEmptyAndTraceable() throws Exception {
        endpoint.resetRequests();
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-event-1", "user-event-1", "session-event-1",
                "workspace-1", "tenant-1", List.of("请回复测试文本")));
        assertNotNull(ref);

        // 等待执行完成（记录订阅前请求数）。
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-event-1")
                        != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-event-1")
                        != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }
        int requestsBefore = endpoint.allRequestBodies().size();

        // 订阅既有执行的事件流（执行前已建立的 replay 事件源，不发起第二次执行）。
        Flow.Publisher<AgentEventEnvelope> publisher = adapter.streamExecutionEvents(ref);
        assertNotNull(publisher, "应返回既有执行的 Flow.Publisher");

        List<AgentEventEnvelope> events = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
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

        assertTrue(done.await(15, TimeUnit.SECONDS), "事件流应在限定时间内结束");
        if (error.get() != null) {
            System.out.println("事件流异常（记录）: " + error.get());
        }

        // P1-8：事件不允许为空（replay 已回放完整序列）。
        assertFalse(events.isEmpty(), "事件流不得为空");

        // 事件同源：同一业务 Task 与同一 Agent 标识。
        for (AgentEventEnvelope event : events) {
            assertEquals("task-event-1", event.taskId(), "事件必须属于同一 Task");
            assertEquals(ref.agentId(), event.executionId(), "事件必须属于同一 Agent 执行");
            assertFalse(event.eventId().isBlank(), "事件必须携带可标识 eventId");
            assertNotNull(event.type());
        }

        // P0-2：订阅事件流不发起第二次执行（请求数不变）。
        assertEquals(requestsBefore, endpoint.allRequestBodies().size(),
                "streamExecutionEvents 不得发起第二次执行；订阅前后请求数应一致");
        // 请求数受控（主调用 + tool result + memory extraction 等内部调用）。
        assertTrue(requestsBefore >= 1 && requestsBefore <= 6,
                "请求总数应受控且至少有一次主调用，实际 " + requestsBefore);
    }
}
