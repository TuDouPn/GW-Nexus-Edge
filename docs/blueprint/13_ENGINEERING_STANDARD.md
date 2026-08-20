# 13 — 工程、仓库与编码规范

> 状态：Accepted

## 1. 命名

| 项目 | 规范 |
|---|---|
| Repository | `gw-nexus-edge` |
| Java GroupId | `com.gwnexusedge` |
| Java root package | `com.gwnexusedge.nexus.edge` |
| DB table prefix | `gw_` |
| Service/image prefix | `nexus-edge-` |
| npm scope | `@gwnexus/` |
| REST base | `/api/v1` |

禁止继续使用 `com.nexusedge` 或 `nx_`。

## 2. Monorepo

```text
gw-nexus-edge/
├─ AGENTS.md
├─ backend/
│  ├─ pom.xml
│  ├─ nexus-edge-boot/
│  ├─ nexus-edge-domain-*/
│  ├─ nexus-edge-agentscope-adapter/
│  └─ nexus-edge-infrastructure/
├─ apps/
│  ├─ web/
│  ├─ desktop/              # V1.1；V1不得实现或构建
│  └─ renderer-windows/
├─ packages/
│  ├─ ui/
│  ├─ assistant-ui/
│  ├─ design-tokens/
│  ├─ api-client/
│  ├─ domain-types/
│  ├─ agent-events/
│  └─ i18n/
├─ contracts/
│  ├─ openapi/
│  ├─ events/
│  ├─ artifact-model/
│  ├─ skill-manifest/
│  ├─ coding-build-contract/
│  ├─ coding-events/
│  └─ deployment-intent/
├─ deployment/
│  ├─ nexus-edge-ctl/
│  ├─ compose/
│  ├─ schemas/
│  ├─ profiles/
│  └─ offline-package/
├─ coding-infrastructure/
│  ├─ sandbox-host/
│  ├─ build-worker/
│  ├─ application-host/
│  ├─ traefik/
│  ├─ envoy-egress/
│  ├─ harbor/
│  └─ security-tools/
├─ deploy/
│  ├─ compose/
│  ├─ nginx/
│  ├─ offline/
│  └─ observability/
├─ docs/
├─ licenses/
├─ NOTICE
└─ THIRD-PARTY-NOTICES.md
```

客户端范围受 ADR-0004 约束：V1 只实现 `apps/web` 和客户端中立共享 Package。`apps/desktop` 属于 V1.1，V1 不得实现、构建、打包或发布 Desktop，也不得新增 Desktop 专用 API。V1.1 Desktop 必须消费 V1 已稳定的共享契约，禁止复制 Web 业务逻辑。

## 3. Backend 基线

- Java 21 LTS。
- Spring Boot 4.1.0。
- AgentScope Java 2.0.1。
- Maven 3.9+ 和 Maven Wrapper。
- MyBatis-Plus、Sa-Token、Redis Client、Drivers 先兼容测试再冻结。

兼容失败不得静默降级核心版本。必须记录 ADR，说明问题、证据、方案和影响。

## 4. 模块结构

每个领域模块采用：

```text
api/              inbound DTO/Controller contract
application/      commands, queries, use cases
domain/           aggregate, value object, policy, port, event
infrastructure/   MyBatis, Redis, MinIO, LDAP, external adapters
```

- Entity 不等同数据库 Record。
- Mapper/DO 留在 infrastructure。
- DTO 不进入 Domain。
- 事务边界位于 Application Service。
- Domain Exception 转换为统一 Problem Error。

## 5. 数据访问

- 使用 MyBatis-Plus 承担通用 CRUD，但复杂查询允许在 Infrastructure 中使用经过测试的显式 SQL。
- “禁止业务代码直接 SQL”不等于禁止必要的 Repository SQL。
- 所有查询显式包含 Tenant/Workspace 过滤或由强制拦截器注入并测试。
- 分页必须有稳定排序；禁止无界列表。
- 乐观锁、唯一约束与幂等不能只依赖应用层先查后写。

## 6. 注释与代码质量

- 生成代码必须有清晰注释，解释关键业务规则、权限、事务、幂等、并发和第三方边界。
- 注释解释“为什么”和不变量，不逐行复述语法。
- 方法保持单一职责；禁止巨大 Service、魔法值、重复逻辑和隐式状态变化。
- 禁止 `return null`、`UnsupportedOperationException`、固定 JSON、Fake Repository 和未声明占位。
- 测试可以使用 Mock/Test Double，但不得用 Mock 冒充真实集成测试或生产数据。

## 7. Frontend

