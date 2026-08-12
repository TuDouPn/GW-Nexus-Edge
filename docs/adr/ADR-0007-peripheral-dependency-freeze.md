# ADR-0007 — G-02 外围依赖版本冻结（MyBatis-Plus/Sa-Token/JDBC/Redis/Flyway）

> 状态：**Proposed（草案，待产品架构负责人批准；测试与治理证据已完成，结论见 docs/dev-0002/COMPATIBILITY_REPORT.md）**
> 日期：2026-08-12（草案；第三轮评审修正 R3）
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
7. **Testcontainers**：`2.0.5`，**单一版本来源**（评审 P0-1）：根 pom 仅 import
   `testcontainers-bom:2.0.5`（与 SB 4.1.0 BOM 的 testcontainers.version 一致），子模块只声明
   坐标、不写版本；坐标与包名按 Testcontainers 2.0 官方迁移规范（`testcontainers-junit-jupiter`/
   `testcontainers-mysql`/`testcontainers-postgresql`；容器类 `org.testcontainers.mysql.MySQLContainer`/
   `org.testcontainers.postgresql.PostgreSQLContainer`）。**迁移性质（评审 P1-12）：模块坐标与包名
   存在破坏性迁移**（旧坐标 junit-jupiter/mysql/postgresql 与旧包 org.testcontainers.containers 在
   2.x 不再作为新坐标使用），升级到 2.x 必须按迁移规范核对坐标与包名。
8. **DM8**：`com.dameng:DmJdbcDriver18` 坐标可解析（Maven Central 核验），**未冻结版本**。
   `-P dm8-compat` Profile 提供真实可执行验证入口（驱动解析 + fail-closed 连接检查，P1-7）；
   **DM8 密码只允许经环境 Secret `DM8_PASSWORD` 或受限 Secret 文件 `DM8_PASSWORD_FILE` 注入，
   禁止 `-Ddm8.password` 命令行参数；错误与日志不输出 Secret Value（P1-13）**。
   **当前无 DM8 授权环境：Profile 仅验证驱动解析，不声称可运行同等级 MP/JD/FW 用例（P1-8）；
   DM8 未验证前 G-02 保持 PARTIAL，不得 PASS。**

## 验证

- `./mvnw clean verify`（JDK 21）：55/55 全绿（41 既有 AgentScope + 14 外围依赖）。
- 组合 Smoke Test：同一 Spring 上下文装配 AgentScope + MyBatis-Plus + Sa-Token + Flyway + Redis +
  JDBC 并真实运行。
- dependency:tree：无版本冲突；Testcontainers 2.0.5 四模块均解析为 2.0.5（单一来源）；
  Jackson 2（2.21.4）与 Jackson 3（3.1.4）共存由 SB4 BOM 管理、实证无冲突。
- dm8-compat Profile：显式启用且缺少 DM8 endpoint/credential 时 fail-closed（真实失败证据）；当前仅
  验证驱动解析（DM8 服务器未验证）。
- 兼容发现（Testcontainers 2.0 破坏性迁移、SB BOM 嵌套 import 未传递、spring-boot-flyway 模块化、
  SB4 JDBC 包迁移、分页插件 optional）详见 COMPATIBILITY_REPORT.md §8。

## 依赖治理证据（评审 P0-2）

### 精确版本与关键传递依赖（Maven Central 实证，2026-08-12）

| 冻结依赖 | 版本 | 关键传递依赖（resolution 实证） |
|---|---|---|
| mybatis-plus-spring-boot4-starter | 3.5.17 | mybatis-plus 3.5.17（core/annotation/spring/extension）；mybatis 3.5.19；mybatis-spring 4.0.0；mybatis-plus-jsqlparser-4.9 3.5.17 |
| sa-token-spring-boot4-starter | 1.45.0 | sa-token-core 1.45.0；sa-token-jackson3 1.45.0（SB4 Jackson 3）；sa-token-spring-boot-webmvc-v3v4-common 1.45.0 |
| sa-token-redis-template | 1.45.0 | sa-token-core 1.45.0；spring-boot-starter-data-redis |
| mysql-connector-j | 9.7.0 | 无（独立驱动） |
| postgresql | 42.7.11 | 无（独立驱动） |
| spring-boot-starter-data-redis | 4.1.0 | spring-data-redis 4.1.0；lettuce-core 7.5.2.RELEASE |
| flyway-core / -mysql / -database-postgresql | 12.4.0 | flyway-core 12.4.0 + 数据库专用模块 |
| spring-boot-flyway | 4.1.0 | spring-boot 4.1.0（Flyway 自动配置模块） |
| testcontainers-bom（统一来源） | 2.0.5 | testcontainers/testcontainers-junit-jupiter/testcontainers-mysql/testcontainers-postgresql 均 2.0.5 |
| agentscope-core/harness/model-openai | 2.0.1 | 核心固定（DEV-0001） |

### Exclusions（本冻结无排除项；记录以明示）

