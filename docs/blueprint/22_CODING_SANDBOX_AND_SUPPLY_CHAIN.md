# 22 — Coding Sandbox、Build 与软件供应链安全规格

> 状态：Accepted  
> 优先级：V1 P0  
> 适用对象：Coding Sandbox、Build Worker、Preview/Production Image、Registry、安全门禁

## 1. 最高级安全不变量

1. 所有代码读取/修改、Shell、依赖安装、测试和开发构建都在 Task 独立临时 Sandbox 中执行。
2. 用户代码或 Agent 生成代码永远不得在 Nexus Edge Core、AgentScope 进程、数据库主机、Renderer 或 Production Host 的管理环境中执行。
3. Coding Agent 永远不得获得 Nexus Edge Host Shell、Docker/CRI Socket 或宿主文件系统。
4. 任意生成代码不得通过网络获得企业基础设施横向访问能力。
5. Production OCI 构建只由独立 Build Worker + Rootless BuildKit 完成。
6. Production Secret 永远不得进入 Coding Sandbox、Agent Context、Build Worker或镜像层。
7. 任一 Production P0 Gate 未执行、失败或超过阈值时不得发布。

这些不变量不能通过普通配置、管理员便利开关或 Agent 指令关闭。

## 2. 安全域拓扑

```text
Nexus Edge Core / AgentScope
        │ mTLS + signed task envelope
        ▼
Sandbox Broker Control Plane
        │
        ├─ Dedicated Sandbox Host Pool
        │    └─ Rootless Docker + gVisor Sandbox
        │
        ├─ Dedicated Build Worker Pool
        │    └─ Rootless BuildKit
        │
        ├─ Dedicated Preview Host Pool
        │    └─ Hardened OCI Runtime
        │
        └─ Dedicated Production Host Pool
             └─ Hardened OCI Runtime

All outbound → Envoy Egress Gateway → Controlled DNS/Internet
Build output  → Harbor → Digest/SBOM/Signature/Provenance
```

Build、Preview、Production 节点池不共享 Docker Daemon、运行凭证或 Secret 安全域。V1 至少部署一台 Preview Host 和一台独立 Production Host；调度模型原生支持多节点。

## 3. Sandbox 生命周期

```text
ALLOCATING
  → PREPARING
  → READY
  → EXECUTING
  → CHECKPOINTING
  → TERMINATING
  → DESTROYED

异常：任一活动状态 → FAILED → TERMINATING
```

每个 Coding Task 使用独立逻辑 Sandbox。创建时固化：

- Tenant、Workspace、Project、Task、Execution ID；
- Base Commit、Project Root、Build Contract Version；
- Resource Profile Version；
- Network/Egress Policy Version；
- TEST/PREVIEW Secret Reference Set；
- Hardened Runtime Profile；
- 到期时间和最大执行时长。

销毁前只允许持久化：

- Git Commit/Patch、ChangeSet 和 Diff Hash；
- 测试、开发 Build 和 Tool 结果；
- AgentScope 官方 Checkpoint 引用；
- 脱敏执行日志、资源指标和审计；
- 经验证的 Artifact。

禁止持久化整个可变容器文件系统作为恢复真相源。

## 4. Hardened Runtime

Coding Sandbox、Preview 和 Production 默认使用：

- Rootless Docker；
- User Namespace；
- gVisor `runsc` 或经过兼容认证的 Hardened OCI Runtime；
- 非 Root User；
- Read-only Root Filesystem；
- `no-new-privileges`；
- Capability Drop All，仅按审查结果添加最小能力；
- Seccomp/AppArmor；
- 独立 Network/PID/Mount Namespace；
- CPU、Memory、PID、FD、Disk、Log、Bandwidth 限额。

绝对禁止：

- Privileged；
- Host PID/IPC/Network；
- HostPath/宿主目录挂载；
- Docker/CRI Socket；
- 云 Metadata；
- 设备直通和危险 Capability。

