package com.gwnexusedge.nexus.edge.domain.agentscope.port;

/**
 * Secret 解析端口（07_SECURITY_AND_PERMISSION.md §7）。
 *
 * <p>Adapter 只持有 Secret Reference（例如 API Key 的引用名），通过本端口在运行期
 * 解析为短生命周期临时值；解析结果不得保存、不得进入日志/审计/数据库/前端。
 * 领域层不关心 Secret 存储实现（KMS/文件/Docker Secret），只依赖本端口。
 *
 * <p>DEV-0001 阶段：main 生产装配不提供本端口的实现（避免伪实现）；测试通过
 * test-only Factory 提供受控 Test Double 凭据。
 */
public interface SecretResolver {

    /**
     * 按引用名解析 Secret 的临时明文值。
     *
     * @param reference Secret Reference 名称
     * @return 运行期临时值（调用方使用后应尽快丢弃，不得持久化）
     */
    String resolve(String reference);
}
