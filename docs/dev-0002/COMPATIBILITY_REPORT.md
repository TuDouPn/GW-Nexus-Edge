# DEV-0002 — G-02 外围依赖兼容性验证报告（PARTIALLY_VERIFIED，评审修正版 R2）

> 状态：**PARTIALLY_VERIFIED**（MyBatis-Plus/Sa-Token/MySQL/PostgreSQL/Redis/Flyway 通过；
> **DM8 未验证（BLOCKED）→ G-02 不得标记 PASS**）
> 日期：2026-08-11（设计批准）；2026-08-12（实现与验证完成；评审修正 R2）
> 分支：`agent/DEV-0002-peripheral-dependency-compatibility`
> 核心版本（固定）：Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1（未降级）
> 关联门禁：G-02（外围依赖冻结，建议状态 **PARTIAL**）

---

## 0. 已验证 / 未验证 / BLOCKED 严格区分（评审 P2-11）

| 类别 | 内容 |
|---|---|
| **已验证（真实运行证据）** | MyBatis-Plus 3.5.17（真实 MySQL CRUD/事务/分页）；Sa-Token 1.45.0（真实 HTTP 认证链路）；Redis/Lettuce 7.5.2（KV + Streams）；Flyway 12.4.0（MySQL/PostgreSQL migrate/validate/幂等/checksum）；JDBC（MySQL 9.7.0 / PostgreSQL 42.7.11 连接/事务/UTC/类型映射）；组合 Smoke（单一上下文全组合真实运行）；55/55 测试 |
| **未验证（有驱动可解析，无服务器环境）** | DM8 兼容认证（驱动坐标 Maven Central 可解析；无官方 DM8 服务器镜像/授权环境）；DM8 的 MP-6/FW-7/JD 同等级用例未执行 |
| **BLOCKED** | DM8 服务器环境（无官方镜像、无授权实例、本地无安装）→ 驱动解析之外的一切 DM8 验证被阻断 |
| **环境工作区（非版本结论）** | colima Docker daemon；DOCKER_HOST；TESTCONTAINERS_RYUK_DISABLED=true（colima socket 挂载限制的官方配置规避）；Docker Hub 网络阻断 → registry mirror（Digest 固定） |

## 1. 结论摘要

| 依赖 | 冻结版本 | 结论 | 关键证据 |
|---|---|---|---|
| MyBatis-Plus | `mybatis-plus-spring-boot4-starter` 3.5.17 | **通过（已验证）** | 官方 SB4 Starter；真实 MySQL CRUD/事务/分页（Testcontainers mysql:8.4.5） |
| Sa-Token | `sa-token-spring-boot4-starter` 1.45.0 + `sa-token-redis-template` 1.45.0 | **通过（已验证）** | 官方 SB4 Starter；真实 HTTP 登录/Token/角色权限/注销/Redis 持久化/第二 Context 会话恢复 |
| MySQL JDBC | `com.mysql:mysql-connector-j` 9.7.0（SB BOM） | **通过（已验证）** | 真实 MySQL 连接/事务/UTC/类型映射 |
| DM8 JDBC | `com.dameng:DmJdbcDriver18`（坐标可解析） | **BLOCKED（未验证）** | 无官方 DM8 服务器镜像/授权环境；仅驱动解析可验证 |
| PostgreSQL JDBC | `org.postgresql:postgresql` 42.7.11（SB BOM） | **通过（已验证）** | 真实 PostgreSQL 连接/事务/UTC/类型映射 |
| Redis Client | `spring-boot-starter-data-redis`（Lettuce 7.5.2.RELEASE，SB BOM） | **通过（已验证）** | KV 读写；Streams Consumer Group/ACK/Pending/Reclaim（redis:7.4.2） |
| Flyway | `flyway-core` 12.4.0 + `flyway-mysql` + `flyway-database-postgresql`（SB BOM）+ `spring-boot-flyway` 4.1.0 | **通过（已验证）** | MySQL/PostgreSQL migrate/validate/幂等/checksum 失败；目录隔离 |
| 组合 Smoke | 全部上述 + AgentScope 2.0.1 | **通过（已验证）** | 单一 Spring 上下文装配并真实运行 |
| Testcontainers | **2.0.5（SB 4.1.0 BOM 版本）** | **通过（已验证）** | 坐标与包名按 2.0 官方迁移规范（§3） |

## 2. 冻结版本（精确，ADR-0007 待批准后落库 00_DECISIONS）

