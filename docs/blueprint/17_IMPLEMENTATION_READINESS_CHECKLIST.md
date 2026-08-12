# 17 — 实施准入与完成清单

> 状态：Accepted  
> 适用范围：V1 Enterprise Pilot 的所有产品、研发、测试与交付工作  
> 目的：把 Blueprint 转换为所有 AI Agent、开发人员和人工评审可逐项核验的执行门禁

## 1. 使用规则

本清单不是建议列表，而是实施门禁。每次只允许启动一个边界清晰的业务模块。开始编码前填写“模块开工记录”，合并前填写“模块完成记录”。任何条目不适用时必须写出理由，不能直接删除。

一项能力只有同时满足以下条件才可以标记为 `Done`：

1. 真实数据链路完整，不依赖 Mock、固定 JSON、内存仓库或人工改库。
2. 正常、异常、权限、并发、幂等和审计路径均已实现。
3. 数据库、API、事件、状态机与文档一致。
4. 自动化测试达到质量门禁。
5. 不引入与 AgentScope 重叠的 Runtime 能力。
6. 不把尚未验证的兼容能力描述为生产能力。

## 2. 全局开发前置门禁

| ID | 检查项 | 通过标准 | 当前状态 |
|---|---|---|---|
| G-01 | 核心依赖兼容 PoC | Java 21、Spring Boot 4.1.0、AgentScope 2.0.1 可共同构建和运行 | **PASS**（2026-08-11，DEV-0001：41/41 真实兼容测试，`./mvnw clean verify`） |
| G-02 | 外围依赖冻结 | MyBatis-Plus、Sa-Token、JDBC、Redis、Flyway 版本有兼容报告和 ADR | **PARTIAL**（2026-08-12，DEV-0002：MySQL/PostgreSQL/Redis/Sa-Token/MyBatis-Plus/Flyway 通过并冻结（ADR-0007 Proposed）；**DM8 兼容认证未完成（无合法服务器环境）→ 不得 PASS**） |
| G-03 | AgentScope 能力盘点 | Harness/Core、Provider、Persistence、Recovery、Observability 的实际 API 与边界形成适配清单 | PARTIAL（DEV-0001/0003：能力清单已验证大部分；**Redis Persistence/Recovery 已验证（真实 Redis，C-1~C-12，ADR-0008 Accepted）**；**真实 Provider BLOCKED_BY_CREDENTIAL**；AgentScope Checkpoint 待评审——不夸大为通过） |
| G-04 | WPS Renderer 门禁 | 免费版的授权、自动化接口、无人值守、模板保真、稳定性和恢复测试通过 | Blocked：待验证 |
| G-05 | Skill 验收材料 | 脱敏真实数据、三类正式模板、人工认可 Golden Result 全部到位 | Blocked：待试点企业提供 |
| G-06 | 私有 GitHub CI | 分支保护和完整 Actions 质量门禁已启用 | Pending |
| G-07 | 第三方开源组件合规基线 | LICENSE、NOTICE、THIRD-PARTY-NOTICES、SBOM 流程已建立，且与闭源商业分发兼容 | Pending |
| G-08 | Coding真实材料 | ≥2个脱敏真实Repository、静态+SSR、任务/验收/测试/非生产Secret齐备 | Blocked：待试点企业提供 |
| G-09 | Sandbox隔离PoC | Rootless Docker+gVisor、Broker、资源隔离、恢复与核心网络阻断通过 | Blocked：待验证 |
| G-10 | Build供应链PoC | Rootless BuildKit、Harbor、Gitleaks、Semgrep、Trivy、Cosign、Provenance通过 | Blocked：待验证 |
| G-11 | Application Host PoC | Preview/Production分池、Traefik、Envoy、Blue-Green、Rollback、ACME通过 | Blocked：待验证 |
| G-12 | Git Provider PoC | GitHub App和至少一个标准Git Provider真实读写/分支/Push/PR或Fetch通过 | Blocked：待验证 |
| G-13 | V1 Web Product Gate | 两条P0均可仅通过Web完成；Web E2E、响应式、无障碍、权限、SSE恢复、组件治理和覆盖率全部通过 | Pending |
| G-14 | assistant-ui Agent交互PoC | React 19/Vite/Tailwind/shadcn组合、精确版本、`@gwnexus/assistant-ui`、Custom Runtime对Nexus REST/SSE/Last-Event-ID、Tool UI安全、zh-CN、a11y、许可与升级矩阵全部通过 | Blocked：待验证 |

