# 01 — V1 产品需求规格

> 状态：Accepted  
> 产品：GW Nexus Edge Enterprise V1  
> 核心场景：企业经营分析报告 + Coding Workspace 前端开发与一键应用发布

## 1. 问题定义

中大型企业知识工作者需要从本地目录、网盘导出文件、邮件附件和业务系统导出表中收集资料，再使用 Word、Excel、PPT 和 PDF 人工完成经营分析。主要问题是：

1. 信息散落在个人电脑、共享位置和企业系统。
2. 大量时间用于查找、复制、核对和排版。
3. 历史报告、分析方法和企业模板无法结构化复用。
4. 财务、合同、客户和内部制度不能无边界发送给公网 AI。
5. 通用 AI 只返回文本，不能交付可审核、可追溯的正式业务成果。

企业前端研发和业务原型交付还存在第二类问题：

1. Agent 可以生成代码，但代码理解、修改、测试、Diff、Git 和部署彼此割裂。
2. 生成代码常在开发者电脑或核心服务器直接执行，缺少不可信代码隔离和网络边界。
3. “一键部署”常以跳过测试、安全、制品追溯或人工发布换取速度。
4. Preview、Production、Secret、域名、证书、回滚和审计缺少统一生命周期。
5. 企业需要导入现有 Git 项目继续开发，也需要从需求创建新项目，而不是只能使用固定模板。

## 2. 产品目标

用户通过 Web 把授权业务资料上传到企业 Workspace，选择已发布的经营分析 Skill，系统通过 AgentScope 执行资料理解、确定性数据计算、知识检索、业务分析、证据核查与成果生成，最终交付：

- 经营分析报告 `.docx`
- 领导汇报材料 `.pptx`
- 经营分析数据 `.xlsx`
- 可选领导摘要 `.pdf`

成果必须使用企业模板、可编辑、可追溯，并经过业务负责人审核和领导批准后发布。

Coding Workspace 的产品目标是：用户通过 Web 创建/导入 Git 项目，批准 Coding Plan，让 Agent 在隔离 Sandbox 内完成前端代码修改、测试和 Preview，经 Diff 确认写入 Agent 分支，再通过不可绕过的供应链门禁和人工 Production Gate，把静态或 Node.js SSR 应用发布为可直接访问的 HTTPS 域名，并可观测、审计和回滚。

## 3. 目标用户

### 3.1 主要用户

- 企业管理人员：部门经理、分管领导、项目负责人。
- 综合办公室/行政人员。
- 财务和经营分析人员。
- 农业、制造、项目管理等业务部门知识工作者。
- 企业前端研发人员、产品经理、业务应用负责人和发布审批人。

### 3.2 固定角色

| 角色 | 标识 | 主要职责 |
|---|---|---|
| 系统管理员 | SYSTEM_ADMIN | 初始化、身份源、模型、策略和系统配置；默认不可查看业务内容 |
| 工作空间负责人 | WORKSPACE_OWNER | 创建 Workspace、成员与数据范围管理、业务审核 |
| 操作员 | OPERATOR | 上传资料、执行已授权 Skill、查看和下载成果 |
| 审批人 | APPROVER | 最终批准、退回和发布成果 |
| 安全审计员 | SECURITY_AUDITOR | 查看与导出安全、数据外发和 Agent 审计；不可修改业务数据 |

角色负责能力，Department/Workspace 通过 Scope 限定授权范围。

## 4. 首验用户旅程

以下旅程全部通过 Web 完成。Desktop 不属于 V1；未来 V1.1 Desktop 只能在相同 Workspace、Task、Artifact、Git 和权限契约之上增加本地 Edge 能力。

### 4.1 企业经营分析

1. SYSTEM_ADMIN 通过本地 Break Glass 账号初始化系统，配置 LDAP/AD、模型、Secret 和安全策略。
2. 企业用户通过 LDAP Bind 登录 Web。
3. WORKSPACE_OWNER 创建“2026 年度经营分析”Managed Workspace，设置成员、L2/L3 等级和模板。
4. OPERATOR 在 Web 上传资料并查看格式、版本、解析与索引状态。
5. Server 为文件生成不可变版本、Hash、元数据和 Knowledge Snapshot。
6. OPERATOR 选择已发布的经营分析 Skill，填写分析周期、范围、目标对象和输出要求。
7. AgentScope 执行主 Agent、Tool、Workflow 和 Review Agent；Web 完整展示安全处理后的计划、步骤、Tool 状态和进度。
8. 系统生成 Artifact Model，经 Render Queue 发送到 Windows WPS Renderer。
9. Renderer 生成 `.docx/.pptx/.xlsx` Draft；Artifact 进入 BUSINESS_REVIEW。
10. WORKSPACE_OWNER 核查数据、观点、Evidence 和格式，可以通过或退回。
11. APPROVER 最终批准并发布；Published Artifact 进入企业 Artifact 库。
12. 系统通过站内通知、Web 实时通知和邮件发送关键事件，并记录完整审计。

### 4.2 Coding Workspace

