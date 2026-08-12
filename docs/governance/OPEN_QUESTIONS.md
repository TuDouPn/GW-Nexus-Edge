# GW Nexus Edge Open Questions

> 状态：Active  
> 作用：记录会导致不同实现者产生不同架构或契约的未决问题  
> 规则：状态为 OPEN/BLOCKED 的问题不得由 AI Agent 自行裁决

Open Question 不是 TODO。问题一旦解决，必须落入 Accepted ADR 或对应 Blueprint/Contract，并在此处记录决策链接。

| ID | 问题 | 影响范围 | 状态 | 阻断阶段 |
|---|---|---|---|---|
| OQ-001 | 数据等级最终值取三者最大值，与安全规范中“授权管理员可降级”的关系如何定义？是否存在正式 Override、有效期、双人复核和审计模型？ | Security、Workspace、Classification、Audit | OPEN | Phase 1 数据分级实现 |
| OQ-002 | Artifact 批注的实体、版本绑定、权限、生命周期和 API 契约是什么？ | Artifact、Approval、API、Data | OPEN | Phase 4 批注实现 |
| OQ-003 | Conflict Record 存储于 MySQL 还是 PostgreSQL，其与 Claim、Evidence、Task、ArtifactVersion 的关系和生命周期是什么？ | Trust、Knowledge、Data、API | OPEN | Phase 3 Trust Gate |
| OQ-004 | 分片上传会话和 Part 的权威存储、续传期限、幂等与崩溃恢复模型是什么？ | Resource、Upload API、MySQL、Redis | OPEN | Phase 2 上传实现 |
| OQ-005 | 最终批准是否原子触发发布？若批准与发布分离，默认流程、权限、重复调用和失败恢复如何定义？ | Approval、Artifact State、API、Audit | OPEN | Phase 4 最终审批 |
| OQ-006 | DOCX/PPTX/XLSX 多输出部分成功时，Task、Artifact、RenderJob 的聚合状态、审核准入和重试粒度如何定义？ | Task、Artifact、Renderer、Notification | OPEN | Phase 4 Renderer |
| OQ-007 | AgentScope 2.0.1 官方 Persistence/Recovery 的具体组件、存储、事务、备份和 Nexus Edge Adapter 边界是什么？ | AgentScope、Task、Operations、Backup | **RESOLVED（关联决策：ADR-0008 Redis Persistence/Recovery、ADR-0009 Recovery Capability 边界）** | Milestone 0 / Phase 3 |
| OQ-008 | Host Agent 的签名 Intent Schema、mTLS 身份签发/轮换、Nonce 防重放、最小操作集合与失联处置契约如何冻结？ | Coding Deployment、Host Agent、PKI、Audit | BLOCKED_BY_POC | Coding Phase 2 Host Pool |
| OQ-009 | `ORGANIZATION_ONLY` 应用跨平台域名和用户自定义域名时，Application Gateway 的企业身份跳转、Session/Cookie 域、安全属性与应用授权协议如何定义？ | Coding Deployment、IAM、Gateway、API | OPEN | Coding Phase 3 Production Access |
| OQ-010 | Harbor 当前目标版本对 OCI Referrers、Cosign 签名、SBOM、Provenance 与不可变 Digest 的实际兼容边界是什么？ | Supply Chain、Registry、CI/CD | BLOCKED_BY_POC | Coding Phase 2 Build Worker |
| OQ-011 | `nexus.yaml` 的正式 JSON Schema、配置继承、环境变量引用、校验错误模型与向后兼容规则是什么？ | Coding Workspace、Build Contract、API | OPEN | Coding Phase 1 Build Contract |
| OQ-012 | Node 22/24、npm/pnpm/yarn、React/Vue/Next/Nuxt 静态及 SSR 的受支持 Runtime Catalog 精确版本与镜像 Digest 是什么？ | Runtime Catalog、Build、Test、Operations | BLOCKED_BY_POC | Coding Phase 1 Compatibility |
| OQ-013 | assistant-ui 在 React 19 + Vite + Tailwind/shadcn 基线下应锁定哪个精确版本、Custom Runtime API 面和官方组件清单，才能完整适配 Nexus REST/SSE、Last-Event-ID、Tool Event 与安全过滤契约？ | Web、Agent UI、SSE、Security、Dependency | BLOCKED_BY_POC | Milestone 0 / Web Agent UI |

## 处理流程

1. 开发者或 Agent 提交问题证据、可选方案和影响分析。
2. 产品架构负责人判断是否需要 ADR。
3. 涉及架构、安全、公共契约或状态机时必须创建 ADR。
4. ADR Accepted 后同步修改 Blueprint、Contracts、Migration 和测试。
5. 本表状态改为 RESOLVED，并链接决策，不删除历史问题。

## 新问题模板

```text
ID：OQ-xxx
问题：
发现位置：
冲突或空白证据：
影响范围：
可选方案：
在未解决前必须阻止的实现：
负责人：
状态：OPEN
```