| 依赖 | 坐标 | 版本 | 版本来源/选择依据 |
|---|---|---|---|
| MyBatis-Plus | `com.baomidou:mybatis-plus-spring-boot4-starter` | 3.5.17 | 官方 Spring Boot 4 Starter（SB4 专用，Maven Central 核验） |
| MyBatis-Plus 分页 | `com.baomidou:mybatis-plus-jsqlparser-4.9` | 3.5.17 | 3.5.17 起分页插件为 optional 模块（发现 §8.5），需显式声明 |
| Sa-Token | `cn.dev33:sa-token-spring-boot4-starter` | 1.45.0 | 官方 Spring Boot 4 Starter（首个 SB4 版本） |
| Sa-Token Redis | `cn.dev33:sa-token-redis-template` | 1.45.0 | Sa-Token Redis 持久化 Dao（Spring RedisTemplate，避免 Jackson 依赖） |
| MySQL JDBC | `com.mysql:mysql-connector-j` | 9.7.0 | Spring Boot 4.1.0 BOM 管理 |
| PostgreSQL JDBC | `org.postgresql:postgresql` | 42.7.11 | Spring Boot 4.1.0 BOM 管理 |
| Redis Client | `io.lettuce:lettuce-core`（经 spring-boot-starter-data-redis） | 7.5.2.RELEASE | Spring Boot 4.1.0 BOM 管理；RD-1 原则优先默认客户端 |
| Flyway | `org.flywaydb:flyway-core` / `flyway-mysql` / `flyway-database-postgresql` | 12.4.0 | Spring Boot 4.1.0 BOM 管理 |
| Spring Boot Flyway 集成 | `org.springframework.boot:spring-boot-flyway` | 4.1.0 | SB4 模块化发现（§8.4），SB BOM 管理 |
| **Testcontainers** | `org.testcontainers:testcontainers(-junit-jupiter/-mysql/-postgresql)` | **2.0.5** | **Spring Boot 4.1.0 BOM 的 testcontainers.version=2.0.5（非覆盖）**；坐标与包名按 2.0 官方迁移规范 |
| DM8 JDBC | `com.dameng:DmJdbcDriver18` | 8.1.3.x（待实际获取时固定） | Maven Central 可解析（8.1.2.79~8.1.3.140 核验）；**未冻结（无环境验证）** |

## 3. BOM 继承与 Testcontainers 2.0.5（评审修正 P0）

- **继承 Spring Boot 4.1.0 BOM**：mysql-connector-j 9.7.0、postgresql 42.7.11、lettuce-core 7.5.2.RELEASE、
  flyway-* 12.4.0、spring-boot-flyway 4.1.0、slf4j 2.0.18、logback 1.5.34、netty 4.2.15.Final、
  reactor-bom 2025.0.6、jackson(2) 2.21.4 / jackson(3) 3.1.4。
- **Testcontainers 2.0.5（恢复 BOM 管理版本）**：
  - **修正（评审 P0-1）**：此前"2.0.x 移除 mysql/postgresql/junit-jupiter 模块"的结论<b>错误</b>——
    Testcontainers 2.0 按官方迁移规范<b>重命名</b>模块（`junit-jupiter`→`testcontainers-junit-jupiter`、
    `mysql`→`testcontainers-mysql`、`postgresql`→`testcontainers-postgresql`），并将容器类迁移到
    `org.testcontainers.mysql.MySQLContainer` / `org.testcontainers.postgresql.PostgreSQLContainer`
    （Maven Central 2.0.5 实证，§8.1）。本报告已删除全部"模块移除"与 1.20.6/1.21.4 覆盖结论。
  - **版本管理**：Testcontainers 版本 = Spring Boot 4.1.0 BOM 的 `testcontainers.version`（2.0.5）。
    因 Maven 3.9 对 SB BOM 嵌套 import（testcontainers-bom）未传递到 reactor 子模块（实证：version
    missing），在根 pom dependencyManagement 显式声明 2.0.5（与 BOM 值一致，<b>非覆盖</b>），并保留
    testcontainers-bom import 供依赖管理参考。
- **显式补充（非覆盖）**：`mybatis-plus-jsqlparser-4.9`（分页插件 optional 模块）、`spring-boot-flyway`（SB4 模块化）。

## 4. 测试矩阵结果（可判定验收用例）

