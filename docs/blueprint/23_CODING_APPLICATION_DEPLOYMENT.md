# 23 — Coding Application 一键发布、域名与运行规格

> 状态：Accepted  
> 优先级：V1 P0  
> “一键发布”定义：对已满足全部前置条件的 Release Candidate 执行一次人工授权，由平台自动完成不可变制品验证、部署、切流、HTTPS、审计和通知

## 1. 部署目标

Coding Agent 完成前端或 Node.js SSR 应用后，用户必须能够：

1. 自动获得隔离 Preview URL；
2. 查看 Diff、测试、安全、SBOM 和 Preview；
3. 创建不可变 Release Candidate；
4. 由 APPROVER 明确执行 Production 一键发布；
5. 获得稳定系统域名或用户自定义域名；
6. 通过 HTTPS 直接访问；
7. 查看日志、指标、健康和发布历史；
8. 一键回滚到历史已验证 Release。

部署平台由 Nexus Edge 自主管理的 Docker Application Host 提供，不依赖 Vercel 或其他第三方 PaaS。

## 2. 长期部署架构

```text
Nexus Edge Deployment Control Plane
        │ signed deployment intent / mTLS
        ├─ Preview Host Agent Pool
        │     └─ Hardened OCI Runtime
        ├─ Production Host Agent Pool
        │     └─ Hardened OCI Runtime
        ├─ Traefik Application Gateway
        ├─ Envoy Egress Gateway
        ├─ Controlled DNS Resolver
        ├─ Harbor OCI Registry
        └─ ACME / Secret Provider
```

约束：

- Application Host 与 Nexus Edge Core、数据库、MinIO、AgentScope、Renderer 分离。
- Preview 与 Production 使用不同节点池、Docker Daemon、网络和 Secret 域。
- Core 不持有 Host 通用 SSH 权限。
- Host Agent 通过 mTLS 主动注册并获取最小部署指令。
- 调度、容量、健康和数据模型从第一天支持多个注册节点。

V1 操作系统基线为 Ubuntu Server 24.04 LTS + Linux x86_64。Docker Engine、Rootless BuildKit、gVisor/OCI Runtime 的具体版本必须通过兼容认证后冻结。

## 3. Environment 模型

| Environment | 目的 | 发布主体 | Secret边界 | 域名 |
|---|---|---|---|---|
| BUILD | 正式OCI构建 | Build Worker | Build Secret Mount | 无 |
| TEST | 测试验证 | Coding Sandbox | TEST Secret | 无 |
| PREVIEW | 候选版本体验 | Agent/授权用户 | PREVIEW Secret | 临时系统域名 |
| PRODUCTION | 正式运行 | APPROVER | PRODUCTION Secret | 稳定系统域名/自定义域名 |

Environment 配置、Secret Reference、Resource Profile、Network Policy 和 Runtime Profile 均版本化。Production Secret 不得注入普通 Coding Sandbox。

## 4. Preview Deployment

### 4.1 创建

- Coding Agent 可以自动创建 Preview，不需要 Production Approver。
- Preview 可基于尚未 Push 的候选快照构建，但必须绑定不可变 Snapshot/ChangeSet Hash。
- Secret Scan 和配置检查必须先通过。
- Preview 与 Production 不共享 Session、Cookie、Token、Secret 或审计域。

### 4.2 访问控制

- 默认只允许当前 Project/Workspace 授权成员访问。
- 授权用户可以创建外部只读分享链接。
- 分享链接必须有期限、可撤销、最小权限并完整审计。
- 创建外部分享前不得存在未处理 Critical 风险。

### 4.3 TTL

- 默认有效期：创建后72小时。
- 授权用户每次最多续期7天。
- 到期前24小时主动通知。
- 到期后撤销Gateway Route并停止容器。
- Deployment、日志引用、Image Reference和Audit保留，便于重新创建。
- 特殊保留必须由管理员设置受审计策略，禁止无人负责的无限期Preview。

Preview 不绑定用户自定义域名。

## 5. Production Authorization

```text
OPERATOR
  └─ Coding / Test / Preview

WORKSPACE_OWNER
  └─ Repository/Environment → Push确认 → Release Candidate → 发布申请

SECURITY_AUDITOR
  └─ Security Exception审核

APPROVER
  └─ Production Publish / Rollback / Decommission批准
```

Agent 只能创建 Release Candidate，不得自主上线 Production。

APPROVER 发布前必须查看：

- Coding Plan 与最终 Diff；
- Build/Test结果；
- Security Findings与Exception；
- Preview；
- SBOM、Signature、Provenance；
- Commit/Artifact/Image Digest映射；
- Environment、Resource、Domain和Secret Readiness。

## 6. One-click Production Semantics

“一键发布”不是跳过流程。所有前置条件满足后，APPROVER 的一次明确操作触发：

