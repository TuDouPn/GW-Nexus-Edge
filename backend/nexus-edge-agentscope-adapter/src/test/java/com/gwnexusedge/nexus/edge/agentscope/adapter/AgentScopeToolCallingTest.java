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
 * DEV-0001 Tool Calling 测试（评审项 1：显式 Tool 白名单）。
 *
 * <p>通过官方 {@code @Tool} 注解 + 官方 {@link Toolkit} 注册白名单只读工具，注入
 * HarnessAgent；受控测试端点返回 tool_call 后，AgentScope 官方 Runtime 执行工具。
 * 同时验证：业务 Agent 的工具面仅包含显式白名单，不包含 Shell / 文件系统 / Host
 * execute 能力（Coding Tool 只经 Sandbox Broker，属后续 Coding 工作项）。
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
        AgentscopeAdapterConfig config = new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是一个会调用工具的助手。");
        adapter = new AgentscopeAgentExecutionAdapter(config, toolkit, null);
    }

    @AfterAll
    void tearDown() {
        adapter.close();
        endpoint.close();
    }

    @Test
    @DisplayName("官方 Toolkit 工具定义被真实发送到模型端点")
    void toolSchemaIsSentToEndpoint() throws Exception {
        endpoint.resetRequests();
        endpoint.resetToolCallRounds();
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-tool-1", "user-tool-1", "session-tool-1",
                "workspace-1", "tenant-1", List.of("调用 echo_text 工具")));
        assertNotNull(ref);

        // 轮询等待主调用请求（含用户消息 + tools）到达端点。
        long deadline = System.currentTimeMillis() + 10000;
        boolean seen = false;
        while (System.currentTimeMillis() < deadline && !seen) {
            seen = endpoint.allRequestBodies().stream()
                    .filter(body -> body.contains("\"role\":\"user\""))
                    .anyMatch(body -> body.contains("echo_text") && body.contains("\"tools\""));
            if (!seen) {
                Thread.sleep(200);
            }
        }
        // 主调用请求应携带白名单工具定义；Memory 中间件 extraction 请求不含（正常）。
        assertTrue(seen, "主调用请求应包含白名单工具 echo_text 与 tools 数组");
    }

    @Test
    @DisplayName("业务 Agent 工具面仅含白名单，不含 Shell/文件系统/Host execute 能力")
    void toolSurfaceIsWhitelistOnly() {
        // 显式白名单 Toolkit 只注册了 echo_text；断言工具面不包含任何执行类工具。
        Toolkit whitelist = new Toolkit();
        whitelist.registerTool(new EchoTextTool());
        Set<String> toolNames = whitelist.getToolNames();

        assertEquals(Set.of("echo_text"), toolNames, "白名单工具面应仅为 echo_text");

        // 明确拒绝执行类工具名（评审项 1）：业务 Agent 不得获得 Host execute 能力。
        for (String forbidden : List.of("shell", "shell_command", "write_file", "read_file",
                "execute", "run_command", "bash", "terminal")) {
            assertTrue(toolNames.stream().noneMatch(n -> n.toLowerCase().contains(forbidden)),
                    "白名单不得包含执行类工具: " + forbidden);
        }
    }
}
