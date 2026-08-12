package com.gwnexusedge.nexus.edge.agentscope.adapter;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provider Smoke 原子配置选择单元测试（评审唯一实现复审三）。
 *
 * <p>不需要真实凭证（纯函数 {@link ProviderSmokeConfig#resolve(Map)}），覆盖：
 * DeepSeek 完整组 / OpenAI 完整组 / 部分缺失 / 两组同时完整但未选择 /
 * 不发生跨 Provider 字段混配 / 错误信息不含 Secret Value。
 */
class ProviderSmokeConfigTest {

    @Test
    @DisplayName("A：DeepSeek 完整组 → deepseek 配置")
    void deepseekCompleteGroup() {
        ProviderSmokeConfig.ProviderConfig cfg = ProviderSmokeConfig.resolve(Map.of(
                "DEEPSEEK_API_KEY", "d-key", "DEEPSEEK_BASE_URL", "https://d.example", "DEEPSEEK_MODEL", "d-model"));
        assertEquals("deepseek", cfg.providerType());
        assertEquals("d-key", cfg.apiKey());
        assertEquals("https://d.example", cfg.baseUrl());
        assertEquals("d-model", cfg.model());
    }

    @Test
    @DisplayName("B：OpenAI 完整组 → openai 配置")
    void openaiCompleteGroup() {
        ProviderSmokeConfig.ProviderConfig cfg = ProviderSmokeConfig.resolve(Map.of(
                "OPENAI_API_KEY", "o-key", "OPENAI_BASE_URL", "https://o.example", "OPENAI_MODEL", "o-model"));
        assertEquals("openai", cfg.providerType());
        assertEquals("o-key", cfg.apiKey());
        assertEquals("o-model", cfg.model());
    }

    @Test
    @DisplayName("C：两组都不存在 → BLOCKED_BY_CREDENTIAL")
    void neitherGroupExists() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProviderSmokeConfig.resolve(Map.of()));
        assertTrue(ex.getMessage().startsWith(ProviderSmokeConfig.BLOCKED_PREFIX),
                "两组都不存在必须 BLOCKED_BY_CREDENTIAL");
    }

    @Test
    @DisplayName("D：部分缺失 → BLOCKED_BY_CREDENTIAL，只报告缺失变量名，不含任何值")
    void partialGroupMissing() {
        // DEEPSEEK 部分存在（缺 BASE_URL/MODEL）。
        String sentinel = "SENTINEL-SECRET-" + System.nanoTime();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProviderSmokeConfig.resolve(Map.of("DEEPSEEK_API_KEY", sentinel)));
        assertTrue(ex.getMessage().startsWith(ProviderSmokeConfig.BLOCKED_PREFIX), "部分缺失必须 BLOCKED");
        assertTrue(ex.getMessage().contains("DEEPSEEK_BASE_URL"), "应报告缺失变量名 DEEPSEEK_BASE_URL");
        assertTrue(ex.getMessage().contains("DEEPSEEK_MODEL"), "应报告缺失变量名 DEEPSEEK_MODEL");
        // 错误信息不得包含 Secret Value（sentinel）。
        assertFalse(ex.getMessage().contains(sentinel), "错误信息不得包含 Secret Value");
    }

    @Test
    @DisplayName("E：两组同时完整存在但未选择 PROVIDER_SMOKE_TYPE → fail-closed")
    void bothGroupsCompleteWithoutSelection() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProviderSmokeConfig.resolve(Map.of(
                        "DEEPSEEK_API_KEY", "d", "DEEPSEEK_BASE_URL", "d", "DEEPSEEK_MODEL", "d",
                        "OPENAI_API_KEY", "o", "OPENAI_BASE_URL", "o", "OPENAI_MODEL", "o")));
        assertTrue(ex.getMessage().contains("PROVIDER_SMOKE_TYPE"),
                "两组同时完整必须要求显式 PROVIDER_SMOKE_TYPE");
    }

    @Test
    @DisplayName("E：两组同时完整且显式 PROVIDER_SMOKE_TYPE=deepseek → deepseek 配置")
    void bothGroupsCompleteWithExplicitSelection() {
        ProviderSmokeConfig.ProviderConfig cfg = ProviderSmokeConfig.resolve(Map.of(
                "DEEPSEEK_API_KEY", "d-key", "DEEPSEEK_BASE_URL", "d-url", "DEEPSEEK_MODEL", "d-model",
                "OPENAI_API_KEY", "o-key", "OPENAI_BASE_URL", "o-url", "OPENAI_MODEL", "o-model",
                "PROVIDER_SMOKE_TYPE", "deepseek"));
        assertEquals("deepseek", cfg.providerType());
        assertEquals("d-key", cfg.apiKey(), "不得混配 OpenAI 字段");
        assertEquals("d-model", cfg.model(), "不得混配 OpenAI 字段");
    }

    @Test
    @DisplayName("D：不发生跨 Provider 字段混配——DEEPSEEK 部分 + OPENAI 完整 → 报 DEEPSEEK 缺失")
    void noCrossProviderMixing() {
        // DEEPSEEK 缺 BASE_URL/MODEL，OPENAI 完整：必须报 DEEPSEEK 组缺失，不得用 OPENAI 值补齐。
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProviderSmokeConfig.resolve(Map.of(
                        "DEEPSEEK_API_KEY", "d-key",
                        "OPENAI_API_KEY", "o-key", "OPENAI_BASE_URL", "o-url", "OPENAI_MODEL", "o-model")));
        assertTrue(ex.getMessage().contains("DEEPSEEK"),
                "应按 A 规则报 DEEPSEEK 组缺失（禁止跨 Provider 混配）");
        assertFalse(ex.getMessage().contains("d-key"), "错误信息不得包含 Secret Value");
        assertFalse(ex.getMessage().contains("o-key"), "错误信息不得包含 Secret Value");
    }
}
