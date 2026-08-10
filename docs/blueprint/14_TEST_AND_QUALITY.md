# 14 — 测试、Eval 与 CI 质量门禁

> 状态：Accepted

## 1. 测试层次

| 层次 | 目标 |
|---|---|
| Unit | Domain rule、Value Object、Policy、状态迁移、纯 Tool 算法 |
| Component | 单模块 Application + Adapter，隔离外部系统 |
| Agent UI | assistant-ui Custom Runtime、Thread/Composer、Tool UI、安全事件映射、SSE恢复、a11y与视觉回归 |
| Integration | MySQL/Testcontainers、PostgreSQL/pgvector、Redis、MinIO、LDAP 测试环境 |
| Contract | OpenAPI、SSE、Event、Artifact Model、Skill Manifest |
| AgentScope | 真实 Harness/Core、官方 Provider test endpoint、恢复/取消/事件 |
| Renderer | Windows + WPS 环境真实文件与模板 |
| E2E | Web → Server → Agent → Renderer → Approval → Published；Coding追加Preview/Production/Domain |
| Security | 越权、注入、外发、Secret、路径、下载、日志泄漏 |
| Performance | 20 users、5 tasks、5k files、1m chunks、30/60 min |
| Recovery | 服务重启、模型失败、Redis Pending、Renderer 离线、备份恢复 |
| Coding Agent | Plan、Code Index、Sandbox Tool、Checkpoint、ChangeSet、Git冲突与模型Fallback |
| Supply Chain | BuildKit、Lockfile、SBOM、SAST/SCA/License、Cosign、Harbor和Digest一致性 |
| App Deployment | Preview、Traefik、ACME、Blue-Green、Rollback、Host Agent和Domain |

## 2. 覆盖率门禁

- 后端 Domain/Application 行覆盖率 ≥80%。
- 安全、权限、数据分级、Policy、状态机关键分支 100%。
- 前端核心流程覆盖率 ≥70%。
- 前端 Pull Request 必须通过组件复用审查；未经批准新增通用基础组件视为合并门禁失败。
- Agent 对话变更必须证明优先复用 `@gwnexus/assistant-ui`/assistant-ui，且不得由页面直接依赖上游包或自行实现 Thread、Message、Composer、Streaming/Retry 和 Tool UI 基础能力。
- 覆盖率不能替代真实集成、Eval 和企业验收。

### 2.1 Agent 对话组件专项门禁

- React 19、TypeScript strict、Vite、Tailwind CSS 和 shadcn/ui 组合构建通过，并锁定 assistant-ui 精确版本。
- 使用 Nexus 自有 REST/SSE 测试服务器验证提交、Streaming、取消、重试、错误、断线、事件去重和 `Last-Event-ID` 续传。
- Tool UI、Inline Approval 和 Action 只调用 Nexus API；伪造客户端状态不得绕过后端权限或业务状态机。
- 未授权 Tool Result、隐藏思维链、完整 Prompt、Secret、L2/L3 原文不得进入 DOM、浏览器日志、遥测或持久缓存。
- Thread、Composer、附件、键盘、焦点、ARIA、屏幕阅读、zh-CN、响应式和视觉回归通过。
- 上游升级必须运行上述完整矩阵，不得只以 TypeScript 编译通过作为兼容证明。

## 3. 必须的安全矩阵

每个资源对五角色、同/异 Department、同/异 Workspace、软删除/归档状态执行允许/拒绝测试。至少覆盖：

- Resource metadata/content/download。
- Chunk/RAG/Evidence。
- Task/SSE/取消/重试。
- Artifact preview/download/version/approval。
- Audit 查询/导出。
- Model/Context/Embedding Policy。
- Coding Project/Repository/Plan/ChangeSet/Preview/Release/Deployment/Domain/Runtime Log。
- Production Authorization与Security Exception职责分离。

任何越权成功均为 P0 阻断。

## 4. AgentScope 集成测试

- 使用 AgentScope 2.0.1 真实 API，不使用自建 Runtime Fake 代替关键测试。
- 测试官方 DeepSeek Provider 和 OpenAI-compatible 私有 Endpoint。
- 覆盖 streaming、tool call、structured output、cancel、retry、state recovery、subagent/review event。
- 验证 Task/Execution/Trace 关联和事件映射。
- AgentScope 升级运行完整回归。

## 5. Golden Dataset Eval

### 平台开发期

可使用明确标记的合成 Fixture 验证框架、Schema 和失败路径，但不得当成生产质量证明。

### Production Readiness

必须由试点企业提供脱敏真实数据、三类模板、指标口径和 Golden Result。Eval 包含：

- Calculation exactness。
- Critical fact/evidence coverage。
- Hallucinated number/citation = 0。
- Conflict/insufficient evidence behavior。
- Retrieval quality and ACL leakage = 0。
- Report structure、业务评分和人工加工时间。

Golden Dataset 未到位前 CI 可运行 Synthetic Suite，但 Production Gate 必须显示 BLOCKED，不能跳过后显示绿色。

## 6. Renderer Test

- 在 Windows Runner/受控测试机执行 Build Test；真实 WPS 自动化测试必须在安装了目标版本 WPS 的自托管 Runner 执行。
- 使用企业模板或结构等价模板验证 Logo、字体、页眉页脚、母版、表格、公式和 Chart。
- 输出重新打开、编辑、保存和 Hash/ZIP package 完整性检查。
- 覆盖进程崩溃、超时、重启、重复任务、Worker 离线和 DLQ。

