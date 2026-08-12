package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import java.util.Objects;
import java.util.regex.Pattern;
import redis.clients.jedis.JedisPooled;

/**
 * AgentScope 官方 Redis Agent State Store 工厂（DEV-0003）。
 *
 * <p>职责与边界：
 * <ul>
 *   <li>构建官方 {@link RedisAgentStateStore}（实现 {@code AgentStateStore}），
 *       作为 AgentScope Session/Agent State 的<b>运行时持久化机制</b>；
 *       Nexus Task/TaskAttempt 权威源属于 MySQL（ADR-0008）；</li>
 *   <li>keyPrefix 环境命名空间校验（C-12）：环境名只允许小写字母/数字/连字符，
 *       防止跨环境污染（不同环境/租户使用不同前缀）；</li>
 *   <li>Redis 客户端生命周期：{@link RedisAgentStateStore} 由本工厂持有并在
 *       {@link #close()} 统一关闭（null-safe）；<b>禁止每次 Task 关闭共享 Client</b>（C-11）；</li>
 *   <li>本工厂不执行 Task 终态自动 Session 清理（Session 删除归独立生命周期服务，
 *       本 Work Item 不实现）；不操作/解析 AgentScope 官方内部键（ADR-0008）。</li>
 * </ul>
 *
 * <p>生产客户端选择（评审 0.4-3）：Jedis 7.4.1 与 Spring Boot 管理的 Lettuce 7.5.2
 * 均需通过真实 Redis 组合测试后再决定；本实现同时提供两种构建入口，默认 Jedis，
 * 最终选择以 COMPATIBILITY_REPORT.md 的测试证据为准。
 */
public final class RedisAgentStateStoreFactory implements AutoCloseable {

    /** keyPrefix 环境段允许字符集（C-12：小写字母/数字/连字符，防跨环境污染）。 */
    private static final Pattern ENV_NAME_PATTERN = Pattern.compile("[a-z0-9-]{1,32}");

    /** 默认 Redis 端口。 */
    public static final int DEFAULT_REDIS_PORT = 6379;

    private final RedisAgentStateStore store;
    private final AutoCloseable client;

    /**
     * 私有构造：经 {@link #jedis(...)} / {@link #lettuce(...)} 创建。
     *
     * @param store  官方 RedisAgentStateStore
     * @param client 底层客户端（Jedis/Lettuce），由本工厂统一关闭
     */
    private RedisAgentStateStoreFactory(RedisAgentStateStore store, AutoCloseable client) {
        this.store = store;
        this.client = client;
    }

    /**
     * 使用 Jedis 客户端构建（扩展官方直接依赖 jedis 7.4.1，独立于 SB BOM 的 Lettuce）。
     *
     * @param environment 环境名（keyPrefix 环境段；C-12 校验允许字符）
     * @param host        Redis 主机
     * @param port        Redis 端口
     * @param password    口令（Secret Reference 解析后的运行期值；null 表示无口令）
     * @return 工厂（持有共享 Client，随 Adapter close 统一关闭）
     */
    public static RedisAgentStateStoreFactory jedis(String environment, String host, int port, String password) {
        String keyPrefix = keyPrefix(environment);
        JedisPooled jedis = password == null || password.isBlank()
                ? new JedisPooled(host, port)
                : new JedisPooled(host, port, null, password);
        RedisAgentStateStore store = RedisAgentStateStore.builder()
                .keyPrefix(keyPrefix)
                .jedisClient(jedis)
                .build();
        return new RedisAgentStateStoreFactory(store, jedis);
    }

    /**
     * 使用 Lettuce 客户端构建（Spring Boot BOM 管理 lettuce-core 7.5.2）。
     *
     * <p>注意：agentscope-extensions-redis POM 声明 lettuce 6.4.2.RELEASE；本 reactor 依赖
     * 调解为 SB BOM 的 7.5.2。该组合的真实兼容性必须经测试验证后再作为生产客户端（评审 0.4-3）。
     *
     * @param environment 环境名（keyPrefix 环境段；C-12 校验允许字符）
     * @param host        Redis 主机
     * @param port        Redis 端口
     * @param password    口令（Secret Reference 解析后的运行期值；null 表示无口令）
     * @return 工厂（持有共享 Client，随 Adapter close 统一关闭）
     */
    public static RedisAgentStateStoreFactory lettuce(String environment, String host, int port, String password) {
        String keyPrefix = keyPrefix(environment);
        io.lettuce.core.RedisClient redisClient;
        if (password == null || password.isBlank()) {
            redisClient = io.lettuce.core.RedisClient.create("redis://" + host + ":" + port);
        } else {
            redisClient = io.lettuce.core.RedisClient.create(
                    "redis://:" + password + "@" + host + ":" + port);
        }
        RedisAgentStateStore store = RedisAgentStateStore.builder()
                .keyPrefix(keyPrefix)
                .lettuceClient(redisClient)
                .build();
        return new RedisAgentStateStoreFactory(store, redisClient);
    }

    /**
     * 计算 keyPrefix（环境命名空间）：{@code nexus:{env}:agentscope-session:}。
     *
     * <p>环境段经 {@link #ENV_NAME_PATTERN} 校验（C-12）：只允许小写字母/数字/连字符，
     * 长度 1~32；防止含路径分隔符等非法字符导致跨环境污染。
     *
     * @param environment 环境名
     * @return Redis keyPrefix
     */
    public static String keyPrefix(String environment) {
        Objects.requireNonNull(environment, "environment 不允许为 null");
        if (!ENV_NAME_PATTERN.matcher(environment).matches()) {
            throw new IllegalArgumentException(
                    "environment 只允许小写字母/数字/连字符（长度 1~32，防跨环境污染）: " + environment);
        }
        return "nexus:" + environment + ":agentscope-session:";
    }

    /**
     * 返回官方 {@link RedisAgentStateStore}（Adapter 作为 {@code AgentStateStore} 注入）。
     *
     * @return 官方 Redis 状态存储
     */
    public RedisAgentStateStore stateStore() {
        return store;
    }

    /**
     * 关闭共享 Redis 客户端（null-safe；仅在 Adapter 整体关闭时调用，不随 Task 关闭）。
     */
    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // 关闭失败不掩盖；记录为内部诊断（无 Secret 内容）。
                System.err.println("Redis 客户端关闭失败（诊断）: " + e.getClass().getSimpleName());
            }
        }
    }
}
