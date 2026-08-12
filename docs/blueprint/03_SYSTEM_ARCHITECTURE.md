# 03 — 系统架构与模块边界

> 状态：Accepted

## 1. 架构原则

1. AgentScope Java 2.0.1 是唯一 Agent Runtime。
2. Nexus Edge 采用模块化单体起步，保持清晰领域边界，不为未来假设提前拆微服务。
3. 企业数据、业务状态和 Artifact 由 Nexus Edge 管理；Agent 执行细节由 AgentScope 管理。
4. V1 只有 Web 客户端；后端契约保持客户端中立。V1.1 Desktop 复用同一契约且不承载独立 Agent 逻辑。
5. 业务可靠事件使用 Outbox + Redis Streams；不使用 Redis Streams 调度 Agent。
6. 所有跨边界调用可审计、可取消、可幂等、可恢复。
7. 用户代码是独立不可信执行域；Coding Sandbox、Build、Preview、Production与Core按主机池、Daemon、网络和Secret分离。
8. Coding Application采用控制面/Host Agent架构；Core不持有宿主机通用SSH，也不直接操作远程Docker Socket。

## 2. 容器视图

```text
React Web ──TLS──> Nginx/API ─> Nexus Edge Spring Boot
                                          │
                                          ├─ Identity / Workspace / Task
                                          ├─ Skill / Policy / Knowledge
                                          ├─ Artifact / Approval / Audit
                                          ├─ AgentScope Adapter
                                          │       └─ AgentScope Harness/Core
                                          ├─ Parser / Notification Workers
                                          └─ Outbox Publisher
                                                   │
             ┌───────────────┬───────────────┬─────┴─────┬─────────────┐
             │               │               │           │             │
           MySQL        PostgreSQL        Redis        MinIO       Model Provider
          Business       pgvector      Cache/Streams  Files       via AgentScope
                                                           │
                                                       Render Queue
                                                           │
                                                Windows WPS Renderer Worker

Coding Workspace extension:

React Web
          │
          ▼
Coding Project / Plan / ChangeSet / Release / Deployment
          │
          ├─ AgentScope Coding Agent → Sandbox Broker → Dedicated Sandbox Pool
          ├─ Build Control → Rootless BuildKit Pool → Harbor OCI Registry
          └─ Deployment Control ─mTLS→ Preview/Production Host Agent Pool
                                      ├─ Traefik Application Gateway
                                      └─ Envoy Egress Gateway + Controlled DNS
```

## 3. 后端模块

| 模块 | 数据所有权 | 主要职责 | 禁止事项 |
|---|---|---|---|
| identity | User、Group、IdentitySource | Break Glass、LDAP Bind、目录同步、会话映射 | 保存企业密码 |
| organization | Tenant、Department | 单企业组织与 Scope | V1 多企业 SaaS 生命周期 |
| workspace | Workspace、Member、Policy Binding | Workspace 生命周期、成员、等级、Snapshot | 直接处理 Agent 执行 |
| resource | Resource、File、Version | 文件元数据、同步、版本、Hash | 把大文件存入业务库 |
| knowledge | KB、Document、Chunk、Evidence | 解析、索引、权限过滤、检索 | 先检索后做权限补救 |
| skill | Skill、Version、Prompt/Workflow Ref | 版本、测试、发布、回滚 | 自研 Workflow Engine |
| task | Task、Idempotency、Execution Link | 业务任务、取消、重试、状态映射 | 复制 AgentScope 内部状态机 |
| agentscope-adapter | 无独立业务实体 | Context/Policy 注入、事件和结果转换 | 自研 Runtime、模型协议、Tool Framework |
| model-governance | ModelRegistration、Policy、Usage | 白名单、Secret Ref、策略、配额、成本与审计 | 自定义 chat/stream SPI |
| tool-governance | ToolRegistration、Permission | Tool/MCP 注册、权限和审计 | 自研 Tool Calling Runtime |
| artifact | Artifact、Version、Evidence Link | Artifact Model、版本、预览、发布、归档 | 覆盖历史版本 |
| approval | Approval、Decision | 两级审核、退回、责任确认 | 承担 Agent HITL Runtime |
| renderer | RendererNode、RenderJob | Provider 路由、队列、心跳、结果校验 | 依赖任务发起人电脑 |
| notification | Notification、Delivery | 站内、Web、Email；Desktop Channel为V1.1 | 在 V1 内硬编码企业 IM |
| audit | AuditEvent、ExportJob | 不可变业务审计和导出 | 用应用日志替代审计 |
| infrastructure | Provider 实现 | DB、Redis、MinIO、LDAP、SMTP、OTel | 反向依赖领域模块 |
| coding-project | CodingProject、RepositoryBinding、BuildContract、CodeIndex | Project/Repo/Root/Runtime/数据等级治理 | 自建Git托管、跨Repo原子变更 |
| coding-plan | CodingPlan、PlanVersion | 只读分析、Plan批准和实现偏差审计 | 未批准Plan直接写代码 |
| coding-execution | Sandbox、ChangeSet、Validation | Coding Agent用例、Sandbox Broker、Diff/Push Gate | 在Core或用户Desktop执行代码 |
| supply-chain | Build、Finding、SBOM、Provenance、Signature | Rootless BuildKit、门禁、Harbor制品关联 | 现场生产构建、mutable tag |
| deployment | ReleaseCandidate、Deployment、DomainBinding | Preview、Production、Blue-Green、回滚、域名证书 | Agent自主Production发布 |
| host-control | HostNode、ResourceProfile、Lease | Host Agent注册、调度、容量和健康 | Core持有通用SSH/Docker Socket |
| egress-policy | DestinationPolicy、NetworkAudit | 受控DNS/Envoy出口策略与审计 | 用户代码直连企业内网 |

