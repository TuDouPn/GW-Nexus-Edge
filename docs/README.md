# GW Nexus Edge V1 权威文档索引

> 文档状态：Accepted  
> 产品版本：V1 Enterprise Pilot  
> 目标验收日期：2026-09-01（质量门禁优先，P0 未通过允许延期）  
> 最后评审：2026-08-10

## 1. 文档权威性

本目录中的 `blueprint/` 是 GW Nexus Edge 后续产品设计、架构决策、编码、测试和企业验收的唯一依据。

`archive/legacy/` 保存历史设计稿，仅用于追溯，不得作为实现依据。当历史文档与 Blueprint 冲突时，以 Blueprint 和 `AGENTS.md` 为准。

决策优先级：

1. `AGENTS.md` 项目开发宪章。
2. `blueprint/00_DECISIONS.md` 已接受架构决策。
3. Blueprint 其他正式规格。
4. 经评审的新 ADR。
5. Archive 历史资料不具备规范效力。

禁止开发人员或 AI 从归档文档恢复已否决设计，包括自研 Agent Runtime、Workflow Engine、Memory Runtime、Tool Calling Framework 或独立模型调用 SPI。

## 2. 官方定位

> GW Nexus Edge 是一个基于 AgentScope Java 2 Runtime 构建的企业级 Agent Workspace 平台，为企业提供安全可控的 AI Agent 工作空间、知识管理、技能编排、任务协作和业务成果交付能力。

V1有两条相互独立、均为P0的生产闭环：企业经营分析报告Workspace，以及Coding Workspace前端/SSR开发与HTTPS一键应用发布。企业业务Skill仍只有经营分析报告承担V1生产承诺；Coding Workspace是第二类Workspace产品能力，不属于Beta Skill扩张。

V1 客户端只交付 Web Workspace，两条 P0 的全部业务、管理、审批和发布闭环必须在浏览器独立完成。Windows Desktop、Tauri 2、本地目录同步、本地 Git 发现、设备能力与 Desktop 系统通知统一进入 V1.1。专用 Windows Renderer 仍是 V1 P0 基础设施节点，与 Desktop 无关。

职责边界：

- AgentScope Java 2.0.1：唯一 Agent Runtime，负责 Agent 执行、通信、Workflow、Memory、Tool Calling、Model Provider、运行事件与执行恢复。
- GW Nexus Edge：负责企业身份、Workspace、Skill 治理、Knowledge、Permission、Policy、Task、Artifact、Approval、Audit 和用户体验。

## 3. 阅读顺序