G-01 已通过（DEV-0001）。G-02 与 G-03 完成前，只允许建立仓库骨架、契约、测试基建和兼容 PoC，不允许大规模业务编码。G-04 未通过前可以实现 Renderer SPI、队列和测试夹具，但不得宣称自动 Office Artifact 达到生产级。G-05 未完成前不得宣称经营分析 Skill 已通过生产验收。G-08 未完成前不得宣称真实企业Coding项目生产就绪；G-09至G-12未通过前不得开放Production Application发布。G-14 未通过前只允许构建 `@gwnexus/assistant-ui` PoC 和契约测试，不得在业务页面散落接入上游或自研临时聊天组件。Desktop属于V1.1，任何V1 Work Item、CI或发布包均不得实现、构建或交付Desktop。

## 3. 单模块开工记录

每个模块在实现前必须形成以下内容，并由产品架构负责人确认：

```text
模块名称：
业务目标：
范围内用例：
明确排除：
领域聚合与不变量：
调用链：
数据流：
权限与 Scope：
数据分级与外发策略：
数据库迁移：
API/事件变化：
状态机变化：
审计事件：
失败与恢复策略：
测试计划：
文档变化：
关联 ADR：
```

任何一项答案不明确且会影响安全、持久化、公共契约或架构边界时，必须先补规格或 ADR，不能靠实现者临场决定。

## 4. 推荐实施顺序与退出条件

### Phase 0：兼容性与工程基座

必须产出：

- Maven Monorepo与前端 pnpm workspace；
- Spring Boot 与 AgentScope 内嵌最小真实执行链；
- MySQL/PostgreSQL/Redis/MinIO 的 Testcontainers 基础设施；
- Flyway 双方言目录与迁移校验器；
- OpenAPI 生成、Problem Details、UUIDv7、UTC、幂等与 Trace 基础组件；
- GitHub Actions、Secret 扫描、SCA、SBOM、镜像扫描；
- WPS Renderer Compatibility Test Harness。

退出条件：G-01 至 G-03 通过；核心版本无隐式降级；失败项均有正式 ADR。

### Phase 1：身份、安全与企业边界

实现顺序：Tenant/Organization → LDAP/AD 与 Break Glass → Role/Scope/Permission → Secret Provider → Audit → Data Classification。

退出条件：五种角色矩阵通过；跨 Tenant/Workspace/Document/Chunk/Artifact 越权测试为零；密码与 Secret 不落业务库、不进日志、不返回前端。

### Phase 2：Managed Workspace 与知识链路

实现顺序：Workspace → Web 文件上传/续传 → 文件版本 → MinIO 权威副本 → 解析 → Chunk/Embedding → ACL 检索 → Knowledge Snapshot。

退出条件：上传、修改、冲突、删除请求、重建索引和 Snapshot 均可追溯；支持格式之外的文件明确拒绝或降级提示；L3 外部 Embedding 为零。

### Phase 3：Task、Skill 与 AgentScope 集成

实现顺序：版本化 Skill 资产 → Task API → AgentScope Adapter → Business Analysis Agent → Data/Knowledge Tools → Review Agent → Checkpoint/Recovery → SSE。

退出条件：业务 Task 与 Execution 状态边界清晰；取消、重试、服务重启、模型异常恢复通过；没有自研 Agent 循环、Runtime Workflow 或模型协议 SPI。

### Phase 4：Artifact、Renderer 与审批

实现顺序：Artifact Model → Render Queue → Renderer SPI → WPS Provider → Preview → 两级审批 → 外部编辑回传 → 发布与归档。

退出条件：G-04 通过；`.docx/.xlsx/.pptx` 可编辑；关键模板元素、公式和图表满足保真测试；Artifact 版本不可覆盖；任务与 Artifact 状态不混用。

### Phase 5：通知、运维与经营分析验收

实现顺序：站内/Web/邮件通知 → OTel/Prometheus/Grafana/Loki → `nexus-edge-ctl`平台安装 → 备份恢复 → DMZ 模板 → 离线交付 → Golden Dataset Eval。

退出条件：通知关键事件无漏发；RPO/RTO 演练通过；安全零事故；至少 20 次真实闭环任务成功率不低于 90%；人工加工时间降低至少 70%。

### Phase 6：Coding Project、Git与Agent执行

