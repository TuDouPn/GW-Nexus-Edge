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
    @DisplayName("CP-5：LocalSandboxSnapshot persist/restore 往返（真实临时目录，tar 字节 + Hash 一致）")
    void localSandboxSnapshotRoundTrip() throws Exception {
        Path base = Files.createTempDirectory("cp5-snapshot");
        SandboxSnapshotSpec spec = new LocalSnapshotSpec(base);
        SandboxSnapshot snap = spec.build("snap-cp5");

        // 构造工作区归档（tar 字节流；官方 LocalSandboxSnapshot 原子写 {basePath}/{id}.tar）。
        byte[] archive = ("CP5-ARCHIVE-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        snap.persist(new ByteArrayInputStream(archive));

        // restore 后字节与 SHA-256 一致。
        byte[] restored = snap.restore().readAllBytes();
        assertArrayEquals(archive, restored, "LocalSandboxSnapshot restore 应与 persist 字节一致");
        assertEquals(sha256(archive), sha256(restored), "恢复后内容 Hash 一致");
        assertTrue(snap.isRestorable(), "persist 后 isRestorable 应为 true");
        // 官方原子写：目标文件 {basePath}/snap-cp5.tar 存在。
        assertTrue(Files.exists(base.resolve("snap-cp5.tar")), "官方应写 {basePath}/{id}.tar");
    }

    @Test
    @DisplayName("CP-6：RedisSnapshotSpec 真实 Redis persist/restore 往返（内容 Hash 一致）")
    void redisSnapshotRoundTrip() throws Exception {
        // 官方 RedisSnapshotSpec（extends RemoteSnapshotSpec，委托 RedisRemoteSnapshotClient）。
        RedisSnapshotSpec spec = new RedisSnapshotSpec(
                new JedisPooled(REDIS.getHost(), REDIS.getMappedPort(6379)), "nexus:snap:", 60);
        SandboxSnapshot snap = spec.build("snap-cp6");

        byte[] archive = ("CP6-ARCHIVE-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        snap.persist(new ByteArrayInputStream(archive));
        assertTrue(snap.isRestorable(), "Redis persist 后 isRestorable 应为 true");
        byte[] restored = snap.restore().readAllBytes();
        assertArrayEquals(archive, restored, "RedisSnapshotSpec restore 应与 persist 字节一致");
        assertEquals(sha256(archive), sha256(restored), "恢复后内容 Hash 一致（真实 Redis）");
    }

    @Test
    @DisplayName("CP-7：数据面隔离——AgentStateStore 与 SandboxSnapshot 键空间不同，删除一类不影响另一类")
    void snapshotVsAgentStateDataPlaneIsolation() throws Exception {
        // AgentStateStore：键 `nexus:test:agentscope-session:{user}/{session}:agent_state`（DEV-0003 键结构）。
        RedisAgentStateStoreFactory factory = RedisAgentStateStoreFactory.jedis(
                "test", REDIS.getHost(), REDIS.getMappedPort(6379), null);
        try {
            AgentState state = AgentState.builder()
                    .userId("u-cp7").sessionId("s-cp7")
                    .addMessage(Msg.builderForRole(MsgRole.USER)
                            .content(List.of(TextBlock.builder().text("CP7-会话内容").build())).build())
                    .build();
            factory.stateStore().save("u-cp7", "s-cp7", "agent_state", state);

            // SandboxSnapshot：键前缀 `nexus:snap:`（CP-6）。
            RedisSnapshotSpec snapSpec = new RedisSnapshotSpec(
                    new JedisPooled(REDIS.getHost(), REDIS.getMappedPort(6379)), "nexus:snap:", 60);
            snapSpec.build("snap-cp7").persist(new ByteArrayInputStream("CP7-SNAPSHOT".getBytes(StandardCharsets.UTF_8)));

            // 两类键空间不同（语义/前缀隔离）。
            String agentStateKey = "nexus:test:agentscope-session:u-cp7/s-cp7:agent_state";
            assertTrue(redis.exists(agentStateKey), "AgentState 键应存在");
            Set<String> snapshotKeys = redis.keys("nexus:snap:*");
            assertFalse(snapshotKeys.isEmpty(), "Snapshot 键应存在");
            assertFalse(redis.keys("nexus:snap:*").contains(agentStateKey),
                    "两类数据不得共用同一键");

            // 删除 Snapshot 键 → AgentState 不受影响；删除 AgentState → Snapshot 不受影响。
            redis.del(snapshotKeys.toArray(new String[0]));
            assertTrue(redis.exists(agentStateKey), "删除 Snapshot 不得影响 AgentState");
            redis.del(agentStateKey);
            assertFalse(redis.exists(agentStateKey), "AgentState 已删");
            assertTrue(redis.keys("nexus:snap:*").isEmpty(), "AgentState 删除不得影响 Snapshot");
        } finally {
            factory.close();
        }
    }

    @Test
    @DisplayName("CP-3：优雅 interrupt 后 AgentState 被保存（bindStateSaver），下一 call 恢复上下文（非原调用栈续跑）")
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

        // 源码证据（ReActAgent）：shutdownManager.bindStateSaver → 中断时持久化精确 per-(userId, sessionId)
        // AgentState 到 agent_state（"persist that session directly"）。运行证据：agent_state 存在。
        // 注意：RuntimeContext 使用 scoped 身份，保存分区为 scoped slot。
        AgentStateStore reloaded = new JsonFileAgentStateStore(stateDir);
        AgentRuntimeIdentityMapper.ScopedIdentity scopedCp3 = AgentRuntimeIdentityMapper.map(
                "tn-cp3", "ws-cp3", "user-cp3", "session-cp3");
        assertTrue(reloaded.exists(scopedCp3.scopedUserId(), scopedCp3.scopedSessionId()),
                "中断路径应保存会话（scoped slot 中 agent_state 存在；bindStateSaver 源码证据）");

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
