# 07 — 安全、身份、权限与审计规格

> 状态：Accepted

## 1. 安全目标

- 默认拒绝，最小权限，显式授权。
- 企业身份与业务权限分离。
- 用户、Agent、Tool、Model、Context、Knowledge 和 Artifact 均受 Policy 控制。
- 事前配置、事中自动执行、事后完整审计；V1 不对每个安全动作增加人工审批。
- 不因模型调用而绕过 Workspace ACL 或数据等级。

## 2. 身份认证

### 2.1 Break Glass

- 首次部署生成一个本地系统管理员。
- 只用于系统初始化、身份源故障恢复和维护。
- 不得作为普通业务账号，不默认拥有业务文件读取权限。
- 首次登录强制改密；密码使用强哈希；使用事件必须产生高优先级审计。

### 2.2 LDAP/Active Directory

- 用户登录通过 LDAP Bind 实时验证，企业密码不进入 Nexus Edge。
- 系统按计划同步 User、Department、Group 和禁用状态，不同步密码。
- Identity Mapping 保存目录对象 ID，不以可变化的显示名作为唯一标识。
- 离职/禁用同步后立即撤销新会话，并根据策略吊销既有会话。
- LDAP 连接使用 TLS/LDAPS；Bind Service Account 通过 Secret Provider 获取。

## 3. 授权模型

授权判定：

```text
Decision = Role Permissions ∩ Scope ∩ Resource ACL ∩ Data Policy ∩ Runtime Policy
```

固定角色：SYSTEM_ADMIN、WORKSPACE_OWNER、OPERATOR、APPROVER、SECURITY_AUDITOR。

| 能力 | SYSTEM_ADMIN | WORKSPACE_OWNER | OPERATOR | APPROVER | SECURITY_AUDITOR |
|---|---:|---:|---:|---:|---:|
| 系统与身份源配置 | ✓ |  |  |  | 只读审计 |
| 模型/Secret Ref/Policy 配置 | ✓ |  |  |  | 只读审计 |
| 查看业务文件 | 默认禁止 | Scope 内 | Scope 内 | 审批所需 | 默认禁止 |
| 创建 Workspace |  | ✓ |  |  |  |
| 管理 Workspace 成员 |  | ✓ |  |  |  |
| 上传/同步文件 |  | ✓ | ✓ |  |  |
| 运行 Published Skill | 配置 | ✓ | ✓ |  | 审计查看 |
| 业务审核 |  | ✓ |  |  |  |
| 最终批准/发布 |  |  |  | ✓ |  |
| 审计查询/导出 | 有限 | Workspace 范围 | 本人范围 | 审批范围 | ✓ |
| Coding/测试/Preview | 配置 | ✓ | Scope内 |  | 审计 |
| ChangeSet Push确认/RC创建 |  | ✓ | Scope可授权 |  | 审计 |
| Production发布/回滚 |  |  |  | ✓ | 审计 |
| Security Exception审批 |  |  |  |  | ✓ |

System Admin 与 Security Auditor 不因平台角色自动获得业务内容访问权。

## 4. 数据分类

| 等级 | 含义 | 示例 |
|---|---|---|
| L0 Public | 公开信息 | 公开政策、官网资料 |
| L1 Internal | 企业内部普通资料 | 内部通知、一般制度 |
| L2 Confidential | 业务敏感 | 经营数据、项目、合同 |
| L3 Restricted | 核心敏感 | 财务明细、战略、客户和受限个人信息 |

最终等级：

```text
max(Workspace 默认等级, 自动识别等级, 上传人声明等级)
```

- 自动识别和上传人只能提高等级。
- 识别失败按 L3。
- 自动分类必须保存规则/模型版本、理由和置信信息。
- 降级只能由被授权管理员依据企业制度执行，并形成独立审计；普通用户永远不能降级。

## 5. Model/Context/Embedding Policy

### L0

允许管理员批准的外部或内部模型；仍记录审计。

### L1

外部生成模型和 Embedding 由管理员策略控制。

### L2

- 管理员必须明确授权 Provider 与 Workspace。
- 允许发送完成任务所需的最小 RAG 片段、结构化指标和任务上下文。
- 禁止整份文件、整个知识库、无关片段和批量原始明细。
- 外部 Embedding 默认禁止，管理员明确批准可信 Provider 后才允许。
- Context Policy 限制 Chunk 数、字符/Token、字段类型和敏感模式。

### L3

- 禁止外部生成模型和外部 Embedding。
- 仅允许企业内网模型。
- 没有合规模型时阻止任务，禁止降级为工具流水线或自动外发。

每次调用记录 Task、Execution、Workspace、数据等级、Policy Version、Provider、Model、外发类型、Chunk/Evidence 引用、Token 和结果状态。审计中不重复保存敏感正文。

Coding Project额外要求：Repository、Code Index、Diff、Build Log和Agent Context继承Project等级。L2仅允许授权Provider接收最小必要代码片段；L3禁止外部生成模型和外部Embedding。Secret命中内容在任何等级下都禁止进入模型或Embedding。

## 6. Tool Policy

经营分析 Skill P0 允许只读/生成型 Tool：文件解析、Excel 分析、知识检索、指标计算、图表数据、Artifact Model。

默认禁止：

- 删除或覆盖源文件。
- 修改数据库业务数据。
- 发送邮件、对外发布或调用未注册网络端点。
- 任意 Shell。
- 读取 Workspace 外路径。

