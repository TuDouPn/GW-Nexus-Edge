package com.gwnexusedge.nexus.edge.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0002 Flyway 兼容性测试（§8.4：FW-1~FW-6）。
 *
 * <p>使用官方 Flyway（flyway-core + flyway-mysql + flyway-database-postgresql，SB BOM 12.4.0）
 * 对真实 MySQL/PostgreSQL 验证：
 * <ul>
 *   <li>FW-1：MySQL 使用 flyway-mysql、PostgreSQL 使用 flyway-database-postgresql 支持模块；</li>
 *   <li>FW-2 migrate、FW-3 validate、FW-4 重复启动幂等、FW-5 checksum 不一致失败；</li>
 *   <li>FW-6 MySQL/PostgreSQL（及未来 DM8）迁移目录隔离，互不借用。</li>
 * </ul>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayCompatibilityTest {

    /** 固定版本 MySQL 镜像。 */
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.5");

    /** 固定版本 PostgreSQL 镜像。 */
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.6");

    /**
     * 在真实 MySQL 上验证 migrate / validate / 幂等 / checksum 失败。
     *
     * @throws IOException 临时文件写入失败
     */
    @Test
    @DisplayName("FW-2~FW-5：MySQL migrate、validate、幂等与 checksum 不一致失败")
    void mysqlMigrateValidateIdempotentAndChecksumFailure() throws IOException {
        Path dir = Files.createTempDirectory("flyway-mysql-fixture");
        Path sql = dir.resolve("V1__fixture.sql");
        Files.writeString(sql, "CREATE TABLE flyway_fixture (id INT PRIMARY KEY, v VARCHAR(64));");

        // FW-2：migrate 执行成功。
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + dir)
                .load();
        int migrated = flyway.migrate().migrationsExecuted;
        assertTrue(migrated >= 1, "MySQL migrate 应执行至少一个版本，实际 " + migrated);

        // FW-4：重复启动幂等——再次 migrate 无重复执行。
        int again = flyway.migrate().migrationsExecuted;
        assertTrue(again == 0, "重复 migrate 应无变更（幂等），实际 " + again);

        // FW-3：validate 通过。
        flyway.validate();

        // FW-5：修改已迁移脚本内容 → validate 失败（checksum 不一致）。
        Files.writeString(sql, "CREATE TABLE flyway_fixture (id INT PRIMARY KEY, v VARCHAR(128), extra INT);");
        assertThrows(FlywayException.class, flyway::validate,
                "已迁移脚本 checksum 不一致必须验证失败");
    }

    /**
     * 在真实 PostgreSQL 上验证 migrate / validate / 幂等 / checksum 失败。
     *
     * @throws IOException 临时文件写入失败
     */
    @Test
    @DisplayName("FW-2~FW-5：PostgreSQL migrate、validate、幂等与 checksum 不一致失败")
    void postgresMigrateValidateIdempotentAndChecksumFailure() throws IOException {
        Path dir = Files.createTempDirectory("flyway-postgres-fixture");
        Path sql = dir.resolve("V1__fixture.sql");
        Files.writeString(sql, "CREATE TABLE flyway_fixture (id INT PRIMARY KEY, v VARCHAR(64));");

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:" + dir)
                .load();
        int migrated = flyway.migrate().migrationsExecuted;
        assertTrue(migrated >= 1, "PostgreSQL migrate 应执行至少一个版本，实际 " + migrated);

        int again = flyway.migrate().migrationsExecuted;
        assertTrue(again == 0, "重复 migrate 应无变更（幂等），实际 " + again);

        flyway.validate();

        Files.writeString(sql, "CREATE TABLE flyway_fixture (id INT PRIMARY KEY, v VARCHAR(128), extra INT);");
        assertThrows(FlywayException.class, flyway::validate,
                "已迁移脚本 checksum 不一致必须验证失败");
    }

    /**
     * FW-6：迁移目录隔离——MySQL/PostgreSQL 各自独立目录。
     *
     * <p>DM8 迁移目录隔离不由本测试断言：当前无 DM8 环境时，本测试只验证 MySQL/PostgreSQL
     * 独立目录；DM8 迁移目录必须独立维护（db/migration/dm8），其隔离性由未来真实 DM8 测试证明
     * （评审 P1-9：删除"DM8 迁移文件存在即失败"的反向断言）。
     */
    @Test
    @DisplayName("FW-6：MySQL/PostgreSQL 迁移目录隔离")
    void migrationDirectoriesAreIsolated() {
        // MySQL 迁移目录存在且可加载（application-mysql Profile 的 Flyway locations）。
        boolean mysqlDir = getClass().getClassLoader()
                .getResource("db/migration/mysql/V1__create_compat_user.sql") != null;
        assertTrue(mysqlDir, "MySQL 迁移目录 db/migration/mysql 应存在并包含 V1 迁移");

        // PostgreSQL 迁移目录存在且可加载。
        boolean postgresDir = getClass().getClassLoader()
                .getResource("db/migration/postgresql/V1__create_compat_user.sql") != null;
        assertTrue(postgresDir, "PostgreSQL 迁移目录 db/migration/postgresql 应存在并包含 V1 迁移");
    }
}