- React 19 与 TypeScript strict。
- API 类型由 OpenAPI 生成或经契约验证，禁止手工漂移。
- Server State 与 UI State 分离。
- 权限隐藏不是安全控制；后端始终重新授权。
- 所有用户文本使用 i18n key，V1 提供 `zh-CN`。
- 组件满足 Loading/Empty/Error/Disabled/Permission Denied/Offline 状态。
- V1.1 Desktop Rust Command 必须使用最小 Tauri Capability 和路径白名单；本条不构成V1实现授权。
- 组件实现必须遵循 `@gwnexus/ui` → shadcn/ui → 官方组合/Primitive → 经批准第三方组件的复用顺序。
- Agent 对话实现必须遵循 `@gwnexus/assistant-ui` → 经验证的 assistant-ui 官方 Component/Primitive/Tool UI → `@gwnexus/ui`/shadcn/ui 的专用顺序。
- `@assistant-ui/react`、assistant-ui Registry 和 Custom Runtime API 只能由 `packages/assistant-ui` 直接依赖；`apps/web` 和业务 Feature 只能消费 `@gwnexus/assistant-ui` 公共 API。
- assistant-ui Custom Runtime Adapter 只映射 Nexus REST/SSE/`Last-Event-ID` 契约，不得引入第二套服务端 Runtime、客户端直连模型、Assistant Cloud 或 Vercel AI SDK 后端协议。
- Tool UI 的输入必须是服务端授权和脱敏后的稳定事件 DTO；隐藏思维链、完整 Prompt、Secret 和无权限内容不得进入前端 State、日志、遥测或持久缓存。
- 禁止在应用或页面目录自行实现已有通用基础组件；业务组合组件只能封装领域语义并组合共享基础组件。
- 新增通用组件必须附复用检索记录、无障碍设计、测试方案和产品架构负责人批准记录，否则不得合并。
- Code Review 必须检查是否存在原生元素重复封装、复制组件、页面私有基础组件或绕过共享组件 API 的实现。
- 上述组件治理只约束GW Nexus Edge自身Web/Desktop，不强制Coding Agent操作的用户项目使用任何UI库。用户项目遵循其Repository规范和用户明确选择。

## 8. API/Compatibility

- OpenAPI、JSON Schema、Event Schema 是代码的一部分。
- 破坏性 API 变更需新版本或正式迁移策略。
- SSE 业务事件与 AgentScope 原始事件解耦。
- 不把 Java Enum ordinal、数据库字段或 AgentScope SDK 类直接暴露给客户端。

## 9. Database Migration

- Flyway 是唯一 Schema 生命周期工具。
- MySQL/DM8 使用平行版本号与供应商目录。
- Migration 不可在已发布环境修改 Hash；修复创建新 Migration。
- 破坏性变更使用 Expand/Migrate/Contract。
- 生产执行前进行备份、Dry Run/测试恢复和兼容性检查。

## 10. OSS 合规

- 仓库公开，源代码采用 Apache License 2.0（ADR-0010）；商标、客户数据、企业模板与商业合同不因代码许可证自动授权。
- 保留 Apache-2.0、MIT 等许可证要求。
- assistant-ui 的 MIT 许可证、精确锁定版本、来源和修改必须进入第三方声明、SBOM、依赖扫描与升级记录。
- 复用 grok-app 组件必须记录来源和修改，不复用其 Grok Runtime。
- CI 生成 SBOM、依赖许可证清单和漏洞报告。
- 禁止引入许可不明或来源不明的依赖；第三方许可证须进入 THIRD-PARTY-NOTICES（G-07），不得凭名称猜测。

## 11. Definition of Done

功能完成需按影响范围包含：设计、迁移、Domain、Application、Adapter、API、权限、审计、可观测、单元/集成/契约/E2E 测试、文档和升级影响。纯前端、文档或基础设施任务按其类型应用相应标准，不强制虚构数据库或 API 变化。

## 12. Coding Workspace工程契约

- `nexus.yaml`及JSON Schema属于公共版本化Contract，前端、API、Agent、Sandbox Broker和Build Worker必须使用同一Schema。
- Runtime/Resource/Scanner/Host Profile均版本化，禁止在Service或脚本散落CPU、内存、Node版本、镜像Tag和门禁阈值。
- Production使用Image Digest，不使用`latest`或mutable tag。
- Host Agent通信采用mTLS、签名Deployment Intent、短Lease和防重放Nonce；Core不通过SSH或Docker Socket管理主机。
- Sandbox/Build/Preview/Production配置分别测试，禁止用开发Compose Profile冒充生产隔离。
- Git Authentication与Commit Identity分离；Agent Commit不能冒用触发用户。
- 所有代码写回先形成ChangeSet、测试和Diff，再由用户明确确认Push。
- Production发布只有APPROVER可执行，且安全门禁结果必须在24小时有效期内。
- 安全工具、规则、漏洞数据库、Runtime Image和Build Tool必须锁定版本/Digest并进入供应链证据。

## 13. Coding Agent生成代码标准

对用户项目，Agent必须遵循Repository现有格式、Lint、测试、License和贡献规范。每个Task先生成并获批Coding Plan，再修改代码。完成声明必须基于真实Build/Test/Security结果；缺少验证环境时标记`VALIDATION_INCOMPLETE`。禁止用Mock、硬编码、TODO、空实现或跳过测试伪造完成。关键逻辑需有解释不变量和边界的清晰注释，但不以无意义注释污染用户代码。