| ID | 验收用例 | 结果 | 证据 |
|---|---|---|---|
| MP-1~MP-5 | MyBatis-Plus：SB4 Starter 装配/Mapper 扫描/BaseMapper CRUD/事务提交与回滚/分页 | **通过** | `MybatisPlusCompatibilityTest`（3/3，真实 MySQL） |
| ST-1~ST-8 | Sa-Token：官方 SB4 Starter/HTTP 登录/身份/角色权限/无错 Token 401/注销/Redis 持久化/第二 Context 恢复/403 | **通过** | `SaTokenHttpCompatibilityTest`（3/3，嵌入式 Web Server + 随机端口） |
| RD-1~RD-4 | Redis：Lettuce 优先/KV/Streams CG·ACK·Pending·Reclaim | **通过** | `RedisClientCompatibilityTest`（2/2，redis:7.4.2） |
| FW-1~FW-6 | Flyway：支持模块/migrate/validate/幂等/checksum 失败/MySQL·PostgreSQL 目录隔离 | **通过** | `FlywayCompatibilityTest`（3/3，真实 MySQL+PostgreSQL） |
| JD-1~JD-6 | JDBC：版本矩阵/镜像 Digest/连接/事务提交回滚/UTC/类型映射 | **通过** | `JdbcCompatibilityTest`（2/2） |
| SM-1~SM-3 | 组合 Smoke：单一上下文/真实运行/dependency:tree | **通过** | `CombinedSmokeTest`（1/1） |
| DM8（P1-7） | dm8-compat Profile：驱动解析 + fail-closed | **BLOCKED（fail-closed 已验证）** | `Dm8CompatibilityTest`：Profile 显式启用且缺 endpoint → 测试失败（真实证据，§6） |

**测试镜像（固定版本 + Digest，JD-2）**：
- `mysql:8.4.5` → `sha256:679e7e924f38a3cbb62a3d7df32924b83f7321a602d3f9f967c01b3df18495d6`
- `postgres:16.6` → `sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36`
- `redis:7.4.2` → `sha256:fbdbaea47b9ae4ecc2082ecdb4e1cea81e32176ffb1dcf643d422ad07427e5d9`

## 5. dependency:tree 冲突结论（SM-3）

- **无版本冲突**：Netty 4.2.15.Final、Reactor 3.8.6、SLF4J 2.0.18（单一）、Logback 1.5.34（唯一日志实现；
  log4j-to-slf4j/jul-to-slf4j 为桥接）、jakarta.servlet-api 6.1.0、Lettuce 7.5.2.RELEASE、
  MyBatis 3.5.19 / mybatis-spring 4.0.0 均为单一版本。
- **Testcontainers 2.0.5**：`testcontainers` / `testcontainers-junit-jupiter` / `testcontainers-mysql` /
  `testcontainers-postgresql` 均解析为 2.0.5（dependency:tree 原始输出见随附日志）。
- **Jackson 2（2.21.4）与 Jackson 3（3.1.4）共存**：均由 SB 4.1.0 BOM 管理；SB4 默认 Jackson 3，
  Sa-Token 1.45.0 内置 `sa-token-jackson3` 对齐；组合 Smoke Test 实证共存无冲突。

## 6. DM8 状态（BLOCKED；dm8-compat Profile fail-closed 已验证）

- **已验证事实**：① 驱动坐标 `com.dameng:DmJdbcDriver18` Maven Central 可解析（8.1.2.79~8.1.3.140）；
  ② `-P dm8-compat` 显式启用时，驱动类 `dm.jdbc.driver.DmDriver` 可解析（Class.forName 通过）。
- **未验证**：DM8 服务器连接/事务/迁移（无官方 DM8 服务器镜像、无授权实例、本地无安装）。
- **fail-closed 证据（P1-7）**：`./mvnw -P dm8-compat -pl nexus-edge-compatibility-test test` 且未提供
  `dm8.url/dm8.username/dm8.password`（或 `DM8_URL/DM8_USERNAME/DM8_PASSWORD`）→
  `Dm8CompatibilityTest` **失败**（`fail-closed，禁止静默跳过或假通过`），不静默跳过。
- **能力边界声明（P1-8）**：当前 dm8-compat Profile **仅验证驱动解析 + fail-closed 检查**；
  **不声称可运行与 MySQL/PostgreSQL 同等级的 MP-6/FW-7/JD 用例**；G-02 保持 PARTIAL。
- 后续：取得合法驱动（固定坐标 + SHA-256 + 许可证，不提交 Git）与 DM8 授权环境后，在
  `Dm8CompatibilityTest` 中扩展真实连接/事务/迁移用例。

## 7. 测试证据（55/55 全绿）