实现顺序：Coding Project/Repository Binding → GitHub/Generic Git → Build Contract/Runtime Catalog → Code Index → Coding Plan Gate → Coding Agent → Sandbox Broker/Host → ChangeSet/Push Gate → Preview。

退出条件：G-09、G-12通过；真实Repository完成Plan、Sandbox修改、Build/Test、Diff、Push和Preview；无Host Shell、Secret和核心网络突破。

### Phase 7：供应链与Application发布

实现顺序：Build Worker/BuildKit → Harbor → Scanner/Findings/Exception → SBOM/Signature/Provenance → Environment/Secret → Release Candidate → Host Agent → Traefik/Envoy → Blue-Green/Rollback → Domain/ACME → Runtime Observability/Retention/Backup。

退出条件：G-10、G-11通过；静态和SSR应用完成Production一键发布、HTTPS、自定义域名和回滚；门禁绕过、Digest不一致和Production Secret泄漏为0。

### Phase 8：V1 Web Product Gate

使用 Web 独立执行经营分析与 Coding 两条 P0 的完整 E2E，完成管理、执行、Evidence/Diff、Preview/Artifact、审批、Production、Domain、通知和审计；验证响应式、无障碍、权限、SSE恢复、视觉回归、组件治理和覆盖率。

退出条件：G-13通过并形成可审计证据。Desktop不属于本Gate或V1。

### Phase 9：双P0 Enterprise Pilot

使用企业材料分别执行经营分析与Coding Workspace验收。两条轨道都必须完成真实用户、真实数据/Repository、成功率、安全、恢复和满意度/价值验证。任一轨道P0失败，整体V1不得签字。

## 5. 模块完成定义

### 5.1 数据层

- 使用 UUIDv7 主键、`tenant_id`、必要的 `workspace_id`、UTC 时间和乐观锁/版本字段；
- Flyway Migration 可从空库重复部署；MySQL 为生产基线，DM8 变体不与 MySQL 脚本混用；
- 唯一约束、检索索引、外键/逻辑引用和软删除条件已明确；
- 大文件不进入数据库；对象、元数据、向量与缓存的删除一致性由事件和补偿保证；
- 不使用数据库自增业务主键、隐式 DDL 或供应商特性泄漏到领域层。

### 5.2 领域与应用层

- 状态转换集中在聚合/领域服务，Controller 和 Mapper 不直接改变状态；
- 业务不变量、幂等、并发冲突、重试边界和错误码均有测试；
- 事务只覆盖本地一致性，跨组件事件使用 Transactional Outbox；
- AgentScope Execution、Redis Stream 消费和 Renderer Job 均保留清晰的责任边界；
- 方法和关键算法包含解释“为什么”的中文注释，不用注释复述代码表面行为。

### 5.3 API 与事件

- 路径使用 `/api/v1`、复数资源名、统一分页与 Problem Details；
- 所有资源查询进行 Role + Scope + Permission 校验，不相信客户端传入的 Tenant；
- 非天然幂等写操作支持 `Idempotency-Key`；
- SSE 事件可持久化回放，支持 `Last-Event-ID`，不暴露模型隐式思维链；
- OpenAPI、TypeScript SDK、领域类型和实际实现一致；
- 破坏性契约变化必须新增 API 版本或 ADR，不能静默修改。

### 5.4 安全与审计

- 认证、授权、数据分类、Model/Context/Embedding/Tool Policy 均在服务端强制执行；
- L2 外发内容满足最小必要原则；L3 对外生成模型与 Embedding 请求均被拒绝；
- 文件下载使用短时授权或应用代理，MinIO 不直接公开；
- 每个关键行为记录 Actor、Resource、Action、Result、Task/Execution/Trace ID；
- 日志、异常、SSE、前端状态、测试夹具均不得包含 Secret 或未经许可的原文。

### 5.5 测试与交付

- 领域/应用层行覆盖率不低于 80%；关键权限、安全策略、状态机分支为 100%；前端核心流程不低于 70%；
- 单元、Testcontainers 集成、契约、越权、故障恢复、Golden Dataset Eval 按模块适用范围通过；
- 镜像可在 Ubuntu Server 24.04 LTS + Docker Compose 的干净环境安装、升级、备份和恢复；
- 部署通过`nexus-edge-ctl`单一入口完成，具备预检、幂等、阶段状态、失败诊断和健康验收；不得要求验收人员手工改库或逐容器操作；
- 没有 TODO、`UnsupportedOperationException`、空实现、假数据或只有 happy path 的实现；
- 变更报告列出文件、Schema、API、测试与未完成项。

