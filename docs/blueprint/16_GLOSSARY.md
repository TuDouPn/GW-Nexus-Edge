# 16 — 统一术语表

> 状态：Accepted

| 术语 | 唯一定义 |
|---|---|
| AgentScope Runtime | AgentScope Java 2.0.1 Harness/Core 提供的 Agent 执行、Workflow、Memory、Tool、Model、事件和恢复能力 |
| Nexus Edge | 构建在 AgentScope 之上的企业 Agent Workspace 平台，不是 Runtime |
| Tenant | 企业数据边界；V1 一个实例只有一个活动 Tenant |
| Department | Tenant 内组织范围，不是系统角色 |
| Workspace | 业务工作的权限、资源、知识、任务和成果容器 |
| Managed Workspace | 源文件同步到 MinIO 并以服务器版本为权威的 Workspace |
| Resource | Workspace 中可治理的数据/文件逻辑对象 |
| ResourceVersion | 不可变的源文件版本，保存 Hash 和 MinIO 引用 |
| Knowledge Snapshot | Task 使用的固定 ResourceVersion 与索引代际集合 |
| Skill | 版本化业务能力定义，组合 Prompt、Workflow、Tool、Knowledge Scope、Policy、Output Schema 和 Template 要求 |
| Agent Definition | 用于实例化 AgentScope Agent 的版本化配置 |
| Task | Nexus Edge 业务任务（业务标识 taskId，贯穿全部 TaskAttempt） |
| TaskAttempt | Task 的一次执行尝试（Nexus 业务主键 taskAttemptId，UUIDv7），关联一个 AgentScope Agent 实例标识（agentId） |
| Execution | AgentScope 2.0.1 无独立 Execution ID 概念；运行实例以 AgentScope Agent 实例标识（agentId，构建时 UUID）为标识，禁止称 agentId 为"官方 Execution ID" |
| Tool | AgentScope Agent 调用的确定性或外部能力 |
| Model Governance | Nexus Edge 对模型注册、Secret Ref、白名单、Policy、额度、成本和审计的治理层，不是调用协议层 |
| assistant-ui | Nexus Edge Agent 对话场景采用的 React UI Component/Primitive 依赖；通过 `@gwnexus/assistant-ui` 治理，不是 Agent Runtime、业务状态机或消息权威存储 |
| `@gwnexus/assistant-ui` | 隔离 assistant-ui 上游 API并承载主题、i18n、Nexus REST/SSE适配、安全展示策略和 Tool UI 注册的共享前端包 |
| Context Policy | 控制可发送给模型的最小必要上下文范围 |
| Data Policy | 按数据等级决定处理、外发和存储边界 |
| Embedding Policy | 单独控制原文向量化 Provider 和外发规则 |
| Evidence | 对 Claim 的不可变来源、计算、推断或人工确认记录 |
| Claim | Artifact 中可被验证的事实、数字或推断陈述 |
| Artifact Model | Agent 生成并通过 Schema/Trust Gate 的结构化中间成果 |
| Artifact | 可审核、版本化和发布的业务成果逻辑对象 |
| ArtifactVersion | 不可覆盖的 AI 或人工成果版本 |
| Renderer | 将 Artifact Model 与 Template 转换为正式 Office 文件的受控 Worker |
| Business Review | WORKSPACE_OWNER 对数据和业务内容的第一审核节点 |
| Final Approval | APPROVER 对正式发布责任的第二审批节点 |
| Audit | Append-only 的企业业务与安全追溯数据，不等于应用日志 |
| Desktop Edge（V1.1） | Tauri Desktop 的目录同步和设备能力，不是 Agent Runtime；不属于V1 |
| Cloud Demo | 使用示例数据与预置账号的非生产展示环境，不是 SaaS |
| P0 | Enterprise Pilot 必须通过的交付项 |
| Beta/Preview | 可以展示但不承担 V1 生产承诺的能力 |
| Production Readiness Gate | 宣称生产就绪前必须满足的人工、数据、技术和测试门槛 |
| Coding Workspace | 面向前端/SSR项目的Agent创建、导入、修改、验证、预览、发布和回滚工作空间能力 |
| Coding Project | Workspace内绑定一个Repository与Project Root的代码治理聚合 |
| Repository Binding | Git Provider、Remote、默认分支、Project Root和Credential Reference的版本化关联 |
| Build Contract | `nexus.yaml`定义的Node、包管理器、命令、端口、健康和部署类型显式契约 |
| Coding Plan | Coding Agent只读分析后、进入可写Sandbox前必须人工批准的版本化设计Artifact |
| Coding Sandbox | Task独立、临时、受资源和网络约束的不可信代码执行环境，不是Agent Runtime |
| Sandbox Broker | 将受Policy控制的AgentScope Tool调用路由到指定Sandbox的边界服务 |
| ChangeSet | 绑定Base/Candidate Commit、Diff、测试和安全结果的不可覆盖代码变更聚合 |
| Release Candidate | 绑定Commit、Image Digest、SBOM、Signature、Provenance、Gate与批准的候选发布 |
| Deployment | Preview或Production中将不可变Release运行并路由流量的业务聚合 |
| Build Once / Promote Same Artifact | 同一不可变Image在验证后晋升环境，Production不从源码重新构建 |
| Application Host | 独立于Nexus Edge Core、运行Preview/Production应用的受控主机节点 |
| Host Agent | 通过mTLS主动连接控制面、验证签名部署指令并管理本机受限Runtime的组件 |
| Application Gateway | Traefik数据面，负责应用域名、TLS、ForwardAuth与Blue-Green流量 |
| Egress Gateway | Envoy数据面，负责Sandbox/Build/Application受控公网出口和目标审计 |
| Domain Binding | Project Production Environment与系统/自定义精确FQDN的可验证关联 |
| Security Finding | 由Gitleaks/Semgrep/Trivy等产生、进入统一Gate与Exception生命周期的风险记录 |
| Security Exception | 绑定Finding、Commit/Digest、Scope和Expiry的限时受审计例外，不等于发布批准 |

## 禁止混用

- 不将 Nexus Edge 称为 Agent Runtime。
- 不将 Task 状态称为 AgentScope 生命周期。
- V1.1 不把 Desktop Edge 或 Tauri 设备能力命名为 Runtime，避免与 AgentScope Runtime 混淆。
- 不将业务审核称为 AgentScope HITL Tool Approval。
- 不将应用日志称为审计。
- 不将 pgvector 索引称为源文件或权威数据。
- 不将 Beta Skill 写入 V1 生产验收范围。
- 不将`nexus-edge-ctl`平台安装称为Coding Application一键发布，二者是独立产品用例。
- 不把Coding Sandbox、Build Worker或Application Host称为Agent Runtime。
- 不把Preview、Release Candidate、Deployment状态塞入通用Task或Artifact状态机。
- 不把mutable tag、源码现场构建或未签名Image称为Production Release。