框架兼容性确实阻塞时，SYSTEM_ADMIN 可选择已认证的受限 Runtime Profile，但必须关联 Security Exception、原因、范围和有效期；不得降低上述绝对禁止项。

## 5. Sandbox Broker 与 Tool Call

AgentScope Tool 不直接执行进程。调用链：

```text
Coding Agent
  → Policy Decision
  → AgentScope Tool
  → Sandbox Broker
  → Sandbox-scoped Command/File/Git API
  → Result Sanitizer
  → AgentScope Event + Audit
```

每次执行型 Tool Call 至少记录：

- Task ID、Execution ID、Sandbox ID；
- Tool Name/Version；
- 操作类型、工作目录、命令或参数摘要；
- 开始/结束时间、退出码、超时、资源消耗；
- 脱敏输出摘要和完整日志受控引用；
- Policy Decision、网络目标和 Secret Reference Name；
- Trace ID。

日志写入前必须过滤 Secret、Token、Cookie、私钥和企业敏感模式。

## 6. 长任务与恢复

业务步骤边界保存：

- AgentScope 官方 Checkpoint；
- Base Commit 和 Sandbox Manifest；
- 已确认的本地 Checkpoint Commit/Patch；
- `nexus.yaml` Version/Hash；
- Tool 执行和测试结果；
- Policy、Runtime Profile、Secret Reference Set 版本。

服务或 Sandbox 故障后：

1. 创建全新 Sandbox。
2. 从 Base Commit 重新 Checkout。
3. 重放经过 Hash 校验的 Checkpoint Change。
4. 依赖安装和未完成命令重新执行。
5. 由 AgentScope 官方恢复 Execution。

Repository、权限、Secret、Build Contract、安全策略或 Runtime Catalog 发生变化时，禁止盲目续跑；任务必须重新验证或重新执行。

## 7. Egress Gateway

Sandbox、Build、Preview 和 Production 默认可以访问公网，但必须经独立 Envoy Egress Gateway。

### 7.1 网络强制

- Network Namespace 只允许连接 Egress Gateway 和受控 DNS Resolver。
- 禁止直接公网路由。
- HTTP/HTTPS 通过显式代理。
- Git SSH 等非 HTTP 流量只能通过受控 TCP 通道。
- UDP/QUIC 默认禁止。

### 7.2 永久拒绝目标

- Loopback、Link-local、RFC1918/企业私网和未授权内网；
- Nexus Edge Core、AgentScope、MySQL、PostgreSQL、Redis、MinIO；
- Renderer、Harbor 管理端、Host Agent 管理端；
- Sandbox/Build/Preview/Production Host 管理地址；
- Docker/CRI API 和云 Metadata；
- 其他 Project/Application 私网地址。

### 7.3 判定与审计

- 同时校验域名、DNS解析后的每个IP、端口和协议。
- Gateway 使用受控 DNS 解析，并拒绝解析到禁止网段。
- 每个新连接重新判定；HTTP Redirect 后的新请求再次判定。
- 防止 DNS Rebinding、SSRF、直接 IP 和代理绕过。
- 审计 Task、Workspace、Project、Sandbox/Application、域名、IP、端口、流量和结果。

访问企业内部 API 只能由 SYSTEM_ADMIN 配置 Project 级精确 Destination Allowlist，并使用独立 Secret；禁止广泛企业内网权限。

## 8. Resource Profile

资源由版本化 Profile 管理，不在代码中散落常量：

| Profile | CPU | Memory | Ephemeral Disk | Max Duration |
|---|---:|---:|---:|---:|
| Coding Sandbox | 2 vCPU | 4 GB | 10 GB | 2 h |
| Build Task | 4 vCPU | 8 GB | 20 GB | 30 min |
| Preview App | 1 vCPU | 1 GB | 2 GB | 按 Preview TTL |
| Production App | 2 vCPU | 2 GB | 2 GB | 持续运行 |

同时限制 PID、File Descriptor、日志和网络带宽。管理员可以创建并向 Project 分配新 Profile Version；普通用户和 Agent 不得突破。超限必须明确失败或限流，并局限于当前 Task/Application。

