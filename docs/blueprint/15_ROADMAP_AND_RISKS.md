# 15 — 路线图、依赖与风险

> 状态：Accepted

## 1. 发布原则

- 2026-09-01 是首个 Enterprise Pilot 目标日期，不是绕过 Gate 的固定发版日。
- P0 安全、可信度、Renderer、Golden Dataset 或真实闭环未通过时允许延期。
- Roadmap 按可验证成果推进，不以代码量、模块数量或页面数量判断完成。

## 2. Milestone 0：文档与技术门禁

交付：

- 本 Blueprint Accepted。
- 公开 GitHub Monorepo（ADR-0010）、CI 基线和依赖锁定。
- Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 兼容 PoC。
- MyBatis-Plus、Sa-Token、Redis/DB Drivers 版本 ADR。
- assistant-ui 在 React 19/Vite/Tailwind/shadcn 下的 Custom Runtime、Nexus REST/SSE、Tool UI、安全过滤与许可兼容 PoC，并冻结精确版本和公共 API 面。
- DeepSeek 与 OpenAI-compatible Provider 真实验证。
- WPS 免费版 Renderer feasibility/许可/稳定性报告。

退出条件：G-01至G-04及G-14所涉及技术结论具备可审计证据；不存在靠假 API、Mock Runtime、自研临时聊天组件或未验证 Renderer 继续开发的关键路径。

## 3. Milestone 1：企业基础底座

- MySQL/Flyway、Tenant/Department/User。
- Break Glass、LDAP Bind、目录同步。
- 五角色 RBAC、Scope、Workspace ACL。
- Secret Provider、Audit、OpenTelemetry。
- Web 登录/Admin 基础。
- `@gwnexus/assistant-ui`治理包、Nexus Custom Runtime Adapter与安全 Agent Thread 基础。

退出条件：权限越权矩阵通过，Secret 不落明文。

## 4. Milestone 2：Managed Workspace

- Workspace/Member/Data Level。
- Web 文件选择、分片上传、状态、失败恢复和版本冲突。
- MinIO 权威版本、Snapshot、软删除和 Retention。
- DOCX/XLSX/PPTX/文本 PDF 解析状态。

退出条件：历史 Snapshot 可复现，文件变化不污染历史 Task。

## 5. Milestone 3：Knowledge 与 Trust

- PostgreSQL/pgvector Schema。
- ACL 预过滤、混合检索、Rerank 接口。
- Embedding Policy、Index Generation。
- Evidence Registry、Calculation、Conflict 和 Trust Gate。

退出条件：跨 Workspace/Tenant 检索泄漏为 0，Evidence 定位稳定。

## 6. Milestone 4：经营分析 Skill

- Skill/Prompt/Workflow/Policy/Template 版本治理。
- AgentScope Adapter。
- Business Analyst Agent、Tools、Review Agent。
- Task 状态、SSE、取消、重试、恢复。
- DeepSeek/私有 Provider 策略。

退出条件：合成数据端到端产生通过 Schema/Trust Gate 的 Artifact Model。

## 7. Milestone 5：Artifact 与审批

- Render Queue、Windows Worker、WPS Provider。
- DOCX/PPTX/XLSX、高保真验证与 Preview。
- Artifact Version、Evidence、批注、两级审批、发布、通知。

退出条件：Renderer Compatibility Gate 通过；Worker 离线/重启/重复任务测试通过。

## 8. Milestone 6：Coding Workspace执行闭环

- Coding Project、Git Provider、Repository Authority与Monorepo Project Root。
- Code Index、Coding Plan Gate、Coding Agent和Sandbox Broker。
- 独立Sandbox Host、gVisor、Envoy Egress和Resource Profile。
- ChangeSet、Diff确认、Agent Branch Push、GitHub PR/Check。
- Node.js Build Contract、Node 22/24、npm/pnpm/yarn、STATIC/SSR和Custom Dockerfile。

退出条件：真实Repository中Plan→Agent修改→Build/Test→Diff→Push→Preview闭环通过；无Host Shell、Secret或核心网络突破。

## 9. Milestone 7：Coding供应链与应用发布

- 独立Rootless BuildKit、Harbor、Gitleaks、Semgrep、Trivy、Cosign和Provenance。
- Preview/Production Host Pool、Traefik、Blue-Green和Rollback。
- Environment/Secret、Readiness Checklist、Production Authorization。
- 系统域名、自定义域名、TXT/CNAME、ACME和证书续期。
- Runtime Logs/Metrics、Notification、Retention、Backup和Decommission。

退出条件：STATIC/SSR的Preview→RC→人工Production→HTTPS→Rollback闭环通过全部Security Gate。

## 10. Milestone 8：Web Completion Gate

- 经营分析与 Coding 两条 P0 均可仅通过 Web 完成完整端到端闭环。
- Web 管理 Console、Workspace、Agent工作台、Evidence/Diff、Artifact/Preview、审批、Production、Domain、通知和审计齐备。
- 响应式、无障碍、权限、SSE重连、错误恢复、组件治理、视觉回归和前端覆盖率通过。
- 完成两条 P0 的 Web-only E2E，验证不存在非本地专属步骤依赖 Desktop。

