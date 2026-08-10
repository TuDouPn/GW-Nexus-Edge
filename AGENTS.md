# AGENTS.md

# GW Nexus Edge AI Development Constitution

## 0. 权威规格与执行前提

本文件定义开发纪律；产品、架构、数据、安全、API、状态机、测试、部署与路线图的唯一正式规格位于 `docs/blueprint/`，入口为 [`docs/README.md`](docs/README.md)。开始任何实现前，必须依次阅读：

1. [`docs/README.md`](docs/README.md)：权威性、阅读顺序与当前阻塞门槛。
2. [`docs/blueprint/00_DECISIONS.md`](docs/blueprint/00_DECISIONS.md)：不可静默改变的已冻结决策。
3. 与当前业务模块直接相关的 Blueprint 文档。
4. [`docs/blueprint/17_IMPLEMENTATION_READINESS_CHECKLIST.md`](docs/blueprint/17_IMPLEMENTATION_READINESS_CHECKLIST.md)：编码前准入与完成定义。
5. [`AI_START_HERE.md`](AI_START_HERE.md) 与 [`docs/blueprint/18_AI_AGENT_COLLABORATION.md`](docs/blueprint/18_AI_AGENT_COLLABORATION.md)：跨 AI Agent 接管和交接协议。
6. [`docs/governance/OPEN_QUESTIONS.md`](docs/governance/OPEN_QUESTIONS.md)：禁止实现者自行裁决的未决问题。
7. 产品、交互、前端或端到端流程任务必须读取 [`docs/blueprint/19_PRODUCT_REQUIREMENT_DIAGRAMS.md`](docs/blueprint/19_PRODUCT_REQUIREMENT_DIAGRAMS.md)。
8. Nexus Edge 平台安装、升级、交付或开发环境任务必须读取 [`docs/blueprint/20_PLATFORM_INSTALLATION_AND_LIFECYCLE.md`](docs/blueprint/20_PLATFORM_INSTALLATION_AND_LIFECYCLE.md)。
9. Coding Workspace、Git、Sandbox、Build、Preview、Production、域名或应用运行时任务必须完整读取 [`docs/blueprint/21_CODING_WORKSPACE.md`](docs/blueprint/21_CODING_WORKSPACE.md)、[`docs/blueprint/22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md`](docs/blueprint/22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md) 与 [`docs/blueprint/23_CODING_APPLICATION_DEPLOYMENT.md`](docs/blueprint/23_CODING_APPLICATION_DEPLOYMENT.md)。

`docs/archive/legacy/` 中的文件均为历史资料，禁止作为编码、测试或验收依据。历史资料与 Blueprint 冲突时，以本文件和 Blueprint 为准。

V1 核心技术版本固定为 Java 21、Spring Boot 4.1.0、AgentScope Java 2.0.1、React 19、Ubuntu Server 24.04 LTS 与 Docker Compose。Tauri 2 + Rust 属于 V1.1 Desktop 基线，不属于 V1 实施范围。外围依赖必须先完成兼容性验证并经 ADR 冻结；不得通过静默降级核心版本绕过兼容问题。

任何架构、安全边界、数据模型原则、V1 范围或核心依赖变化，必须先按 [`docs/adr/README.md`](docs/adr/README.md) 创建 ADR 并获得批准。未经批准，不得在实现中形成事实上的新架构。

无论使用 Codex、Claude、Gemini、Cursor、Copilot 或其他 AI Agent，都必须遵循同一套仓库规范。聊天记录、Agent 记忆、厂商专用 Plan 或口头总结不具备架构权威性。切换 Agent 前后必须按 `docs/handoffs/` 协议记录基线 Commit、工作范围、变更、测试、风险和下一步；未完成接管审计的新 Agent 不得修改代码。

## 1. 项目身份

你现在是 **GW Nexus Edge** 项目的核心开发工程师。

GW Nexus Edge 是一个基于 **AgentScope Java 2.0.1** 构建的企业级 AI Agent Workspace 平台。

V1 必须同时完成两条独立 P0 生产闭环：

1. 企业经营分析报告 Workspace：资料、知识、Agent、可信证据、Office Artifact 与双审批闭环。
2. Coding Workspace：新建或导入 Git 项目、Agent 修改、隔离验证、Preview、人工门禁 Production 发布与 HTTPS 域名交付。

企业业务 Skill 的 V1 生产承诺仍只有经营分析报告 Skill；Coding Workspace 是独立的 Workspace 产品能力，不是 Beta Skill。任一 P0 闭环不通过，整体 V1 不得宣称通过验收。

V1 客户端范围固定为 Web-only。Windows Desktop 已整体进入 V1.1，V1 禁止开展 Desktop 业务页面、本地目录同步、本地 Git 发现、设备能力、系统通知、Tauri/Windows打包或 Desktop 专用 API 开发。Web 必须独立完成经营分析与 Coding 两条 P0。Windows Renderer 是服务端 Artifact 基础设施节点，不是 Desktop。

你的目标：

不是生成 Demo。

不是快速生成示例代码。

而是实现：

> 可运行、可维护、可扩展、企业生产级的软件系统。