Preview/Production 磁盘是 Ephemeral Data，不是业务持久化存储。

## 9. Build Worker

### 9.1 Build Once / Promote Same Artifact

```text
Reviewed Commit + nexus.yaml
  → Rootless BuildKit
  → OCI Image
  → Image Digest
  → SBOM / Provenance / Scan / Signature
  → Release Candidate
  → Preview or Production Promotion
```

- Build 不发生在 Core、AgentScope、Sandbox或Application Host。
- Production Host 不从源码现场构建。
- Production 只按不可变 Digest 拉取。
- 禁止 `latest` 或 mutable tag 作为部署身份。
- Preview 与 Production Promote 同一已审核制品时不得重新构建。

### 9.2 Runtime Image Contract

`NODE_CONTRACT`：

- STATIC 使用平台维护的非 Root Static Web Runtime Image。
- SSR 使用平台维护的 Node.js LTS 非 Root Runtime Image。
- 默认内部端口 8080，可显式覆盖。
- Production 必须有 HTTP Health Path；STATIC 默认 `/`，SSR 必须显式配置。
- 不能只以 Port Open 判断 SSR 就绪。

基础镜像由 Runtime Catalog 按 Digest 锁定、扫描和受控升级。

`CUSTOM_DOCKERFILE` 同样受非 Root、健康检查、声明端口、无持久化本地状态和全部安全门禁约束。

## 10. Environment 与 Secret

Project 至少区分：

- BUILD；
- TEST；
- PREVIEW；
- PRODUCTION。

`nexus.yaml` 只声明 Config Key 与 Secret Reference，禁止 Secret Value。

| 阶段 | 注入主体 | 允许方式 | 禁止 |
|---|---|---|---|
| BUILD | BuildKit | Secret Mount | ARG、Image Layer、Build Log |
| TEST/PREVIEW | Sandbox Broker/Preview Host Agent | 临时Env或只读tmpfs文件 | 持久化镜像、Agent返回明文 |
| PRODUCTION | Production Host Agent | Runtime Env或只读tmpfs文件 | Coding Agent、Sandbox、Build Worker读取 |

敏感凭证默认以只读 tmpfs 文件注入。Secret Rotation 通过授权滚动重启/重新部署生效，不重建镜像。

审计只记录：Actor、Project、Environment、Secret Name/Ref、Task/Deployment、Sandbox/Host、时间和结果，永不记录 Secret Value。

## 11. Harbor OCI Registry

V1 使用 Harbor 作为私有 OCI Registry：

- Tenant/Project Repository 隔离；
- Robot Account 和最小权限；
- Immutable Tag 与 Retention；
- OCI Artifact/Attestation；
- 漏洞扫描接入。

权限：

- Build Worker：目标 Repository 最小 Push。
- Preview Host：对应 Preview Repository Read-only Pull。
- Production Host：对应 Production Repository Read-only Pull。
- Nexus Edge Core：只保存凭证引用，不保存明文 Harbor Credential。

Production 拉取前必须验证 Release Candidate、Image Digest、签名和 Gate 状态。

## 12. 供应链证据

以 Image Digest 为核心强绑定：

```text
Source Commit
  → Build Contract Hash
  → Build Provenance
  → Image Digest
  → CycloneDX SBOM
  → Security Findings
  → Cosign Signature
  → Release Candidate
  → Deployment
```

- SBOM 至少为 CycloneDX JSON，保留 SPDX 输出能力。
- Rootless BuildKit 生成 Provenance。
- Cosign 生成签名；Key 由 Secret Provider 管理。
- SBOM、Signature、Provenance 作为 OCI Artifact/Attestation 存入 Harbor。
- 任一关联 Hash 不一致即阻止发布。

## 13. 安全工具链

