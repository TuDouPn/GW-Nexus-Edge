package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
 * DEV-0001 最小真实执行测试（G-01 + P1-4）。
 *
 * <p>验证：Java 21 + Spring Boot 4.1.0 编译产物下，AgentScope Harness/Core 官方代码能够
 * 通过受控 OpenAI 兼容测试端点完成一次真实异步执行，并在长任务完成前返回真实可关联引用。
 *
 * <p>P1-4：traceId 必须在 Adapter 的 {@code startExecution} 真实链路中采集并返回
 * （非测试手工构造），且必须是真实非空 OTel Trace ID。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeMinimalExecutionTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-workspace");
        adapter = AgentScopeCompatTestSupport.newAdapter(
                AgentScopeCompatTestSupport.newConfig(
                        endpoint, workspace, "你是 DEV-0001 兼容性验证助手。"));
    }

    @AfterAll
    void tearDown() {
        adapter.close();
        endpoint.close();
    }

    @Test
    @DisplayName("startExecution 在长任务完成前返回真实引用，且携带真实 OTel traceId（P1-4）")
    void startExecutionReturnsReferenceBeforeCompletion() throws Exception {
        endpoint.setArtificialDelayMillis(3000);
        AtomicReference<AgentExecutionReference> refHolder = new AtomicReference<>();
        CountDownLatch returned = new CountDownLatch(1);

        Thread starter = new Thread(() -> {
            refHolder.set(adapter.startExecution(new AgentExecutionRequest(
                    "task-0001", "user-0001", "session-0001",
                    "workspace-0001", "tenant-0001",
                    List.of("请回复一句测试文本。"))));
            returned.countDown();
        });
        starter.start();

        assertTrue(returned.await(1, TimeUnit.SECONDS),
                "startExecution 应在长任务完成前返回引用");
        AgentExecutionReference ref = refHolder.get();
        assertNotNull(ref);
        // P1-7：taskAttemptId 为 UUIDv7；agentId 为 AgentScope 真实标识。
        assertTrue(ref.taskAttemptId().matches("[0-9a-f-]{36}"),
                "taskAttemptId 应为 UUIDv7，实际: " + ref.taskAttemptId());
        assertTrue(ref.agentId().matches("[0-9a-f-]{36}"),
                "agentId 应为官方真实标识，实际: " + ref.agentId());
        // P1-4：traceId 来自 Adapter 真实链路，且为真实 OTel Trace ID。
        assertTrue(TestOtel.isRealTraceId(ref.traceId()),
                "startExecution 必须返回真实非空 OTel traceId，实际: " + ref.traceId());
        assertEquals(1, ref.attemptNo());
        assertEquals(AgentExecutionReference.ExecutionStatus.STARTED, ref.status());

        // 等待任务完成（轮询）。
        endpoint.setArtificialDelayMillis(0);
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-0001") != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-0001") != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }
        assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                adapter.statusOf("task-0001"), "执行应真实完成");
    }

    @Test
    @DisplayName("官方 Provider 真实请求到达测试端点（验证 HTTP 协议链路）")
    void providerReallyCallsEndpoint() throws Exception {
        endpoint.setArtificialDelayMillis(0);
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-0002", "user-0001", "session-0001",
                "workspace-0001", "tenant-0001", List.of("你好")));
        assertNotNull(ref);
        assertTrue(TestOtel.isRealTraceId(ref.traceId()), "traceId 应为真实 OTel Trace ID");

        long deadline = System.currentTimeMillis() + 10000;
        while (endpoint.lastRequestBody() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        String lastBody = endpoint.lastRequestBody();
        assertNotNull(lastBody, "官方 Provider 应真实请求测试端点");
        assertTrue(lastBody.contains("\"model\":\"test-model\""));
        assertTrue(lastBody.contains("你好"));
    }
}