### 5.6 前端组件治理

- 开发前已检索 `@gwnexus/ui`、shadcn/ui 和批准的第三方组件；Agent 对话场景还必须检索 `@gwnexus/assistant-ui` 与经锁定的 assistant-ui 官方能力，不凭记忆判断“没有可用组件”；
- 页面使用共享组件和 Design Token，不复制组件源码到业务目录后独立修改；
- 业务组合组件只封装 Workspace、Task、Evidence、Artifact、Approval 等领域语义；
- 不自行实现已有 Button、Form、Dialog、Table、Toast、Menu、Date Picker、Loading 等通用能力；
- 不自行实现已有 Thread、Message、Composer、ThreadList、ActionBar、Streaming/Retry State、Tool UI 等 Agent 对话基础能力；
- 业务页面不直接导入 `@assistant-ui/react`；只有 `@gwnexus/assistant-ui` 可以封装上游 Component/Primitive、Custom Runtime、主题、i18n和Tool UI注册；
- assistant-ui 只消费 Nexus REST/SSE 安全事件，不能直连模型、替代 AgentScope/Nexus 状态机或展示隐藏思维链、Secret和未授权原文；
- 新增通用基础组件具备复用分析、批准记录、无障碍测试、交互测试和视觉回归测试；
- 未通过组件治理审查的前端变更不得合并，即使页面功能和覆盖率已经通过。
- 本组件治理只适用于GW Nexus Edge自身Web/Desktop，不强制用户Coding Project使用shadcn/ui或`@gwnexus/ui`。

## 6. 发布门禁

### 6.1 技术发布候选（RC）

- 所有 P0 自动化门禁通过；
- 阻断级与高危漏洞为零，第三方许可证无未处置冲突；
- 数据迁移、回滚/前向修复、备份恢复和升级演练通过；
- 关键 Dashboard、告警、审计导出和支持手册可用；
- WPS Compatibility Gate 已给出明确的 Pass/Fail，不允许“部分可用”冒充 Pass。

### 6.2 Enterprise Pilot 验收

- 使用企业提供的脱敏真实数据、正式模板和 Golden Result；
- 关键数字、计算公式和事实引用 100% 可追溯；无来源数字和虚构引用为零；
- 冲突数据被提示，证据不足被说明，推断结论有标记；
- Word/PPT/Excel 只需轻量人工审核，不需重新制作；
- 完成不少于 20 次闭环任务，成功率不低于 90%；
- Workspace/文件/Chunk/Artifact 越权、L3 外发和关键审计缺失均为零；
- 核心用户满意比例不低于 80% 或平均评分不低于 4/5；
- 任一 P0 未通过即延期，不得以 Mock、人工后台补数据或降低标准换取签字。
- Coding Workspace完成≥20次闭环、成功率≥90%，并满足新建/导入、静态/SSR、Git Provider、域名、发布、回滚和性能样本。
- Coding越权发布、Gate绕过、Production Secret泄漏、核心网络横向访问、Commit/Image不一致均为0。
- 经营分析与Coding两条P0轨道必须分别通过；任何一条失败即整体V1延期。

## 7. AI Agent 执行规则

任何 AI Agent 接到开发任务时必须：

1. 先定位本文件、决策基线和对应领域文档；
2. 输出模块职责、数据流、调用链、Schema、API 与文件变化；
3. 只实现一个业务模块，不顺手扩张相邻领域；
4. 优先复用 AgentScope 与成熟框架，不以“更方便”为由新建平台抽象；
5. 在缺少外部依赖、企业样本或许可结论时实现可验证边界，并明确报告阻塞；
6. 完成后按 `AGENTS.md` 报告修改文件、数据库变化、API 变化、测试与未完成项。
7. 前端任务先列出现有组件复用映射；未经批准不得创建新的通用基础组件。
8. Coding模块先读取`21_CODING_WORKSPACE.md`、`22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md`和`23_CODING_APPLICATION_DEPLOYMENT.md`，不得把用户代码放到Core/AgentScope/Application Host管理环境执行。
9. 用户Coding Project的UI选型由用户决定，不得把Nexus Edge自身组件约束强加给用户Repository。
10. V1不得接受Desktop功能开发任务；Desktop必须建立独立V1.1 Work Item并遵循ADR-0004。

本清单不能替代领域规格。若清单与 `00_DECISIONS.md` 或领域文档冲突，停止实现并发起 ADR，不自行选择更宽松的解释。
