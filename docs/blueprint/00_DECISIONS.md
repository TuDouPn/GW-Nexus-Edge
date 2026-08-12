# 00 — GW Nexus Edge V1 决策基线

> 状态：Accepted  
> 适用范围：V1 Enterprise Pilot  
> 变更方式：仅允许通过 ADR 修改

## 1. 产品决策

| ID | 已冻结决策 |
|---|---|
| P-001 | 官方定位为“基于 AgentScope Runtime 的企业 Agent Workspace 平台”，不是 Agent Runtime 平台。 |
| P-002 | V1 有两条独立且均为 P0 的生产闭环：企业经营分析报告 Workspace、Coding Workspace 前端开发与一键应用发布。 |
| P-003 | 企业业务 Skill 中只有经营分析报告 Skill 承担生产级承诺；项目汇报、知识问答、合同审查、会议纪要仅为 Beta/路线图。Coding Workspace 是第二类 Workspace 产品能力，不计入上述五个业务 Skill。 |
| P-004 | 主入口为预设 Skill 和报告模板，自然语言是参数补充入口。 |
| P-005 | V1 面向单企业私有部署，一个实例对应一个企业 Tenant；数据模型保留 tenant_id。 |
| P-006 | V1 不是公网 SaaS；Cloud Demo 仅使用预置账号和示例数据，不属于 P0。 |
| P-007 | Web Workspace 是 P0；公网/手机接力不是首验 P0，但必须提供 DMZ 接入方案。 |
| P-008 | V1 以生产质量优先；P0 未通过允许晚于 2026-09-01。 |
| P-009 | 项目采用厂商无关的 AI Coding 协作模式；仓库规范、Accepted ADR、版本化契约、测试和 Git Commit 是共识来源，任何 Agent 对话或记忆均不构成决策。 |
| P-010 | Coding Workspace 同时支持 Agent 新建项目与导入标准 Git 仓库；远程 Git Repository 是代码权威源，Nexus Edge 不自建 Git 托管。 |
| P-011 | Coding Workspace V1 只交付静态前端与 Node.js SSR/Server-rendered 应用，不建设通用后端 PaaS、托管数据库或在线 IDE。 |
| P-012 | Preview 可由 Agent 自动创建；Production 必须经过人工明确发布，并绑定已审核、不可变且通过安全门禁的 Commit/Image Digest。 |
| P-013 | V1 唯一用户工作入口为 Web Workspace。Windows Desktop、Tauri 2、目录单向同步、本地 Git 发现、设备能力和 Desktop 系统通知整体进入 V1.1，不属于 V1 开发、构建、部署或验收范围。Windows Renderer 是独立 P0 节点，不属于 Desktop。 |

## 2. AgentScope 边界

| ID | 已冻结决策 |
|---|---|
| A-001 | AgentScope Java 2.0.1 是唯一 Agent Runtime。 |
| A-002 | 采用 Spring Boot 内嵌 AgentScope Harness/Core，不部署独立 AgentScope Service 或 Runtime Control Plane。 |
| A-003 | 不自研 Agent 执行循环、Workflow Engine、Memory Runtime、Tool Framework、Agent 状态机或模型调用协议层。 |
| A-004 | Nexus Edge 维护四标识模型：taskId（业务 Task）/ taskAttemptId（Nexus 业务主键，UUIDv7）/ agentId（AgentScope Agent 实例标识，构建时 UUID）/ traceId（OTel）。AgentScope 2.0.1 无独立 Execution ID 概念，禁止称 agentId 为"官方 Execution ID"；事件 eventId 由 Nexus 生成（UUIDv7）。 |
| A-005 | Model Gateway 是治理层，模型协议适配优先使用 AgentScope 官方 Provider。 |
| A-006 | 经营分析采用 Business Analyst 主 Agent、专业 Tool、AgentScope Workflow 和独立 Review Agent；不为展示效果强拆多个 Agent。 |
| A-007 | AgentScope Redis Persistence/Recovery（ADR-0008）：采用 `agentscope-extensions-redis` 的 `RedisAgentStateStore`（Jedis 7.4.1 VERIFIED）作为 Session/Agent State 运行时持久化；恢复引用显式携带 tenantId/workspaceId/userId/sessionId（scoped 分区，六字段 fail-closed）；恢复请求仅来自 MySQL 授权数据；Redis 为运行时恢复投影、MySQL 为长期权威源；Session 删除归独立生命周期服务；真实 Provider 保持 BLOCKED_BY_CREDENTIAL；AgentScope Checkpoint 待评审。 |

