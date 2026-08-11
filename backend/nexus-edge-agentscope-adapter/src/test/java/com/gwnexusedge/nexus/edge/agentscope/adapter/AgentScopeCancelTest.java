package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 取消语义测试（P1-5）。
 *
 * <p>验证：
 * <ol>
 *   <li>{@code cancelExecution} 只置 CANCEL_REQUESTED 并触发官方 interrupt，
 *       终态 CANCELLED 必须由真实中断事件确认（interrupt 恢复消息或中断异常）；</li>
 *   <li>COMPLETED/FAILED/CANCELLED 为互斥终态：cancelExecution 不得把已终态任务改回 CANCELLED；</li>
 *   <li>删除 interrupt（不调用 cancelExecution）时执行正常完成，证明取消确实依赖真实中断。</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeCancelTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-cancel");
        adapter = AgentScopeCompatTestSupport.newAdapter(
                AgentScopeCompatTestSupport.newConfig(
                        endpoint, workspace, "你是取消验证助手。"));
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
    @DisplayName("P1-5：cancelExecution 后由真实中断事件确认 CANCELLED，而非自行标记")
    void cancelWaitsForRealInterrupt() throws Exception {
        // 3 秒延迟使执行长时间处于运行中。
        endpoint.setArtificialDelayMillis(3000);

        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-cancel-1", "user-cancel-1", "session-cancel-1",
                "workspace-1", "tenant-1", List.of("请生成一份很长的报告")));
        assertNotNull(ref);

        assertEquals(AgentExecutionReference.ExecutionStatus.STARTED,
                adapter.statusOf("task-cancel-1"), "执行应处于运行中");

        // 在途请求取消：仅置 CANCEL_REQUESTED + 触发官方 interrupt。
        adapter.cancelExecution(ref);
        assertEquals(AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED,
                adapter.statusOf("task-cancel-1"),
                "cancelExecution 后应先为 CANCEL_REQUESTED，等待真实中断事件确认");

        // 轮询等待真实中断事件将终态推进为 CANCELLED（而非自行设置）。
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-cancel-1")
                        == AgentExecutionReference.ExecutionStatus.CANCEL_REQUESTED) {
            Thread.sleep(200);
        }
        endpoint.setArtificialDelayMillis(0);

        assertEquals(AgentExecutionReference.ExecutionStatus.CANCELLED,
                adapter.statusOf("task-cancel-1"),
                "终态必须由真实中断事件确认为 CANCELLED（不能是自行标记，也不能是 COMPLETED）");
    }

    @Test
    @DisplayName("P1-5：不调用 cancelExecution 时执行正常完成（证明取消依赖真实中断）")
    void withoutCancelExecutionCompletesNormally() throws Exception {
        endpoint.setArtificialDelayMillis(0);
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-cancel-2", "user-cancel-1", "session-cancel-2",
                "workspace-1", "tenant-1", List.of("你好")));
        assertNotNull(ref);

        // 不调用 cancelExecution，等待自然完成。
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-cancel-2") != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-cancel-2") != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }
        assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                adapter.statusOf("task-cancel-2"),
                "未取消的执行应正常完成（证明取消确实依赖真实中断事件）");
    }

    @Test
    @DisplayName("P1-5：已终态（COMPLETED）的任务不得再改为 CANCELLED（互斥终态）")
    void completedTaskCannotBeCancelled() throws Exception {
        endpoint.setArtificialDelayMillis(0);
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-cancel-3", "user-cancel-1", "session-cancel-3",
                "workspace-1", "tenant-1", List.of("你好")));

        // 等待完成。
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf("task-cancel-3") != AgentExecutionReference.ExecutionStatus.COMPLETED
                && adapter.statusOf("task-cancel-3") != AgentExecutionReference.ExecutionStatus.FAILED) {
            Thread.sleep(300);
        }
        assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                adapter.statusOf("task-cancel-3"));

        // 对已完成任务调用 cancelExecution 必须失败（终态互斥）。
        boolean rejected = false;
        try {
            adapter.cancelExecution(ref);
        } catch (IllegalStateException e) {
            rejected = true;
            assertTrue(e.getMessage().contains("终态"), "错误信息应说明已进入终态");
        }
        assertTrue(rejected, "已完成任务调用 cancelExecution 应被拒绝");
        assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                adapter.statusOf("task-cancel-3"), "已完成任务不得改为 CANCELLED");
    }
}
