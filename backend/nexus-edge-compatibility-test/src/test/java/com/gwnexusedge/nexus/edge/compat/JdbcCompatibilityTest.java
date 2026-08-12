package com.gwnexusedge.nexus.edge.compat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0002 JDBC 兼容性测试（§8.5：JD-3~JD-6）。
 *
 * <p>使用官方 JDBC 驱动（mysql-connector-j 9.7.0 / postgresql 42.7.11，SB BOM 管理）+
 * Testcontainers 固定版本镜像，直接经 {@link DriverManager} 验证：
 * <ul>
 *   <li>JD-3 连接；JD-4 事务提交与回滚；JD-5 UTC 时间；JD-6 基础类型映射。</li>
 * </ul>
 * 版本矩阵见 §8.5-JD-1；镜像 Digest 记录于 COMPATIBILITY_REPORT.md。
 */
@Testcontainers
class JdbcCompatibilityTest {

    /** 固定版本 MySQL 镜像。 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.5");

    /** 固定版本 PostgreSQL 镜像。 */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6");

    /**
     * MySQL：连接、事务提交与回滚、UTC 时间、基础类型映射。
     *
     * @throws Exception JDBC 异常
     */
    @Test
    @DisplayName("JD-3~JD-6：MySQL 连接/事务/UTC/类型映射")
    void mysqlConnectCommitRollbackUtcAndTypeMapping() throws Exception {
        // JD-3：真实连接（connectionTimeZone=UTC：JD-5 UTC 时间约定，00_DECISIONS §5）。
        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                MYSQL.getUsername(), MYSQL.getPassword())) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS jdbc_compat");
                st.execute("CREATE TABLE jdbc_compat ("
                        + "id BIGINT PRIMARY KEY, "
                        + "name VARCHAR(64), "
                        + "amount DECIMAL(12,2), "
                        + "flag BOOLEAN, "
                        + "ts TIMESTAMP"
                        + ")");
            }

            // JD-4：事务提交——手动提交后数据可见。
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO jdbc_compat(id, name, amount, flag, ts) VALUES(?,?,?,?,?)")) {
                ps.setLong(1, 1L);
                ps.setString(2, "mysql-commit");
                ps.setBigDecimal(3, new BigDecimal("123.45"));
                ps.setBoolean(4, true);
                ps.setTimestamp(5, Timestamp.from(Instant.parse("2026-08-11T09:00:00Z")));
                ps.executeUpdate();
            }
            conn.commit();
            assertTrue(countRows(conn, 1L) == 1, "提交后数据应可见");

            // JD-4：事务回滚——回滚后数据不可见。
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO jdbc_compat(id, name, amount, flag, ts) VALUES(?,?,?,?,?)")) {
                ps.setLong(1, 2L);
                ps.setString(2, "mysql-rollback");
                ps.setBigDecimal(3, new BigDecimal("1.00"));
                ps.setBoolean(4, false);
                ps.setTimestamp(5, Timestamp.from(Instant.parse("2026-08-11T10:00:00Z")));
                ps.executeUpdate();
            }
            conn.rollback();
            assertEquals(0, countRows(conn, 2L), "回滚后数据应不可见");

            // JD-5/JD-6：UTC 时间与基础类型映射（读写往返一致）。
            try (PreparedStatement q = conn.prepareStatement(
                    "SELECT name, amount, flag, ts FROM jdbc_compat WHERE id = ?")) {
                q.setLong(1, 1L);
                try (ResultSet rs = q.executeQuery()) {
                    assertTrue(rs.next(), "应查询到已提交数据");
                    assertEquals("mysql-commit", rs.getString("name"));
                    assertEquals(0, new BigDecimal("123.45").compareTo(rs.getBigDecimal("amount")),
                            "DECIMAL 往返一致");
                    assertTrue(rs.getBoolean("flag"), "BOOLEAN 往返一致");
                    Instant stored = rs.getTimestamp("ts").toInstant();
                    assertEquals(Instant.parse("2026-08-11T09:00:00Z"), stored,
                            "UTC 时间戳往返一致（connectionTimeZone=UTC）");
                }
            }
        }
    }

    /**
     * PostgreSQL：连接、事务提交与回滚、UTC 时间、基础类型映射。
     *
     * @throws Exception JDBC 异常
     */
    @Test
    @DisplayName("JD-3~JD-6：PostgreSQL 连接/事务/UTC/类型映射")
    void postgresConnectCommitRollbackUtcAndTypeMapping() throws Exception {
        // JD-3：真实连接。
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS jdbc_compat");
                st.execute("CREATE TABLE jdbc_compat ("
                        + "id BIGINT PRIMARY KEY, "
                        + "name VARCHAR(64), "
                        + "amount DECIMAL(12,2), "
                        + "flag BOOLEAN, "
                        + "ts TIMESTAMP WITH TIME ZONE"
                        + ")");
            }

            // JD-4：事务提交。
            conn.setAutoCommit(false);
            // JD-5：显式 UTC 瞬时（timestamptz 按瞬时存储；避免本地时区 LocalDateTime 歧义）。
            java.time.OffsetDateTime utcInstant = Instant.parse("2026-08-11T09:30:00Z")
                    .atOffset(ZoneOffset.UTC);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO jdbc_compat(id, name, amount, flag, ts) VALUES(?,?,?,?,?)")) {
                ps.setLong(1, 1L);
                ps.setString(2, "pg-commit");
                ps.setBigDecimal(3, new BigDecimal("999.99"));
                ps.setBoolean(4, true);
                ps.setObject(5, utcInstant);
                ps.executeUpdate();
            }
            conn.commit();
            assertEquals(1, countRows(conn, 1L), "提交后数据应可见");

            // JD-4：事务回滚。
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO jdbc_compat(id, name, amount, flag, ts) VALUES(?,?,?,?,?)")) {
                ps.setLong(1, 2L);
                ps.setString(2, "pg-rollback");
                ps.setBigDecimal(3, new BigDecimal("2.00"));
                ps.setBoolean(4, false);
                ps.setObject(5, utcInstant.plusHours(1));
                ps.executeUpdate();
            }
            conn.rollback();
            assertEquals(0, countRows(conn, 2L), "回滚后数据应不可见");

            // JD-5/JD-6：UTC 时间与基础类型映射。
            try (PreparedStatement q = conn.prepareStatement(
                    "SELECT name, amount, flag, ts FROM jdbc_compat WHERE id = ?")) {
                q.setLong(1, 1L);
                try (ResultSet rs = q.executeQuery()) {
                    assertTrue(rs.next(), "应查询到已提交数据");
                    assertEquals("pg-commit", rs.getString("name"));
                    assertEquals(0, new BigDecimal("999.99").compareTo(rs.getBigDecimal("amount")));
                    assertTrue(rs.getBoolean("flag"));
                    // PG timestamptz 读回为同一瞬时（UTC 等价）。
                    Instant stored = rs.getObject("ts", java.time.OffsetDateTime.class).toInstant();
                    assertEquals(Instant.parse("2026-08-11T09:30:00Z"), stored,
                            "UTC 时间戳往返一致（timestamptz）");
                }
            }
        }
    }

    /** 统计指定 id 的行数。 */
    private static int countRows(Connection conn, long id) throws Exception {
        try (PreparedStatement q = conn.prepareStatement(
                "SELECT COUNT(*) FROM jdbc_compat WHERE id = ?")) {
            q.setLong(1, id);
            try (ResultSet rs = q.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
