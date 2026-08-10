# 21 — Coding Workspace 产品与工程契约

> 状态：Accepted  
> 优先级：V1 P0  
> 验收日期目标：2026-09-01；质量门禁优先，未通过允许延期  
> 产品边界：前端与 Node.js SSR 应用的 Agent 创建、修改、验证、预览和发布

## 1. 定位

Coding Workspace 是 GW Nexus Edge V1 的第二条生产级 Workspace 闭环，与企业经营分析 Workspace 并列。它不是通用云 IDE、Git 托管平台、后端 PaaS 或 CI/CD 平台。

目标闭环：

```text
创建新项目 / 导入 Git 仓库
  → 代码理解与版本化 Coding Plan
  → 人工批准 Plan
  → Coding Agent 在隔离 Sandbox 修改和验证
  → ChangeSet / Diff 人工确认
  → Agent 分支 Push / GitHub PR
  → Preview
  → Build Once / Security Gates
  → Release Candidate
  → 人工 Production 发布
  → 系统域名或自定义域名 + HTTPS
  → 运行观测 / 一键回滚 / 审计
```

V1 支持：

- Agent 创建全新前端项目。
- 导入并继续修改已有标准 Git 仓库。
- 静态前端和需要 Node.js Server Runtime 的 SSR/Server-rendered 项目。
- V1全部Coding P0操作只通过Web完成；Windows Desktop与本地Repository发现进入V1.1。
- Nexus Edge 自管 Docker 应用主机、Preview、Production、域名和 HTTPS。

## 2. 明确非目标

V1 不提供：

- 托管数据库、Redis、对象存储、后台 Worker、业务定时任务或通用后端服务平台。
- 多仓库原子变更、跨仓库发布编排。
- 人工在线自由编辑代码、完整 IDE 或交互式 Terminal。
- Git 托管服务、GitHub Actions 替代品或通用 CI/CD 产品。
- Git Push/PR 自动启动 Agent、自动创建 Preview 或持续部署。
- Production 无人审批发布。
- DNS Provider API 自动配置、自定义 wildcard domain。
- Bun 生产兼容、多架构镜像、Linux ARM64 或 Windows Container。

SSR 项目可以运行 Framework Server、Server Actions、API Routes 和前端 BFF；这不等于 Nexus Edge 承诺完整后端应用平台。

## 3. 领域归属

```text
Tenant
  └─ Workspace
      └─ Coding Project
          ├─ Repository Binding
          ├─ Project Root / Build Contract
          ├─ Code Index
          ├─ Coding Plan
          ├─ Coding Task / Sandbox
          ├─ ChangeSet
          ├─ Environment / Secret References
          ├─ Preview Deployment
          ├─ Release Candidate
          ├─ Production Deployment
          └─ Domain Binding
```

不变量：

1. 一个 Coding Project 只属于一个 Tenant 和 Workspace。
2. V1 一个 Coding Project 绑定一个权威 Git Repository 和一个版本化 Project Root。
3. 远程 Git Repository 是代码真相源；Workspace 不成为 Git 托管服务。
4. Task、ChangeSet、Release、Deployment 和 Domain 必须绑定具体 Repository Commit 与 Project Root。
5. 同一 Repository 可按不同 Project Root 建立多个 Project，以支持 Monorepo。
6. 单个 Coding Task 不得跨多个独立 Repository 修改代码。

## 4. 角色与权限

| 能力 | SYSTEM_ADMIN | WORKSPACE_OWNER | OPERATOR | APPROVER | SECURITY_AUDITOR |
|---|---:|---:|---:|---:|---:|
| 配置 Host/Registry/Gateway/Runtime Policy | ✓ |  |  |  | 审计 |
| 创建/导入 Coding Project |  | ✓ | Scope 可授权 |  |  |
| 运行 Coding Agent / 开发验证 |  | ✓ | ✓ |  | 审计 |
| 创建 Preview |  | ✓ | ✓ |  | 审计 |
| 确认 ChangeSet Push |  | ✓ | Scope 可授权 |  |  |
| 管理 Environment/Secret Reference | 系统策略 | ✓ | 受限 |  | 审计 |
| 创建 Release Candidate |  | ✓ |  |  | 审计 |
| Production 发布/回滚 |  |  |  | ✓ | 审计 |
| 申请 Security Exception | Scope 可授权 | ✓ |  |  |  |
| 审批 Security Exception |  |  |  |  | ✓ |

