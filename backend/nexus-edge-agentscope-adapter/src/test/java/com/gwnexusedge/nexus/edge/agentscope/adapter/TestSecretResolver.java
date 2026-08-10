package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.SecretResolver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用 Secret 解析器（test-only Factory，P0-3 修正）。
 *
 * <p>仅存在于 test source。区别于旧版（忽略 reference 恒返回同值），本实现：
 * <ul>
 *   <li>记录每次解析的 reference（供断言 Secret Reference 被真实传递）；</li>
 *   <li>按 reference 前缀区分返回值，证明 Reference → Value 映射非恒同值；</li>
 *   <li>测试可断言解析后的值真实进入请求（Authorization 头）。</li>
 * </ul>
 * 生产环境 Secret 由平台基础设施层的 SecretResolver 实现提供（07 §7），main 不含本类。
 */
public final class TestSecretResolver implements SecretResolver {

    /** 默认测试端点凭据（Test Double 专用，非生产 Secret）。 */
    public static final String TEST_API_KEY = "test-key";

    /** 记录所有被解析过的 reference（供断言 Reference 传递）。 */
    private final Map<String, Integer> resolvedReferences = new ConcurrentHashMap<>();

    @Override
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Secret Reference 不允许为空");
        }
        resolvedReferences.merge(reference, 1, Integer::sum);
        // 按 reference 生成可区分值：证明 Reference → Value 映射（非恒同值）。
        return TEST_API_KEY + ":" + Integer.toHexString(reference.hashCode() & 0xFFFF);
    }

    /** 返回某个 reference 被解析的次数。 */
    public int resolveCount(String reference) {
        return resolvedReferences.getOrDefault(reference, 0);
    }

    /** 全部被解析过的 reference。 */
    public Map<String, Integer> resolvedReferences() {
        return Map.copyOf(resolvedReferences);
    }
}