## 4. 模块依赖规则

```text
adapter-in (REST/SSE/Worker)
          ↓
application use cases
          ↓
domain model + ports
          ↓
adapter-out (MyBatis/Redis/MinIO/LDAP/AgentScope)
```

- Domain 不依赖 Spring、MyBatis、AgentScope 或基础设施类型。
- Controller 不调用 Mapper、AgentScope 或 MinIO Client。
- 模块通过 Application Port、领域 ID 和业务事件协作。
- 禁止跨模块直接访问 Mapper/Table。
- 跨模块查询使用公开 Query Service；写操作通过 Command Service。
- ArchUnit 测试必须执行上述依赖规则。

## 5. Agent 调用链

```text
POST /api/v1/tasks
  → TaskApplicationService
  → PermissionService + PolicyDecision
  → SkillVersionResolver
  → AgentExecutionApplicationService
  → AgentScopeAdapter
  → AgentScope Harness/Core
  → AgentScope Model/Tool/Workflow
  → AgentScope Events
  → Task Event Projection + Audit + SSE
  → Artifact Model
```

AgentScopeAdapter 只允许：

- 构造 RuntimeContext、Agent/Skill 配置和授权 Context。
- 调用官方 Harness/Core。
- 将官方事件映射为稳定业务事件。
- 关联 Task ID、Execution ID 和 Trace ID。
- 将结果交给 Artifact Application Service。

## 6. 长任务与恢复

- Agent 执行状态持久化与恢复使用 AgentScope 官方能力组合（ADR-0009）：AgentStateStore 负责会话/
  AgentState 恢复（Redis 实现已验证，ADR-0008）；优雅 session interrupt 后 AgentState 持久化、下一调用
  恢复上下文（VERIFIED）；SandboxSnapshot 沙箱快照 payload 原语已验证，但**不构成已验证的完整
  Sandbox 文件系统跨调用自动恢复**；**不存在已验证的 Token、Tool 栈或任意崩溃点精确续跑能力**
  （技术 NOT_VERIFIED，V1 承诺 OUT_OF_SCOPE）。
- Nexus Edge 持久化 Task、使用的 Skill/Prompt/Policy/Snapshot 版本及 Execution ID；**业务 Task 和
  TaskAttempt 由 MySQL 保存权威业务状态，并采用业务步骤级恢复**（不依赖 AgentScope Checkpoint API）。
- V1 支持业务步骤级恢复，不要求 Token 级续跑。
- 输入 Snapshot、Skill Version、Permission 或 Policy 发生变化时，不恢复原 Execution；创建新尝试并保留历史。
- 所有重试使用同一 Task ID 与递增 Attempt，外部副作用必须依赖 Idempotency Key。
- **Nexus Edge 不自研第二套 Agent Checkpoint Runtime**；Coding 权威恢复 = 新 Sandbox + 不可变
  Base Commit + Commit/Patch/ChangeSet 重放（22 §6）。

Coding Task恢复额外遵循：新Sandbox从Base Commit检出并重放Hash校验的Checkpoint Commit/Patch；不恢复旧容器或整个可变文件系统。Repository、Build Contract、Secret、Permission或Policy变化时重新验证或重新执行。

## 7. Coding执行与发布调用链

```text
POST Coding Task
  → CodingPlanApplicationService (read-only analysis)
  → Human Plan Approval
  → CodingExecutionApplicationService
  → AgentScopeAdapter / Coding Agent
  → Policy-controlled Tools
  → SandboxBroker → Dedicated gVisor Sandbox
  → Validation → ChangeSet → Human Push Confirmation
  → Git Provider → Agent Branch / optional GitHub PR
  → BuildApplicationService → Rootless BuildKit
  → Harbor (Digest + SBOM + Signature + Provenance)
  → ReleaseCandidate → Security Gates → Human Approval
  → DeploymentControlPlane → mTLS Host Agent
  → Green Health Check → Traefik Atomic Switch → Production URL
```

Redis Streams可承载通知、扫描、构建结果和部署业务事件，但不得取代AgentScope Execution恢复、Host Agent签名指令或Deployment聚合状态机。

## 8. Deployment Profile

### Enterprise Pilot（P0）

- Ubuntu Server 24.04 LTS + Docker Compose。
- 单企业 Tenant。
- Web 内网/VPN 可用。
- React Web 与专用 Windows Renderer；Desktop不属于V1部署Profile。
- Coding P0额外部署独立Sandbox Host、Build Worker、Preview Host和Production Host安全域；V1至少一台Preview Host和一台独立Production Host。
- Harbor、Traefik、Envoy Egress Gateway和受控DNS是Coding应用发布基础设施。
- DMZ 公网接入为可选部署能力。

### Cloud Demo（非 P0）

- 预置 Demo Tenant、账号、Workspace 和示例数据。
- 不允许真实企业数据。
- 不提供注册、多租户、计费或 SLA。