约束：

- SYSTEM_ADMIN 不能仅凭平台角色绕过发布或安全门禁。
- Security Exception 申请人与审批人不得是同一用户。
- 安全例外不等于发布批准；Production 仍需 APPROVER 明确执行。

## 5. Project 创建与导入

### 5.1 Agent 创建新项目

1. 用户提交 Project Brief，包括目标、应用类型、功能、目标用户和非功能要求。
2. UI 框架、组件库和视觉技术选型由用户决定；Nexus Edge 不强制用户项目使用 shadcn/ui。
3. Agent 可在 Sandbox 创建项目和 Preview。
4. 正式代码写回前必须绑定远程 Git Repository。
5. GitHub First-Class Provider 可在用户授权后创建远程仓库。
6. GitLab、Gitee 和普通私有 Git Server 在 V1 由用户预先创建空仓库并提供地址。

### 5.2 导入已有仓库

标准 Git HTTPS/SSH 是 V1 接入基线，必须支持 GitHub、GitLab、Gitee 和企业私有 Git Server 的：

- clone、fetch、pull；
- branch、diff、commit；
- push。

V1.1 Desktop 可发现本地 Git Repository，但：

- 只上传用户明确确认的 Commit 历史与选定变更；
- 未提交文件不得静默上传；
- Secret Scan 命中的文件禁止导入；
- 导入后必须指定远程权威 Repository；
- 首次 Push 仍经过 Diff 人工确认。

### 5.3 GitHub First-Class Provider

V1 通过 GitHub App 实现：

- 安装授权和仓库列表；
- 创建新仓库；
- Repository 读写和 Agent 分支；
- Pull Request 创建；
- Push/Branch/PR Webhook；
- Check Run 回写。

V1 不在 Nexus Edge 内执行 PR Merge，也不管理 Issue、Actions Workflow、Release、Package、Project 或 GitHub Organization。

### 5.4 Git Credential 与 Commit Identity

- Authentication 与 Author/Committer Identity 分离建模。
- 通用 Git 支持 HTTPS Access Token 与 SSH Key。
- GitHub 优先使用 GitHub App 短期 Installation Token。
- 长期凭证只由 Secret Provider 管理，数据库保存 Secret Reference。
- Sandbox 只在 Task 生命周期内获得最小权限临时凭证。
- Agent 默认只能写独立工作分支，不得直接 Push 受保护分支。
- Agent Commit 使用明确机器身份，例如 `GW Nexus Edge Agent`，不得冒用人工作者。
- Commit Trailer 必须记录触发用户、Nexus Task ID 和 AgentScope Execution ID。

审计映射：

```text
Trigger User
  → Task ID
  → AgentScope Execution ID
  → Sandbox ID
  → Agent Branch
  → Agent Commit
  → Pull Request
```

## 6. Repository Cardinality 与容量

### 6.1 Monorepo

- `nexus.yaml` 使用 `rootDirectory` 声明应用根目录。
- Workspace Pattern 和 Build Context 必须版本化。
- 共享包只能通过显式允许路径进入 Code Index 与 Build Context。
- Release 和 Domain 必须同时绑定 Repository Commit 与 Project Root。

### 6.2 默认容量