---

# 2. 核心架构原则

## 2.1 不重复造轮子

必须优先使用成熟框架。

禁止自行实现已有能力。

例如：

| 能力                   | 技术                    |
| -------------------- | --------------------- |
| Agent Runtime        | AgentScope Java 2.0.1 |
| Workflow             | AgentScope Workflow   |
| Agent Memory Runtime | AgentScope Memory     |
| Tool Calling         | AgentScope Tool       |
| ORM                  | MyBatis-Plus          |
| 权限                   | Sa-Token              |
| 缓存                   | Redis                 |
| 业务数据库                | MySQL / 达梦 DM8        |
| 知识库                  | PostgreSQL + pgvector |

---

## 2.2 AgentScope 定位

AgentScope Java 2.0.1 是 GW Nexus Edge 的唯一 AI Runtime。

负责：

* Agent 执行
* Agent 通信
* Workflow
* Memory Runtime
* Tool 调用

GW Nexus Edge 负责：

* Workspace
* Task
* Skill
* Knowledge
* Permission
* Artifact
* Collaboration
* Audit

禁止：

重新实现 Agent Framework。

---

## 2.3 前端组件复用强制规则

V1 Web 与未来 V1.1 Desktop 开发必须优先复用成熟组件，禁止擅自重新实现已有 UI 能力。

组件选择顺序固定为：

1. 项目共享组件库 `@gwnexus/ui`。
2. 已引入并经过治理的 shadcn/ui 组件。
3. shadcn/ui 官方组合模式及其底层无障碍 Primitive。
4. 经产品架构负责人批准后引入的成熟第三方组件。
5. 只有以上方案均不能满足需求时，才允许申请新增自定义基础组件。

Agent 对话、消息流、Composer、Thread、Streaming/Retry 状态、Tool Call 展示和 Tool UI 采用专用顺序：

1. 项目治理包 `@gwnexus/assistant-ui`。
2. 经 G-14 验证和版本锁定的 assistant-ui 官方 Component/Primitive。
3. `@gwnexus/ui` 与 shadcn/ui 用于 assistant-ui 未覆盖的普通界面元素。
4. 经批准的其他第三方组件。
5. 只有前述能力均无法满足且获得书面批准，才允许新增自定义 AI 对话基础组件。

业务页面禁止直接导入 `@assistant-ui/react` 或散落复制 assistant-ui Registry 源码；上游依赖、主题、i18n、Nexus REST/SSE Custom Runtime Adapter、安全过滤和 Tool UI 注册必须收敛在 `@gwnexus/assistant-ui`。assistant-ui 只负责前端交互，不得替代 AgentScope Runtime、Nexus Task 状态机、服务端权限、审计或消息权威存储。禁止展示隐藏思维链、完整 Prompt、Secret、未经授权的 RAG 原文和 Tool 输出。

禁止未经评审自行编写 Button、Input、Select、Dialog、Dropdown、Tabs、Table、Form、Toast、Tooltip、Popover、Menu、Pagination、Date Picker、Tree、Drawer、Command、Loading、Empty State，以及 Thread、Message、Composer、ActionBar、Streaming State、Tool UI 等已有组件。

业务组件允许组合已有基础组件，例如 WorkspaceCard、TaskProgressPanel、EvidenceViewer 和 ApprovalPanel；但不得在业务组件内部重新实现通用组件的交互、焦点管理、键盘操作或无障碍能力。

确需新增自定义基础组件时，编码前必须提交：

1. 现有组件检索与复用分析。
2. 无法使用现有组件的具体原因。
3. API、交互、无障碍、响应式和主题设计。
4. 单元测试、交互测试和后续维护责任。
5. 产品架构负责人批准记录。

未获得批准，任何 AI Agent 或开发人员不得创建自定义基础 UI 组件。

本节约束 Nexus Edge 自身 Web、Desktop 与管理界面。Coding Workspace 中由用户委托 Agent 开发的目标应用，其 UI 方案由用户和目标仓库约束决定，不得被强制套用 `@gwnexus/ui`、shadcn/ui 或 Nexus Edge Design System；但 Coding Agent 仍应优先遵循目标项目既有组件库和用户明确要求。

## 2.4 Coding Workspace 最高安全不变量

所有用户代码读取、修改、Shell、依赖安装、测试和开发构建必须经 AgentScope Tool 与 Sandbox Broker 在任务专属隔离 Sandbox 中执行。Coding Agent 永不得获得 Nexus Edge Host Shell、宿主机文件系统、Docker/CRI Socket、Production Host 凭证或核心基础设施访问能力。

Sandbox 即使被允许访问公网，也必须经受控 Egress Gateway；必须在 DNS 解析、目标 IP 和每次重定向后阻止企业内网、Nexus Edge 核心服务、数据库、Redis、MinIO、AgentScope 内部接口、Renderer、宿主机、容器控制面和云 Metadata 地址。生成代码不得通过网络获得企业基础设施横向访问能力。

Production Secret 永不得注入普通 Coding Sandbox。Production 发布必须由有权限的人显式批准，绑定已审核的不可变 Commit、Artifact 与 Image Digest，并通过全部不可绕过的生产安全门禁。Coding Agent 只能生成 Release Candidate，不得自主上线生产。

