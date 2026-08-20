# 19 — GW Nexus Edge V1 产品需求图集

> 状态：Accepted  
> 适用范围：产品评审、交互设计、研发拆分、测试设计和 Enterprise Pilot 验收  
> 说明：本图集是正式需求的可视化投影，不替代文字规格；若图与 Blueprint 文字冲突，必须停止并修正规范，不能选择更方便的一方实现

## 1. 图集用途与维护规则

本图集回答产品设计阶段的十一类问题：

| 图号 | 需要回答的问题 | 主要使用者 |
|---|---|---|
| PRD-D01 | 谁使用产品，各自承担什么责任？ | 产品、权限、测试 |
| PRD-D02 | V1 用户能完成哪些核心用例？ | 产品、研发、验收 |
| PRD-D03 | 经营分析任务的完整用户旅程是什么？ | 产品、UX、业务验收 |
| PRD-D04 | 用户动作、平台服务和后台能力如何协同？ | 产品、架构、运维 |
| PRD-D05 | V1 Web功能域与V1.1 Desktop边界如何组织？ | UX、Web、Desktop路线图 |
| PRD-D06 | V1.1 Desktop如何在V1 Web契约上接力？ | 产品、客户端、V1.1测试 |
| PRD-D07 | 数据、权限和模型调用边界在哪里？ | 安全、Agent、测试 |
| PRD-D08 | Task、Artifact 与人工审批如何衔接？ | 产品、后端、审批验收 |
| PRD-D09 | V1 做什么、不做什么、以后做什么？ | 产品、项目管理、销售 |
| PRD-D10 | Coding Agent如何从Git安全地产生ChangeSet和Preview？ | 产品、Agent、安全、研发 |
| PRD-D11 | Release Candidate如何经过供应链门禁发布为HTTPS应用？ | 产品、安全、部署、验收 |

维护要求：

- 角色、核心流程、状态机、P0范围或端职责变化时，必须同步更新相关图。
- 图中的节点名称必须使用 `16_GLOSSARY.md` 术语。
- 图只表达用户可验证行为和正式系统边界，不展示模型隐藏思维链。
- Mermaid 源码必须在公开 GitHub 中可渲染；CI 应检查代码围栏和 Mermaid 语法。
- 需求评审必须引用图号，例如“变更影响 PRD-D03、PRD-D06、PRD-D08”。

### 1.1 汇报展示版图片

以下 PNG 用于产品汇报、企业方案和评审展示。正式需求仍以本 Blueprint 的文字与 Mermaid 源码为准；展示图片中的文字或布局若与正式规格冲突，不得反向修改需求迁就图片。v3已按ADR-0004明确标注“V1 Web-only、Desktop V1.1”。

#### 技术架构图（v3）

![GW Nexus Edge V1 Web-only 双闭环技术架构](../assets/diagrams/gw-nexus-edge-v1-technical-architecture-v3.png)

#### 整体需求闭环图（v3）

![GW Nexus Edge V1 Web-only 双P0整体需求闭环](../assets/diagrams/gw-nexus-edge-v1-requirement-loop-v3.png)

## 2. PRD-D01：用户角色与责任图

这张图用于防止把系统角色、企业职位和组织部门混为一谈。

```mermaid
flowchart LR
    Enterprise["企业组织 / Tenant"]

    Enterprise --> SA["系统管理员<br/>SYSTEM_ADMIN"]
    Enterprise --> WO["工作空间负责人<br/>WORKSPACE_OWNER"]
    Enterprise --> OP["业务操作员<br/>OPERATOR"]
    Enterprise --> AP["审批人<br/>APPROVER"]
    Enterprise --> AU["安全审计员<br/>SECURITY_AUDITOR"]

    SA --> SA1["初始化部署与企业身份源"]
    SA --> SA2["模型、Policy、Renderer与系统配置"]
    SA -. "默认无业务正文权限" .-> Boundary["业务数据边界"]

    WO --> WO1["创建并治理 Workspace"]
    WO --> WO2["配置成员、数据范围、Skill与模板"]
    WO --> WO3["业务审核 Draft Artifact"]

    OP --> OP1["同步或上传授权资料"]
    OP --> OP2["运行已发布 Skill"]
    OP --> OP3["查看任务、Evidence与成果"]

    AP --> AP1["最终批准或退回"]
    AP --> AP2["批准正式发布"]

    AU --> AU1["查询与导出审计"]
    AU --> AU2["检查权限、模型和数据外发"]
    AU -. "默认无业务正文权限" .-> Boundary

    Scope["Department / Workspace Scope"] --> WO
    Scope --> OP
    Scope --> AP
    RolePerm["Role + Scope + Permission + ACL"] --> Boundary
```