- 单 Repository 工作树：2 GB。
- 单文件：100 MB。
- 单个进入 Code Index 的文本文件：5 MB。
- 超限文件标记 `NOT_INDEXED`，禁止静默截断。
- 支持 Git LFS；LFS 二进制只参与 Checkout、Build 和 Artifact，不进入 Embedding/模型上下文。
- 优先 Partial/Shallow Clone，但 Base Commit、Diff 和构建所需对象必须完整。
- 更高配额只能由管理员分配版本化 Profile。

## 7. Frontend Build Contract

平台支持标准 Node.js 构建/运行契约，不把 Framework List 当作产品边界。

### 7.1 自动识别与显式契约

- React、Vue、Next.js、Nuxt 等框架识别仅是便利能力。
- 版本化 `nexus.yaml` 是权威显式契约。
- 显式配置永远优先于自动识别。
- SSR 项目不得被错误识别或降级成静态站点。

示例：

```yaml
apiVersion: gwnexus.dev/v1
kind: FrontendApplication
metadata:
  name: business-portal
spec:
  projectRoot: apps/web
  deploymentType: SSR # STATIC | SSR
  runtime:
    node: "24"
    packageManager:
      name: pnpm
      version: "10.14.0"
  commands:
    install: pnpm install --frozen-lockfile
    test: pnpm test --run
    build: pnpm build
    start: pnpm start
  output:
    staticDirectory: dist
  network:
    port: 8080
  health:
    protocol: HTTP
    path: /health
  build:
    mode: NODE_CONTRACT # NODE_CONTRACT | CUSTOM_DOCKERFILE
    dockerfile: null
  paths:
    include:
      - apps/web/**
      - packages/ui/**
    ignore:
      - .nexusignore
```

真实 JSON Schema 必须进入版本控制并由 API、CLI、Build Worker 和前端共同消费；上述示例不替代 Schema。

### 7.2 Node.js 与包管理器

- 默认：Node.js 24 LTS。
- 兼容：Node.js 22 LTS。
- Node.js 20 及更早版本不进入 Production 基线。
- 受支持版本由 Runtime Catalog 管理，禁止散落在代码中。
- 基础镜像锁定 Image Digest，禁止静默升级。
- EOL 版本可用于分析和迁移；创建 Production Release 必须有期限 Security Exception。
- 正式支持 npm、pnpm、yarn；使用 Corepack 或显式版本锁定。
- Bun 不进入 V1 生产验收基线。

### 7.3 两种构建模式

`NODE_CONTRACT`：平台根据 `nexus.yaml` 生成确定性 OCI 构建。

`CUSTOM_DOCKERFILE`：

- 必须显式选择并指定路径，平台不得自动采用仓库 Dockerfile；
- 仍由独立 Rootless BuildKit 构建；
- 受基础镜像、Egress、SBOM、SAST、SCA、License、Image Scan 和运行契约门禁控制；
- 不得成为绕过非 Root、健康检查、端口和无状态约束的途径。

### 7.4 依赖供应链

- 支持公共 npm Registry 和企业 npm-compatible Registry。
- Registry 地址与凭证按 Project/Environment 管理。
- Production 必须存在且使用唯一匹配的 Lockfile。
- 使用 `npm ci`、`pnpm install --frozen-lockfile` 或 `yarn install --immutable`。
- 缺少 Lockfile、Manifest/Lockfile 不一致或解析漂移时阻止 Release Candidate。
- Git URL/Tarball 依赖必须固定不可变 Commit 或 Checksum。
- Registry 凭证不得进入镜像层，缓存按内容 Hash 与安全域隔离。

## 8. Coding Plan Gate

Coding Agent 先只读分析，再生成版本化 Coding Plan。Plan 至少包含：

- 目标与验收条件；
- 当前模块职责、数据流和调用链；
- 影响模块、文件和依赖；
- API、Schema、配置和兼容性变化；
- 测试与安全方案；
- 部署与回滚方式。

用户批准 Plan 后才创建可写 Sandbox。实质性偏离必须生成新 PlanVersion 并重新确认。小改可以使用精简模板，但不得跳过 Plan。

