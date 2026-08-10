package com.gwnexusedge.nexus.edge.agentscope.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DEV-0001 测试公共基础设施。
 *
 * <p>统一管理受控测试端点、工作区目录、测试 Secret 解析器，减少各测试类重复代码。
 * 所有测试值仅存在于 test source（P0-3）。
 */
public final class AgentScopeCompatTestSupport {

    private AgentScopeCompatTestSupport() {
        // 工具类，禁止实例化
    }

    /**
     * 创建一个受控 OpenAI 兼容测试端点（端口自动分配）。
     */
    public static CompatEndpoint newEndpoint() throws IOException {
        return new CompatEndpoint(0);
    }

    /**
     * 创建临时工作区目录。
     */
    public static Path newWorkspace(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    /**
     * 构造测试用 Adapter 配置（端点地址 + Test Double 凭据引用）。
     */
    public static AgentscopeAdapterConfig newConfig(
            CompatEndpoint endpoint, Path workspace, String prompt) {
        return new AgentscopeAdapterConfig(
                "openai:test-model",
                endpoint.baseUrl(),
                TestSecretResolver.TEST_API_KEY,
                workspace.toString(),
                prompt);
    }

    /**
     * 构造 Adapter（安全白名单空 Toolkit + 无 State Store + 测试 Secret 解析器）。
     */
    public static AgentscopeAgentExecutionAdapter newAdapter(
            AgentscopeAdapterConfig config) {
        return new AgentscopeAgentExecutionAdapter(
                config, null, null, new TestSecretResolver());
    }
}
