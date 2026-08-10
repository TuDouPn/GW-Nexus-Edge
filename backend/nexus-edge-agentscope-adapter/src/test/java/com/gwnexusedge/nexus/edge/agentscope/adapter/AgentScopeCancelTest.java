package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 取消语义测试（评审项 2：必须验证在途 Adapter 执行确实被中断）。
 *
 * <p>通过 Adapter 的 {@code cancelExecution} 触发官方 interrupt，并验证：
 * <ol>
 *   <li>执行在运行中（STARTED 或后续状态）时调用取消；</li>
 *   <li>取消后 Adapter 状态推进为 CANCELLED（而非"不抛异常"冒充成功）；</li>
 *   <li>被中断的执行不产生伪成功结果。</li>
 * </ol>
 *
 * <p>Task 状态机（CANCEL_REQUESTED → CANCELLED）属于 Nexus Edge 业务层，
 * 本测试验证 Adapter 层的取消生命周期语义。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeCancelTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-cancel");
        AgentscopeAdapterConfig config = new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是取消验证助手。");
        adapter = new AgentscopeAgentExecutionAdapter(config);
    }

    @AfterAll
    void tearDown() {
        adapter.close();
        endpoint.close();
    }

    @Test
    @DisplayName("cancelExecution 中断在途 Adapter 执行并推进到 CANCELLED")
    void cancelStopsInFlightAdapterExecution() throws Exception {
        // 3 秒延迟使执行长时间处于运行中。
        endpoint.setArtificialDelayMillis(3000);

        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-cancel-1", "user-cancel-1", "session-cancel-1",
                "workspace-1", "tenant-1", List.of("请生成一份很长的报告")));
        assertNotNull(ref);

        // 执行已启动（异步），状态应为 STARTED。
        assertEquals(AgentExecutionReference.ExecutionStatus.STARTED,
                adapter.statusOf("task-cancel-1"), "执行应处于运行中");

        // 在途时请求取消。
        adapter.cancelExecution(ref);

        // 评审项 2：状态必须推进到 CANCELLED，验证中断确实发生。
        assertEquals(AgentExecutionReference.ExecutionStatus.CANCELLED,
                adapter.statusOf("task-cancel-1"),
                "cancelExecution 后 Adapter 状态必须为 CANCELLED（不是不抛异常冒充成功）");

        // 取消后不应推进到 COMPLETED（被中断的执行不产生伪成功）。
        endpoint.setArtificialDelayMillis(0);
        Thread.sleep(500);
        assertTrue(adapter.statusOf("task-cancel-1") != AgentExecutionReference.ExecutionStatus.COMPLETED,
                "被取消的执行不应以 COMPLETED 结束");
    }

    @Test
    @DisplayName("cancelExecution 对已登记执行不抛意外异常")
    void cancelExecutionDoesNotThrowUnexpectedly() throws Exception {
        endpoint.setArtificialDelayMillis(0);
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-cancel-2", "user-cancel-1", "session-cancel-2",
                "workspace-1", "tenant-1", List.of("你好")));

        // 等待执行推进，再取消。
        Thread.sleep(1000);
        adapter.cancelExecution(ref);
        assertTrue(adapter.statusOf("task-cancel-2") != null, "取消后状态应可查询");
    }
}