## 9. Agent 执行与 ChangeSet

### 9.1 AgentScope 边界

- Coding Agent 由内嵌 AgentScope Java 2.0.1 Runtime 执行。
- 搜索、文件读写、Git、Shell、依赖、测试和开发构建是受 Policy 控制的 AgentScope Tool。
- Tool 通过 Sandbox Broker 操作 Task 绑定的临时 Sandbox。
- Coding Agent 永不获得 Nexus Edge Host Shell、Docker/CRI Socket、宿主文件系统、Production Host 凭证或核心基础设施访问。
- Production OCI 正式构建只由独立 Build Worker + Rootless BuildKit 完成。

### 9.2 完成门禁

Task 只有在以下条件满足时才能声明完成：

- 可审计 Acceptance Criteria 已定义；
- Build 成功；
- 既有测试无回归；
- 与变更相关的新测试通过；
- 完整 Diff 已生成；
- 无未处理 P0 安全问题。

禁止 Mock、硬编码业务结果、TODO、空实现或跳过测试伪造完成。缺少测试或环境时必须标记 `VALIDATION_INCOMPLETE` 并说明风险。

### 9.3 代码写回门禁

1. Agent 可在 Sandbox 内创建本地 Checkpoint Commit。
2. 默认不得直接 Push 远程仓库。
3. 用户查看 Diff、测试和安全结果后执行“确认并推送”。
4. 系统把不可变 Commit Push 到独立 Agent 分支。
5. GitHub 可自动创建 PR；Merge 必须人工并受 Branch Protection 控制。
6. Preview 可在 Push 前基于候选快照创建。
7. Production 只能绑定已 Push、已审核的正式 Commit。

### 9.4 并发仓库变化

- Task 启动时绑定 Base Commit。
- Push 前重新 Fetch 并检查目标分支是否前进。
- Base 过期时禁止 Force Push 和静默覆盖。
- Agent 可在新 Sandbox Rebase/Merge，但必须重跑测试、安全检查和 Diff 审核。
- 冲突产生新 ChangeSetVersion；旧版本不可覆盖。
- 受保护分支永远禁止 Agent Force Push。

## 10. Code Context Index

Repository 按不可变 Commit 建立版本化 Code Index，组合：

- 文件树；
- 全文检索；
- 符号索引；
- 语义 Embedding。

V1 重点解析 JavaScript、TypeScript、JSX/TSX、Vue、HTML、CSS、JSON、YAML 和 Markdown；其他文本仍可全文检索。

必须排除：

- `.git`、`node_modules`、构建产物和二进制；
- `.gitignore`、`.nexusignore` 声明路径；
- Secret Scan 命中内容；
- 超过索引限制的文件。

索引按 Commit 增量更新并与 Task Base Commit 绑定。Embedding 与最终模型上下文继续受 Project L0–L3、Embedding Policy 和最小必要 Context Policy 控制。

## 11. Coding Data 与 Model Policy

- Coding Project 必须设置 L0–L3 等级。
- Repository、Agent Context、Build Log、Diff 和 Code Index 默认继承 Project 等级。
- 自动识别与用户声明只能提高等级。
- L2 只有管理员授权可信外部模型后，才允许发送最小必要代码上下文。
- L3 禁止外部生成模型和外部 Embedding，必须使用企业内部模型。
- `.env`、私钥、凭证及 Secret 命中内容在任何等级下都禁止进入模型、Embedding、日志或 Artifact。

Coding Agent 只能使用管理员发布且通过 Coding Capability Profile 的模型，至少验证长上下文、Tool Calling、结构化输出、代码理解和 Patch 生成。Fallback 只能在相同数据边界和已认证模型之间发生，不能跨安全边界或降级为无模型流程。

## 12. V1 Web 与 V1.1 Desktop 体验

V1 Coding Workspace 必须在 Web 中完成本节全部能力并通过真实 Repository E2E。Desktop Coding 页面、本地Repository发现和Desktop系统通知均不属于V1。