| 顺序 | 文档 | 用途 |
|---|---|---|
| 1 | [00 决策基线](blueprint/00_DECISIONS.md) | 不可绕过的架构与产品决策 |
| 2 | [01 产品需求](blueprint/01_PRODUCT_REQUIREMENTS.md) | 目标用户、场景、范围和业务流程 |
| 3 | [02 范围与验收](blueprint/02_SCOPE_AND_ACCEPTANCE.md) | P0/P1/P2、性能和生产门禁 |
| 4 | [03 系统架构](blueprint/03_SYSTEM_ARCHITECTURE.md) | 系统边界、组件、部署和调用链 |
| 5 | [04 领域与状态机](blueprint/04_DOMAIN_AND_STATE_MACHINES.md) | 聚合、状态、版本和业务不变量 |
| 6 | [05 数据架构](blueprint/05_DATA_ARCHITECTURE.md) | MySQL、DM8、PostgreSQL、Redis、MinIO |
| 7 | [06 API 与事件契约](blueprint/06_API_AND_EVENT_CONTRACT.md) | REST、SSE、错误、幂等、Outbox |
| 8 | [07 安全与权限](blueprint/07_SECURITY_AND_PERMISSION.md) | 身份、RBAC、数据分级、Secret、审计 |
| 9 | [08 AgentScope 与 Skill](blueprint/08_AGENTSCOPE_AND_SKILL.md) | Runtime 集成、经营分析 Skill 与治理 |
| 10 | [09 知识、证据与可信度](blueprint/09_KNOWLEDGE_EVIDENCE_TRUST.md) | RAG、Evidence、计算、冲突和防幻觉 |
| 11 | [10 客户端与用户体验](blueprint/10_CLIENT_AND_UX.md) | V1 Web、V1.1 Desktop边界和设计系统 |
| 12 | [11 Artifact 与 Renderer](blueprint/11_ARTIFACT_AND_RENDERER.md) | 高保真 Office 生成、审核和版本 |
| 13 | [12 部署与运维](blueprint/12_DEPLOYMENT_AND_OPERATIONS.md) | Ubuntu、Docker、可观测、备份和安全接入 |
| 14 | [13 工程与仓库规范](blueprint/13_ENGINEERING_STANDARD.md) | Monorepo、命名、依赖、迁移和代码标准 |
| 15 | [14 测试与质量门禁](blueprint/14_TEST_AND_QUALITY.md) | 自动化测试、Agent Eval 和 CI |
| 16 | [15 路线图与风险](blueprint/15_ROADMAP_AND_RISKS.md) | 阶段、依赖、风险、退出条件 |
| 17 | [16 术语表](blueprint/16_GLOSSARY.md) | 唯一术语定义 |
| 18 | [17 实施准入清单](blueprint/17_IMPLEMENTATION_READINESS_CHECKLIST.md) | 模块开工、合并、发布和验收的可执行检查表 |
| 19 | [18 AI Agent 协作与交接](blueprint/18_AI_AGENT_COLLABORATION.md) | 跨 Codex、Claude、Gemini 等 Agent 的共识和交接协议 |
| 20 | [19 产品需求图集](blueprint/19_PRODUCT_REQUIREMENT_DIAGRAMS.md) | 角色、旅程、功能、跨端、服务蓝图、审批和范围可视化 |
| 21 | [20 平台安装与生命周期](blueprint/20_PLATFORM_INSTALLATION_AND_LIFECYCLE.md) | V1 Server与Renderer安装、升级、诊断和恢复契约 |
| 22 | [21 Coding Workspace](blueprint/21_CODING_WORKSPACE.md) | Git、Plan、Agent、Build Contract、ChangeSet、双端与Coding验收 |
| 23 | [22 Coding Sandbox与供应链](blueprint/22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md) | 不可信执行隔离、Egress、BuildKit、Harbor、安全门禁和Secret |
| 24 | [23 Coding Application发布](blueprint/23_CODING_APPLICATION_DEPLOYMENT.md) | Preview、Production、域名、HTTPS、Blue-Green、回滚和运行规格 |

架构决策记录流程与模板见 [ADR 索引](adr/README.md)。

所有 AI Agent 的厂商无关启动入口见 [`AI_START_HERE.md`](../AI_START_HERE.md)。当前禁止自行裁决的问题见 [Open Questions](governance/OPEN_QUESTIONS.md)，开发交接模板见 [Handoffs](handoffs/README.md)。

## 4. 变更规则

- Accepted 文档不得通过普通代码提交静默改变核心决策。
- 架构、数据边界、安全策略、依赖基线或 V1 范围变化必须先新增 ADR，经产品架构负责人批准后更新文档。
- 所有契约变更必须同步更新 API、数据模型、状态机、测试和迁移影响。
- 文档不得包含 TODO、假实现或把未验证能力描述为已完成。
- 未通过兼容性测试的能力必须明确标记为 Risk/Gate，不得写成生产承诺。

## 5. 当前阻塞门槛

以下条件尚未满足，因此不得宣称 V1 已达到生产验收：

1. 试点企业尚未提供脱敏真实数据集、正式 Word/PPT/Excel 模板和 Golden Result。
2. WPS Office 免费版尚未完成无人值守 Renderer Compatibility Test。
3. 核心依赖组合（Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1）已通过 G-01（41/41 真实兼容测试）；MyBatis-Plus、Sa-Token 等外围依赖组合兼容验证（G-02）尚未完成。
4. 仓库已有 DEV-0001 兼容性实现与 41 个真实 AgentScope 集成测试（G-01 通过）；尚无 CI、部署包或企业环境测试结果。
5. 试点企业尚未提供Coding Workspace所需的两个脱敏真实Repository、真实改造任务、验收条件和非生产Secret。
6. Rootless Docker+gVisor、Rootless BuildKit、Harbor、Traefik、Envoy Egress及Host Agent尚未完成组合兼容与安全红队验证。
7. GitHub App与通用Git Provider、Node 22/24静态/SSR Build Contract、自定义域名/ACME尚无E2E证据。
8. assistant-ui 尚未完成 React 19/Vite/Tailwind/shadcn 组合、Nexus REST/SSE Custom Runtime、Tool UI安全、无障碍和版本锁定的 G-14 验证。
