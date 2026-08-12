-- DEV-0002 Flyway PostgreSQL 迁移夹具（验证 Flyway 迁移能力，非业务 Schema）。
-- 目录约定：PostgreSQL 迁移独立维护于 db/migration/postgresql。
CREATE TABLE compat_user (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
