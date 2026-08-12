package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.OpenAIModelProvider;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * DEV-0003 真实 Provider Smoke Test（仅 provider-smoke 独立 Maven Profile 显式启用时运行）。
 *
 * <p>行为（评审 0.4-4）：
 * <ul>
 *   <li>读取环境变量凭证：DeepSeek {@code DEEPSEEK_API_KEY/DEEPSEEK_BASE_URL/DEEPSEEK_MODEL}
 *       或企业 OpenAI-compatible {@code OPENAI_API_KEY/OPENAI_BASE_URL}；</li>
 *   <li>凭证存在 → 经官方 OpenAIChatModel/ModelRegistry + HarnessAgent 对真实 Provider 执行一次调用，
 *       断言返回文本；</li>
 *   <li>凭证缺失 → 测试<b>失败</b>并记录 <b>BLOCKED_BY_CREDENTIAL</b>（不把跳过写成通过证据，
 *       不 Mock 冒充真实验证）。</li>
 * </ul>
 * API Key 只经环境变量注入，不写入代码、YAML、数据库、测试报告或日志（断言消息不输出 Key 值）。
 * 默认 `clean verify` 不运行本测试（surefire excludes）；显式启用：
 * {@code ./mvnw -P provider-smoke -pl nexus-edge-agentscope-adapter test -Dtest=ProviderSmokeTest}。
 */
class ProviderSmokeTest {

    /** 模型标识（Provider 冒烟专用，非生产模型注册）。 */
    private static final String MODEL_ID = "openai:provider-smoke";

    @Test
    @DisplayName("P-1：真实 Provider 调用或 BLOCKED_BY_CREDENTIAL（不 Mock 冒充）")
    void realProviderSmokeOrBlockedByCredential() throws Exception {
        String apiKey = firstNonBlank(System.getenv("DEEPSEEK_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String baseUrl = firstNonBlank(System.getenv("DEEPSEEK_BASE_URL"), System.getenv("OPENAI_BASE_URL"));
        String modelName = firstNonBlank(System.getenv("DEEPSEEK_MODEL"), "provider-smoke-model");

        if (apiKey == null || baseUrl == null) {
            fail("BLOCKED_BY_CREDENTIAL: 未配置真实 Provider 凭证——"
                    + "需要环境变量 DEEPSEEK_API_KEY/DEEPSEEK_BASE_URL（DeepSeek）或 "
                    + "OPENAI_API_KEY/OPENAI_BASE_URL（企业 OpenAI-compatible）；"
                    + "未提供真实凭证时不得以跳过冒充通过（API Key 值不输出）");
        }

        // 真实模型注册（API Key 仅运行期经环境注入；不打印、不断言 Key 值）。
        OpenAIModelProvider provider = new OpenAIModelProvider();
        if (!provider.supports(MODEL_ID)) {
            fail("模型标识 " + MODEL_ID + " 不被 OpenAI Provider 支持");
        }
        ModelRegistry.register(MODEL_ID, OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .build());

        Path workspace = Files.createTempDirectory("nexus-edge-provider-smoke-ws");
        HarnessAgent agent = HarnessAgent.builder()
                .name("provider-smoke-agent")
                .sysPrompt("你是 Provider 冒烟验证助手。")
                .model(MODEL_ID)
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
        try {
            Msg result = agent.call(
                            List.of(new UserMessage("请用一句话回复：Provider 连通性验证。")),
                            RuntimeContext.builder().userId("provider-smoke").sessionId("provider-smoke").build())
                    .block(Duration.ofMinutes(3));
            assertNotNull(result, "真实 Provider 调用应返回结果");
            assertNotNull(result.getTextContent(), "真实 Provider 应返回文本");
            assertFalse(result.getTextContent().isBlank(), "真实 Provider 文本不应为空");
        } finally {
            agent.close();
        }
    }

    /** 返回首个非空白值（null 表示两者皆缺失）。 */
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
