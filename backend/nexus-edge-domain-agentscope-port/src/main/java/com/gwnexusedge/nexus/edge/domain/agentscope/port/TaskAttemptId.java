package com.gwnexusedge.nexus.edge.domain.agentscope.port;

import java.security.SecureRandom;

/**
 * Nexus TaskAttempt UUIDv7 生成器（00_DECISIONS.md §5：业务主键统一 UUIDv7）。
 *
 * <p>UUIDv7 结构（RFC 9562）：
 * <ul>
 *   <li>位 0-47：48 位 Unix 毫秒时间戳；</li>
 *   <li>位 48-51：版本号 0111（=7）；</li>
 *   <li>位 52-63：12 位随机；</li>
 *   <li>位 64-65：变体 10（RFC 4122）；</li>
 *   <li>位 66-127：62 位随机。</li>
 * </ul>
 * 标准 8-4-4-4-12 格式，小写。
 */
public final class TaskAttemptId {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TaskAttemptId() {
        // 工具类，禁止实例化
    }

    /**
     * 生成一个 UUIDv7 字符串（小写，标准 8-4-4-4-12 格式）。
     */
    public static String newUuidV7() {
        long millis = System.currentTimeMillis();
        long timestamp = millis & 0xFFFFFFFFFFFFL; // 48 位

        long msb = (timestamp << 16)
                | 0x7000L                       // version 7
                | (RANDOM.nextLong() & 0x0FFFL); // 12 位随机
        long lsb = 0x8000000000000000L           // variant 10
                | (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL); // 62 位随机

        return new java.util.UUID(msb, lsb).toString();
    }
}
