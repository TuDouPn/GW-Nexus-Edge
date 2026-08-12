-- DEV-0002 Flyway MySQL 迁移夹具（验证 Flyway 迁移能力，非业务 Schema）。
-- 目录约定：MySQL 迁移独立维护于 db/migration/mysql（FW-6 目录隔离）；
-- DM8 迁移必须独立维护于 db/migration/dm8，禁止借用 MySQL 迁移结果（FW-7）。
CREATE TABLE compat_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
