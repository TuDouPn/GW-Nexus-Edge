package com.gwnexusedge.nexus.edge.compat;

import io.agentscope.core.model.ModelRegistry;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.OpenAIModelProvider;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AgentScope 组合 Smoke Test 装配（@Profile("agentscope")，SM-1）。
 *
 * <p>在<b>同一 Spring Boot 应用上下文</b>中注册真实 AgentScope 组件：
 * <ul>
 *   <li>经官方 {@link OpenAIModelProvider}/{@link OpenAIChatModel} 注册模型，指向
 *       {@link ModelStubEndpoint}（下游模型 Test Double，DEV-0001 先例）；</li>
 *   <li>构建真实 {@link HarnessAgent}（与 DEV-0001 一致的安全构建：禁用
 *       filesystem/shell/subagent/skill/memory 工具与 Memory Hooks）。</li>
 * </ul>
 * 用于证明 AgentScope 2.0.1 与 MyBatis-Plus/Sa-Token/Redis/JDBC 在同一上下文共存并可执行。
 */
@Configuration
@Profile("agentscope")
public class AgentscopeSmokeConfig {

    /**
     * 构建组合上下文中的 HarnessAgent Bean（真实 AgentScope 实例）。
     *
     * @param endpoint 本地模型 Test Double 端点
     * @return 安全配置的 HarnessAgent
     * @throws IOException 临时工作区创建失败
     */
    @Bean
    public HarnessAgent compatHarnessAgent(ModelStubEndpoint endpoint) throws IOException {
        // 注册 OpenAI 兼容模型（官方扩展），指向本地 stub 端点。
        String modelId = "openai:compat-model";
        OpenAIModelProvider provider = new OpenAIModelProvider();
        if (!provider.supports(modelId)) {
            throw new IllegalStateException("模型标识 " + modelId + " 不被 OpenAI Provider 支持");
        }
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey("compat-key")
                .modelName("compat-model")
                .baseUrl(endpoint.baseUrl())
                .build();
        ModelRegistry.register(modelId, model);

        Path workspace = Files.createTempDirectory("nexus-edge-compat-smoke-ws");
        // 与 DEV-0001 一致的安全构建（组合验证不引入危险能力）。
        return HarnessAgent.builder()
                .name("compat-smoke-agent")
                .sysPrompt("你是兼容性冒烟验证助手。")
                .model(modelId)
                .workspace(workspace)
                .enableMetaTool(false)
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableMemoryTools()
                .disableMemoryHooks()
                .build();
    }
}
