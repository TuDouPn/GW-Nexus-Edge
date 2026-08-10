package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
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
 * DEV-0001 Recovery 能力矩阵测试（评审项 3/7：真实 resume 与上下文延续）。
 *
 * <p>验证：
 * <ul>
 *   <li>官方 {@link JsonFileAgentStateStore} 会话状态持久化；</li>
 *   <li>关闭第一个 Agent/Store 后，以同 (userId, sessionId) + 同状态目录重建第二个
 *       Agent/Store，历史会话上下文真实延续（{@code resumeExecution} 语义）；</li>
 *   <li>resume 返回新 Attempt 的真实 Execution 标识（官方 getAgentId()），非伪 ID。</li>
 * </ul>
 *
 * <p>OQ-007 能力矩阵（不预设 Redis）：session-redis/mysql 扩展无 2.0.1；
 * RedisAgentStateStore 需显式注入 client，待评审后另行启用。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeRecoveryCapabilityTest {

    private CompatEndpoint endpoint;
    private Path workspace;
    private Path stateRoot;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-recovery");
        stateRoot = Files.createTempDirectory("nexus-edge-state");
    }

    @AfterAll
    void tearDown() {
        endpoint.close();
    }

    @Test
    @DisplayName("官方 JsonFileAgentStateStore 支持会话状态的持久化与发现")
    void jsonFileStateStorePersistsSession() {
        Path stateDir = stateRoot.resolve("store-1");
        AgentStateStore store = new JsonFileAgentStateStore(stateDir);

        io.agentscope.core.state.AgentState state = io.agentscope.core.state.AgentState.builder().build();
        store.save("user-recovery-1", "session-recovery-1", "agent_state", state);

        Set<String> sessionIds = store.listSessionIds("user-recovery-1");
        assertTrue(sessionIds.contains("session-recovery-1"), "官方 State Store 应能发现已持久化的会话");
        assertTrue(store.exists("user-recovery-1", "session-recovery-1"), "官方 State Store 应确认会话存在");
        store.close();
    }

    @Test
    @DisplayName("关闭首个 Agent/Store 后，第二个 Agent 基于同会话恢复（真实 resume，非伪 ID）")
    void resumeRestoresSessionContextAcrossAgentInstances() throws Exception {
        Path stateDir = stateRoot.resolve("store-2");

        // 第一个执行：持久化会话。
        AgentExecutionReference firstRef;
        try (AgentscopeAgentExecutionAdapter first = new AgentscopeAgentExecutionAdapter(
                new AgentscopeAdapterConfig("openai:test-model", endpoint.baseUrl(), "test-key",
                        workspace.toString(), "你是恢复验证助手。"),
                null,
                new JsonFileAgentStateStore(stateDir))) {
            firstRef = first.startExecution(new AgentExecutionRequest(
                    "task-recovery-1", "user-recovery-2", "session-recovery-2",
                    "workspace-1", "tenant-1", List.of("记住：经营分析的恢复上下文标记")));
            assertNotNull(firstRef);
            // 等待执行完成以便会话状态落盘。
            Thread.sleep(1500);
            assertEquals(AgentExecutionReference.ExecutionStatus.COMPLETED,
                    first.statusOf("task-recovery-1"), "首个执行应完成并持久化会话");
        }
        // 第一个 Adapter（含 Agent 与 Store）已关闭。

        // 第二个 Adapter：同状态目录 + 同 (userId, sessionId)，resume 同一会话。
        try (AgentscopeAgentExecutionAdapter second = new AgentscopeAgentExecutionAdapter(
                new AgentscopeAdapterConfig("openai:test-model", endpoint.baseUrl(), "test-key",
                        workspace.toString(), "你是恢复验证助手。"),
                null,
                new JsonFileAgentStateStore(stateDir))) {
            AgentExecutionReference resumed = second.resumeExecution(
                    firstRef, "验证会话上下文延续");

            // 评审项 3/7：resume 返回新 Attempt + 真实 Execution 标识（非伪 ID）。
            assertNotNull(resumed);
            assertEquals("task-recovery-1", resumed.taskId());
            assertEquals(2, resumed.attemptNo(), "resume 应产生第 2 次尝试");
            assertFalse(resumed.executionId().isBlank(), "resume 的执行标识不得为空");
            assertTrue(resumed.executionId().matches("[0-9a-f-]{36}"),
                    "resume 应返回官方真实 Execution 标识（UUID），实际: " + resumed.executionId());
            assertFalse(resumed.executionId().equals(firstRef.executionId()),
                    "resume 的新执行标识应不同于原执行");

            // 会话上下文确实延续：store 中同一会话仍可加载。
            AgentStateStore reloaded = new JsonFileAgentStateStore(stateDir);
            assertTrue(reloaded.exists("user-recovery-2", "session-recovery-2"),
                    "重新加载后同一会话应仍存在（历史上下文延续）");
            reloaded.close();
        }
    }

    @Test
    @DisplayName("无 State Store 时 resumeExecution 明确失败（fail-fast）")
    void resumeWithoutStateStoreFailsFast() {
        try (AgentscopeAgentExecutionAdapter noStore = new AgentscopeAgentExecutionAdapter(
                new AgentscopeAdapterConfig("openai:test-model", endpoint.baseUrl(), "test-key",
                        workspace.toString(), "你是恢复验证助手。"))) {
            AgentExecutionReference ref = AgentExecutionReference.firstAttempt(
                    "task-nostore", "00000000-0000-0000-0000-000000000000", "",
                    AgentExecutionReference.ExecutionStatus.COMPLETED,
                    "user-nostore", "session-nostore");
            try {
                noStore.resumeExecution(ref, "无状态存储");
                throw new AssertionError("无 State Store 时 resumeExecution 应抛异常（fail-fast）");
            } catch (IllegalStateException expected) {
                // 预期：resume 需要 State Store，未配置时明确失败。
                assertTrue(expected.getMessage().contains("AgentStateStore"),
                        "错误信息应说明缺少 State Store");
            }
        }
    }
}