产品约束：企业职位不自动映射为系统权限；同一人员可在不同 Workspace 拥有不同 Role/Scope。

## 3. PRD-D02：V1 核心用例图

```mermaid
flowchart LR
    SA["系统管理员"]
    WO["工作空间负责人"]
    OP["业务操作员"]
    AP["审批人"]
    AU["安全审计员"]

    subgraph Product["GW Nexus Edge V1"]
        UC01["企业身份登录"]
        UC02["配置企业模型与安全策略"]
        UC03["创建 Managed Workspace"]
        UC04["同步本地业务资料"]
        UC05["建立可追溯 Knowledge Snapshot"]
        UC06["运行经营分析报告 Skill"]
        UC07["查看 Agent 长任务与 Evidence"]
        UC08["生成可编辑 Office Artifact"]
        UC09["业务负责人审核与退回"]
        UC10["领导最终批准与发布"]
        UC11["通知推动后续动作"]
        UC12["查询和导出审计"]
    end

    SA --> UC01
    SA --> UC02
    WO --> UC03
    WO --> UC05
    OP --> UC04
    OP --> UC06
    OP --> UC07
    OP --> UC08
    WO --> UC09
    AP --> UC10
    WO --> UC11
    OP --> UC11
    AP --> UC11
    AU --> UC12

    UC04 --> UC05
    UC05 --> UC06
    UC06 --> UC07
    UC07 --> UC08
    UC08 --> UC09
    UC09 --> UC10
```

生产承诺只覆盖经营分析报告 Skill。其他四个 Skill 即使出现在演示环境，也不得改变本图的 V1 验收中心。

## 4. PRD-D03：经营分析用户旅程

```mermaid
journey
    title 企业经营分析报告端到端用户旅程
    section 准备
      企业身份登录并进入授权Workspace: 4: 业务操作员
      Web上传业务资料: 4: 业务操作员
      查看格式、上传和解析状态: 3: 业务操作员
      负责人确认数据范围与模板: 4: 工作空间负责人
    section 发起
      选择已发布经营分析Skill: 5: 业务操作员
      填写周期、范围和汇报对象: 4: 业务操作员
      确认Snapshot、模型策略和输出: 4: 业务操作员
      提交长任务后离开页面: 5: 业务操作员
    section 执行
      通过通知和Web查看业务进度: 4: 业务操作员
      查看资料冲突和缺失提示: 3: 业务操作员, 工作空间负责人
      必要时补充资料或重新执行: 3: 业务操作员
      等待Renderer生成正式文件: 4: 业务操作员
    section 审核
      查看Artifact预览和Evidence: 4: 工作空间负责人
      通过或填写原因退回: 4: 工作空间负责人
      必要时在WPS或Office修改并回传新版本: 3: 业务操作员
      领导批准或退回: 4: 审批人
    section 交付
      发布Word、PPT和Excel成果: 5: 审批人
      下载并进入企业正式流程: 5: 业务操作员
      安全审计员完成追溯检查: 4: 安全审计员
```

重点体验指标：用户不依赖刷新页面；客户端关闭不影响任务；每次退回都说明原因；正式成果不需要重新制作。

## 5. PRD-D04：经营分析服务蓝图

该图把用户界面和后台能力分开，适合产品、研发、测试和运维共同评审。

```mermaid
flowchart TB
    subgraph User["用户行为"]
        U1["Web上传资料"] --> U2["选择Skill和模板"] --> U3["提交任务"] --> U4["查看进度和Evidence"] --> U5["审核/退回"] --> U6["批准并使用成果"]
    end

    subgraph Frontstage["用户可见产品触点"]
        F1["Web上传"] --> F2["Web Workspace"] --> F3["Task工作台 + SSE"] --> F4["Artifact预览与批注"] --> F5["审批与通知"]
    end

    subgraph Backstage["平台业务服务"]
        B1["Resource Version + Snapshot"] --> B2["Permission / Policy"] --> B3["Skill Resolver"] --> B4["Task / Artifact / Approval"] --> B5["Audit / Notification"]
    end

    subgraph Runtime["智能与专业执行"]
        R1["解析 / Chunk / Embedding"] --> R2["AgentScope Execution"] --> R3["Data / Knowledge Tools"] --> R4["Review Agent + Trust Gate"] --> R5["Artifact Model"]
    end

    subgraph Support["后台基础能力"]
        S1["MinIO / MySQL / PostgreSQL"] --> S2["Outbox / Redis Streams"] --> S3["Windows WPS Renderer"] --> S4["OTel / Backup / Operations"]
    end

    U1 --> F1
    U2 --> F2
    U3 --> F3
    U4 --> F3
    U5 --> F4
    U6 --> F5

    F1 --> B1
    F2 --> B2
    F3 --> B3
    F4 --> B4
    F5 --> B5

    B1 --> R1
    B3 --> R2
    R5 --> B4

    R1 --> S1
    B4 --> S2
    R5 --> S3
    S3 --> B4
    B5 --> S4
```