| 模块 | 测试类 | 用例 | 结果 |
|---|---|---|---|
| agentscope-adapter（DEV-0001 既有，无回归） | 18 类 | 41 | ✅ |
| compatibility-test（DEV-0002 新增） | MybatisPlusCompatibilityTest | 3 | ✅ |
| | SaTokenHttpCompatibilityTest | 3 | ✅ |
| | RedisClientCompatibilityTest | 2 | ✅ |
| | FlywayCompatibilityTest | 3 | ✅ |
| | JdbcCompatibilityTest | 2 | ✅ |
| | CombinedSmokeTest | 1 | ✅ |
| | Dm8CompatibilityTest | （dm8-compat Profile 专属；默认 excluded） | BLOCKED/fail-closed 已验证 |
| **合计（默认构建）** | | **55** | **0 失败** |

`./mvnw clean verify`（backend，JDK 21）：**EXIT=0，BUILD SUCCESS**，DuplicateJsonObject 警告 0。

**测试运行环境要求（复现）**：Docker daemon 可用（本机经 colima）；`DOCKER_HOST=unix://~/.colima/default/docker.sock`；
`TESTCONTAINERS_RYUK_DISABLED=true`（colima socket 挂载限制的官方配置规避，与版本无关）；
镜像经 registry mirror 拉取（Docker Hub 在该环境被网络阻断），Digest 已固定（§4）。

## 8. 已知兼容发现（记录，已解决/规避）

1. **Testcontainers 2.0 官方迁移**（评审修正 P0-1）：2.0 重命名模块与迁移容器类包
   （`testcontainers-junit-jupiter/-mysql/-postgresql`；`org.testcontainers.mysql.MySQLContainer`、
   `org.testcontainers.postgresql.PostgreSQLContainer`）。**此前"模块移除"结论错误，已删除**。
   另：2.0.5 新包容器类为非泛型（无菱形操作符）。
2. **SB BOM 嵌套 import 未传递**（Maven 3.9 实证）：testcontainers 模块版本未随 SB BOM 传递到
   reactor 子模块 → 根 pom 显式声明 2.0.5（与 BOM 值一致，非覆盖）。
3. **Spring Boot 4.1.0 将 Flyway 自动配置模块化到 `spring-boot-flyway`**（spring-boot-autoconfigure
   不含 Flyway）→ 显式引入（否则启动迁移不执行，实证：Table 'compat_user' doesn't exist）。
4. **Spring Boot 4 JDBC 自动配置包迁移**（`org.springframework.boot.autoconfigure.jdbc` →
   `org.springframework.boot.jdbc.autoconfigure`）→ Test Slice 排除名使用新包名。
5. **MyBatis-Plus 3.5.17 分页插件为 optional 模块**（`mybatis-plus-jsqlparser-4.9`）→ 显式声明。
6. **Sa-Token 1.45.0 内置 sa-token-jackson3**（对齐 SB4 Jackson 3）→ 无 Jackson 冲突（Smoke 实证）。

## 9. 未解决限制（严格区分）

1. **DM8 兼容认证：BLOCKED（未验证）**——无官方 DM8 服务器镜像/授权环境；仅驱动解析可验证；
   MP-6/FW-7/JD 的 DM8 部分未执行；G-02 不得 PASS。
2. **MySQL/DM8 双方言生产迁移**（05 §4）未验证 DM8 侧；MySQL 侧 Flyway 迁移已验证。
3. **Redis Streams XAUTOCLAIM**：spring-data-redis 4.1.0 的 `StreamOperations` 未暴露 XAUTOCLAIM，
   Reclaim 经连接层 XCLAIM 验证（设计允许 XAUTOCLAIM/XCLAIM 二选一）。
4. **CI（G-06）未建立**：本证据为本地 clean verify；CI 需配置 Docker/colima 与镜像源复现。
5. **Redisson 未评估**：RD-1 原则优先 Lettuce，无引入需求。
6. **Sa-Token 仅验证 Harness 级最小语义**（登录/鉴权/Redis 会话恢复），非生产认证。

## 10. G-02 建议状态

- **G-02：PARTIAL**（MySQL/PostgreSQL/Redis/Sa-Token/MyBatis-Plus/Flyway 通过并冻结；
  **DM8 兼容认证未完成 → 不得 PASS**）。
- 待 DM8 真实验证通过后，G-02 方可标记 PASS（届时更新本报告与 17 清单）。

## 11. ADR 与 Blueprint 同步状态

- **ADR-0007（外围依赖版本冻结）：Proposed**，待产品架构负责人批准（本报告 §2 版本表为草案内容）。
- **未经批准不更新 00_DECISIONS.md** 冻结版本（沿用 P0-9 纪律）。
- 批准后原子同步：00_DECISIONS §3（ORM/Auth/DB/Redis 版本）、05（驱动/迁移支持矩阵）、17（G-02）、16 术语表。
