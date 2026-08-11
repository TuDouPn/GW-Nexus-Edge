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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 并发 Trace 隔离测试（P0-1 关键证据）。
 *
 * <p>验证：两个并发 Task 各自创建独立 OTel span 与 Trace ID，互不泄漏、互不污染
 * （修复前调用线程 makeCurrent 会导致 span 上下文泄漏与并发交叉）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeConcurrentTraceIsolationTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-concurrent");
        adapter = AgentScopeCompatTestSupport.newAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是并发 Trace 验证助手。"));
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
    @DisplayName("P0-1：两个并发 Task 的 traceId 相互隔离")
    void concurrentTasksHaveIsolatedTraceIds() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicReference<AgentExecutionReference> refA = new AtomicReference<>();
        AtomicReference<AgentExecutionReference> refB = new AtomicReference<>();

        Thread taskA = new Thread(() -> {
            refA.set(adapter.startExecution(new AgentExecutionRequest(
                    "task-iso-a", "user-iso", "session-iso-a",
                    "workspace-a", "tenant-a", List.of("并发任务 A"))));
            bothStarted.countDown();
        });
        Thread taskB = new Thread(() -> {
            refB.set(adapter.startExecution(new AgentExecutionRequest(
                    "task-iso-b", "user-iso", "session-iso-b",
                    "workspace-b", "tenant-b", List.of("并发任务 B"))));
            bothStarted.countDown();
        });

        taskA.start();
        taskB.start();
        assertTrue(bothStarted.await(10, TimeUnit.SECONDS),
                "两个并发 Task 应同时启动");

        AgentExecutionReference a = refA.get();
        AgentExecutionReference b = refB.get();
        assertNotNull(a);
        assertNotNull(b);

        // 每个 Task 的 traceId 都必须是真实 OTel Trace ID。
        assertTrue(TestOtel.isRealTraceId(a.traceId()), "Task A traceId 应真实，实际: " + a.traceId());
        assertTrue(TestOtel.isRealTraceId(b.traceId()), "Task B traceId 应真实，实际: " + b.traceId());

        // P0-1 核心：两个并发 Task 的 traceId 必须不同（隔离，无泄漏交叉）。
        assertFalse(a.traceId().equals(b.traceId()),
                "并发 Task 的 traceId 必须相互隔离；A=" + a.traceId() + " B=" + b.traceId());

        // taskAttemptId 也必须独立。
        assertFalse(a.taskAttemptId().equals(b.taskAttemptId()),
                "并发 Task 的 taskAttemptId 必须独立");
    }
}