失败体验要求：任何一层失败都必须投影为用户可理解的业务状态、下一步和通知，不能只留下后台日志。

## 6. PRD-D05：产品功能域与信息架构

```mermaid
flowchart TB
    Entry["GW Nexus Edge"]

    Entry --> Work["工作入口"]
    Entry --> Govern["企业治理"]
    Entry --> Edge["Desktop Edge（V1.1）"]

    Work --> W1["Workspace"]
    Work --> W2["Skill中心"]
    Work --> W3["Task工作台"]
    Work --> W4["Knowledge与Evidence"]
    Work --> W5["Artifact库"]
    Work --> W6["审核与审批"]
    Work --> W7["通知中心"]

    Govern --> G1["企业身份与用户"]
    Govern --> G2["Role / Scope / Permission"]
    Govern --> G3["模型注册与Policy"]
    Govern --> G4["Skill / Prompt / Workflow版本"]
    Govern --> G5["Template与Renderer"]
    Govern --> G6["审计与保留策略"]
    Govern --> G7["运行诊断"]

    Edge --> E1["目录授权"]
    Edge --> E2["文件扫描与格式检测"]
    Edge --> E3["Hash、上传和版本同步"]
    Edge --> E4["冲突与Delete Request"]
    Edge --> E5["Windows系统通知"]

    W1 --> W3
    W2 --> W3
    W3 --> W4
    W3 --> W5
    W5 --> W6
```

Web 是 V1 唯一工作入口。Desktop Edge 整体进入 V1.1，只能在共享工作能力之上增加本地能力，不建立独立业务模型。本图中的 Desktop 节点是路线图投影，不属于 V1 P0。

## 7. PRD-D06：V1.1 Web/Desktop 跨端接力图

本图仅描述V1.1目标能力，不属于V1验收。V1完整流程在Web中结束，不依赖图中的Desktop步骤。

```mermaid
sequenceDiagram
    autonumber
    actor User as 业务操作员
    participant Desktop as Windows Desktop
    participant API as Nexus Edge API
    participant Workspace as Workspace/Knowledge
    participant AS as AgentScope Runtime
    participant Renderer as Windows Renderer
    participant Web as Web Workspace
    actor Owner as 工作空间负责人
    actor Approver as 审批人

    User->>Web: 上传资料并查看解析状态
    Web->>API: 分片上传新增或修改文件
    API->>Workspace: 登记不可变版本并建立Snapshot
    User->>Web: 选择经营分析Skill并提交
    Web->>API: POST /api/v1/tasks
    API->>AS: 启动绑定版本的Execution
    AS-->>API: 业务安全事件与Artifact Model
    API-->>Web: SSE进度，可安全退出页面
    User->>Desktop: V1.1可绑定本地目录
    Desktop->>API: 同步新版本并复用同一Workspace契约
    User->>Web: 在浏览器继续查看原任务
    Web->>API: REST状态 + Last-Event-ID恢复SSE
    API->>Renderer: 投递RenderJob
    Renderer-->>API: 上传并登记Office Artifact
    API-->>Owner: 站内/Web/邮件审核通知
    Owner->>Web: 查看Evidence并业务审核
    API-->>Approver: 最终批准通知
    Approver->>Web: 批准并发布
    Web-->>User: 查看和下载已发布成果
```

V1 首验可在企业内网完成；DMZ公网和手机接力是部署增强，不改变统一 Workspace、Task 和 Artifact 状态。

## 8. PRD-D07：权限、数据与模型边界图