退出条件：G-13通过。V1不启动Desktop功能开发。

## 11. Milestone 9：Enterprise Pilot

- Ubuntu 24.04 + Docker Compose 离线包。
- `nexus-edge-ctl`一键预检、安装、状态、诊断、备份恢复和安全升级入口。
- Windows Renderer签名安装包及Server配对流程。
- Observability、Backup/Restore、DMZ 模板。
- 企业数据、模板、Golden Result 到位。
- ≥20 次真实任务和全部验收指标。
- Coding试点真实Repository材料到位，完成≥20次Coding闭环、≥10次Production、≥5次Rollback与≥5次自定义域名。

退出条件：业务、安全、质量和恢复签字通过。

## 12. V1.1

- Windows Desktop：React 19 + Tauri 2 + Rust、企业登录、本地目录扫描/Hash/单向同步、本地Git发现、设备身份、系统通知、诊断、签名MSI及双端契约测试。
- SMB/NAS Connector。
- MySQL/DM8 只读 Connector 与 Semantic Layer。
- Scheduled Skill/月报。
- WPS 企业版/Microsoft Renderer。
- OIDC/SAML/MFA 和公网移动增强。
- OCR Provider 与扫描资料人工确认。
- Coding自动PR Preview、Push触发Build、持续部署、在线编辑器、交互式Terminal、多Repository变更和DNS Provider API。

## 13. V2+

- 多企业 SaaS、Tenant 生命周期、Quota/Billing。
- Kubernetes、高可用、异地灾备。
- 在线 Office/OnlyOffice 等集成。
- 更多行业 Skill、Connector 和合规认证。
- Cloud Demo 产品化与开放生态。

## 14. 最高风险

| 风险 | 影响 | Gate/缓解 |
|---|---|---|
| WPS 免费版无人值守自动化不可行或许可不适合 | 正式 Artifact 阻断 | 先做 Compatibility/许可验证；失败走 ADR 选择企业版或其他 Provider |
| 无真实数据/模板/Golden Result | 无法证明业务质量 | Production Readiness Gate，禁止虚假完成 |
| AgentScope/Spring Boot/权限栈不兼容 | 后端基线阻断 | Milestone 0 组合 PoC，不静默降级 |
| L2 Context 外发过量 | 数据泄漏 | Policy 最小化、审计、红队和 Provider 白名单 |
| RAG 权限后过滤 | 严重越权 | DB 检索预过滤和泄漏测试 |
| Agent 幻觉数字/事实 | 业务错误 | Deterministic Tool、Evidence、Review Agent、Trust Gate、人工两级审批 |
| V1 范围再次膨胀 | 延期与质量下降 | P0/P1/P2 冻结，变化必须 ADR |
| AI Coding 主导导致假实现或不同 Agent 产生架构漂移 | 不可维护 | AGENTS 宪章、AI交接协议、完整链路、CI、人工审查和 Gate |
| 私有化交付环境差异 | 部署失败 | 唯一 Ubuntu/Compose 基线，其他环境后续认证 |
| 把一键部署做成不可恢复的临时脚本 | 安装漂移、升级失控、数据损坏 | 稳定`nexus-edge-ctl`契约、幂等阶段、失败诊断和全链路安装E2E |
| 把Coding应用“一键发布”误解为跳过安全/审批 | 供应链攻击和未授权上线 | Readiness、不可绕过Gate、APPROVER、Digest与签名验证 |
| 普通Docker隔离任意代码 | Host逃逸和核心横向访问 | 独立Host Pool、Rootless、gVisor、无Socket、Envoy Egress红队 |
| Production Secret进入Agent/Build | 企业凭证泄漏 | 四环境分域、Secret Reference、Host Agent运行时注入和泄漏测试 |
| 任意Framework承诺不可验证 | 兼容范围失控 | 标准Node Build Contract、显式`nexus.yaml`、Runtime Catalog与真实Repository Gate |
| Coding基础设施扩大V1范围 | 日期严重失真 | 两条P0独立里程碑、材料/安全Gate，任何未通过均延期 |
| Desktop范围回流V1 | 分散Web资源、增加Windows/Tauri/双端验收负担 | ADR-0004；V1 CI、安装包和Work Item禁止Desktop实现 |
| assistant-ui 被误当成第二套 Runtime 或页面直接耦合上游 | AgentScope边界破坏、协议漂移、升级与安全过滤失控 | ADR-0005；`@gwnexus/assistant-ui`隔离层、G-14、SSE/权限/敏感数据专项测试 |

## 15. 时间现实性

从仅有文档的仓库在 2026-08-10 到 2026-09-01 同时完成经营分析与Coding Workspace两条生产级P0，风险已从“极高”上升为“按现有证据不可可信承诺”。日期只能作为目标；不得删减安全、Evidence、Renderer、Sandbox隔离、供应链门禁或真实验收换取按时。应先完成Milestone 0以及WPS/gVisor/AgentScope/BuildKit/Harbor兼容PoC，再基于真实吞吐重新排期。任一P0未达标必须延期。
