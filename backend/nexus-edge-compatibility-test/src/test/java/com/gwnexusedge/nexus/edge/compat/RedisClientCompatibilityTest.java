package com.gwnexusedge.nexus.edge.compat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0002 Redis 客户端兼容性测试（§8.3：RD-2/RD-3）。
 *
 * <p>使用 Spring Boot 默认 Spring Data Redis + Lettuce（SB BOM 管理 lettuce-core 7.5.2.RELEASE，
 * RD-1 原则：优先默认客户端）对真实 Redis 验证：
 * <ul>
 *   <li>RD-2：KV 读写；</li>
 *   <li>RD-3：Redis Streams 的 Consumer Group（XGROUP/XREADGROUP）、ACK（XACK）、
 *       Pending（XPENDING）、Reclaim（XCLAIM）命令兼容性。</li>
 * </ul>
 * 只验证客户端能力，不实现 Outbox 业务逻辑（RD-4）。
 *
 * <p>兼容发现（记录于 COMPATIBILITY_REPORT.md）：spring-data-redis 4.1.0 的
 * {@link StreamOperations} 未暴露 XAUTOCLAIM，Reclaim 经连接层 {@link RedisStreamCommands#xClaim}
 * 验证（设计允许 XAUTOCLAIM/XCLAIM 二选一）。
 */
@Testcontainers
class RedisClientCompatibilityTest {

    /** 固定版本 Redis 镜像。 */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2")
            .withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;
    private static LettuceConnectionFactory factory;

    /** 建立 Lettuce 连接工厂 + StringRedisTemplate（真实客户端）。 */
    @BeforeAll
    static void setUpRedisTemplate() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
    }

    /** 关闭连接工厂（P2：null-safe）。 */
    @AfterAll
    static void tearDownRedisTemplate() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("RD-2：KV 读写（Lettuce + StringRedisTemplate 真实往返）")
    void kvReadWrite() {
        String key = "compat:kv:" + UUID.randomUUID();
        String value = "kv-value-" + System.nanoTime();
        redisTemplate.opsForValue().set(key, value);
        assertEquals(value, redisTemplate.opsForValue().get(key), "SET/GET 应往返一致");
        redisTemplate.delete(key);
        assertFalse(redisTemplate.hasKey(key), "删除后键不应存在");
    }

    @Test
    @DisplayName("RD-3：Redis Streams Consumer Group / ACK / Pending / Reclaim 命令兼容")
    void streamsConsumerGroupAckPendingReclaim() {
        String stream = "compat:stream:" + UUID.randomUUID();
        String group = "compat-group";
        StreamOperations<String, Object, Object> ops = redisTemplate.opsForStream();

        // XGROUP CREATE：创建消费组。
        ops.createGroup(stream, group);

        // XADD：写入 2 条消息（第 2 条不 ACK，用于 Pending/Reclaim 验证）。
        RecordId id1 = ops.add(MapRecord.create(stream, Map.of("payload", "msg-1")));
        RecordId id2 = ops.add(MapRecord.create(stream, Map.of("payload", "msg-2")));
        assertNotNull(id1);
        assertNotNull(id2);

        // XREADGROUP：Consumer Group 读取（> 表示从未 ACK 的消息开始）。
        List<MapRecord<String, Object, Object>> read = ops.read(
                Consumer.from(group, "consumer-1"),
                StreamReadOptions.empty().count(2),
                StreamOffset.create(stream, ReadOffset.from(">")));
        assertTrue(read.size() >= 2, "Consumer Group 应读取到消息，实际 " + read.size());
        assertTrue(read.stream().anyMatch(r -> "msg-1".equals(r.getValue().get("payload"))),
                "读取消息负载应一致");

        // XACK：确认第 1 条消息。
        Long acked = ops.acknowledge(stream, group, id1);
        assertEquals(1, acked, "XACK 应确认 1 条");

        // XPENDING：Pending 汇总应剩 1 条（第 2 条未 ACK）。
        var pending = ops.pending(stream, group);
        assertNotNull(pending, "XPENDING 应返回汇总");
        assertEquals(1, pending.getTotalPendingMessages(), "Pending 应为 1 条（第 2 条未 ACK）");

        // Reclaim（XCLAIM）：把 Pending 消息认领给 consumer-2（经连接层 RedisStreamCommands）。
        RedisStreamCommands streamCommands =
                redisTemplate.getConnectionFactory().getConnection().streamCommands();
        List<ByteRecord> claimed = streamCommands.xClaim(
                stream.getBytes(StandardCharsets.UTF_8), group, "consumer-2", Duration.ZERO, id2);
        assertFalse(claimed.isEmpty(), "XCLAIM 应认领到 Pending 消息");

        // 认领后 ACK 第 2 条，Pending 清空。
        ops.acknowledge(stream, group, id2);
        var pendingAfter = ops.pending(stream, group);
        assertEquals(0, pendingAfter.getTotalPendingMessages(), "全部 ACK 后 Pending 应为 0");

        // 清理流（删除键）。
        redisTemplate.delete(stream);
    }
}
