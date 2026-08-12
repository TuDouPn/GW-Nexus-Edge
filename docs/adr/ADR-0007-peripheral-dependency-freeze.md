# ADR-0007 — G-02 外围依赖版本冻结（MyBatis-Plus/Sa-Token/JDBC/Redis/Flyway）

> 状态：**Proposed（草案，待产品架构负责人批准；测试已完成，结论见 docs/dev-0002/COMPATIBILITY_REPORT.md）**
> 日期：2026-08-12（草案）
> 决策人：（待产品架构负责人）
> 关联决策/Issue：DEV-0002、G-02
> 关联 ADR：ADR-0001、ADR-0006（核心版本 Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 固定不变）

## 背景

G-02 要求 MyBatis-Plus、Sa-Token、JDBC、Redis、Flyway 版本有兼容报告和 ADR。DEV-0002 在
Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 固定核心下，用真实 Testcontainers 环境完成
兼容验证（55/55 测试全绿，其中新增 14 个外围依赖测试）；DM8 因无合法服务器环境未验证（BLOCKED）。

## 决策

1. **MyBatis-Plus**：`com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17`（官方 Spring Boot 4
   Starter）；分页插件显式声明 `com.baomidou:mybatis-plus-jsqlparser-4.9:3.5.17`（3.5.17 起为
   optional 模块）。
2. **Sa-Token**：`cn.dev33:sa-token-spring-boot4-starter:1.45.0`（官方 Spring Boot 4 Starter，
   内置 sa-token-jackson3 对齐 SB4 Jackson 3）；Redis 持久化 `cn.dev33:sa-token-redis-template:1.45.0`。
3. **MySQL JDBC**：`com.mysql:mysql-connector-j:9.7.0`（Spring Boot 4.1.0 BOM 管理）。
4. **PostgreSQL JDBC**：`org.postgresql:postgresql:42.7.11`（Spring Boot 4.1.0 BOM 管理）。
5. **Redis Client**：`spring-boot-starter-data-redis`（Lettuce `7.5.2.RELEASE`，SB BOM 管理；
   RD-1 原则优先默认客户端；不引入 Redisson）。
6. **Flyway**：`flyway-core`/`flyway-mysql`/`flyway-database-postgresql:12.4.0`（SB BOM 管理）+
   `org.springframework.boot:spring-boot-flyway:4.1.0`（SB4 模块化自动配置）。
7. **Testcontainers**：`1.21.4`（覆盖 SB 4.1.0 BOM 2.0.5；原因：2.0.x 移除 mysql/postgresql/
   junit-jupiter 模块；1.20.6 的 docker-java 无法协商现代 daemon API）。
8. **DM8**：`com.dameng:DmJdbcDriver18` 坐标可用（Maven Central 可解析），**未冻结版本**——
   待取得合法驱动与 DM8 授权环境后固定（独立 Profile `-P dm8-compat`；禁 systemPath、禁提交 Git、
   记录 SHA-256 与许可证）。**DM8 未验证前 G-02 保持 PARTIAL，不得 PASS。**

## 验证

- `./mvnw clean verify`（JDK 21）：55/55 全绿（41 既有 AgentScope + 14 外围依赖）。
- 组合 Smoke Test：同一 Spring 上下文装配 AgentScope + MyBatis-Plus + Sa-Token + Flyway + Redis +
  JDBC 并真实运行。
- dependency:tree：无版本冲突；Jackson 2（2.21.4）与 Jackson 3（3.1.4）共存由 SB4 BOM 管理、实证无冲突。
- 兼容发现（spring-boot-flyway 模块化、SB4 JDBC 包迁移、Testcontainers 模块变更、分页插件 optional）
  详见 COMPATIBILITY_REPORT.md §8。

## 影响

- 领域：无业务 Schema/API/事件变化；新增 `nexus-edge-compatibility-test` 测试模块（不进生产制品）。
- 安全：Sa-Token Redis 会话；Secret 仍走 Provider/引用；测试值仅存 test。
- 迁移：后续业务模块（Workspace/用户/权限）直接使用本 ADR 冻结版本。
- 升级风险：Testcontainers 2.x 与 SB 未来 BOM 升级需重新评估（2.x 移除 JDBC 容器模块）。

## 未解决问题

- **DM8 兼容认证**：驱动可解析但无服务器环境 → BLOCKED；取得环境后经 `-P dm8-compat` 补充验证。
- **CI（G-06）**：本地验证已过；CI 需配置 Docker/colima 与镜像源复现。

## 待批准后原子同步

- 00_DECISIONS.md §3：ORM（MyBatis-Plus 3.5.17）/Auth（Sa-Token 1.45.0）/DB（MySQL 9.7.0、
  PostgreSQL 42.7.11、DM8 待定）/Cache（Redis/Lettuce 7.5.2）/Flyway 12.4.0 版本冻结。
- 05_DATA_ARCHITECTURE.md：驱动与迁移支持矩阵。
- 17_IMPLEMENTATION_READINESS_CHECKLIST.md：G-02 状态（DM8 未验证 → PARTIAL）。
- 16_GLOSSARY.md：如有新增术语。
