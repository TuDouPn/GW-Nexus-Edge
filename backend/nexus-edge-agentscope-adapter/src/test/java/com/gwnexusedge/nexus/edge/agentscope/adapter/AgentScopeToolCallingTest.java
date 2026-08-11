package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.tool.Toolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Tool 安全边界测试（P0-1/P0-2）。
 *
 * <p>P0-1：断言构建后 Agent 的真实工具面（{@code agent.getToolkit().getToolNames()}）
 * 不包含 read_file/write_file/edit_file/execute 等危险工具；disableFilesystemTools/
 * disableShellTool/disableSubagents 等已由 {@code buildSecureAgent} 应用。
 *
 * <p>P0-2：一个 Task 只产生一次主调用生命周期；streamExecutionEvents 不发起第二次执行。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeToolCallingTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-tool");
        Toolkit toolkit = new Toolkit();
        // 显式白名单：仅注册只读回显工具。
        toolkit.registerTool(new EchoTextTool());
        AgentscopeAdapterConfig config = AgentScopeCompatTestSupport.newConfig(
                endpoint, workspace, "你是一个会调用工具的助手。");
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
    @DisplayName("P0-1：构建后 Agent 工具面不含危险工具（构建后 getToolkit() 断言）")
    void toolSurfaceIsSecureAfterBuild() throws Exception {
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-secure-1", "user-secure-1", "session-secure-1",
                "workspace-1", "tenant-1", List.of("你好")));
        assertNotNull(ref);

        // 等待 Agent 构建完成并登记（异步启动后 handle 已存在）。
        long deadline = System.currentTimeMillis() + 5000;
        Set<String> surface = Set.of();
        while (System.currentTimeMillis() < deadline && surface.isEmpty()) {
            surface = adapter.toolSurface("task-secure-1");
            if (surface.isEmpty()) {
                Thread.sleep(100);
            }
        }

        System.out.println("=== 构建后工具面（getToolkit().getToolNames()）: " + surface + " ===");
        // 禁止危险工具名（P0-1）。
        for (String forbidden : List.of("read_file", "write_file", "edit_file",
                "execute", "shell", "shell_command", "run_command", "bash", "terminal")) {
            assertTrue(surface.stream().noneMatch(n -> n.toLowerCase().contains(forbidden)),
                    "构建后工具面不得包含危险工具: " + forbidden
                            + "；实际工具面: " + surface);
        }
        // 白名单工具应可见（若端点触发工具调用）。
        // 注：断言重点是"不含危险工具"，白名单 echo_text 是否出现在请求由端点行为决定。
    }

    @Test
    @DisplayName("P0-2：一个 Task 只产生一次主调用生命周期，streamExecutionEvents 不发起第二次执行")
    void singleMainInvocationPerTask() throws Exception {
        endpoint.resetRequests();
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-single-1", "user-single-1", "session-single-1",
                "workspace-1", "tenant-1", List.of("请回复测试文本")));
        assertNotNull(ref);

        // 等待执行完成（记录请求序列）。
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-single-1")
                        != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-single-1")
                        != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }
        int requestsBeforeSubscription = endpoint.allRequestBodies().size();
        System.out.println("=== 单执行源证据: 完成时请求总数=" + requestsBeforeSubscription + " ===");

        // P0-2 结构性保证：订阅事件流只返回执行前建立的 Publisher，
        // 绝不发起第二次执行——订阅前后请求数必须一致。
        var publisher = adapter.streamExecutionEvents(ref);
        assertNotNull(publisher);
        Thread.sleep(500); // 给潜在（不应存在的）第二次执行留出时间窗口
        int requestsAfterSubscription = endpoint.allRequestBodies().size();

        System.out.println("=== 单执行源证据: 订阅后请求总数=" + requestsAfterSubscription + " ===");
        assertEquals(requestsBeforeSubscription, requestsAfterSubscription,
                "streamExecutionEvents 不得发起第二次执行（请求数应保持不变）");
        // 请求总数受控（主调用 + tool result + memory extraction 等内部调用；
        // OTel 中间件启用后正常执行轮次 ≤ 8）。
        assertTrue(requestsAfterSubscription >= 1 && requestsAfterSubscription <= 8,
                "请求总数应受控且至少有一次主调用，实际 " + requestsAfterSubscription);
    }
}