V1 Web 必须完整提供：

- 创建/导入 Project；
- Coding Agent 对话与 Plan 审批；
- Repository 文件树和 Monaco 只读源码查看；
- Diff、Tool/命令输出和测试结果；
- Preview 面板和 URL；
- ChangeSet Push 确认；
- Release Candidate、Deployment、Domain、日志和基础指标；
- 权限范围内的发布与回滚操作。

V1.1 Desktop 规划增加本地 Git Repository 发现、文件选择和系统通知，但仍不得本地执行用户代码。

V1 不提供人工在线自由编辑或交互式 Terminal。人工深度编辑在本地 IDE 完成并 Push 到远程 Repository。

Nexus Edge 自身 Web/Desktop 仍强制复用 `@gwnexus/ui`、shadcn/ui 和批准组件；这一 UI 组件约束不强加给用户的 Coding Project。

## 13. Git 事件边界

GitHub Webhook 只用于：

- 同步 Push、Branch、PR 状态；
- 标记 Project 是否落后；
- 回写 Build/Test/Preview Check。

V1 不因 Webhook 自动启动 Coding Agent、Preview 或 Production。普通 Git Provider 由用户主动 Fetch。自动 PR Preview、Push 构建和持续部署进入 V1.1。

## 14. 主动通知

强制通知事件：

- Coding Task 完成、失败、取消或等待用户处理；
- ChangeSet 等待确认或发生冲突；
- Preview 成功、失败或即将到期；
- Release Candidate 就绪、阻断或等待批准；
- Production 成功、失败、自动回滚或需要人工处置；
- Domain 验证和证书续期异常。

安全阻断、Production 审批、自动回滚和证书失效风险不得被用户完全关闭。

## 15. V1 容量

| 指标 | 验收值 |
|---|---:|
| Coding Project | 50 |
| 同时在线 Preview | 20 |
| Production 应用 | 20 |
| 并发 Coding Sandbox | 5 |
| 并发 Build | 3 |
| 并发 Deployment | 2 |

这些是 V1 验收容量，不是代码常量或架构上限。调度、Quota 和 Worker Pool 从第一天支持增加节点。

## 16. Production Readiness Gate

试点企业必须提供：

1. 至少两个脱敏但结构真实的前端 Repository。
2. 至少一个静态项目、一个 Node.js SSR 项目。
3. 明确的真实改造任务与可验证 Acceptance Criteria。
4. 测试命令、部署配置、非生产 Secret 和人工认可目标结果。
5. 至少一个 Repository 体现企业真实依赖、组件系统和构建复杂度。

材料未到位时可以完成平台、Sandbox、Agent、Build 和 Deployment 工程能力，但不得声明 Coding Agent 已对真实企业项目达到生产级。

## 17. Enterprise Pilot 验收

- 至少 20 次完整 Coding 闭环。
- Agent 新建项目与导入项目各不少于 5 次。
- 覆盖 React/Vue 静态应用和 Next.js/Nuxt SSR 应用。
- 验证 GitHub First-Class Provider 和至少一个标准 Git Provider。
- 端到端成功率至少 90%。
- 至少 5 次自定义域名绑定、10 次 Production 发布、5 次历史 Release 一键回滚。
- Commit、Artifact、Image Digest 不一致次数为 0。
- 越权发布、门禁绕过、Production Secret 泄漏、核心网络横向访问均为 0。

性能指标：

- Sandbox 创建 P95 ≤60 秒。
- Build 完成后 Preview URL 可访问 ≤3 分钟。
- 已批准 Release Candidate Production 发布 ≤5 分钟。
- 历史 Release 一键回滚 ≤2 分钟。
- DNS 生效并完成所有权验证后，证书与 Gateway 激活 ≤10 分钟。
- 目标并发下不得影响 Nexus Edge Core、Agent Task 和既有 Production 应用。