| 能力 | V1 工具 |
|---|---|
| Secret Scan | Gitleaks CLI |
| SAST | Semgrep Community Edition + 版本化规则 |
| SCA/Vulnerability | Trivy |
| Container Image Scan | Trivy |
| IaC/Deployment Config | Trivy |
| License Compliance | Trivy + 企业Policy |
| SBOM | Trivy CycloneDX JSON |
| Signature | Cosign |
| Provenance | Rootless BuildKit OCI Attestation |

默认全部在企业环境本地运行，不把源码发送到第三方扫描 SaaS。工具、规则、漏洞库与输出Schema必须版本化并记录。统一 Security Finding 领域模型允许未来替换 Scanner Provider，但不能绕过门禁。

## 14. Security Gates

### 14.1 Preview

构建前强制：

- Secret Scan；
- Deployment Config 安全检查。

Preview Image 必须生成最小 SBOM，并执行 SCA 和 Image Scan。

阻断：

- 有效 Secret；
- Critical 漏洞；
- 恶意依赖；
- Root/Privileged/Host配置；
- 平台隔离违规。

内部 Preview 的非 Critical 风险可带警告运行；创建外部分享链接前不得存在未处理 Critical 风险。SAST/License 在 Preview 展示，Production 执行完整门禁。

### 14.2 Production

不可绕过检查：

- Secret Scan；
- SCA；
- SAST；
- License Compliance；
- Container Image Vulnerability Scan；
- Deployment Config Security；
- SBOM、Signature、Provenance；
- Commit/Artifact/Image Digest 一致性。

默认阻断阈值：

- 任一有效 Secret 命中；
- SAST Critical/High；
- SCA/Image Critical；
- 已有修复且可利用的 High；
- 禁止 License 或无法确认的 Unknown License；
- Privileged、Root、Host Mount、Docker Socket、Host Network、危险 Capability；
- 明文 Secret；
- 缺少 Health Check。

扫描结果超过24小时，或 Commit、Lockfile、Build Contract、Base Image变化时必须重扫。

### 14.3 Security Exception

Exception 必须绑定：

- 具体 Finding；
- Commit 和 Image Digest；
- 原因与业务影响；
- 补偿措施；
- Scope；
- 到期时间；
- 申请人与 SECURITY_AUDITOR 审批记录。

禁止永久 Project 级豁免。Exception 不替代 APPROVER 的 Production 发布。

## 15. Retention

| 数据 | 默认保留 |
|---|---|
| Build Cache | 7天 |
| Preview到期后的镜像 | 7天 |
| Preview运行日志 | 7天 |
| 未发布/拒绝 Release Candidate | 90天 |
| 已发布Production Image/SBOM/Signature/Provenance | 默认永久归档 |
| 安全/发布审计 | 365天，可延长 |

运行日志清理不删除关键审计。Published Supply Chain Artifact 的物理删除必须通过 Retention/Purge 权限流程，并明确记录对历史回滚能力的影响。

## 16. Backup 与恢复

沿用 RPO≤24小时、RTO≤4小时。必须备份：

- Project/Repository Binding；
- Coding Plan、ChangeSet；
- Release Candidate、Deployment、Domain Binding；
- Resource Profile、Environment/Secret Reference；
- Audit；
- Harbor 已发布 Image、SBOM、Signature、Provenance。

证书私钥由 Secret Provider/KMS 备份，Nexus Edge 只备份引用和元数据。

Sandbox、Build Cache、运行容器和 Preview 实例不备份。恢复后按不可变 Image、配置和 Deployment 重建；Production 激活前重新验证签名、门禁、Secret Reference 和证书。

## 17. 安全验收

必须证明：

- Sandbox 不能访问 Core、DB、Redis、MinIO、AgentScope、Renderer、Host或Metadata。
- DNS Rebinding、SSRF、Redirect、直接IP和QUIC绕过均失败。
- Production Secret 不进入Agent、Sandbox、Build、Image、Log、Artifact。
- Host Agent 不接受未签名、过期或越权部署指令。
- Preview/Production和不同Project之间不能横向访问。
- Production只能拉取已签名、Gate通过的Digest。
- Security Exception不能越Scope、越期限复用。
- 资源耗尽只影响当前Task/Application。

