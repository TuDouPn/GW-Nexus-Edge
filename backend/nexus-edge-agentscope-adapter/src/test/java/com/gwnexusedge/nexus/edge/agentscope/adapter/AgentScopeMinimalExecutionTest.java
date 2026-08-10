package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.tool.Toolkit;
import java.io.IOException;
import java.nio.file.Files;
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
 * DEV-0001 最小真实执行测试（G-01）。
 *
 * <p>验证：Java 21 + Spring Boot 4.1.0 编译产物下，AgentScope Harness/Core 官方代码能够
 * 通过受控 OpenAI 兼容测试端点完成一次真实异步执行，并在长任务完成前返回真实可关联引用。
 * executionId 必须来自官方 {@code getAgentId()}（真实 UUID），禁止合成假 ID。
 *
 * <p>边界：本测试中的测试端点是下游模型 Test Double，只证明运行时与协议集成；
 * 不构成任何模型 Provider 的生产认证（用户修订要求五）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeMinimalExecutionTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-workspace");
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new EchoTextTool());
        AgentscopeAdapterConfig config = new AgentscopeAdapterConfig(
                "openai:test-model",
                endpoint.baseUrl(),
                "test-key",
                workspace.toString(),
                "你是 DEV-0001 兼容性验证助手。");
        adapter = new AgentscopeAgentExecutionAdapter(config, toolkit, null);
    }

    @AfterAll
    void tearDown() {
        adapter.close();
        endpoint.close();
    }

    @Test
    @DisplayName("startExecution 在长任务完成前返回真实可关联引用")
    void startExecutionReturnsReferenceBeforeCompletion() throws Exception {
        endpoint.setArtificialDelayMillis(3000);
        AtomicReference<AgentExecutionReference> refHolder = new AtomicReference<>();
        CountDownLatch returned = new CountDownLatch(1);

        // 在独立线程启动（模拟长任务），验证引用在任务完成前即可返回。
        Thread starter = new Thread(() -> {
            refHolder.set(adapter.startExecution(new AgentExecutionRequest(
                    "task-0001", "user-0001", "session-0001",
                    "workspace-0001", "tenant-0001",
                    List.of("请回复一句测试文本。"))));
            returned.countDown();
        });
        starter.start();

        // 1 秒内应已返回引用（端点延迟 3 秒，任务尚未完成）。
        assertTrue(returned.await(1, TimeUnit.SECONDS),
                "startExecution 应在长任务完成前返回引用");
        AgentExecutionReference ref = refHolder.get();
        assertNotNull(ref);
        // executionId 必须为真实 UUID（官方 getAgentId()），禁止合成 ID。
        assertFalse(ref.executionId().isBlank());
        assertTrue(ref.executionId().matches("[0-9a-f-]{36}"),
                "executionId 应为官方真实标识，实际: " + ref.executionId());
        assertEquals("task-0001", ref.taskId());
        assertEquals(1, ref.attemptNo());
        assertEquals(AgentExecutionReference.ExecutionStatus.STARTED, ref.status());

        // 等待任务完成，验证状态推进到 COMPLETED（轮询而非固定 sleep）。
        endpoint.setArtificialDelayMillis(0);
        long deadline = System.currentTimeMillis() + 15000;
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

        // 等待请求到达端点。
        long deadline = System.currentTimeMillis() + 5000;
        while (endpoint.lastRequestBody() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        String lastBody = endpoint.lastRequestBody();
        assertNotNull(lastBody, "官方 Provider 应真实请求测试端点");
        assertTrue(lastBody.contains("\"model\":\"test-model\""));
        assertTrue(lastBody.contains("你好"));
    }
}