- 本 ADR 冻结的依赖组合**无排除项**（dependency:tree 实证无版本冲突）；`spring-boot-starter-test`
  对 `android-json` 的排除沿用 DEV-0001 记录（DuplicateJsonObjectContextCustomizer 警告处理）。
- 若未来引入需排除的传递依赖，必须在本 ADR 或新 ADR 记录原因与证据。

### 许可证（Maven Central POM 声明实证，2026-08-12；POM 未声明项需在 G-07 开源合规基线核验）

| 依赖 | 许可证（POM 声明） |
|---|---|
| mybatis-plus-spring-boot4-starter | Apache License, Version 2.0 |
| sa-token-spring-boot4-starter | POM 未声明（G-07 核验） |
| mysql-connector-j | GPL v2 with Universal FOSS Exception, v1.0 |
| postgresql | BSD-2-Clause |
| lettuce-core | MIT |
| flyway-core | POM 未声明（G-07 核验） |
| testcontainers | MIT |
| agentscope-core | Apache 2.0 |
| spring-boot | Apache License, Version 2.0 |
| mybatis | POM 未声明（G-07 核验） |
| slf4j-api / logback-classic / netty-common | POM 未声明（G-07 核验） |
| reactor-core | Apache License, Version 2.0 |
| jakarta.servlet-api | EPL 2.0 |

### 已知 CVE / 漏洞扫描（BLOCKED，评审 P0-2-9~11）

- **CycloneDX SBOM 已生成**：`docs/dev-0002/evidence/compatibility-test-sbom.json/.xml`
  （CycloneDX 1.6，138 组件；命令 `org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom`）。
- **漏洞扫描：BLOCKED（漏洞数据不可获得）**——OWASP Dependency-Check Maven Plugin 13.0.0：
  - NVD 数据更新失败（`NvdApiException: Invalid API Key, length of 0`——13.x 客户端强制校验
    NVD API v2 Key，本环境无 Key）；
  - 本地缓存无 NVD 数据（`NoDataException: Autoupdate is disabled and the database does not exist`）；
  - OSS Index 不可达（HTTP 000）。
  - **完整命令、错误与时间证据见 `docs/dev-0002/evidence/dependency-check-evidence.md`。**
- **不得声明"无已知漏洞"**；取得 NVD API Key（Secret Provider）或企业私有漏洞数据源后重扫，
  并对中高危结果逐项记录 disposition（不受影响/已修复/已缓解/接受风险/阻断）——当前无结果可记录。

### 升级风险

1. **Testcontainers 2.x 破坏性迁移**（评审 P1-12）：模块坐标（junit-jupiter→testcontainers-junit-jupiter
   等）与容器类包名（org.testcontainers.containers→org.testcontainers.mysql/.postgresql）变更；
   SB 未来 BOM 升级至 Testcontainers 2.x 时须按迁移规范复核坐标与包名。
2. **SB 未来 BOM 升级**：mysql/postgresql/lettuce/flyway 版本由 SB BOM 管理，升级随 BOM 推进；
   需重新跑本矩阵与组合 Smoke。
3. **Jackson 2/3 共存**：SB4 默认 Jackson 3；新增依赖若强依赖 Jackson 2 API，需复核冲突。
4. **Sa-Token/MyBatis-Plus 与 SB 未来大版本**：官方 SB4 Starter 依赖 SB 4.x；SB 5 需新 Starter/新 ADR。
5. **DM8 未冻结**：取得合法驱动与环境后补验证并更新本 ADR。

## 影响

- 领域：无业务 Schema/API/事件变化；新增 `nexus-edge-compatibility-test` 测试模块（不进生产制品）。
- 安全：Sa-Token Redis 会话；DM8 密码仅环境/Secret 文件注入（P1-13）；Secret 走 Provider/引用。
- 迁移：后续业务模块（Workspace/用户/权限）直接使用本 ADR 冻结版本。
- 供应链：CycloneDX SBOM 已生成；漏洞扫描 BLOCKED（待 NVD Key/数据源）。

## 未解决问题

- **DM8 兼容认证**：驱动可解析但无服务器环境 → BLOCKED；取得环境后经 `-P dm8-compat` 补充验证。
- **漏洞扫描 BLOCKED**：需 NVD API Key（Secret Provider）或企业漏洞数据源后重扫并记录 disposition。
- **G-07 开源合规基线**：POM 未声明许可证的组件需在 G-07 核验（本 ADR 冻结不替代 G-07）。
- **CI（G-06）**：本地验证已过；CI 需配置 Docker/colima 与镜像源复现。

## 待批准后原子同步

- 00_DECISIONS.md §3：ORM（MyBatis-Plus 3.5.17）/Auth（Sa-Token 1.45.0）/DB（MySQL 9.7.0、
  PostgreSQL 42.7.11、DM8 待定）/Cache（Redis/Lettuce 7.5.2）/Flyway 12.4.0 版本冻结。
- 05_DATA_ARCHITECTURE.md：驱动与迁移支持矩阵。
- 17_IMPLEMENTATION_READINESS_CHECKLIST.md：G-02 状态（DM8 未验证 → PARTIAL）。
- 16_GLOSSARY.md：如有新增术语。
