package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.SecretResolver;

/**
 * 测试用 Secret 解析器（test-only Factory，P0-3）。
 *
 * <p>仅存在于 test source：把受控测试端点凭据解析为运行期值。生产环境
 * Secret 由平台基础设施层的 SecretResolver 实现提供（07 §7），main 不包含
 * 本类也不包含任何伪 Secret 实现。
 */
public final class TestSecretResolver implements SecretResolver {

    /** 测试端点凭据（Test Double 专用，非生产 Secret）。 */
    public static final String TEST_API_KEY = "test-key";

    @Override
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Secret Reference 不允许为空");
        }
        return TEST_API_KEY;
    }
}