Coding Agent允许的Shell/File/Git/Build Tool只能通过Sandbox Broker在Task独立Sandbox执行。`任意Shell`对Core、Desktop本机、Renderer、Build/Production Host管理面仍然绝对禁止。Production正式OCI Build不是Agent Tool副作用，由独立Build Worker执行。

Tool 调用使用 AgentScope 官方权限能力；Nexus Edge 注入治理决策，不实现平行 Permission Runtime。

## 7. Secret

- Secret Provider 只返回运行期短生命周期值。
- 数据库保存 Secret Reference，不保存 API Key、LDAP/SMTP/DB 密码明文。
- Docker Compose 使用受限 Secret 文件或 Docker Secret；文件权限最小化。
- 主密钥通过环境或企业 KMS 注入，不与密文同库存储。
- Secret 不返回前端、不进入异常详情、Trace Attribute、日志或审计 Payload。
- 支持轮换后不重建业务实体。
- BUILD、TEST、PREVIEW、PRODUCTION Secret必须分域。Production Secret只能由Production Host Agent在运行阶段解析，不能被Coding Agent、Sandbox、Build Worker或Nexus Edge Core读取明文。
- Build Secret只使用BuildKit Secret Mount，禁止Docker ARG；敏感运行Secret默认以只读tmpfs文件挂载。

## 8. 加密与网络

- 客户端、Nginx、后端、Renderer 和外部 Provider 通信使用 TLS。
- 公网/DMZ 禁止 HTTP；后端、DB、Redis、MinIO、Renderer 不直接暴露公网。
- MinIO 启用服务端加密；磁盘/整库加密由企业基础设施提供。
- MinIO 使用短期受控下载，不公开 Bucket。
- DMZ 方案必须支持正式证书、反向代理、限流和企业 WAF/ZTNA/VPN 对接。
- V1 不自研 WAF、KMS、VPN 或零信任产品。
- Coding Sandbox、Build、Preview和Production只允许经Envoy Egress Gateway和受控DNS访问公网；永久拒绝Core、企业内网、Metadata、宿主机、Docker/CRI和其他Project。
- Preview/Production入站只能经过Traefik Application Gateway；Traefik不得访问Docker Socket。
- Coding Sandbox、Preview和Production默认使用Rootless Docker、User Namespace与gVisor Hardened Runtime。

## 9. Audit Event

审计至少记录：

- event_id、event_time、tenant/workspace。
- actor_type、actor_id、identity_source、source_ip、device_id。
- action、resource_type、resource_id、resource_version。
- task_id、execution_id、trace_id。
- policy_decision、policy_version、data_level。
- result、reason_code、metadata 摘要。

关键事件：登录、失败登录、角色/成员变更、文件上传/读取/下载/删除请求、Knowledge 检索、Skill 发布/执行、模型/Embedding 外发、Tool 调用、Artifact 审核/发布、Secret 配置、审计导出。

Coding关键事件还包括：Repository绑定/Fetch/Push、Plan批准、Sandbox创建/销毁、命令执行、Secret注入名称、Egress目标、ChangeSet审核、Build、Security Finding/Exception、SBOM/Signature/Provenance、Preview分享、Production发布/回滚、Domain/Certificate、Host Agent指令和Decommission。审计永不保存Secret Value。

Audit 表为 Append-only Application Contract；业务 API 不提供更新和删除。默认保留 365 天并支持 CSV/JSON 导出。

## 10. 安全验收

- 每个 P0 资源执行同 Tenant、Workspace、Role、Scope 和 ACL 越权测试。
- 检索必须证明权限过滤发生在召回阶段。
- L3 外部调用必须被阻止。
- L2 Context 必须证明符合最小必要性限制。
- 下载、预签名 URL、SSE 重连和 ID 猜测均测试越权。
- 日志/Trace/错误响应执行敏感数据扫描。
- Sandbox对Core、DB、Redis、MinIO、AgentScope、Renderer、Host、Metadata和其他Project的访问测试必须全部失败。
- DNS Rebinding、SSRF、Redirect、直接IP、QUIC和代理绕过测试必须全部失败。
- Production Secret进入Agent Context、Sandbox、Build Layer、Artifact或Log次数必须为0。
- 未签名、Gate失败、Digest不匹配、扫描过期或越权Release在Production Host拉取/激活次数必须为0。
- Security Exception必须验证Finding/Commit/Digest/Scope/Expiry，禁止永久Project级豁免。

## 11. Coding Production Authorization

```text
OPERATOR: Coding Task / Test / Preview
WORKSPACE_OWNER: Repo与Environment治理 / Push确认 / RC创建与提交
SECURITY_AUDITOR: Security Exception批准或拒绝
APPROVER: Production发布 / 回滚 / Decommission批准
SYSTEM_ADMIN: Host、Registry、Gateway、Runtime和Policy配置
```

职责分离是服务端不变量。SYSTEM_ADMIN不能绕过Production Gate；Exception申请人与审批人不得相同；Security Exception不等同Production Approval。

## 12. Coding Security Gate

Preview最少执行Secret Scan、Deployment Config、SBOM、SCA和Image Scan。有效Secret、Critical漏洞、恶意依赖、Root/Privileged或隔离违规阻断；外部分享前不得有未处理Critical。

Production强制Gitleaks、Semgrep CE、Trivy SCA/Image/IaC/License/SBOM、Cosign Signature和BuildKit Provenance。阻断阈值与例外详见`22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md`。任何Gate未执行等同失败。