## 3. 技术基线

| 领域 | 决策 |
|---|---|
| Java | Java 21 LTS |
| Backend | Spring Boot 4.1.0 |
| Agent Runtime | AgentScope Java 2.0.1 Harness/Core |
| ORM | MyBatis-Plus，版本经兼容验证后冻结 |
| Auth | Sa-Token，版本经兼容验证后冻结 |
| Business DB | MySQL 为生产基线；DM8 为兼容认证 |
| Knowledge DB | PostgreSQL + pgvector |
| Cache/Event | Redis + Redis Streams |
| Object Storage | MinIO/S3 Provider |
| Frontend | React 19 + TypeScript + Vite + Tailwind CSS + shadcn/ui |
| Agent Conversation UI | assistant-ui，通过 `@gwnexus/assistant-ui` 治理层连接 Nexus REST/SSE；不引入第二套 Agent Runtime 或托管后端 |
| UI Component Policy | 普通界面优先 `@gwnexus/ui` 与 shadcn/ui；Agent 对话优先 `@gwnexus/assistant-ui` 与 assistant-ui 官方能力；禁止未经批准自研已有组件 |
| Desktop（V1.1） | React + TypeScript + Tauri 2 + Rust，Windows 10/11；V1不实施 |
| Server OS | Ubuntu Server 24.04 LTS |
| Deployment | Docker Compose 为唯一首验基线 |
| Platform Installation UX | `nexus-edge-ctl` 是平台自身安装、升级、诊断与恢复的长期稳定入口；不得与 Coding Application“一键发布”混用 |
| Office Renderer | WPS Office 免费版为验证基线；未通过兼容门禁前不承诺生产保真 |
| Observability | OpenTelemetry + Prometheus + Grafana + Loki |
| CI | 私有 GitHub 仓库 + GitHub Actions |
| Coding Runtime | 标准 Node.js Build Contract；Node.js 24 LTS 默认、22 LTS兼容；npm/pnpm/yarn |
| Coding Sandbox | 独立 Ubuntu 24.04 x86_64 Host Pool；Rootless Docker + gVisor；Sandbox Broker |
| OCI Build | 独立 Build Worker + Rootless BuildKit；Build Once / Promote Same Artifact |
| Application Hosting | 独立 Preview/Production Host Pool；Hardened OCI Runtime；Blue-Green |
| Application Gateway | Traefik File Provider，不访问 Docker Socket |
| Egress | Envoy Egress Gateway + Policy Service + 受控 DNS |
| OCI Registry | Harbor；Digest、SBOM、Cosign Signature、Build Provenance 强关联 |
| Coding Security | Gitleaks + Semgrep CE + Trivy + Cosign |

## 4. 数据与安全决策

