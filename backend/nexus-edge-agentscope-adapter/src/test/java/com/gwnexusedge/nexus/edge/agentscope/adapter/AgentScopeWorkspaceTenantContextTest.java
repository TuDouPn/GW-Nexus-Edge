package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DEV-0001 Workspace/Tenant 上下文注入测试（P1-9）。
 *
 * <p>验证：{@code startExecution} 把业务请求中的 workspaceId/tenantId 注入执行上下文
 * （RuntimeContext 的 extra 字段），供可观测与审计关联。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeWorkspaceTenantContextTest {

    private CompatEndpoint endpoint;
    private AgentscopeAgentExecutionAdapter adapter;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-workspace-ctx");
        adapter = AgentScopeCompatTestSupport.newAdapter(
                AgentScopeCompatTestSupport.newConfig(endpoint, workspace, "你是上下文验证助手。"));
    }

    @AfterAll
    void tearDown() {
        adapter.close();
        endpoint.close();
    }

    @Test
    @DisplayName("P1-9：workspaceId/tenantId 被注入执行上下文")
    void workspaceAndTenantContextInjected() throws Exception {
        AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                "task-ws-1", "user-ws-1", "session-ws-1",
                "workspace-ws-123", "tenant-t-456", List.of("你好")));
        assertNotNull(ref);

        Map<String, String> context = adapter.executionContext("task-ws-1");
        assertNotNull(context, "执行上下文应可查询");
        assertEquals("workspace-ws-123", context.get("workspaceId"),
                "workspaceId 应被注入执行上下文");
        assertEquals("tenant-t-456", context.get("tenantId"),
                "tenantId 应被注入执行上下文");
    }
}