```text
LOCK_RELEASE_CANDIDATE
→ REVALIDATE_PERMISSION
→ REVALIDATE_GATE_FRESHNESS
→ VERIFY_COMMIT_ARTIFACT_IMAGE
→ VERIFY_SIGNATURE_AND_PROVENANCE
→ RESOLVE_PRODUCTION_SECRET_REFERENCES
→ SELECT_PRODUCTION_HOST
→ PULL_IMAGE_BY_DIGEST
→ START_GREEN
→ STARTUP_AND_READINESS_CHECKS
→ ACTIVATE_GATEWAY_ROUTE
→ DRAIN_BLUE_CONNECTIONS
→ MARK_DEPLOYMENT_ACTIVE
→ WRITE_AUDIT
→ SEND_NOTIFICATION
```

- 缺失配置、域名、Secret、容量或安全条件时，按钮禁用并展示可操作 Readiness Checklist。
- 请求必须带 `Idempotency-Key`。
- 相同Key/相同请求返回首次Deployment；相同Key/不同Hash返回冲突。
- 过程通过SSE持续反馈。
- 页面刷新或客户端离线不影响执行，也不重复部署。

## 7. Release Candidate

Release Candidate 固化：

- Tenant、Workspace、Project；
- Repository、Project Root、Commit；
- Coding Plan和ChangeSet Version；
- `nexus.yaml` Version/Hash；
- Lockfile Hash；
- Node/Runtime/Base Image Digest；
- OCI Image Digest；
- SBOM、Signature、Provenance；
- Security Scan与Exception；
- Test Result；
- Build Worker/Tool版本；
- 创建者、审批者和时间。

Production 遵循 Build Once / Promote Same Artifact。禁止从Source重新构建、使用mutable tag或替换Image后复用审批结果。

## 8. Application Runtime

V1 支持：

- 静态Web应用；
- Node.js SSR/Server-rendered应用；
- Framework Server、Server Actions、API Routes、前端BFF；
- HTTP、SSE、WebSocket。

不提供：

- 托管数据库、Redis、对象存储；
- 后台Worker/Queue；
-业务 Scheduler；
- 持久化容器磁盘。

应用必须无状态运行。外部API通过Environment Config和Secret接入。容器本地磁盘仅为Ephemeral Storage。

## 9. Application Gateway

V1 使用Traefik Proxy作为Preview/Production Application Gateway：

- 系统域名与自定义域名路由；
- TLS终止；
- HTTP、SSE、WebSocket；
- ForwardAuth；
- Blue-Green权重与原子切流；
- 旧连接优雅排空。

Traefik不得读取Docker Socket。Deployment Control Plane生成版本化期望路由配置，Host Agent校验后写入受限File Provider并原子生效。

企业WAF/LB可以位于Traefik前方，但不替代Nexus Edge Deployment状态管理。

## 10. 访问策略

Production发布时必须显式选择：

- `PUBLIC`：通过绑定域名直接访问。
- `ORGANIZATION_ONLY`：Application Gateway强制企业身份认证和应用访问授权。

不得由域名、网络位置或Agent推断访问策略。

Preview 与 Production 的Access Policy、Session、Cookie、Share Token和Audit必须隔离。不同Project之间不得共享应用会话密钥。

## 11. 系统域名

### 11.1 Production

每个Project获得稳定系统域名：

```text
{project-slug}-{project-short-id}.apps.example.com
```

- 发布新Release或回滚时域名不变化。
- Project展示名称变化不改变已分配标识。
- 域名变更必须通过显式Domain Binding操作。

### 11.2 Preview

每次Preview获得不可复用临时域名：

```text
preview-{deployment-short-id}.preview.apps.example.com
```

独立域名用于隔离缓存、Cookie和历史版本。

## 12. 自定义域名与证书

### 12.1 域名绑定流程

```text
Nexus Edge生成CNAME/TXT要求
→ 用户在自己的DNS Provider手工配置
→ Nexus Edge检测DNS
→ TXT Ownership Verification
→ CNAME Route Verification
→ ACME Certificate Issue
→ Gateway Route Activation
→ Renewal Monitoring
```

- V1 不接入 DNS Provider API，不要求用户提供 DNS 管理凭证。
- 用户自定义域名仅支持精确FQDN，不支持Wildcard Domain。
- 自定义域名绑定Project的Production Environment，不绑定某个Release。
- DNS API自动配置进入V1.1。

### 12.2 证书

- 平台Preview/Production子域名使用平台统一管理的Wildcard Certificate。
- 平台基础设施证书与客户域名证书生命周期分离。
- 用户域名默认使用ACME HTTP-01。
- Gateway支持时可使用TLS-ALPN-01作为备用。
- 支持企业上传自有证书。
- Private Key只由Secret Provider管理，不返回前端，不进入Agent、Sandbox、Log或业务DB明文字段。
- 托管证书到期前30天进入自动续期窗口。
- 连续续期失败产生分级管理员告警。
- 无法保证有效HTTPS时阻止新的Production Traffic Activation。