- Workspace 采用 Managed Workspace；MinIO 中的服务器文件版本是权威源。
- V1 通过 Web 上传文件；V1.1 Desktop 只执行本地目录到服务器的单向同步，不实现完整双向网盘。
- 所有 Artifact、Agent Task 和 Evidence 必须绑定不可变的 Source/Knowledge Snapshot。
- 数据等级固定为 L0 Public、L1 Internal、L2 Confidential、L3 Restricted。
- 最终数据等级为 Workspace 默认、自动识别、上传人声明三者最大值；用户只能提高不能降低。
- 无法判断时按 L3；无合规模型时阻止任务。
- L2 在管理员授权可信外部模型后，可发送最小必要 RAG 片段和结构化数据；禁止整文件、无关片段和批量明细外发。
- L3 禁止外部生成模型和外部 Embedding。
- Secret 使用 Provider/引用，不在代码、YAML、业务字段、前端或日志中保存明文。
- 通信使用 TLS；MinIO 使用服务端加密；磁盘加密由企业基础设施提供。
- Coding Project、Repository Context、Diff、Build Log 和 Code Index 继承 L0–L3；L3 代码禁止外部模型与外部 Embedding。
- 任何用户代码、Agent 生成代码、Shell、依赖、测试与开发构建都只能在独立 Sandbox 执行；永不得获得 Nexus Edge Host Shell 或横向访问核心基础设施。
- BUILD/TEST/PREVIEW/PRODUCTION Secret 分域；Production Secret 永不进入 Coding Agent、Sandbox、Build Worker 或镜像层。
- Production 拉取前必须验证 Commit、Image Digest、SBOM、Signature、Provenance 与 Gate 状态。

## 5. 数据与契约决策

- 业务主键统一 UUIDv7，以字符串通过 API 传输。
- 时间统一 UTC 存储、ISO 8601 传输、客户端按时区展示。
- 对外 API 使用 `/api/v1` 和复数资源名。
- Agent 流式事件使用 SSE，并支持 `Last-Event-ID`；V1 不使用 WebSocket 作为主协议。
- 写操作支持 `Idempotency-Key`。
- 数据库对象使用 `gw_` 前缀；Java GroupId 为 `com.gwnexusedge`；npm scope 为 `@gwnexus/`。
- Schema 使用 Flyway；MySQL/DM8 分别维护版本化迁移，禁止自动建表和隐式 Schema 更新。
- 业务可靠事件使用 Transactional Outbox + Redis Streams；Agent 执行不由 Redis Streams 调度。
- Coding Task、Coding Plan、ChangeSet、Release Candidate 与 Deployment 分离建模，不把 Git 审核或应用发布状态塞入通用 Task 状态机。
- `nexus.yaml` 是 Coding Project 的版本化显式 Build Contract；Framework 自动识别仅是便利能力。

## 6. 许可与交付

- V1 为闭源商业软件，代码托管于私有 GitHub 仓库。
- 私有部署交付 Docker 镜像、配置、迁移、部署与运维文档，不默认交付完整源码。
- AgentScope Apache-2.0、grok-app MIT 组件及其他依赖必须进入第三方许可清单。
- assistant-ui MIT 依赖必须进入第三方许可清单、SBOM、漏洞扫描和版本锁定；生产构建禁止使用未锁定的 `latest`。
- V1.1 Desktop 可参考并复用 grok-app 合规组件，但必须移除 Grok CLI、ACP Runtime、账号、自动化和宿主状态机；不得把其 Runtime 逻辑带入 Nexus Edge。

## 7. 明确排除

V1 不包含：

- 公网注册、多企业 SaaS、Billing、开放 Agent 市场。
- SMB/NAS、业务 MySQL/DM8 Connector、ERP/OA/邮件/企业 IM Connector。
- 在线 Office 编辑、完整双向文件同步、移动 App。
- 扫描 PDF/OCR 生产承诺、旧 Office/WPS 专有格式生产承诺。
- 业务定时调度、自动月报、事件触发 Skill。
- Kubernetes 生产验收、等保/国密正式认证、自动故障切换和异地灾备。
- 自研 AgentScope 已提供的任何 Runtime 能力。
- Coding Workspace 的托管数据库、Redis、对象存储、后台 Worker、业务定时任务、在线自由编辑器和交互式 Terminal。
- 多仓库原子变更、自动 PR Preview、Push 触发构建、持续部署和 DNS Provider API 自动配置。
- Coding Application 的 Linux ARM64、Windows Container、多架构镜像、Bun 生产兼容和用户自定义通配符域名。