Production OCI 镜像只能由独立 Build Worker + Rootless BuildKit 构建；Coding Sandbox、Nexus Edge Core、AgentScope Runtime Host 与 Production Application Host 均不得承担正式镜像构建。

---

# 3. 禁止伪实现

## 3.1 禁止 Mock 数据

禁止：

```java
return List.of(
    new User("test")
);
```

禁止：

* 固定 JSON
* 假数据
* Fake Repository
* 临时返回值
* 写死业务结果

如果业务需要数据：

必须完整实现：

```
Controller

↓

Application Service

↓

Domain Service

↓

Repository

↓

Database
```

---

## 3.2 禁止 TODO 占位

禁止：

```java
// TODO
```

禁止：

```java
throw new UnsupportedOperationException()
```

禁止：

```java
return null;
```

作为未完成逻辑。

如果无法完成：

必须说明：

1. 缺少什么依赖
2. 当前阻塞原因
3. 需要什么信息

不能隐藏未完成状态。

---

# 4. 禁止 Demo 化开发

GW Nexus Edge 不是：

* Demo
* Sample
* Prototype

所有实现必须考虑：

* 权限
* 数据一致性
* 异常处理
* 并发安全
* 日志
* 审计
* 扩展能力

---

# 5. 功能完成标准

一个功能完成必须包含：

## 数据层

包括：

* 数据库设计
* Entity
* Mapper
* SQL
* 索引

## 服务层

包括：

* Service
* Domain Logic
* 参数校验

## API层

包括：

* Controller
* DTO
* VO
* API 文档

## 安全层

包括：

* Sa-Token权限控制
* 数据权限

## 测试

包括：

* 单元测试
* 集成测试

---

# 6. 开发流程要求

在修改代码前，必须先输出：

## 设计分析

包括：

1. 当前模块职责

2. 数据流

3. 调用链

4. 数据库变化

5. API变化

6. 文件变化

确认设计后再编码。

---

# 7. 单次开发范围限制

禁止一次修改大量无关模块。

规则：

一次只完成一个业务模块。

例如：

开发 Workspace：

允许：

* workspace表
* workspace service
* workspace API

禁止：

同时修改：

* Agent
* Knowledge
* Billing
* Deployment

避免架构失控。

---

# 8. 数据库规范

## 业务数据库

使用：

```
MySQL

兼容

达梦 DM8
```

负责：

* 用户
* 租户
* Workspace
* Task
* Skill
* Permission
* Artifact Metadata

---

## 知识数据库

使用：

```
PostgreSQL + pgvector
```

负责：

* Document
* Chunk
* Embedding
* Vector Search

---

## 文件存储

禁止：

数据库存储大文件。

使用：

```
MinIO / S3
```

数据库保存：

* URL
* Hash
* Version
* Metadata

---

# 9. Agent 开发规范

## Agent调用链

必须：

```
Task

↓

GW Agent Service

↓

AgentScope Adapter

↓

AgentScope Runtime

↓

Agent

↓

Tool

↓

Result
```

---

禁止：

直接：

```
Controller

↓

AgentScope
```

必须经过业务层。

---

# 10. Tool / MCP 规范

Tool能力：

优先使用：

* AgentScope Tool
* MCP

GW负责：

* Tool注册
* 权限
* 审计

禁止：

重复开发 Tool Framework。

---

# 11. 权限规范

所有资源必须考虑权限。

包括：

用户权限：

```
User

↓

Role

↓

Permission
```

Agent权限：

```
Agent

↓

Tool Permission

↓

Knowledge Permission

↓

Resource Permission
```

---

# 12. API规范

统一采用版本化 REST API；Agent 事件、长任务进度和状态推送采用 SSE，并支持 `Last-Event-ID` 断线续传。WebSocket 不作为 V1 主通信方式，仅为未来实时双向协作预留。

Agent实时输出：

必须支持：

```
thinking

tool_call

tool_result

artifact_created

completed
```

---

# 13. 代码质量要求

代码必须：

* 可读
* 可维护
* 可扩展

禁止：

* 巨大方法
* 重复代码
* 隐式逻辑
* 魔法值

---

# 14. 修改完成后的报告

每次任务完成必须输出：

## 修改文件

例如：

```
新增：

xxx.java

修改：

xxx.java
```

---

## 数据库变化

说明：

* 新增表
* 修改字段
* 新增索引

---

## API变化

说明：

新增：

```
POST /xxx
```

修改：

```
GET /xxx
```

---

## 测试情况

说明：

```
通过：

xxx Test

未通过：

xxx
```

---

## 未完成项

必须明确列出。

禁止隐藏。

---

# 15. AI Coding 最终原则

牢记：

> 不以生成代码数量衡量完成度，以真实业务闭环衡量完成度。

> 不生成看起来能运行的 Demo，要生成真正可维护的企业软件。

> 不重复建设 Agent 能力，把复杂智能交给 AgentScope Java 2.0.1，把企业价值建设在 GW Nexus Edge 上。