1. WORKSPACE_OWNER 创建 Coding Project，选择 Agent 新建或导入标准 Git Repository。
2. Project 固化 Repository、Project Root、L0–L3、Build Contract、Runtime/Resource/Network Policy。
3. Coding Agent 在只读阶段理解代码并生成版本化 Coding Plan。
4. 用户批准 Plan 后，系统在专用 Sandbox Host 创建隔离 Sandbox。
5. Agent 通过 AgentScope Tool + Sandbox Broker 修改、测试和开发构建；用户在 Web 查看进度、源码、Diff 和测试结果。
6. Agent 创建 Preview；授权成员通过临时域名验证，必要时创建限时只读分享链接。
7. 用户确认 Diff 后，系统把不可变 Commit 推送到独立 Agent Branch；GitHub 可自动创建 PR。
8. WORKSPACE_OWNER 创建 Release Candidate；独立 Build Worker 使用 Rootless BuildKit 生成 OCI Image、SBOM、Signature 和 Provenance。
9. Production 安全门禁通过后，APPROVER 查看 Diff、测试、Preview、安全和制品证据，明确点击发布。
10. Production Host 按 Digest 拉取镜像，通过 Blue-Green、健康检查和 Gateway 原子切流完成发布。
11. 用户获得稳定系统域名，或通过 CNAME/TXT 绑定自己的精确域名并自动签发 HTTPS 证书。
12. 平台提供日志、指标、主动通知、一键回滚、Decommission 和完整审计。

## 5. V1 功能范围

### 5.1 P0

- 单企业 Tenant、Department、五角色 RBAC 与 Scope。
- LDAP/AD Bind 登录、用户/组同步、本地 Break Glass 管理员。
- Web Workspace 完整工作入口。
- Managed Workspace、成员、文件版本、Snapshot 和权限。
- `.docx/.xlsx/.pptx/文本型.pdf` 输入解析。
- Knowledge Service、权限预过滤 RAG、Evidence 和来源定位。
- 经营分析 Skill、Business Analyst Agent、确定性 Tool、Review Agent。
- DeepSeek 官方 Provider 与 OpenAI-compatible 私有模型 Provider 实际验证。
- Model/Context/Embedding/Data Policy 与外发审计。
- WPS Renderer SPI、队列、重试、结果登记和兼容性门禁。
- Artifact 预览、批注、退回、版本上传、两级审批和发布。
- REST `/api/v1`、SSE、幂等、统一错误和 OpenAPI。
- Transactional Outbox、Redis Streams、通知、解析和 Renderer Worker。
- 审计、可观测、备份恢复、离线安装与 Docker Compose 部署。
- `nexus-edge-ctl` 平台安装生命周期能力：环境预检、Secret初始化、镜像加载、Flyway迁移、启动、健康验收、诊断和安全升级；它与 Coding Application 一键发布是两个不同用例。
- Coding Project：Agent 新建与标准 Git HTTPS/SSH 导入；GitHub First-Class Provider 与通用 Git Provider。
- 版本化 Coding Plan、Code Index、Coding Agent、Sandbox Broker、ChangeSet/Diff 人工确认和 Agent Branch Push。
- 标准 Node.js `nexus.yaml` Build Contract；React/Vue/Next.js/Nuxt 自动识别；静态与 Node.js SSR Runtime。
- 独立 Sandbox/Build/Preview/Production 节点池，Rootless Docker、gVisor、Rootless BuildKit和Resource Profile。
- 受控 Envoy Egress、Traefik Application Gateway、Harbor OCI Registry及供应链证据。
- Preview临时域名、访问控制、72小时TTL与限时只读分享。
- Production Release Candidate、安全门禁、人工一键发布、Blue-Green和一键回滚。
- 稳定系统域名、用户自定义精确域名、TXT所有权验证、ACME HTTP-01和自动续期。
- Coding Application运行日志、指标、主动通知、Retention、Backup和Decommission。

### 5.2 Beta/Preview

- 项目汇报、知识问答、合同审查、会议纪要 Skill。
- DM8 核心链路兼容认证。
- 公网 DMZ、手机响应式访问。
- OCR Provider 接口。

Beta 不得出现在 V1 采购验收承诺中。

### 5.3 V1.1+

- Windows Desktop：React + TypeScript + Tauri 2 + Rust，本地目录扫描/Hash/单向同步、本地 Git Repository 发现、设备身份、系统通知与签名 MSI。
- macOS Desktop 兼容评估在 Windows Desktop 之后，不作为V1.1首要承诺。
- SMB/NAS、MySQL/DM8 只读 Connector。
- Skill Scheduler、自动月报和事件触发。
- WPS 企业版/Microsoft Office Renderer Provider。
- OIDC/SAML、MFA、公网移动接力增强。
- OCR、旧 Office/WPS 格式增强。
- Coding自动PR Preview、Push触发构建、持续部署、多仓库变更、在线编辑器、交互式Terminal和DNS Provider API。

## 6. 文件格式

| 格式 | V1 级别 | 行为 |
|---|---|---|
| `.docx` | Production | 解析与生成 |
| `.xlsx` | Production | Sheet、值、公式、格式、图表数据；生成可编辑公式和图表 |
| `.pptx` | Production | 解析结构；基于模板生成可编辑内容和图表 |
| 文本型 `.pdf` | Production input | 文本提取、页码定位；可由 Renderer 导出 PDF |
| `.doc/.xls/.ppt` | Compatibility only | 明确提示转换为 OOXML |
| `.wps/.et/.dps` | Compatibility only | 明确提示转换为 OOXML |
| 扫描 PDF/图片 | Optional OCR | 未启用 OCR 时标记不可解析，不进入正式 Agent 分析 |

## 7. 非目标

Nexus Edge 不替代网盘、Office Online、企业数据中台、通用 ETL、通用 Agent Runtime、大模型服务、Git托管、完整云IDE、通用后端PaaS或CI/CD平台。V1 不以 Agent 数量、模型数量或页面数量衡量完成度，而以经营分析成果闭环和 Coding 应用从需求/Git到HTTPS Production的可追溯闭环衡量。
