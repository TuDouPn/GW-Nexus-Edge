package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.tool.Toolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * DEV-0001 最小真实执行测试（G-01）。
 *
 * <p>验证：Java 21 + Spring Boot 4.1.0 编译产物下，AgentScope Harness/Core 官方代码能够
 * 通过受控 OpenAI 兼容测试端点完成一次真实阻塞执行，并产生 Task ↔ Execution 关联。
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
        adapter = new AgentscopeAgentExecutionAdapter(config, toolkit);
    }

    @AfterAll
    void tearDown() {
        adapter.close();
        endpoint.close();
    }

    @Test
    @DisplayName("官方 HarnessAgent 经受控 OpenAI 兼容端点完成最小真实执行并产生 Execution 关联")
    void startExecutionProducesReference() {
        AgentExecutionRequest request = new AgentExecutionRequest(
                "task-0001",
                "user-0001",
                "session-0001",
                "workspace-0001",
                "tenant-0001",
                List.of("请回复一句测试文本。"));

        AgentExecutionReference reference = adapter.startExecution(request);

        assertNotNull(reference);
        assertNotNull(reference.executionId());
        assertFalse(reference.executionId().isBlank());
        assertTrue(reference.taskId().startsWith("task-"));
        assertNotNull(reference.traceId());
    }

    @Test
    @DisplayName("官方 Provider 真实请求到达测试端点（验证 HTTP 协议链路）")
    void providerReallyCallsEndpoint() {
        // 触发一次执行，确保测试端点收到官方 Provider 的 HTTP 请求。
        adapter.startExecution(new AgentExecutionRequest(
                "task-0002", "user-0001", "session-0001",
                "workspace-0001", "tenant-0001", List.of("你好")));

        String lastBody = endpoint.lastRequestBody();
        assertNotNull(lastBody);
        assertTrue(lastBody.contains("\"model\":\"test-model\""));
        assertTrue(lastBody.contains("你好"));
    }
}
