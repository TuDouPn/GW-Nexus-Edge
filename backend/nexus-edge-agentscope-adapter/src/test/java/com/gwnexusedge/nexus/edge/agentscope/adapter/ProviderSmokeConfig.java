package com.gwnexusedge.nexus.edge.agentscope.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provider Smoke 原子配置选择（评审唯一实现复审三）。
 *
 * <p><b>禁止逐字段 firstNonBlank 混合 DeepSeek 与 OpenAI 配置</b>；采用原子 Provider 配置选择：
 * <ol>
 *   <li><b>A</b> 存在任一 {@code DEEPSEEK_*} → 要求 {@code DEEPSEEK_API_KEY/DEEPSEEK_BASE_URL/
 *       DEEPSEEK_MODEL} 三项全部存在；</li>
 *   <li><b>B</b> 否则存在任一 {@code OPENAI_*} → 要求 {@code OPENAI_API_KEY/OPENAI_BASE_URL/
 *       OPENAI_MODEL} 三项全部存在；</li>
 *   <li><b>C</b> 两组都不存在 → {@code BLOCKED_BY_CREDENTIAL}；</li>
 *   <li><b>D</b> 某组部分存在 → {@code BLOCKED_BY_CREDENTIAL}，只报告缺失的环境变量名称，不输出任何值；</li>
 *   <li><b>E</b> 两组同时完整存在 → 必须显式 {@code PROVIDER_SMOKE_TYPE=deepseek|openai}；
 *       未选择时 fail-closed，禁止隐式猜测。</li>
 * </ol>
 *
 * <p>纯函数（无 I/O、无真实凭证需求），供单元测试直接验证；
 * 错误信息只含变量名与 BLOCKED 描述，<b>不含任何 Secret Value</b>。
 */
final class ProviderSmokeConfig {

    /** BLOCKED 前缀（fail-closed 标记，不得被当作通过证据）。 */
    static final String BLOCKED_PREFIX = "BLOCKED_BY_CREDENTIAL";

    /** 解析结果（仅 Provider 冒烟使用，非生产配置）。 */
    record ProviderConfig(String providerType, String apiKey, String baseUrl, String model) {
    }

    private ProviderSmokeConfig() {
        // 工具类，禁止实例化
    }

    /**
     * 按原子规则解析 Provider 配置。
     *
     * @param env 环境变量视图（生产调用传 {@code System.getenv()}；单元测试传 Map）
     * @return 完整 Provider 配置
     * @throws IllegalArgumentException 任意 BLOCKED 场景（消息不含 Secret Value）
     */
    static ProviderConfig resolve(Map<String, String> env) {
        boolean anyDeepseek = hasAny(env, "DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL");
        boolean anyOpenai = hasAny(env, "OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL");

        // C：两组都不存在。
        if (!anyDeepseek && !anyOpenai) {
            return fail(BLOCKED_PREFIX + ": 未配置任何 Provider 凭证"
                    + "（需要 DEEPSEEK_* 或 OPENAI_* 环境变量组；不输出任何值）");
        }

        // A：存在任一 DEEPSEEK_* → 要求三项全存在（D 部分缺失 → BLOCKED 只报缺失变量名）。
        if (anyDeepseek) {
            List<String> missingDeepseek = missing(env,
                    "DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL");
            if (!missingDeepseek.isEmpty()) {
                return fail(BLOCKED_PREFIX + ": DEEPSEEK 组部分存在，缺失环境变量: "
                        + missingDeepseek + "（不输出任何值）");
            }
            if (anyOpenai) {
                List<String> missingOpenai = missing(env,
                        "OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL");
                if (!missingOpenai.isEmpty()) {
                    return fail(BLOCKED_PREFIX + ": OPENAI 组部分存在，缺失环境变量: "
                            + missingOpenai + "（不输出任何值）");
                }
                // E：两组同时完整存在 → 显式选择，禁止隐式猜测。
                String type = env.get("PROVIDER_SMOKE_TYPE");
                if (type == null || !(type.equals("deepseek") || type.equals("openai"))) {
                    return fail(BLOCKED_PREFIX + ": DeepSeek 与 OpenAI 两组凭证同时完整存在，"
                            + "必须显式 PROVIDER_SMOKE_TYPE=deepseek|openai（禁止隐式猜测；不输出任何值）");
                }
                return "deepseek".equals(type) ? deepseek(env) : openai(env);
            }
            return deepseek(env);
        }

        // B：仅有 OPENAI_* → 要求三项全存在（D 部分缺失 → BLOCKED 只报缺失变量名）。
        List<String> missingOpenai = missing(env, "OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL");
        if (!missingOpenai.isEmpty()) {
            return fail(BLOCKED_PREFIX + ": OPENAI 组部分存在，缺失环境变量: "
                    + missingOpenai + "（不输出任何值）");
        }
        return openai(env);
    }

    private static ProviderConfig deepseek(Map<String, String> env) {
        return new ProviderConfig("deepseek",
                env.get("DEEPSEEK_API_KEY"), env.get("DEEPSEEK_BASE_URL"), env.get("DEEPSEEK_MODEL"));
    }

    private static ProviderConfig openai(Map<String, String> env) {
        return new ProviderConfig("openai",
                env.get("OPENAI_API_KEY"), env.get("OPENAI_BASE_URL"), env.get("OPENAI_MODEL"));
    }

    private static boolean hasAny(Map<String, String> env, String... names) {
        for (String name : names) {
            String v = env.get(name);
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** 返回缺失（null/空白）的变量名列表（只报名称，不报值）。 */
    private static List<String> missing(Map<String, String> env, String... names) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            String v = env.get(name);
            if (v == null || v.isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    private static ProviderConfig fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
