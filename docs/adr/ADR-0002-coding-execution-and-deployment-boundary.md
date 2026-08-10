# ADR-0002 — Coding 不可信执行、供应链与自托管应用发布边界

> 状态：Accepted  
> 日期：2026-08-10  
> 决策人：产品架构负责人  
> 关联规格：Blueprint 03、07、21、22、23

## 背景

Coding Workspace 会读取第三方仓库、执行 Agent 生成代码、安装公网依赖、运行 Shell、构建镜像并把应用发布到域名。该威胁模型远高于普通文档 Workspace。早期方案曾考虑 Vercel 等外部平台，但最终要求由 Nexus Edge 自己管理 Docker 应用主机，并同时支持静态应用和 Node.js SSR 应用。

如果为追求快速落地而在 Nexus Edge Core 主机直接执行代码、把 Docker Socket 暴露给 Agent、在 Production 源码构建或允许 Agent 自主上线，后续无法通过局部替换修复信任边界，必须整体迁移。因此执行与发布边界必须在首版即采用长期架构。

## 决策

1. Coding Agent 由内嵌 AgentScope Java 2.0.1 执行；文件、Git、Shell、依赖、测试与开发构建均封装为受 Policy 控制的 Tool，经 Sandbox Broker 进入任务专属临时 Docker Sandbox。
2. Sandbox 采用 Rootless 与 gVisor 隔离，不得访问宿主机 Shell/文件系统、Docker/CRI Socket、Production Secret、企业内网或 Nexus Edge 核心基础设施。
3. Sandbox 公网流量经过独立 Egress Gateway 与受控 DNS；默认可访问公网，但域名、解析后 IP、端口与每次重定向都必须重新执行策略。
4. Production OCI Release Candidate 由独立 Build Worker + Rootless BuildKit 构建，进入 Nexus Edge 管理的 Harbor Registry。镜像、SBOM、签名、Provenance 和扫描结果以不可变 Image Digest 关联。
5. Preview 与 Production 使用独立 Host Pool。Production 只能拉取已审核、已签名且通过安全门禁的同一不可变 Digest，不在目标主机从源码构建。
6. Agent 只生成 Release Candidate。Production 发布必须由授权人显式批准，经过不可绕过的 Secret/SCA/SAST/License/Image/Config 门禁，并支持 Blue-Green 健康检查、原子切换和回滚。
7. Nexus Edge 自主管理应用主机、Traefik Application Gateway、域名验证和 ACME 证书生命周期；V1 不依赖 Vercel，也不直接集成用户 DNS Provider API。

## 长期适配性

Sandbox、Build、Registry、Application Host 和 Gateway 是不同信任域，可独立横向扩展、更换 Provider 或进入 Kubernetes，而不改变 Task、Release Candidate、Deployment、Domain Binding 和 Digest 契约。Provider 抽象用于可替换实现，安全不变量不随基础设施变化。

## 候选方案

- Vercel 托管：上手快，但与自主管理运行环境、企业私有化、Secret 和审计边界不一致。
- Core 主机直接运行 Docker：组件少，但把不可信代码带入企业核心控制面，风险不可接受。
- Coding Sandbox 直接构建并发布：开发制品与生产制品身份不可信，无法实现 Build Once/Promote Same Artifact。
- Kubernetes 作为 V1 唯一基线：可以长期扩展，但会显著增加首验基础设施复杂度；当前基线采用独立 Docker Host Pool，领域和控制协议不绑定 Docker Daemon。
- Agent 自主发布：违背企业责任确认和 Production Secret 隔离原则。

## 影响

- 必须开发 Sandbox Broker、Build Orchestrator、Host Agent、Deployment/Domain 控制面及严格审计。
- 至少需要独立 Preview 与 Production 应用主机，Build Worker 也不得与核心或生产主机共用。
- 需要 Harbor、Rootless BuildKit、gVisor、Traefik、Envoy Egress、受控 DNS、Cosign 及安全扫描工具的兼容 PoC。
- 运行与安全成本高于直接接入外部 PaaS，但获得企业可控、可追溯和私有化一致性。

## 迁移与回退

未开始生产部署，无既有应用迁移。具体 Provider 可在保持 Intent、Digest、Policy、Audit 和 Host Pool 契约的前提下替换。任何降低隔离、允许 Agent 自主生产发布或取消不可变制品的方案，都必须通过新的 Superseding ADR，不能作为临时降级。

## 验证

- Rootless + gVisor 逃逸、Docker Socket、SSRF、DNS Rebinding、Metadata 与内网横向访问红队测试。
- BuildKit/Harbor/Cosign/OCI Referrers/SBOM/Provenance 组合 PoC。
- Preview、Production、域名、证书、Blue-Green、回滚、Host 失联及幂等 E2E。
- ≥20 次完整 Coding 任务，成功率 ≥90%，安全事故、越权和 Production Secret 泄露均为 0。

## 未解决问题

Host Agent 签名 Intent、Organization-only Gateway、Registry 组合版本、`nexus.yaml` Schema 与 Runtime Catalog 精确版本见 OQ-008 至 OQ-012。
