package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七轮端到端语义测试。
 *
 * <p>验证：
 * <ul>
 *   <li>P0-1/P0-4：长期 Runtime Identity（{@link AgentRuntimeIdentityMapper}）稳定、
 *       无碰撞、路径安全，且 workspaceId/tenantId 缺失时 fail-closed；</li>
 *   <li>P0-2：跨 Workspace Memory 泄露重写——恢复 A/B 的模型请求分别包含各自标记
 *       且不含对方标记（正负断言），AgentState/Memory 目录不存在跨 Workspace 共享；</li>
 *   <li>P0-3：模型异常时先 FAILED 状态、再发布脱敏 FAILED 业务事件、然后结束事件流，
 *       SSE FAILED 回调内 Task 状态为 FAILED；事件 ID 由 Nexus 生成（UUIDv7）；</li>
 *   <li>P1-1：resume 恢复内容与引用逐项校验（taskId/userId/sessionId），篡改 fail-closed；</li>
 *   <li>P1-2：取消后迟到普通结果不得发布 COMPLETED——每个 TaskAttempt 只出现一个
 *       与最终状态一致的终态事件。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeRound7SemanticsTest {

    /** Task A 注入的隔离标记。 */
    private static final String MARKER_A = "隔离标记-WORKSPACE-A-R7";
    /** Task B 注入的隔离标记。 */
    private static final String MARKER_B = "隔离标记-WORKSPACE-B-R7";

    private CompatEndpoint endpoint;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-r7");
    }

    @AfterAll
    void tearDown() {
        // P2：@BeforeAll 失败时资源为 null，清理必须 null-safe（避免二次 NPE）。
        if (endpoint != null) {
            endpoint.close();
        }
    }

    @Test
    @DisplayName("P0-1/P0-4：Runtime Identity 稳定、无碰撞、路径安全，且 fail-closed")
    void runtimeIdentityIsStableCollisionFreeAndPathSafe() {
        // 稳定：同一输入恒等输出（跨实例/重启可重建）。
        AgentRuntimeIdentityMapper.ScopedIdentity s1 = AgentRuntimeIdentityMapper.map(
                "tenant-7", "workspace-7", "user-7", "session-7");
        AgentRuntimeIdentityMapper.ScopedIdentity s1Again = AgentRuntimeIdentityMapper.map(
                "tenant-7", "workspace-7", "user-7", "session-7");
        assertEquals(s1, s1Again, "Runtime Identity 必须稳定（同一输入恒等输出）");

        // 无碰撞：任一维度不同 → scoped 标识不同。
        assertNotEquals(s1.scopedUserId(),
                AgentRuntimeIdentityMapper.map("tenant-7b", "workspace-7", "user-7", "session-7")
                        .scopedUserId(), "不同 Tenant 必须产生不同 scopedUserId");
        assertNotEquals(s1.scopedUserId(),
                AgentRuntimeIdentityMapper.map("tenant-7", "workspace-7b", "user-7", "session-7")
                        .scopedUserId(), "不同 Workspace 必须产生不同 scopedUserId");
        assertNotEquals(s1.scopedUserId(),
                AgentRuntimeIdentityMapper.map("tenant-7", "workspace-7", "user-7b", "session-7")
                        .scopedUserId(), "不同 User 必须产生不同 scopedUserId");
        assertNotEquals(s1.scopedSessionId(),
                AgentRuntimeIdentityMapper.map("tenant-7", "workspace-7", "user-7", "session-7b")
                        .scopedSessionId(), "不同 Session 必须产生不同 scopedSessionId");

        // 路径安全：仅含安全字符集，不含路径分隔符/危险段/冒号/空白。
        for (String id : List.of(s1.scopedUserId(), s1.scopedSessionId())) {
            assertFalse(id.contains("/"), "scoped 标识不得含路径分隔符 '/'：" + id);
            assertFalse(id.contains("\\"), "scoped 标识不得含反斜杠：" + id);
            assertFalse(id.contains(".."), "scoped 标识不得含 '..'：" + id);
            assertFalse(id.contains(":"), "scoped 标识不得含冒号（禁止冒号拼接方案）：" + id);
            assertFalse(id.isBlank(), "scoped 标识不得为空白");
            assertTrue(id.matches("[A-Za-z0-9._-]+"),
                    "scoped 标识必须由路径安全字符集组成：" + id);
        }
        // 原始业务标识不进入 scoped 标识（不泄露明文业务 ID 于目录结构）。
        assertFalse(s1.scopedUserId().contains("user-7"), "scopedUserId 不得含原始 user 明文");
        assertFalse(s1.scopedSessionId().contains("session-7"), "scopedSessionId 不得含原始 session 明文");

        // fail-closed：workspaceId/tenantId 缺失/空白时拒绝映射。
        assertThrows(IllegalArgumentException.class,
                () -> AgentRuntimeIdentityMapper.map("tenant-7", "  ", "user-7", "session-7"),
                "空白 workspaceId 必须 fail-closed");
        assertThrows(IllegalArgumentException.class,
                () -> AgentRuntimeIdentityMapper.map(null, "workspace-7", "user-7", "session-7"),
                "null tenantId 必须 fail-closed");

        // AgentExecutionRequest 同样 fail-closed：workspaceId/tenantId 非空。
        assertThrows(IllegalArgumentException.class,
                () -> new AgentExecutionRequest("task", "user", "session",
                        "", "tenant-7", List.of("x")));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentExecutionRequest("task", "user", "session",
                        "workspace-7", null, List.of("x")));
    }

    @Test
    @DisplayName("P0-2：跨 Workspace Memory 泄露——恢复 A/B 模型请求 Marker 正负断言 + 目录隔离")
    void crossWorkspaceMemoryIsolationWithRealMarkers() throws Exception {
        Path stateDir = workspace.resolve("r7-state-isolation");
        AgentStateStore store = new JsonFileAgentStateStore(stateDir);

        // 第一阶段：Task A（workspace-R7-A）与 Task B（workspace-R7-B）同 user/session，
        // 各自注入唯一标记（历史内容进入各自 scoped 会话状态）。
        try (AgentscopeAgentExecutionAdapter first = new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是隔离验证助手。"),
                null, store, new TestSecretResolver())) {
            first.startExecution(new AgentExecutionRequest(
                    "task-r7-a", "user-r7", "session-r7",
                    "workspace-R7-A", "tenant-R7-A",
                    List.of("记住标记：" + MARKER_A)));
            first.startExecution(new AgentExecutionRequest(
                    "task-r7-b", "user-r7", "session-r7",
                    "workspace-R7-B", "tenant-R7-B",
                    List.of("记住标记：" + MARKER_B)));
            waitForStatus(first, "task-r7-a",
                    AgentExecutionReference.ExecutionStatus.COMPLETED);
            waitForStatus(first, "task-r7-b",
                    AgentExecutionReference.ExecutionStatus.COMPLETED);
        }

        // AgentState 目录隔离证据：Task A/B 的 scoped 标识不同，状态文件落在不同目录。
        AgentRuntimeIdentityMapper.ScopedIdentity scopedA = AgentRuntimeIdentityMapper.map(
                "tenant-R7-A", "workspace-R7-A", "user-r7", "session-r7");
        AgentRuntimeIdentityMapper.ScopedIdentity scopedB = AgentRuntimeIdentityMapper.map(
                "tenant-R7-B", "workspace-R7-B", "user-r7", "session-r7");
        assertNotEquals(scopedA, scopedB, "Task A/B 的 scoped 标识必须不同（无碰撞）");
        System.out.println("=== AgentState/Memory 目录隔离证据（scopedIdentityA=" + scopedA
                + ", scopedIdentityB=" + scopedB + "） ===");
        try (var paths = Files.walk(stateDir)) {
            List<Path> stateFiles = paths.filter(Files::isRegularFile).toList();
            System.out.println("=== State Store 文件清单: "
                    + stateFiles.stream().map(p -> stateDir.relativize(p).toString()).toList() + " ===");
            assertTrue(stateFiles.stream().anyMatch(p -> contains(p, scopedA)),
                    "Task A 的 AgentState 应落在 scopedA 目录（目录隔离）");
            assertTrue(stateFiles.stream().anyMatch(p -> contains(p, scopedB)),
                    "Task B 的 AgentState 应落在 scopedB 目录（目录隔离）");
            // Memory Hooks 已禁用：不得存在跨 Workspace 共享的 memory/ 台账文件。
            assertFalse(stateFiles.stream().anyMatch(p -> isMemoryLedger(p)),
                    "禁用 Memory Hooks 后 State Store 目录不得出现 memory/ 台账");
        }
        // Memory Flush 落盘路径（工作区）同样不得出现共享台账。
        try (var paths = Files.walk(workspace)) {
            assertFalse(paths.filter(Files::isRegularFile).anyMatch(p -> isMemoryLedger(p)),
                    "禁用 Memory Hooks 后工作区不得出现 memory/ 台账（跨 Workspace 共享）");
        }

        // 第二阶段：分别恢复 Task A 与 Task B，各自捕获模型请求做 Marker 正负断言。
        try (AgentscopeAgentExecutionAdapter resume = new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是隔离验证助手。"),
                null, new JsonFileAgentStateStore(stateDir), new TestSecretResolver())) {

            // 恢复 A：先重置端点，捕获 A 的全部模型请求。
            endpoint.resetRequests();
            endpoint.resetToolCallRounds();
            resume.resumeExecution(AgentExecutionReference.firstAttempt(
                    "task-r7-a", "attempt-r7-a",
                    "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tenant-R7-A", "workspace-R7-A", "user-r7", "session-r7"), "恢复 A");
            waitForStatus(resume, "task-r7-a",
                    AgentExecutionReference.ExecutionStatus.COMPLETED);
            List<String> requestsA = endpoint.allRequestBodies();
            assertFalse(requestsA.isEmpty(), "恢复 A 应产生模型请求");
            assertTrue(requestsA.stream().anyMatch(b -> b.contains(MARKER_A)),
                    "恢复 A 的模型请求必须包含 MARKER_A（历史内容延续）");
            assertTrue(requestsA.stream().noneMatch(b -> b.contains(MARKER_B)),
                    "恢复 A 的模型请求不得包含 MARKER_B（跨 Workspace 内容不泄露）");
            System.out.println("=== 恢复 A 模型请求 Marker 断言: 请求数=" + requestsA.size()
                    + ", 含 MARKER_A=" + requestsA.stream().anyMatch(b -> b.contains(MARKER_A))
                    + ", 含 MARKER_B=" + requestsA.stream().anyMatch(b -> b.contains(MARKER_B))
                    + " ===");

            // 恢复 B：重置端点后捕获 B 的请求。
            endpoint.resetRequests();
            endpoint.resetToolCallRounds();
            resume.resumeExecution(AgentExecutionReference.firstAttempt(
                    "task-r7-b", "attempt-r7-b",
                    "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tenant-R7-B", "workspace-R7-B", "user-r7", "session-r7"), "恢复 B");
            waitForStatus(resume, "task-r7-b",
                    AgentExecutionReference.ExecutionStatus.COMPLETED);
            List<String> requestsB = endpoint.allRequestBodies();
            assertFalse(requestsB.isEmpty(), "恢复 B 应产生模型请求");
            assertTrue(requestsB.stream().anyMatch(b -> b.contains(MARKER_B)),
                    "恢复 B 的模型请求必须包含 MARKER_B（历史内容延续）");
            assertTrue(requestsB.stream().noneMatch(b -> b.contains(MARKER_A)),
                    "恢复 B 的模型请求不得包含 MARKER_A（跨 Workspace 内容不泄露）");
            System.out.println("=== 恢复 B 模型请求 Marker 断言: 请求数=" + requestsB.size()
                    + ", 含 MARKER_B=" + requestsB.stream().anyMatch(b -> b.contains(MARKER_B))
                    + ", 含 MARKER_A=" + requestsB.stream().anyMatch(b -> b.contains(MARKER_A))
                    + " ===");
        }
    }

    @Test
    @DisplayName("P0-3：模型异常时先 FAILED 状态、再发布脱敏 FAILED 事件、然后结束事件流")
    void failedEventAfterModelError() throws Exception {
        endpoint.setFailWith500(true);
        try {
            try (AgentscopeAgentExecutionAdapter adapter = AgentScopeCompatTestSupport.newAdapter(
                    AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是失败验证助手。"))) {
                AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                        "task-r7-fail", "user-r7-fail", "session-r7-fail",
                        "workspace-R7", "tenant-R7", List.of("触发模型失败")));
                assertNotNull(ref);

                // 先到达终态 FAILED（模型/Agent 异常路径）。
                waitForStatus(adapter, "task-r7-fail",
                        AgentExecutionReference.ExecutionStatus.FAILED);

                // SSE 订阅：FAILED 回调内 Task 状态必须已为 FAILED（先状态后事件，P0-3）。
                AtomicReference<AgentExecutionReference.ExecutionStatus> statusAtFailed =
                        new AtomicReference<>();
                List<AgentEventEnvelope> events = new ArrayList<>();
                CountDownLatch failedSeen = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(1);
                Flow.Publisher<AgentEventEnvelope> publisher = adapter.streamExecutionEvents(ref);
                publisher.subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(AgentEventEnvelope item) {
                        events.add(item);
                        if (item.type() == AgentEventEnvelope.AgentEventType.FAILED) {
                            statusAtFailed.set(adapter.statusOf("task-r7-fail"));
                            failedSeen.countDown();
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        done.countDown();
                    }

                    @Override
                    public void onComplete() {
                        done.countDown();
                    }
                });

                assertTrue(failedSeen.await(10, TimeUnit.SECONDS),
                        "应观察到 FAILED 业务事件（Blueprint task.failed 契约）");
                assertEquals(AgentExecutionReference.ExecutionStatus.FAILED,
                        statusAtFailed.get(), "SSE FAILED 回调内 Task 状态应为 FAILED（先状态后事件）");
                assertTrue(done.await(5, TimeUnit.SECONDS), "FAILED 事件后事件流应结束");
                System.out.println("=== FAILED 事件证据: 状态=" + statusAtFailed.get()
                        + ", FAILED 摘要=" + events.stream()
                        .filter(e -> e.type() == AgentEventEnvelope.AgentEventType.FAILED)
                        .map(AgentEventEnvelope::summary).toList() + " ===");

                // FAILED 事件恰好一次；eventId 为 Nexus 生成（UUIDv7 格式）。
                long failedCount = events.stream()
                        .filter(e -> e.type() == AgentEventEnvelope.AgentEventType.FAILED)
                        .count();
                assertEquals(1, failedCount, "FAILED 事件应恰好一次，实际 " + failedCount);
                for (AgentEventEnvelope e : events) {
                    assertTrue(e.eventId().matches("[0-9a-f-]{36}"),
                            "eventId 必须由 Nexus 生成（UUIDv7），实际: " + e.eventId());
                    if (e.type() == AgentEventEnvelope.AgentEventType.FAILED) {
                        // 脱敏：FAILED 摘要不得含 Secret 明文/测试端点凭据。
                        assertFalse(e.summary().contains("test-key"),
                                "FAILED 事件必须脱敏（不得含 Secret 明文）");
                    }
                }
            }
        } finally {
            endpoint.setFailWith500(false);
        }
    }

    @Test
    @DisplayName("P1-1/DEV-0003：resume 恢复内容与引用逐项校验——篡改/错绑 fail-closed（scoped 分区）")
    void resumeRejectsTamperedContext() {
        Path stateDir = workspace.resolve("r7-state-tamper");
        AgentStateStore store = new JsonFileAgentStateStore(stateDir);
        // DEV-0003 修正：执行上下文保存/读取位于 scoped 分区（与 AgentScope 会话同一隔离域），
        // 篡改状态必须写入引用所对应的 scoped slot，resume 才会读到并逐项校验 fail-closed。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped1 = AgentRuntimeIdentityMapper.map(
                "tenant-R7", "workspace-R7", "user-r7-tamper", "session-r7-tamper");

        // 篡改 1：内容 taskId 与键不符（模拟状态错绑/篡改）。
        store.save(scoped1.scopedUserId(), scoped1.scopedSessionId(),
                ExecutionContextState.storeKey("task-r7-tamper"),
                new ExecutionContextState("task-OTHER", "workspace-R7", "tenant-R7",
                        "user-r7-tamper", "session-r7-tamper"));
        assertTampered(store, "task-r7-tamper", "user-r7-tamper", "session-r7-tamper",
                "taskId");

        // 篡改 2：内容 userId 与引用不一致。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped2 = AgentRuntimeIdentityMapper.map(
                "tenant-R7", "workspace-R7", "user-r7-tamper2", "session-r7-tamper2");
        store.save(scoped2.scopedUserId(), scoped2.scopedSessionId(),
                ExecutionContextState.storeKey("task-r7-tamper2"),
                new ExecutionContextState("task-r7-tamper2", "workspace-R7", "tenant-R7",
                        "user-OTHER", "session-r7-tamper2"));
        assertTampered(store, "task-r7-tamper2", "user-r7-tamper2", "session-r7-tamper2",
                "userId");

        // 篡改 3：内容 sessionId 与引用不一致。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped3 = AgentRuntimeIdentityMapper.map(
                "tenant-R7", "workspace-R7", "user-r7-tamper3", "session-r7-tamper3");
        store.save(scoped3.scopedUserId(), scoped3.scopedSessionId(),
                ExecutionContextState.storeKey("task-r7-tamper3"),
                new ExecutionContextState("task-r7-tamper3", "workspace-R7", "tenant-R7",
                        "user-r7-tamper3", "session-OTHER"));
        assertTampered(store, "task-r7-tamper3", "user-r7-tamper3", "session-r7-tamper3",
                "sessionId");
    }

    private void assertTampered(AgentStateStore store, String taskId, String userId,
                                String sessionId, String mismatchField) {
        try (AgentscopeAgentExecutionAdapter adapter = new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是 fail-closed 验证助手。"),
                null, store, new TestSecretResolver())) {
            AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                    taskId, "attempt-" + taskId,
                    "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tenant-R7", "workspace-R7", userId, sessionId);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> adapter.resumeExecution(ref, "恢复被篡改的上下文"),
                    "恢复内容与引用不一致必须 fail-closed 抛异常");
            assertTrue(ex.getMessage().contains(mismatchField),
                    "错误信息应指出不一致字段 " + mismatchField + "，实际: " + ex.getMessage());
        }
        // 清理：删除 scoped 分区中对应键（JsonFileAgentStateStore 支持三参 delete；测试夹具清理）。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                "tenant-R7", "workspace-R7", userId, sessionId);
        store.delete(scoped.scopedUserId(), scoped.scopedSessionId(),
                ExecutionContextState.storeKey(taskId));
    }

    @Test
    @DisplayName("P1-2：取消后迟到普通结果不发布 COMPLETED——单终态且与最终状态一致")
    void cancelThenLateResultEmitsSingleConsistentTerminal() throws Exception {
        ModelAssembler.registerOpenAiCompatibleModel(
                "openai:test-model", endpoint.baseUrl(), TestSecretResolver.TEST_API_KEY);
        HarnessAgent agent = HarnessAgent.builder()
                .name("race-agent")
                .sysPrompt("你是竞态验证助手。")
                .model("openai:test-model")
                .workspace(workspace)
                .build();
        try {
            AgentExecutionRequest request = new AgentExecutionRequest(
                    "task-r7-race", "user-r7-race", "session-r7-race",
                    "workspace-R7", "tenant-R7", List.of("竞态测试"));
            EventStreams<AgentEventEnvelope> stream = EventStreams.replayBounded();
            AgentscopeAgentExecutionAdapter.ExecutionHandle handle =
                    new AgentscopeAgentExecutionAdapter.ExecutionHandle(
                            request, agent, "attempt-r7-race", agent.getAgentId(), stream,
                            RuntimeContext.builder().userId("u").sessionId("s").build());

            // 1) 取消确认：INTERRUPTED 结果 → CANCELLED + 发布 CANCELLED 终态事件。
            AgentscopeAgentExecutionAdapter.applyResultReason(
                    handle, GenerateReason.INTERRUPTED, "执行已取消");
            assertEquals(AgentExecutionReference.ExecutionStatus.CANCELLED, handle.status,
                    "取消结果应推进终态 CANCELLED");

            // 2) 迟到普通结果：不得覆盖已确认的 CANCELLED，也不得发布 COMPLETED。
            AgentscopeAgentExecutionAdapter.applyResultReason(
                    handle, GenerateReason.MODEL_STOP, "迟到的普通结果");
            assertEquals(AgentExecutionReference.ExecutionStatus.CANCELLED, handle.status,
                    "迟到普通结果不得覆盖已确认的 CANCELLED（终态互斥）");

            // 3) 流正常完成：无新终态事件。
            AgentscopeAgentExecutionAdapter.onStreamComplete(handle);

            // 收集全部事件：恰好一个 CANCELLED、零个 COMPLETED——终态事件与最终状态一致。
            List<AgentEventEnvelope> events = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);
            stream.asFlowPublisher().subscribe(new Flow.Subscriber<>() {
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
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS), "事件流应结束");
            long cancelled = events.stream()
                    .filter(e -> e.type() == AgentEventEnvelope.AgentEventType.CANCELLED).count();
            long completed = events.stream()
                    .filter(e -> e.type() == AgentEventEnvelope.AgentEventType.COMPLETED).count();
            assertEquals(1, cancelled,
                    "每个 TaskAttempt 应恰好一个 CANCELLED 终态事件，实际 " + cancelled);
            assertEquals(0, completed,
                    "迟到普通结果不得发布 COMPLETED（与最终状态 CANCELLED 一致）");
            assertEquals(AgentExecutionReference.ExecutionStatus.CANCELLED, handle.status,
                    "最终状态必须为 CANCELLED");
        } finally {
            agent.close();
        }
    }

    /** 轮询等待指定 Task 进入指定终态。 */
    private static void waitForStatus(AgentscopeAgentExecutionAdapter adapter, String taskId,
                                      AgentExecutionReference.ExecutionStatus expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline
                && adapter.statusOf(taskId) != expected) {
            Thread.sleep(300);
        }
        assertEquals(expected, adapter.statusOf(taskId),
                "Task " + taskId + " 应在限定时间内进入 " + expected);
    }

    /** 路径是否同时包含指定 scoped 身份的用户与会话段（AgentState 目录隔离断言）。 */
    private static boolean contains(Path path, AgentRuntimeIdentityMapper.ScopedIdentity scoped) {
        String p = path.toString();
        return p.contains(scoped.scopedUserId()) && p.contains(scoped.scopedSessionId());
    }

    /** 是否为 Memory Hooks 写入的共享台账文件（memory/ 目录下的 .md）。 */
    private static boolean isMemoryLedger(Path path) {
        String p = path.toString();
        return p.contains("/memory/") && p.endsWith(".md");
    }
}