## 7. Performance

- 500 用户目录数据。
- 20 在线用户混合行为。
- 5 并发 Agent Task。
- 5,000 Resource Workspace 与 100 GB 元数据/上传策略。
- 1,000,000 Chunk 检索。
- 标准任务 30 分钟、复杂任务 60 分钟目标。
- 5并发Sandbox、3并发Build、2并发Deployment、20在线Preview和20 Production Application。

性能测试记录环境、模型、数据、版本和瓶颈，禁止脱离硬件/模型给出无条件结果。

## 8. GitHub Actions 门禁

Pull Request 必须通过：

- Backend format/static analysis/build/unit/integration/ArchUnit。
- Frontend lint/typecheck/unit/component/build；包括 `@gwnexus/assistant-ui` Custom Runtime、SSE恢复、Tool UI安全、a11y和视觉回归矩阵。
- OpenAPI/Event/Schema compatibility。
- MySQL Migration clean install/upgrade。
- `nexus-edge-ctl`在干净Ubuntu 24.04环境完成preflight/install/status/doctor，重复install保持幂等。
- 在线与完全离线安装包分别执行E2E；离线测试期间检测并阻止意外公网访问。
- 安装阶段故障注入覆盖镜像损坏、端口占用、磁盘不足、Secret缺失、Migration失败和健康检查失败。
- 升级前备份、兼容迁移、健康验收、可允许回退和必须前向修复的路径均有测试。
- Windows Renderer Installer执行安装、升级、修复、卸载和签名验证；卸载默认不删除企业数据。Desktop Installer测试进入V1.1。
- DM8 compatibility pipeline（有可用环境时为认证门禁）。
- AgentScope integration suite。
- Security/permission suite。
- Secret scan、dependency vulnerability、license scan。
- Docker image build、SBOM 和 image scan。
- Renderer Windows build；WPS compatibility 使用受控 self-hosted runner。
- `nexus.yaml` JSON Schema、Node 22/24、npm/pnpm/yarn和STATIC/SSR Contract Matrix。
- Sandbox Broker、gVisor、资源超限、销毁和恢复测试。
- Envoy Egress的SSRF、DNS Rebinding、Redirect、直接IP、SSH目标、Metadata和QUIC绕过测试。
- Rootless BuildKit、Harbor、Trivy、Gitleaks、Semgrep、Cosign和Provenance集成测试。
- Preview TTL、分享、访问隔离和Critical Gate测试。
- Production权限、Gate时效、Security Exception、Signature、Digest和Blue-Green/自动回滚测试。
- Traefik无Docker Socket、ForwardAuth、WebSocket/SSE、系统域名和Custom Domain ACME测试。
- Host Agent mTLS、签名Intent、防重放、Node离线和多节点调度测试。

禁止把需要真实 Secret 的测试放到来自不可信 Fork 的流水线。

## 9. Enterprise Pilot Gate

- ≥20 次完整任务。
- 成功率 ≥90%。
- 标准/复杂时间目标通过。
- 关键事实/数字追溯门槛通过。
- 人工加工时间降低 ≥70%。
- 满意比例 ≥80% 或平均 ≥4/5。
- 越权、L3 外发、关键审计缺失均为 0。

所有结果形成签字验收记录，单次 Demo 成功不构成通过。

## 10. Coding Workspace Eval

### 10.1 真实材料

至少两个脱敏真实企业Repository：一个静态、一个SSR；提供真实改造任务、Acceptance Criteria、测试方式、部署配置、非生产Secret和人工认可结果。纯平台样例不能替代此Gate。

### 10.2 任务完成判定

- Coding Plan已批准。
- Build成功且既有测试无回归。
- 变更相关新测试通过。
- ChangeSet/Diff完整。
- 无未处理P0安全风险。
- 缺少测试/环境时必须为`VALIDATION_INCOMPLETE`，不得计入成功闭环。

### 10.3 Enterprise Pilot

- ≥20次完整Coding闭环，成功率≥90%。
- 新建/导入各≥5次。
- React/Vue/Next.js/Nuxt及GitHub+一个通用Git Provider覆盖。
- 自定义域名≥5次、Production发布≥10次、回滚≥5次。
- Commit/Artifact/Image不一致、越权、门禁绕过、Production Secret泄漏、核心网络横向访问均为0。
- Sandbox P95≤60秒、Preview≤3分钟、Production≤5分钟、Rollback≤2分钟、证书激活≤10分钟。

## 11. Coding Security Red Team

必须包含恶意Repository/Dependency/Dockerfile/Build Script测试：

- 读取Host文件、Socket、Environment和Service Account Token；
- 访问Core/DB/Redis/MinIO/AgentScope/Renderer/Harbor管理端；
- SSRF到Metadata/私网、DNS Rebinding、Redirect和直接IP；
- Fork Bomb、磁盘填满、日志洪泛、网络洪泛和僵尸进程；
- Secret写入Git、Build Layer、SBOM、Log、Agent Context和Artifact；
- 伪造Commit/Signature/Provenance、复用过期Gate或跨Project Pull Image；
- 绕过Approver或用System Admin直接Production。

任何成功突破P0安全不变量均阻止V1验收。