```mermaid
flowchart LR
    User["企业用户身份"] --> Auth["LDAP Bind / Break Glass"]
    Auth --> Access["Role + Scope + Permission"]
    Access --> ACL["Workspace / Resource / Chunk ACL"]
    ACL --> Classify["L0 / L1 / L2 / L3 分类"]

    Classify --> Local["企业边界内处理"]
    Local --> File["MinIO权威文件"]
    Local --> Knowledge["解析 / RAG / Embedding"]
    Local --> Tools["确定性Tool"]

    Classify --> Policy["Model + Context + Embedding Policy"]
    Policy --> L0L1["L0/L1：按管理员策略"]
    Policy --> L2["L2：可信Provider + 最小必要上下文"]
    Policy --> L3["L3：仅企业内模型与Embedding"]

    L0L1 --> External["授权外部Provider"]
    L2 --> MinContext["相关RAG片段 / 结构化指标"]
    MinContext --> External
    L3 --> Private["Private Model Endpoint"]

    File -. "禁止整文件直接外发" .-> Deny["Outbound Denied"]
    Knowledge -. "禁止无关Chunk或批量明细" .-> Deny
    L3 -. "禁止公网LLM/Embedding" .-> Deny

    External --> Audit["Outbound Audit"]
    Private --> Audit
    Deny --> Audit
```

产品必须向用户解释当前任务使用哪个模型、为什么允许或阻止，而不能把安全策略表现为不明原因失败。

## 9. PRD-D08：Task、Artifact 与审批协同图

```mermaid
flowchart LR
    subgraph TaskLifecycle["业务 Task 生命周期"]
        T1["CREATED"] --> T2["QUEUED"] --> T3["RUNNING"] --> T4["WAITING_RENDER"] --> T5["COMPLETED"]
        T3 --> TR["RETRY_WAIT"] --> T2
        T1 --> TC1["CANCEL_REQUESTED"] --> TC2["CANCELLED"]
        T2 --> TC1
        T3 --> TC1
        T4 --> TC1
        T3 --> TF["FAILED"]
        T4 --> TF
        TR --> TF
    end

    T4 --> AM["Artifact Model + RenderJob"]

    subgraph ArtifactLifecycle["Artifact 生命周期"]
        AM --> A1["GENERATED_DRAFT"] --> A2["BUSINESS_REVIEW"]
        A2 -->|"通过"| A3["BUSINESS_APPROVED"] --> A4["FINAL_APPROVAL"]
        A2 -->|"退回"| AR["RETURNED"]
        A4 -->|"退回"| AR
        AR --> NV["创建新ArtifactVersion"] --> A2
        A4 -->|"批准并发布"| A5["PUBLISHED"] --> A6["ARCHIVED"]
    end

    A1 --> T5
    A2 --> N1["通知工作空间负责人"]
    A4 --> N2["通知审批人"]
    A5 --> N3["通知任务参与者"]
```

Task 完成只表示执行和正式文件生成完成，不表示成果已经审核或发布。`RESUBMITTED` 是事件，新版本重新进入 BUSINESS_REVIEW。

## 10. PRD-D09：V1 范围与演进图

```mermaid
flowchart LR
    subgraph P0["V1 Enterprise Pilot — P0"]
        P01["单企业Tenant + LDAP/AD + 五角色"]
        P02["Web完整工作入口"]
        P04["Managed Workspace + Snapshot"]
        P05["DOCX/XLSX/PPTX/文本PDF"]
        P06["RAG + Evidence + Trust Gate"]
        P07["经营分析报告Skill"]
        P08["长任务 + SSE + Recovery"]
        P09["WPS Renderer Compatibility Gate"]
        P10["Artifact版本 + 两级审批"]
        P11["通知、审计、备份、平台安装生命周期"]
        P12["Coding Project + Git + Plan/ChangeSet"]
        P13["隔离Sandbox + Code Index + Preview"]
        P14["BuildKit + Harbor + 供应链安全门禁"]
        P15["人工Production + HTTPS域名 + 回滚"]
    end

    subgraph V11["V1.1 — 企业增强"]
        E00["Windows Desktop + Tauri<br/>目录同步/本地Git/系统通知"]
        E01["SMB/NAS Connector"]
        E02["MySQL/DM8只读Connector"]
        E03["Scheduler / 自动月报"]
        E04["WPS企业版 / Microsoft Renderer"]
        E05["OIDC/SAML/MFA与公网移动增强"]
        E06["OCR Provider"]
        E07["自动PR Preview / Push Build / CD"]
        E08["在线编辑器 / Terminal / 多Repo"]
        E09["DNS Provider API"]
    end

    subgraph V2["V2+ — 平台扩展"]
        F01["多企业SaaS与Billing"]
        F02["Kubernetes / HA / 异地灾备"]
        F03["在线Office协同"]
        F04["更多行业Skill和Connector"]
        F05["Cloud Demo产品化"]
    end

    P0 -->|"P0全部通过后"| V11
    V11 -->|"商业与规模验证后"| V2
```

