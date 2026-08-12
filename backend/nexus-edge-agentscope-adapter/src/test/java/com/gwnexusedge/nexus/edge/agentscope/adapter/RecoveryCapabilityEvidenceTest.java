package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSandboxSnapshot;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshot;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.agentscope.extensions.redis.snapshot.RedisSnapshotSpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0004 AgentScope Recovery Capability 实证（CP-2~CP-7）。
 *
 * <p>全部基于 AgentScope 2.0.1 官方 Artifact（Maven 依赖闭包）+ 官方 sources jar 证据 +
 * 可复现实验（真实 Redis Testcontainers / 真实临时目录）；不得 Mock 冒充官方能力。
 * 结论分列：VERIFIED / NOT_VERIFIED（无法实证项如实标记）。
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecoveryCapabilityEvidenceTest {

    /** 真实 Redis 容器（固定版本镜像）。 */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2")
            .withExposedPorts(6379);

    private CompatEndpoint endpoint;
    private Path workspace;
    private JedisPooled redis;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-r4-recovery");
        redis = new JedisPooled(REDIS.getHost(), REDIS.getMappedPort(6379));
    }

    @AfterAll
    void tearDown() {
        // P2/null-safe：@BeforeAll 失败时资源为 null，清理不二次 NPE。
        if (redis != null) {
            redis.close();
        }
        if (endpoint != null) {
            endpoint.close();
        }
    }

    @Test
    @DisplayName("CP-2/CP-4：AgentState 字段持久化往返（含 shutdownInterrupted）+ 跨实例恢复 + transient 运行时对象不序列化")
    void agentStateFieldPersistenceRoundTrip() throws Exception {
        // 官方 AgentState（per-session 可变状态；字段见 sources：userId/sessionId/context/replyId/
        // curIter/shutdownInterrupted/permissionContext/toolContext/tasksContext/planModeContext；
        // interruptControl 为 transient volatile —— 不序列化）。
        String marker = "CP2-MARKER-" + System.nanoTime();
        AgentState state = AgentState.builder()
                .userId("u-cp2")
                .sessionId("s-cp2")
                .addMessage(Msg.builderForRole(MsgRole.USER)
                        .content(List.of(TextBlock.builder().text("记住：" + marker).build()))
                        .build())
                .shutdownInterrupted(true)
                .build();

        // 官方 JsonFileAgentStateStore 序列化/反序列化往返（官方序列化契约）。
        Path dir = Files.createTempDirectory("cp2-state");
        JsonFileAgentStateStore store = new JsonFileAgentStateStore(dir);
        store.save("u-cp2", "s-cp2", "agent_state", state);
        // 跨实例：第二个 store 实例读回（同 (userId, sessionId)）。
        AgentStateStore reloaded = new JsonFileAgentStateStore(dir);
        AgentState loaded = reloaded.get("u-cp2", "s-cp2", "agent_state", AgentState.class)
                .orElseThrow(() -> new AssertionError("AgentState 应可重新加载"));
        // CP-2：跨实例恢复上下文内容。
        boolean markerInContext = loaded.getContext().stream()
                .map(Msg::getTextContent)
                .anyMatch(t -> t != null && t.contains(marker));
        assertTrue(markerInContext, "AgentState context 应跨实例恢复（官方 AgentStateStore）");
        // CP-4：shutdownInterrupted 字段持久化（VERIFIED）；自动续跑 NOT_VERIFIED（本实验不构造续跑断言）。
        assertTrue(loaded.isShutdownInterrupted(), "shutdown_interrupted 字段应经官方序列化持久化（VERIFIED）");
        // 运行时对象不序列化：interruptControl 为 transient volatile（源码实证）。
        assertTrue(Modifier.isTransient(AgentState.class.getDeclaredField("interruptControl").getModifiers()),
                "interruptControl 为 transient，运行时中断控制不序列化（源码证据）");

        // Redis 版（真实 Redis）：同字段往返 + 键空间 `agentscope:session:{user}/{session}:agent_state`。
        RedisAgentStateStoreFactory factory = RedisAgentStateStoreFactory.jedis(
                "test", REDIS.getHost(), REDIS.getMappedPort(6379), null);
        try {
            factory.stateStore().save("u-cp2", "s-cp2", "agent_state", state);
            AgentState fromRedis = factory.stateStore()
                    .get("u-cp2", "s-cp2", "agent_state", AgentState.class)
                    .orElseThrow(() -> new AssertionError("Redis AgentState 应可加载"));
            assertTrue(fromRedis.isShutdownInterrupted(), "Redis 版 shutdownInterrupted 应持久化");
            // keyPrefix = nexus:test:agentscope-session:（DEV-0003 工厂）；官方键结构 {prefix}{user}/{session}:{key}。
            assertTrue(redis.exists("nexus:test:agentscope-session:u-cp2/s-cp2:agent_state"),
                    "Redis 键空间 `{keyPrefix}{user}/{session}:agent_state`（官方键结构实证）");
        } finally {
            factory.close();
        }
    }

    @Test
    @DisplayName("CP-5：LocalSandboxSnapshot payload 持久化往返（官方存储原语；非完整 Sandbox 工作区跨 call 自动恢复）")
    void localSandboxSnapshotRoundTrip() throws Exception {
        Path base = Files.createTempDirectory("cp5-snapshot");
        SandboxSnapshotSpec spec = new LocalSnapshotSpec(base);
        SandboxSnapshot snap = spec.build("snap-cp5");

        // 官方 LocalSandboxSnapshot 是 SandboxSnapshot 存储原语：persist 把输入流原子写
        // {basePath}/{id}.tar。本实验验证的是该存储原语的 payload 往返（任意字节流），
        // **不**验证真实 Sandbox 文件系统被快照、也不验证 Sandbox Manager 在下一 call 自动恢复工作区
        // （后者为 NOT_VERIFIED）。payload 为测试字节流，非"真实 tar 工作区归档"。
        byte[] payload = ("CP5-PAYLOAD-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        snap.persist(new ByteArrayInputStream(payload));

        // restore 后字节与 SHA-256 一致（payload persistence round-trip VERIFIED）。
        byte[] restored = snap.restore().readAllBytes();
        assertArrayEquals(payload, restored, "LocalSandboxSnapshot restore 应与 persist payload 字节一致");
        assertEquals(sha256(payload), sha256(restored), "恢复后 payload Hash 一致");
        assertTrue(snap.isRestorable(), "persist 后 isRestorable 应为 true");
        // 官方原子写：目标文件 {basePath}/snap-cp5.tar 存在。
        assertTrue(Files.exists(base.resolve("snap-cp5.tar")), "官方应写 {basePath}/{id}.tar");
    }

    @Test
    @DisplayName("CP-6：RedisSnapshotSpec payload 持久化往返（真实 Redis；非完整 Sandbox 工作区跨 call 自动恢复）")
    void redisSnapshotRoundTrip() throws Exception {
        // 官方 RedisSnapshotSpec（extends RemoteSnapshotSpec，委托 RedisRemoteSnapshotClient）。
        // 本实验验证 Snapshot 存储原语的 payload 往返；完整 Sandbox 文件系统跨 call 自动恢复为 NOT_VERIFIED。
        try (JedisPooled jedis = new JedisPooled(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            RedisSnapshotSpec spec = new RedisSnapshotSpec(jedis, "nexus:snap:", 60);
            SandboxSnapshot snap = spec.build("snap-cp6");

            byte[] payload = ("CP6-PAYLOAD-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
            snap.persist(new ByteArrayInputStream(payload));
            assertTrue(snap.isRestorable(), "Redis persist 后 isRestorable 应为 true");
            byte[] restored = snap.restore().readAllBytes();
            assertArrayEquals(payload, restored, "RedisSnapshotSpec restore 应与 persist payload 字节一致");
            assertEquals(sha256(payload), sha256(restored), "恢复后 payload Hash 一致（真实 Redis）");
        } // JedisPooled 经 try-with-resources 关闭（评审修正 4：避免连接泄漏）
    }

    @Test
    @DisplayName("CP-7：双向数据面隔离——删除 Snapshot 不影响 AgentState 读取；删除 AgentState 不影响 Snapshot restore")
    void snapshotVsAgentStateDataPlaneIsolation() throws Exception {
        // 黑盒说明：Redis KEYS/DEL 仅用于测试观察与受控清理，**不是生产契约**（评审修正 3）。
        RedisAgentStateStoreFactory factory = RedisAgentStateStoreFactory.jedis(
                "test", REDIS.getHost(), REDIS.getMappedPort(6379), null);
        try (JedisPooled snapJedis = new JedisPooled(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            String marker = "CP7-会话内容-" + System.nanoTime();
            AgentState state = AgentState.builder()
                    .userId("u-cp7").sessionId("s-cp7")
                    .addMessage(Msg.builderForRole(MsgRole.USER)
                            .content(List.of(TextBlock.builder().text(marker).build())).build())
                    .build();
            factory.stateStore().save("u-cp7", "s-cp7", "agent_state", state);

            // Snapshot：键前缀 `nexus:snap:`。
            RedisSnapshotSpec snapSpec = new RedisSnapshotSpec(snapJedis, "nexus:snap:", 60);
            byte[] payload = ("CP7-SNAPSHOT-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
            SandboxSnapshot snap = snapSpec.build("snap-cp7");
            snap.persist(new ByteArrayInputStream(payload));

            String agentStateKey = "nexus:test:agentscope-session:u-cp7/s-cp7:agent_state";
            assertTrue(redis.exists(agentStateKey), "AgentState 键应存在（观察）");

            // 方向 A：删除 Snapshot 后，AgentState 仍能经官方 AgentStateStore 读取且含 Marker（真实读取，非 exists）。
            Set<String> snapshotKeys = redis.keys("nexus:snap:*");
            assertFalse(snapshotKeys.isEmpty(), "Snapshot 键应存在（观察）");
            redis.del(snapshotKeys.toArray(new String[0]));
            AgentState afterSnapshotDelete = factory.stateStore()
                    .get("u-cp7", "s-cp7", "agent_state", AgentState.class)
                    .orElseThrow(() -> new AssertionError("删除 Snapshot 后 AgentState 应可经官方 Store 读取"));
            assertTrue(afterSnapshotDelete.getContext().stream()
                            .map(Msg::getTextContent)
                            .anyMatch(t -> t != null && t.contains(marker)),
                    "方向 A：删除 Snapshot 后 AgentState 读取仍含 Marker");

            // 方向 B：重新创建 Snapshot 后删除 AgentState，Snapshot 仍 isRestorable 且 restore 内容与 payload/Hash 一致。
            byte[] payloadB = ("CP7-SNAPSHOT-B-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
            snapSpec.build("snap-cp7").persist(new ByteArrayInputStream(payloadB));
            redis.del(agentStateKey);
            SandboxSnapshot snapAfterAgentStateDelete = snapSpec.build("snap-cp7");
            assertTrue(snapAfterAgentStateDelete.isRestorable(),
                    "方向 B：删除 AgentState 后 Snapshot 仍 isRestorable");
            byte[] restoredB = snapAfterAgentStateDelete.restore().readAllBytes();
            assertArrayEquals(payloadB, restoredB, "方向 B：Snapshot restore 内容与原 payload 一致");
            assertEquals(sha256(payloadB), sha256(restoredB), "方向 B：Snapshot restore Hash 一致");
        } finally {
            factory.close();
        }
    }

    @Test
    @DisplayName("CP-3：优雅 session interrupt 后 call 结束时 AgentState 已持久化，下一 call 可恢复上下文（非原调用栈续跑）")
    void interruptPersistsAgentStateAndNextCallRecovers() throws Exception {
        Path stateDir = Files.createTempDirectory("cp3-state");
        AgentStateStore store = new JsonFileAgentStateStore(stateDir);
        String marker = "CP3-MARKER-" + System.nanoTime();

        AgentExecutionReference ref;
        try (AgentscopeAgentExecutionAdapter adapter = new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是中断恢复验证助手。"),
                null, store, new TestSecretResolver())) {
            endpoint.setArtificialDelayMillis(3000);
            ref = adapter.startExecution(new AgentExecutionRequest(
                    "task-cp3", "user-cp3", "session-cp3", "ws-cp3", "tn-cp3",
                    List.of("请记住：" + marker)));
            assertNotNull(ref);
            Thread.sleep(500);
            // 优雅中断（interrupt）→ 终态 CANCELLED。
            adapter.cancelExecution(ref);
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline
                    && adapter.statusOf("task-cp3")
                            != AgentExecutionReference.ExecutionStatus.CANCELLED) {
                Thread.sleep(200);
            }
            endpoint.setArtificialDelayMillis(0);
            assertEquals(AgentExecutionReference.ExecutionStatus.CANCELLED,
                    adapter.statusOf("task-cp3"), "优雅中断应确认 CANCELLED");
        }

        // 归因说明（评审修正 2）：
        // - 本实验验证的是 **session interrupt** 路径：cancelExecution → agent.interrupt → call 结束 →
        //   AgentState 已持久化（scoped slot 中 agent_state 存在）→ 下一 call 恢复上下文。
        // - 具体保存由 interrupt handler 还是 call-finalization 路径完成，本实验不归因；
        //   **不得声称 bindStateSaver（process graceful shutdown 路径）是本次 interrupt 触发的保存路径**
        //   （GracefulShutdownManager.bindStateSaver 源码存在，但本实验无其调用证据）。
        // - 进程 shutdown 的自动恢复：NOT_VERIFIED。
        AgentStateStore reloaded = new JsonFileAgentStateStore(stateDir);
        AgentRuntimeIdentityMapper.ScopedIdentity scopedCp3 = AgentRuntimeIdentityMapper.map(
                "tn-cp3", "ws-cp3", "user-cp3", "session-cp3");
        assertTrue(reloaded.exists(scopedCp3.scopedUserId(), scopedCp3.scopedSessionId()),
                "session interrupt 后 scoped slot 中 agent_state 应存在（call 结束保存，VERIFIED）");

        // 下一 call（同 scoped 会话）恢复上下文（VERIFIED）；不声称"原调用栈原位置继续"（NOT_VERIFIED）。
        endpoint.resetRequests();
        try (AgentscopeAgentExecutionAdapter resume = new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是中断恢复验证助手。"),
                null, new JsonFileAgentStateStore(stateDir), new TestSecretResolver())) {
            AgentExecutionReference resumed = resume.resumeExecution(
                    AgentExecutionReference.firstAttempt(
                            "task-cp3", "cp3-0", "00000000-0000-0000-0000-000000000000", "",
                            AgentExecutionReference.ExecutionStatus.COMPLETED,
                            "tn-cp3", "ws-cp3", "user-cp3", "session-cp3"),
                    "中断后恢复上下文");
            assertNotNull(resumed);
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline
                    && resume.statusOf("task-cp3")
                            != AgentExecutionReference.ExecutionStatus.COMPLETED) {
                Thread.sleep(300);
            }
            assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                    resume.statusOf("task-cp3"));
            assertTrue(endpoint.allRequestBodies().stream().anyMatch(b -> b.contains(marker)),
                    "优雅中断后下一 call 应恢复会话上下文（AgentState 持久化，VERIFIED）");
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
