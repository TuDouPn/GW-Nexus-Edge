package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 Secret Reference/Value 测试（P0-3）。
 *
 * <p>验证：
 * <ol>
 *   <li>Secret Reference 被真实传递给 {@link com.gwnexusedge.nexus.edge.domain.agentscope.port.SecretResolver}
 *       （记录解析调用，非忽略 reference 恒同值）；</li>
 *   <li>解析后的 Secret 值真实进入模型请求的 Authorization 头（Reference→Value→请求链路）；</li>
 *   <li>不同 reference 解析出不同值（映射非恒同值）。</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeSecretResolutionTest {

    private CompatEndpoint endpoint;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        TestOtel.init();
        endpoint = AgentScopeCompatTestSupport.newEndpoint();
        workspace = AgentScopeCompatTestSupport.newWorkspace("nexus-edge-secret");
    }

    @AfterAll
    void tearDown() {
        endpoint.close();
    }

    @Test
    @DisplayName("P0-3：Secret Reference 被传递解析且解析值进入请求 Authorization 头")
    void secretReferenceResolvedAndSentInRequest() throws Exception {
        TestSecretResolver resolver = new TestSecretResolver();
        AgentscopeAdapterConfig config = AgentScopeCompatTestSupport.newConfig(
                endpoint, workspace, "你是 Secret 验证助手。");
        try (AgentscopeAgentExecutionAdapter adapter = new AgentscopeAgentExecutionAdapter(
                config, null, null, resolver)) {
            endpoint.resetRequests();
            adapter.startExecution(new AgentExecutionRequest(
                    "task-secret-1", "user-secret-1", "session-secret-1",
                    "workspace-1", "tenant-1", List.of("你好")));

            // 1) Reference 被真实传递（resolver 记录了解析调用）。
            assertTrue(resolver.resolveCount(config.apiKeyReference()) >= 1,
                    "Secret Reference 应被传递给 resolver 解析");

            // 2) 等待请求到达端点。
            long deadline = System.currentTimeMillis() + 10000;
            while (endpoint.lastAuthorization() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            String auth = endpoint.lastAuthorization();
            assertNotNull(auth, "模型请求应携带 Authorization 头");

            // 3) Authorization 应包含 resolver 解析出的值（Bearer <resolved>）。
            //    解析值 = TEST_API_KEY + ":" + hex(reference)，随 reference 变化。
            //    断言消息不含 Secret 明文（第四轮：不泄露解析值）。
            String expectedResolved = resolver.resolve(config.apiKeyReference());
            assertTrue(auth.contains(expectedResolved),
                    "Authorization 头应包含 SecretResolver 解析出的值（断言消息不输出 Secret 明文）");
        }
    }

    @Test
    @DisplayName("P0-3：不同 reference 解析出不同值（映射非恒同值）")
    void differentReferencesResolveToDifferentValues() {
        TestSecretResolver resolver = new TestSecretResolver();
        String v1 = resolver.resolve("ref-alpha");
        String v2 = resolver.resolve("ref-beta");
        assertTrue(!v1.equals(v2), "不同 reference 应解析出不同值");
        assertTrue(resolver.resolveCount("ref-alpha") == 1, "ref-alpha 应被解析一次");
        assertTrue(resolver.resolveCount("ref-beta") == 1, "ref-beta 应被解析一次");
        assertTrue(resolver.resolvedReferences().containsKey("ref-alpha"),
                "解析器应记录被解析的 reference");
    }
}