## 13. Blue-Green 与回滚

### 13.1 发布

1. 按Digest拉取已签名Image。
2. 启动Green实例。
3. 执行Startup、Readiness和HTTP业务健康检查。
4. 验证通过后Gateway原子切流。
5. 旧Blue实例执行连接排空。
6. 进入稳定观察窗口。

验证失败不得切流。观察期内连续健康异常自动回滚上一已验证Release。

### 13.2 一键回滚

- 只能选择历史已签名且Gate证据有效的Published Release。
- 回滚复用原不可变Image，不重新构建。
- Commit、SBOM、Signature、Provenance保持不变。
- APPROVER明确授权并记录原因。
- 回滚过程沿用健康检查、原子切流、连接排空和通知。

## 14. Runtime Network

- 入站只能经过Application Gateway。
- 出站只能经过运行环境专用Envoy Egress Gateway。
- 默认允许公网，永久阻止Core、DB、Redis、MinIO、AgentScope、Renderer、Host、Registry管理面、Metadata和其他Project。
- 企业内部API只能由SYSTEM_ADMIN按Project、FQDN/IP/Port精确允许，并使用独立Secret。
- Production应用不得获得企业内网广泛访问权限。

## 15. 可观测性

必须采集：

- Container stdout/stderr；
- Gateway Access Log；
- CPU、Memory、Network、Restart、Health；
- Request Count、Error Rate、Latency；
- Deployment和Rollback阶段；
- Certificate和Domain状态。

关联标签：Tenant、Workspace、Project、Environment、Deployment ID、Release ID、Image Digest。

授权用户可查看实时日志和基础指标。日志写入前执行Secret/敏感字段过滤。

V1不强制生成应用嵌入特定APM SDK；应用主动输出的OpenTelemetry可通过标准接口接入。

保留：Preview运行日志7天，Production运行日志30天，安全/发布审计365天。

## 16. 状态机

### 16.1 Release Candidate

```text
CREATED
  → BUILDING
  → SECURITY_CHECKING
  → READY_FOR_APPROVAL
  → APPROVED
  → PROMOTED

BUILDING / SECURITY_CHECKING → BLOCKED
READY_FOR_APPROVAL → REJECTED
任意未发布版本 → SUPERSEDED
```

### 16.2 Deployment

```text
REQUESTED
  → PULLING
  → STARTING
  → VERIFYING
  → ACTIVATING
  → ACTIVE

任一执行阶段 → FAILED
ACTIVE → ROLLING_BACK → ROLLED_BACK
ACTIVE → STOPPING → STOPPED
```

Preview复用Deployment状态机，但不进入Production人工批准和`PROMOTED`语义。

## 17. 主动通知

必须通知：

- Preview Ready/Failed/Expiring；
- Release Candidate Ready/Blocked；
- Production Approval Required；
- Deployment Succeeded/Failed；
- Automatic Rollback；
- Manual Intervention Required；
- Domain Verification；
- Certificate Renewal Risk。

V1站内、Web和邮件遵循企业Channel Policy；安全阻断、Production审批、自动回滚和证书风险不可完全关闭。Desktop通知进入V1.1。

## 18. Decommission

Active Production存在时不得直接删除Project。

```text
WORKSPACE_OWNER requests Decommission
→ APPROVER approves
→ Stop Production Traffic
→ Revoke System/Custom Domain Binding
→ Stop and Remove Runtime Containers
→ Archive Current Release
→ Soft-delete Project
```

- Project软删除保留90天。
- 恢复Project不自动恢复公网流量。
- 重新上线必须重新通过Production门禁。
- 已发布Release和供应链证据默认永久归档。
- 自定义证书根据其他Binding引用决定保留或撤销。

## 19. 性能与可靠性验收

| 指标 | V1目标 |
|---|---:|
| Sandbox创建P95 | ≤60秒 |
| Build完成后Preview可访问 | ≤3分钟 |
| Approved RC Production发布 | ≤5分钟 |
| 历史Release一键回滚 | ≤2分钟 |
| DNS验证后证书+Gateway激活 | ≤10分钟 |
| Web关键通知 | ≤30秒 |
| 邮件进入发送队列 | ≤2分钟 |

Blue-Green发布不得产生计划内服务中断，并对旧连接优雅排空。

在5个并发Sandbox、3个并发Build和2个并发Deployment下，Nexus Edge Core、Agent任务及既有Production应用不得因资源争抢不可用。

## 20. 备份恢复

- 应用容器本身不备份，按Image Digest和Deployment记录重建。
- Production Release、Domain、Access Policy、Environment、Secret Reference、Gateway期望配置和Audit必须纳入平台备份。
- 恢复后先验证Signature、Gate、Secret和Certificate，再激活流量。
- 沿用平台RPO≤24小时、RTO≤4小时。
