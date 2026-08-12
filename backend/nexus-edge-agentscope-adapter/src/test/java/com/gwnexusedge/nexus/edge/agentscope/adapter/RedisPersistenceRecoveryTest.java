package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0003 AgentScope Redis Persistence/Recovery 验证（C-1~C-12）。
 *
 * <p>使用<b>真实 Redis</b>（Testcontainers {@link GenericContainer} redis:7.4.2，评审 0.4-2：
 * 不使用 testcontainers-redis 模块表述）+ 官方 {@code RedisAgentStateStore}
 * （agentscope-extensions-redis 2.0.1）+ 既有 AgentScope Harness 集成，验证：
 * <ul>
 *   <li>C-1 scoped 身份写入真实 Redis；C-2 跨实例（新 Adapter）恢复；</li>
 *   <li>C-3 同一原始 user/session 不同 Tenant/Workspace 完全隔离；</li>
 *   <li>C-4 篡改 tenant/workspace/user/session/task 任一字段 fail-closed；</li>
 *   <li>C-5 缺失/损坏状态 fail-closed；C-6 重复恢复产生新 Attempt；</li>
 *   <li>C-7 Task/Attempt/Agent/Trace 关联；C-8 Redis 中断如实失败；</li>
 *   <li>C-9 Task 完成不误删共享 Session 状态；C-10 受控 Session 删除；</li>
 *   <li>C-11 Redis Client 生命周期（共享，不随 Task 关闭）；C-12 keyPrefix 环境校验。</li>
 * </ul>
 * 禁止 Mock/Fake 冒充 Redis；全部断言命中真实 Redis。
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPersistenceRecoveryTest {

    /** 真实 Redis 容器（固定版本镜像，评审 0.4-2：GenericContainer）。 */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2")
            .withExposedPorts(6379);

    /** 环境命名空间（keyPrefix 环境段；C-12 合法示例）。 */
    private static final String ENV = "test";

    private CompatEndpoint endpoint;
    private Path workspace;
    private RedisAgentStateStoreFactory factory;
    private JedisPooled inspector;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-r3-redis");
        // 共享 RedisAgentStateStore 工厂（C-11：共享 Client，随 Adapter 整体 close 统一关闭）。
        factory = RedisAgentStateStoreFactory.jedis(
                ENV, REDIS.getHost(), REDIS.getMappedPort(6379), null);
        inspector = new JedisPooled(REDIS.getHost(), REDIS.getMappedPort(6379));
    }

    @AfterAll
    void tearDown() {
        // P2/null-safe：@BeforeAll 失败时资源为 null，清理不二次 NPE。
        if (factory != null) {
            factory.close();
        }
        if (inspector != null) {
            inspector.close();
        }
        if (endpoint != null) {
            endpoint.close();
        }
    }

    @Test
    @DisplayName("C-1：scoped 身份下真实 Redis 状态写入")
    void scopedWriteToRealRedis() throws Exception {
        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(factory.stateStore())) {
            AgentExecutionRequest req = new AgentExecutionRequest(
                    "task-c1", "user-c1", "session-c1", "ws-c1", "tn-c1", List.of("C-1 写入验证"));
            AgentExecutionReference ref = adapter.startExecution(req);
            assertNotNull(ref);
            waitForStatus(adapter, "task-c1", AgentExecutionReference.ExecutionStatus.COMPLETED);

            AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                    "tn-c1", "ws-c1", "user-c1", "session-c1");
            // 官方键结构实证：{prefix}{userId}/{sessionId}:{key}
            String ctxKey = RedisAgentStateStoreFactory.keyPrefix(ENV)
                    + scoped.scopedUserId() + "/" + scoped.scopedSessionId()
                    + ":nexus_execution_context:task-c1";
            String agentStateKey = RedisAgentStateStoreFactory.keyPrefix(ENV)
                    + scoped.scopedUserId() + "/" + scoped.scopedSessionId()
                    + ":agent_state";
            assertEquals("OK", inspector.set("__probe__", "1"), "Redis 应可连接（inspector 探活）");
            inspector.del("__probe__");
            assertTrue(inspector.exists(ctxKey), "执行上下文应写入 scoped slot（真实 Redis）");
            assertTrue(inspector.exists(agentStateKey), "Agent 会话状态应写入 scoped slot（真实 Redis）");
        }
    }

    @Test
    @DisplayName("C-2：新 Adapter 实例（跨实例）恢复会话历史")
    void crossInstanceRecovery() throws Exception {
        String marker = "C-2-恢复标记-" + System.nanoTime();
        AgentExecutionReference firstRef;
        try (AgentscopeAgentExecutionAdapter first = newAdapter(factory.stateStore())) {
            firstRef = first.startExecution(new AgentExecutionRequest(
                    "task-c2", "user-c2", "session-c2", "ws-c2", "tn-c2",
                    List.of("请记住：" + marker)));
            assertNotNull(firstRef);
            waitForStatus(first, "task-c2", AgentExecutionReference.ExecutionStatus.COMPLETED);
        } // 第一个 Adapter 关闭；共享 store/factory 仍存活（C-11）。

        // 第二个 Adapter（新实例）：同四 scope 字段 resume，从 scoped slot 恢复。
        endpoint.resetRequests();
        try (AgentscopeAgentExecutionAdapter second = newAdapter(factory.stateStore())) {
            AgentExecutionReference resumed = second.resumeExecution(
                    AgentExecutionReference.firstAttempt(
                            "task-c2", "attempt-c2-0",
                            "00000000-0000-0000-0000-000000000000", "",
                            AgentExecutionReference.ExecutionStatus.COMPLETED,
                            "tn-c2", "ws-c2", "user-c2", "session-c2"),
                    "跨实例恢复验证");
            assertNotNull(resumed);
            assertEquals(2, resumed.attemptNo(), "resume 应产生第 2 次尝试");
            waitForStatus(second, "task-c2", AgentExecutionReference.ExecutionStatus.COMPLETED);

            boolean markerInRequest = endpoint.allRequestBodies().stream()
                    .anyMatch(b -> b.contains(marker));
            assertTrue(markerInRequest, "恢复后的模型请求应包含首次会话内容标记（跨实例恢复）");
        }
    }

    @Test
    @DisplayName("C-3：同一原始 user/session、不同 Tenant/Workspace 完全隔离")
    void tenantWorkspaceIsolation() throws Exception {
        String markerA = "C3-隔离-A-" + System.nanoTime();
        String markerB = "C3-隔离-B-" + System.nanoTime();
        try (AgentscopeAgentExecutionAdapter first = newAdapter(factory.stateStore())) {
            first.startExecution(new AgentExecutionRequest(
                    "task-c3-a", "user-c3", "session-c3", "ws-c3-A", "tn-c3-A",
                    List.of("记住：" + markerA)));
            first.startExecution(new AgentExecutionRequest(
                    "task-c3-b", "user-c3", "session-c3", "ws-c3-B", "tn-c3-B",
                    List.of("记住：" + markerB)));
            waitForStatus(first, "task-c3-a", AgentExecutionReference.ExecutionStatus.COMPLETED);
            waitForStatus(first, "task-c3-b", AgentExecutionReference.ExecutionStatus.COMPLETED);
        }

        try (AgentscopeAgentExecutionAdapter second = newAdapter(factory.stateStore())) {
            // 恢复 A：模型请求含 MARKER_A 且不含 MARKER_B。
            endpoint.resetRequests();
            second.resumeExecution(AgentExecutionReference.firstAttempt(
                    "task-c3-a", "a0", "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tn-c3-A", "ws-c3-A", "user-c3", "session-c3"), "恢复 A");
            waitForStatus(second, "task-c3-a", AgentExecutionReference.ExecutionStatus.COMPLETED);
            assertTrue(endpoint.allRequestBodies().stream().anyMatch(b -> b.contains(markerA)),
                    "恢复 A 应含 MARKER_A");
            assertTrue(endpoint.allRequestBodies().stream().noneMatch(b -> b.contains(markerB)),
                    "恢复 A 不得含 MARKER_B（跨 Tenant/Workspace 隔离）");

            // 恢复 B：模型请求含 MARKER_B 且不含 MARKER_A。
            endpoint.resetRequests();
            second.resumeExecution(AgentExecutionReference.firstAttempt(
                    "task-c3-b", "b0", "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tn-c3-B", "ws-c3-B", "user-c3", "session-c3"), "恢复 B");
            waitForStatus(second, "task-c3-b", AgentExecutionReference.ExecutionStatus.COMPLETED);
            assertTrue(endpoint.allRequestBodies().stream().anyMatch(b -> b.contains(markerB)),
                    "恢复 B 应含 MARKER_B");
            assertTrue(endpoint.allRequestBodies().stream().noneMatch(b -> b.contains(markerA)),
                    "恢复 B 不得含 MARKER_A（跨 Tenant/Workspace 隔离）");

            // 负向：用错误的 Tenant 恢复 A → scoped slot 不同 → fail-closed（跨 Scope 读不到）。
            assertThrows(IllegalStateException.class,
                    () -> second.resumeExecution(AgentExecutionReference.firstAttempt(
                            "task-c3-a", "a1", "00000000-0000-0000-0000-000000000000", "",
                            AgentExecutionReference.ExecutionStatus.COMPLETED,
                            "tn-c3-B", "ws-c3-A", "user-c3", "session-c3"), "跨 Scope 恢复"),
                    "跨 Scope（错误 Tenant）恢复必须 fail-closed");
        }
    }

    @Test
    @DisplayName("C-4：篡改 tenant/workspace/user/session/task 任一字段均 fail-closed")
    void tamperAnyFieldFailsClosed() {
        tamperOneField("task-c4-t", "user-c4", "session-c4", "ws-c4", "tn-c4", "taskId");
        tamperOneField("task-c4-1", "user-c4", "session-c4", "ws-c4", "tn-c4", "tenantId");
        tamperOneField("task-c4-2", "user-c4", "session-c4", "ws-c4", "tn-c4", "workspaceId");
        tamperOneField("task-c4-3", "user-c4", "session-c4", "ws-c4", "tn-c4", "userId");
        tamperOneField("task-c4-4", "user-c4", "session-c4", "ws-c4", "tn-c4", "sessionId");
    }

    /** 篡改一个字段：写入与引用不一致的执行上下文到 scoped slot，resume 应指出该字段 fail-closed。 */
    private void tamperOneField(String taskId, String user, String session,
                                String ws, String tn, String field) {
        AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                tn, ws, user, session);
        String tamperedTaskId = "taskId".equals(field) ? "task-OTHER" : taskId;
        String tamperedTn = "tenantId".equals(field) ? "tenant-OTHER" : tn;
        String tamperedWs = "workspaceId".equals(field) ? "workspace-OTHER" : ws;
        String tamperedUser = "userId".equals(field) ? "user-OTHER" : user;
        String tamperedSession = "sessionId".equals(field) ? "session-OTHER" : session;
        factory.stateStore().save(scoped.scopedUserId(), scoped.scopedSessionId(),
                ExecutionContextState.storeKey(taskId),
                new ExecutionContextState(tamperedTaskId, tamperedWs, tamperedTn,
                        tamperedUser, tamperedSession));

        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(factory.stateStore())) {
            AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                    taskId, "attempt-" + taskId,
                    "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    tn, ws, user, session);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> adapter.resumeExecution(ref, "篡改字段 " + field),
                    "篡改 " + field + " 必须 fail-closed");
            assertTrue(ex.getMessage().contains(field),
                    "错误信息应指出字段 " + field + "，实际: " + ex.getMessage());
        }
        // 清理（JsonFile 无关；Redis 场景直接删除 scoped slot 键集合）。
        factory.stateStore().delete(scoped.scopedUserId(), scoped.scopedSessionId());
    }

    @Test
    @DisplayName("C-5：缺失或损坏状态 fail-closed（不静默创建错误上下文）")
    void missingAndCorruptedFailClosed() {
        // 缺失：scoped slot 无执行上下文。
        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(factory.stateStore())) {
            AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                    "task-c5-missing", "m0", "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tn-c5", "ws-c5", "user-c5", "session-c5");
            assertThrows(IllegalStateException.class,
                    () -> adapter.resumeExecution(ref, "缺失状态"),
                    "缺失执行上下文必须 fail-closed");
        }

        // 损坏：向 scoped slot 的上下文键写入非法 JSON。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                "tn-c5", "ws-c5", "user-c5", "session-c5");
        String ctxKey = RedisAgentStateStoreFactory.keyPrefix(ENV)
                + scoped.scopedUserId() + "/" + scoped.scopedSessionId()
                + ":nexus_execution_context:task-c5-corrupt";
        inspector.set(ctxKey, "{not-valid-json");
        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(factory.stateStore())) {
            AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                    "task-c5-corrupt", "c0", "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tn-c5", "ws-c5", "user-c5", "session-c5");
            assertThrows(RuntimeException.class,
                    () -> adapter.resumeExecution(ref, "损坏状态"),
                    "损坏状态必须 fail-closed（不静默创建错误上下文）");
        }
        inspector.del(ctxKey);
    }

    @Test
    @DisplayName("C-6：重复恢复产生唯一 taskAttemptId；attemptNo 依传入引用递增（业务幂等归 MySQL 服务）")
    void repeatedResumeProducesNewAttempts() throws Exception {
        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(factory.stateStore())) {
            AgentExecutionReference first = adapter.startExecution(new AgentExecutionRequest(
                    "task-c6", "user-c6", "session-c6", "ws-c6", "tn-c6",
                    List.of("C-6 重复恢复")));
            waitForStatus(adapter, "task-c6", AgentExecutionReference.ExecutionStatus.COMPLETED);

            AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                    "task-c6", "attempt-c6-0",
                    "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "tn-c6", "ws-c6", "user-c6", "session-c6");
            AgentExecutionReference r1 = adapter.resumeExecution(ref, "第一次恢复");
            AgentExecutionReference r2 = adapter.resumeExecution(ref, "第二次恢复");
            assertNotNull(r1);
            assertNotNull(r2);
            // 如实口径（评审唯一实现复审 C-6）：
            // - Adapter 每次 resume 产生唯一 taskAttemptId；
            // - attemptNo 依据传入引用递增（同一旧引用重复调用得到相同 attemptNo）；
            // - 真正的并发串行化、幂等与连续 Attempt 编号由后续 MySQL Task Application Service 负责，
            //   本 Adapter 不声称已完成业务 Task 幂等持久化。
            assertEquals(2, r1.attemptNo(), "第一次恢复的 attemptNo 依据引用 attemptNo=1 递增为 2");
            assertEquals(2, r2.attemptNo(), "同一旧引用重复调用得到相同 attemptNo（2）");
            assertEquals(r1.attemptNo(), r2.attemptNo(), "同一引用两次恢复 attemptNo 应相同");
            assertNotEquals(r1.taskAttemptId(), r2.taskAttemptId(),
                    "每次恢复必须产生唯一 taskAttemptId（新 Attempt）");
            assertNotEquals(r1.agentId(), r2.agentId(), "每次恢复必须产生新 Agent 实例标识");
        }
    }

    @Test
    @DisplayName("C-7：Task/Attempt/Agent/Trace 四标识可关联")
    void identityLinkage() throws Exception {
        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(factory.stateStore())) {
            AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                    "task-c7", "user-c7", "session-c7", "ws-c7", "tn-c7",
                    List.of("C-7 标识关联")));
            assertNotNull(ref);
            assertEquals("task-c7", ref.taskId());
            assertTrue(ref.taskAttemptId().matches("[0-9a-f-]{36}"), "taskAttemptId 应为 UUIDv7");
            assertTrue(ref.agentId().matches("[0-9a-f-]{36}"), "agentId 应为真实 Agent 标识");
            assertTrue(TestOtel.isRealTraceId(ref.traceId()), "traceId 应为真实 OTel trace id");
            waitForStatus(adapter, "task-c7", AgentExecutionReference.ExecutionStatus.COMPLETED);
        }
    }

    @Test
    @DisplayName("C-8：Redis 中断如实失败且异常不含 Secret（脱敏，不伪装成功）")
    void redisDownFailsOpenly() {
        // 唯一 sentinel Secret（评审唯一实现复审 P0）：连接失败异常及可捕获诊断不得包含该 Secret。
        String sentinel = "C8-SENTINEL-SECRET-" + System.nanoTime();
        // 指向未监听端口的 Redis（真实连接失败；非 Mock）。口令不拼接进 URI。
        RedisAgentStateStoreFactory deadFactory =
                RedisAgentStateStoreFactory.jedis(ENV, "127.0.0.1", 59999, sentinel);
        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(deadFactory.stateStore())) {
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> adapter.startExecution(new AgentExecutionRequest(
                            "task-c8", "user-c8", "session-c8", "ws-c8", "tn-c8",
                            List.of("C-8 Redis 中断"))),
                    "Redis 中断时 startExecution 必须如实失败（不伪装成功）");
            // 脱敏断言：整条异常链（消息+原因）不得包含 sentinel Secret；只比较是否包含，不输出 Secret 值。
            boolean leak = false;
            for (Throwable t = ex; t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains(sentinel)) {
                    leak = true;
                    break;
                }
            }
            assertFalse(leak, "Redis 连接失败异常不得包含 Secret（sentinel 脱敏断言）");
        } finally {
            deadFactory.close();
        }
    }

    @Test
    @DisplayName("C-9：Task 完成不会误删同一 Session 的 Agent 状态或其他 Task 状态")
    void taskCompletionDoesNotDeleteSharedSession() throws Exception {
        AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                "tn-c9", "ws-c9", "user-c9", "session-c9");
        try (AgentscopeAgentExecutionAdapter adapter = newAdapter(factory.stateStore())) {
            adapter.startExecution(new AgentExecutionRequest(
                    "task-c9-a", "user-c9", "session-c9", "ws-c9", "tn-c9",
                    List.of("C-9 Task A")));
            adapter.startExecution(new AgentExecutionRequest(
                    "task-c9-b", "user-c9", "session-c9", "ws-c9", "tn-c9",
                    List.of("C-9 Task B")));
            waitForStatus(adapter, "task-c9-a", AgentExecutionReference.ExecutionStatus.COMPLETED);
            waitForStatus(adapter, "task-c9-b", AgentExecutionReference.ExecutionStatus.COMPLETED);
        }

        // Task 均完成：Agent 会话状态与另一 Task 的执行上下文必须仍然存在（不误删共享 Session）。
        String prefix = RedisAgentStateStoreFactory.keyPrefix(ENV)
                + scoped.scopedUserId() + "/" + scoped.scopedSessionId() + ":";
        assertTrue(inspector.exists(prefix + "agent_state"),
                "Task 完成后 agent_state 不得被删除（C-9）");
        assertTrue(inspector.exists(prefix + "nexus_execution_context:task-c9-a"),
                "Task A 完成不得删除 Task B 所在共享 Session 的其他状态（C-9）");
        assertTrue(inspector.exists(prefix + "nexus_execution_context:task-c9-b"),
                "Task B 完成不得删除 Task A 的执行上下文（C-9）");
    }

    @Test
    @DisplayName("C-10：受控 Session 删除仅在显式生命周期操作中生效")
    void controlledSessionDeleteOnlyOnExplicitOperation() {
        // 显式调用官方两参 Session 级 delete（独立生命周期操作，非 Task 终态自动清理）。
        AgentRuntimeIdentityMapper.ScopedIdentity scoped = AgentRuntimeIdentityMapper.map(
                "tn-c10", "ws-c10", "user-c10", "session-c10");
        // 预置一个会话状态（直接经官方 store 写入）。
        factory.stateStore().save(scoped.scopedUserId(), scoped.scopedSessionId(),
                "agent_state", new ExecutionContextState("t", "w", "tn", "u", "s"));
        String prefix = RedisAgentStateStoreFactory.keyPrefix(ENV)
                + scoped.scopedUserId() + "/" + scoped.scopedSessionId();
        assertTrue(inspector.exists(prefix + ":agent_state"), "预置会话状态应存在");

        // 显式 Session 删除：该 Session 键全部消失。
        factory.stateStore().delete(scoped.scopedUserId(), scoped.scopedSessionId());
        Set<String> remaining = inspector.keys(prefix + "*");
        assertTrue(remaining.isEmpty(),
                "受控 Session 删除后该 Session 全部键应消失，实际剩余: " + remaining);
    }

    @Test
    @DisplayName("C-11：Redis Client 生命周期——共享 Client，不随 Task/单 Adapter 关闭")
    void sharedClientLifecycle() throws Exception {
        // 两个 Adapter 共享同一 factory/stateStore；关闭第一个后第二个仍可恢复（C-2 语义 + C-11）。
        AgentExecutionReference firstRef;
        try (AgentscopeAgentExecutionAdapter first = newAdapter(factory.stateStore())) {
            firstRef = first.startExecution(new AgentExecutionRequest(
                    "task-c11", "user-c11", "session-c11", "ws-c11", "tn-c11",
                    List.of("C-11 共享客户端")));
            assertNotNull(firstRef);
            waitForStatus(first, "task-c11", AgentExecutionReference.ExecutionStatus.COMPLETED);
        } // 第一个 Adapter 关闭（不关闭共享 store/客户端）。

        endpoint.resetRequests();
        try (AgentscopeAgentExecutionAdapter second = newAdapter(factory.stateStore())) {
            AgentExecutionReference resumed = second.resumeExecution(
                    AgentExecutionReference.firstAttempt(
                            "task-c11", "c11-0", "00000000-0000-0000-0000-000000000000", "",
                            AgentExecutionReference.ExecutionStatus.COMPLETED,
                            "tn-c11", "ws-c11", "user-c11", "session-c11"),
                    "共享客户端恢复验证");
            assertNotNull(resumed);
            waitForStatus(second, "task-c11", AgentExecutionReference.ExecutionStatus.COMPLETED);
            assertTrue(endpoint.allRequestBodies().stream().anyMatch(b -> b.contains("C-11 共享客户端")),
                    "关闭第一个 Adapter 后，共享 Redis Client 仍应可用并完成恢复（C-11）");
        }
    }

    @Test
    @DisplayName("C-12：keyPrefix 环境名校验（防跨环境污染）")
    void keyPrefixEnvironmentValidation() {
        // 非法环境名：含路径分隔符/大写/非法字符 → 拒绝（防跨环境污染）。
        assertThrows(IllegalArgumentException.class,
                () -> RedisAgentStateStoreFactory.keyPrefix("Bad/Env"),
                "含 '/' 的环境名必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> RedisAgentStateStoreFactory.keyPrefix("UPPER"),
                "含大写字母的环境名必须拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> RedisAgentStateStoreFactory.keyPrefix(""),
                "空环境名必须拒绝");
        // 合法环境名：小写字母/数字/连字符。
        assertEquals("nexus:test-env-1:agentscope-session:",
                RedisAgentStateStoreFactory.keyPrefix("test-env-1"));
    }

    /** 构造使用指定 State Store 的 Adapter（白名单空 Toolkit + 测试 Secret 解析器 + 类级 endpoint/workspace）。 */
    private AgentscopeAgentExecutionAdapter newAdapter(
            io.agentscope.core.state.AgentStateStore store) {
        return new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是 Redis 持久化验证助手。"),
                null, store, new TestSecretResolver());
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
}
