# ADR-0008 — AgentScope Redis Persistence/Recovery 接入与边界

> 状态：**Proposed（草案，待产品架构负责人批准）**
> 日期：2026-08-12（草案）
> 决策人：（待产品架构负责人）
> 关联决策/Issue：DEV-0003、G-03、OQ-007
> 关联 ADR：ADR-0001、ADR-0006（核心版本固定）；ADR-0007（外围依赖冻结，Proposed）

## 背景

G-03（AgentScope 能力盘点）与 OQ-007（Persistence/Recovery 边界）要求实证并接入 AgentScope 2.0.1
官方 Redis 持久化/恢复能力。DEV-0003 在 Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 固定核心下，
以真实 Redis（Testcontainers）完成 12 项验证（C-1~C-12）；真实 Provider 因无凭证标记 BLOCKED_BY_CREDENTIAL。

## 决策

1. **官方组件**：采用 `io.agentscope:agentscope-extensions-redis:2.0.1` 的
   `RedisAgentStateStore`（实现 `AgentStateStore`，与 JsonFile 同接口 drop-in）。
   **生产验证客户端（评审唯一实现复审 P0）：仅 Jedis 7.4.1 经真实 Redis C-1~C-12 验证（VERIFIED）**；
   Lettuce 7.5.2 未验证（NOT_VERIFIED），未来如采用必须单独完成兼容测试并经 ADR 决策；
   **不提供 Lettuce 构造入口，Redis 口令不拼接进 URI**（避免 Secret 出现在连接 URI/日志）。
2. **恢复 Scope（P0 修正）**：`AgentExecutionReference` 显式携带 `tenantId/workspaceId/userId/sessionId`
   （全部 fail-closed）；`startExecution` 先计算 scoped 身份再用 scoped 分区保存执行上下文；
   `resume` 依据四字段重算 scoped 身份、从 scoped Redis slot 读取，并逐项校验
   `taskId/tenantId/workspaceId/userId/sessionId`（缺失/错绑/篡改/跨 Scope 一律 fail-closed）。
   **恢复请求只能由正式业务层基于 MySQL 中已授权的 Task/TaskAttempt 数据构造，不得信任客户端提交的恢复 Scope。**
3. **数据权威边界**：`RedisAgentStateStore` 只作为 AgentScope Session/Agent State 的**运行时持久化机制**；
   Nexus Task/TaskAttempt/版本/状态/恢复授权的**权威源长期属于 MySQL**；`ExecutionContextState` 暂存
   Redis 为**运行期恢复投影与一致性校验副本**，不得称业务权威数据。不新增第二套 Agent 状态机，
   不自行实现 AgentScope Checkpoint。
4. **键与清理**：键结构 `{keyPrefix}{scopedUserId}/{scopedSessionId}:{key}`（官方 sources 实证）；
   keyPrefix = `nexus:{env}:agentscope-session:`，环境名经允许字符校验（防跨环境污染）。
   官方实现**不设 TTL**（sources 实证）；**Session 删除只允许在正式会话生命周期结束、无可恢复 Task、
   满足 Retention Policy 后，由独立生命周期服务调用官方两参 Session delete**——该服务不属于本 Work Item；
   本项**不实现 Task 终态自动 Session 清理**，不直接 EXPIRE/操作官方内部键。
   **实证：`AgentStateStore.delete(userId, sessionId, stateKey)` 为 no-op 默认方法，不可用于单状态键清理。**
5. **Redis/模型异常**：Adapter 只抛脱敏异常并产生 FAILED 执行事件；不得伪装成功，
   不得声称已持久化业务 Task=FAILED。
6. **Provider 边界**：真实 Provider 验证仅当环境存在合法凭证（`DEEPSEEK_API_KEY/BASE_URL` 或
   `OPENAI_API_KEY/BASE_URL`）时执行；无凭证 → **BLOCKED_BY_CREDENTIAL**（独立 Maven Profile，
   fail-closed，不 Mock 冒充、不把跳过写成通过证据）。

## 验证

- `./mvnw clean verify`：全绿（既有 41 + DEV-0003 新增 12 + Provider 配置 7 + DEV-0002 兼容 14）。
- 真实 Redis（Testcontainers GenericContainer redis:7.4.2，Jedis 7.4.1）：C-1~C-12 全部通过——
  scoped 写入、跨实例恢复、Tenant/Workspace 隔离、篡改/缺失/损坏 fail-closed、重复恢复唯一
  taskAttemptId（attemptNo 依引用递增）、标识关联、Redis 中断如实失败且异常不含 Secret（sentinel 脱敏）、
  不误删共享 Session、受控 Session 删除、共享客户端生命周期、keyPrefix 校验。
- Provider 原子配置选择：ProviderSmokeConfigTest 7 用例（DeepSeek/OpenAI 完整组、部分缺失、
  双组未选择、不跨 Provider 混配、错误不含 Secret Value）。
- Provider Smoke：provider-smoke Profile 显式启用且无凭证 → 测试失败（BLOCKED_BY_CREDENTIAL 证据）。
- **C-6 如实口径**：Adapter 每次 resume 产生唯一 taskAttemptId；attemptNo 依传入引用递增；
  同一旧引用重复调用得到相同 attemptNo 但不同 taskAttemptId；真正的并发串行化、幂等与连续
  Attempt 编号由后续 MySQL Task Application Service 负责——本 Adapter 不声称已完成业务 Task 幂等持久化。

## 影响

- 领域：`AgentExecutionReference` 增加 tenantId/workspaceId（恢复 Scope）；Port resume 注释明确
  "恢复请求仅来自 MySQL 授权数据"。
- Adapter：start/resume 使用 scoped 分区；resume 六字段逐项校验；失败路径不遗留未关闭 Agent/span。
- 安全：Redis 键按 scoped 身份 + 环境前缀隔离；Secret 走 Provider/引用；API Key 不落日志/报告。
- 迁移：后续业务模块（Task/Attempt）在 MySQL 建权威表后，经本 ADR 边界构造恢复请求。

## 未解决问题

- **Provider 真实验证**：BLOCKED_BY_CREDENTIAL（需 DEEPSEEK_* 或 OPENAI_* 环境凭证）。
- **Session 生命周期/清理服务**：独立后续工作项（本项不实现）。
- **Checkpoint（沙箱快照）**：V1 经营分析不采用（Coding 取向），OQ-007 该部分待评审。
- **生产 Redis 客户端**：Jedis/Lettuce 组合测试结论将落库（实现阶段以测试证据为准）。

## 待批准后原子同步

- 08_AGENTSCOPE_AND_SKILL.md：Persistence/Recovery 边界（Redis 官方组件、scoped 隔离域、Session 清理责任）。
- 16_GLOSSARY.md：如需新增术语（运行期恢复投影等）。
- 17_IMPLEMENTATION_READINESS_CHECKLIST.md：G-03 行（Redis 已验证；Provider BLOCKED_BY_CREDENTIAL；Checkpoint 待评审）。
- docs/governance/OPEN_QUESTIONS.md：OQ-007 状态更新（Redis 部分 RESOLVED；Checkpoint 待评审），落本 ADR 决策链接。
