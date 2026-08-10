package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Recovery 测试（P1-6）。
 *
 * <p>验证：
 * <ul>
 *   <li>官方 {@link JsonFileAgentStateStore} 会话状态持久化；</li>
 *   <li>关闭首个 Agent/Store 后，以同 (userId, sessionId) + 同状态目录重建第二个
 *       Agent/Store，历史会话上下文真实延续（{@code resumeExecution}）；</li>
 *   <li>恢复后的模型请求必须包含第一次保存的特定上下文（验证内容，而非仅 store.exists）；</li>
 *   <li>模拟 {@link ModelRegistry} 重新初始化（reset + 重新注册），不依赖同 JVM 静态注册残留；</li>
 *   <li>等待 resumed execution 完成并验证结果。</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeRecoveryCapabilityTest {

    /** 第一次执行注入并期望恢复后出现的上下文标记。 */
    private static final String CONTEXT_MARKER = "恢复验证专用标记-R2-2026";

    private CompatEndpoint endpoint;
    private Path workspace;
    private Path stateRoot;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-recovery");
        stateRoot = Files.createTempDirectory("nexus-edge-state");
    }

    @AfterAll
    void tearDown() {
        endpoint.close();
    }

    @Test
    @DisplayName("P1-6：官方 JsonFileAgentStateStore 持久化会话并支持内容级验证")
    void stateStorePersistsSessionContent() {
        Path stateDir = stateRoot.resolve("store-1");
        AgentStateStore store = new JsonFileAgentStateStore(stateDir);

        // 写入包含特定上下文标记的状态。
        AgentState state = AgentState.builder().build();
        state.contextMutable().add(io.agentscope.core.message.Msg.builderForRole(
                        io.agentscope.core.message.MsgRole.USER)
                .content(List.of(io.agentscope.core.message.TextBlock.builder()
                        .text("记住：" + CONTEXT_MARKER).build()))
                .build());
        store.save("user-recovery-1", "session-recovery-1", "agent_state", state);

        // 发现 + 内容级验证（不仅是 exists）。
        Set<String> sessionIds = store.listSessionIds("user-recovery-1");
        assertTrue(sessionIds.contains("session-recovery-1"), "官方 State Store 应发现会话");

        // 加载并验证上下文内容。
        var loaded = store.get("user-recovery-1", "session-recovery-1",
                "agent_state", AgentState.class);
        assertTrue(loaded.isPresent(), "应能加载会话状态");
        String loadedText = loaded.get().getContext().stream()
                .map(m -> m.getTextContent())
                .filter(t -> t != null && t.contains(CONTEXT_MARKER))
                .findFirst()
                .orElse("");
        assertTrue(loadedText.contains(CONTEXT_MARKER),
                "加载的会话上下文应包含标记: " + CONTEXT_MARKER);
        store.close();
    }

    @Test
    @DisplayName("P1-6：跨实例 resume 后恢复请求包含第一次的上下文标记，且 ModelRegistry 重新初始化")
    void resumeRestoresContextContentAcrossInstances() throws Exception {
        Path stateDir = stateRoot.resolve("store-2");

        // ---- 第一个执行：注入上下文标记（经 Memory 中间件/会话状态持久化）。 ----
        AgentExecutionReference firstRef;
        try (AgentscopeAgentExecutionAdapter first = new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是恢复验证助手。"),
                null,
                new JsonFileAgentStateStore(stateDir),
                new TestSecretResolver())) {
            firstRef = first.startExecution(new AgentExecutionRequest(
                    "task-recovery-1", "user-recovery-2", "session-recovery-2",
                    "workspace-1", "tenant-1",
                    List.of("请记住这个标记：" + CONTEXT_MARKER)));
            assertNotNull(firstRef);
            assertTrue(TestOtel.isRealTraceId(firstRef.traceId()), "首次执行应返回真实 traceId");

            // 等待首次执行完成，使会话状态落盘。
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline
                    && first.statusOf("task-recovery-1")
                            != AgentExecutionReference.ExecutionStatus.COMPLETED
                    && first.statusOf("task-recovery-1")
                            != AgentExecutionReference.ExecutionStatus.FAILED) {
                Thread.sleep(300);
            }
            assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                    first.statusOf("task-recovery-1"), "首个执行应完成并持久化会话");
        }
        // 第一个 Adapter（含 Agent 与 Store）已关闭。

        // ---- 模拟 ModelRegistry 重新初始化（P1-6）：清空静态注册，不依赖残留。 ----
        ModelRegistry.reset();

        // ---- 第二个 Adapter：同状态目录 + 同 (userId, sessionId)，resume 同一会话。 ----
        AgentExecutionReference resumedRef;
        try (AgentscopeAgentExecutionAdapter second = new AgentscopeAgentExecutionAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是恢复验证助手。"),
                null,
                new JsonFileAgentStateStore(stateDir),
                new TestSecretResolver())) {
            endpoint.resetRequests();
            resumedRef = second.resumeExecution(firstRef, "验证会话上下文延续");
            assertNotNull(resumedRef);
            assertEquals(2, resumedRef.attemptNo(), "resume 应产生第 2 次尝试");
            assertTrue(resumedRef.agentId().matches("[0-9a-f-]{36}"),
                    "resume 应返回真实 Agent 标识（UUID），实际: " + resumedRef.agentId());
            assertFalse(resumedRef.agentId().equals(firstRef.agentId()),
                    "resume 的新 Agent 标识应不同于原执行");
            assertTrue(TestOtel.isRealTraceId(resumedRef.traceId()),
                    "resume 应返回真实 traceId");

            // 等待 resumed execution 完成。
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline
                    && second.statusOf("task-recovery-1")
                            != AgentExecutionReference.ExecutionStatus.COMPLETED
                    && second.statusOf("task-recovery-1")
                            != AgentExecutionReference.ExecutionStatus.FAILED) {
                Thread.sleep(300);
            }
            assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                    second.statusOf("task-recovery-1"), "resume 的执行应真实完成");

            // P1-6 核心：恢复后的模型请求必须包含第一次保存的上下文标记。
            // （Memory 中间件把会话上下文注入恢复请求的系统/消息部分）
            long deadline2 = System.currentTimeMillis() + 10000;
            boolean markerInRequest = false;
            while (System.currentTimeMillis() < deadline2 && !markerInRequest) {
                markerInRequest = endpoint.allRequestBodies().stream()
                        .anyMatch(b -> b.contains(CONTEXT_MARKER));
                if (!markerInRequest) {
                    Thread.sleep(200);
                }
            }
            assertTrue(markerInRequest,
                    "恢复后的模型请求应包含第一次保存的上下文标记: " + CONTEXT_MARKER
                            + "；请求数=" + endpoint.allRequestBodies().size());

            // 内容级验证：State Store 中同一会话的上下文仍含标记。
            AgentStateStore reloaded = new JsonFileAgentStateStore(stateDir);
            var loaded = reloaded.get("user-recovery-2", "session-recovery-2",
                    "agent_state", AgentState.class);
            assertTrue(loaded.isPresent(), "重新加载后会话应存在");
            String loadedText = loaded.get().getContext().stream()
                    .map(m -> m.getTextContent())
                    .filter(t -> t != null && t.contains(CONTEXT_MARKER))
                    .findFirst()
                    .orElse("");
            assertTrue(loadedText.contains(CONTEXT_MARKER),
                    "State Store 中的会话上下文应含标记（内容级验证）");
            reloaded.close();
        }
    }

    @Test
    @DisplayName("P1-6：无 State Store 时 resumeExecution 明确失败（fail-fast）")
    void resumeWithoutStateStoreFailsFast() {
        try (AgentscopeAgentExecutionAdapter noStore = AgentScopeCompatTestSupport.newAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是恢复验证助手。"))) {
            AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                    "task-nostore", "attempt-nostore",
                    "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "user-nostore", "session-nostore");
            try {
                noStore.resumeExecution(ref, "无状态存储");
                throw new AssertionError("无 State Store 时 resumeExecution 应抛异常（fail-fast）");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("AgentStateStore"),
                        "错误信息应说明缺少 State Store");
            }
        }
    }
}