明确不允许把 V1.1/V2 能力提前塞入 V1，然后以 Mock、隐藏开关或未验收代码声称“架构已经支持”。长期扩展通过稳定领域边界、SPI、契约和版本治理实现，而不是预先实现全部功能。

## 11. PRD-D10：Coding Agent安全执行闭环

```mermaid
flowchart LR
    U["用户需求 / 已有Git"] --> RP["Repository + Base Commit"]
    RP --> IDX["版本化Code Index"]
    IDX --> PLAN["只读Coding Plan"]
    PLAN --> PA{"用户批准Plan?"}
    PA -->|否| PLAN
    PA -->|是| SB["独立gVisor Sandbox"]
    SB --> AS["AgentScope Coding Agent"]
    AS --> TOOLS["Search / File / Git / Shell / Test Tools"]
    TOOLS --> VAL["Build + Test + Security Validation"]
    VAL --> CS["ChangeSet + Diff"]
    CS --> CA{"用户确认Push?"}
    CA -->|退回| AS
    CA -->|确认| BR["Agent Branch Push"]
    BR --> PR["GitHub PR可选"]
    CS --> PRE["受控Preview"]

    NET["Envoy Egress + Controlled DNS"] -. "唯一公网出口" .-> SB
    DENY["禁止Host Shell / Docker Socket / Core Network / Production Secret"] -.-> SB
```

## 12. PRD-D11：Coding Application供应链与发布闭环

```mermaid
flowchart LR
    COMMIT["已审核Commit"] --> BW["Rootless BuildKit"]
    BW --> IMG["OCI Image Digest"]
    IMG --> H["Harbor"]
    IMG --> SBOM["SBOM"]
    IMG --> SCAN["Gitleaks / Semgrep / Trivy"]
    IMG --> PROV["Build Provenance"]
    IMG --> SIGN["Cosign Signature"]
    SBOM --> RC["Release Candidate"]
    SCAN --> RC
    PROV --> RC
    SIGN --> RC
    RC --> GATE{"P0 Gate通过?"}
    GATE -->|否| BLOCK["BLOCKED / Security Exception"]
    GATE -->|是| APPROVE{"APPROVER发布?"}
    APPROVE -->|否| RC
    APPROVE -->|是| GREEN["Production Green"]
    GREEN --> HEALTH{"Health通过?"}
    HEALTH -->|否| FAIL["不切流 / FAILED"]
    HEALTH -->|是| GW["Traefik原子切流"]
    GW --> URL["系统域名 / 自定义域名 + HTTPS"]
    URL --> OBS["Logs / Metrics / Audit / Notification"]
    OBS --> RB["一键回滚历史Digest"]
```

## 13. 需求到验收追踪

| 图号 | 主要规格 | 关键验收 |
|---|---|---|
| PRD-D01 | 07 Security、01 PRD | 五角色权限矩阵、越权为0 |
| PRD-D02 | 01 PRD、02 Scope | 核心用例端到端闭环 |
| PRD-D03 | 01 PRD、10 UX | 加工时间降低≥70%、满意度达标 |
| PRD-D04 | 03 Architecture、12 Operations | 每层失败可观察、可恢复、可通知 |
| PRD-D05 | 10 UX、13 Engineering | V1 Web与V1.1 Desktop职责和共享包边界一致 |
| PRD-D06 | 06 API、10 UX | REST+SSE断线恢复、客户端离线任务继续 |
| PRD-D07 | 07 Security、09 Trust | 越权、L3外发、审计缺失均为0 |
| PRD-D08 | 04 State Machines、11 Artifact | Task与Artifact状态不混用、两级审批完整 |
| PRD-D09 | 02 Scope、15 Roadmap | P0无范围漂移，未通过Gate允许延期 |
| PRD-D10 | 21 Coding Workspace、22 Sandbox | Plan/ChangeSet门禁、Sandbox隔离、Git审计 |
| PRD-D11 | 22 Supply Chain、23 Deployment | Digest一致、人工Production、HTTPS、回滚与零安全事故 |

产品、设计、研发和测试评审应共同使用本图集。任何实现若无法在图中找到所属角色、用户价值、系统触点、状态或验收关系，应先判断它是否超出 V1 范围。
