# ADR-0006 — 采用 AgentScope 2.0.1 官方 BOM 并内嵌 Harness/Core（第七轮评审修订版）

> 状态：**Proposed（草案，待产品架构负责人批准）**
> 日期：2026-08-10（首次）；2026-08-11（第七轮修订）
> 决策人：（待产品架构负责人）
> 关联决策/Issue：DEV-0001、G-01、G-03、OQ-007
> 关联 ADR：ADR-0001、ADR-0002、ADR-0004

## 背景

DEV-0001 验证 Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 组合（G-01）、盘点能力
（G-03）、明确 Recovery 边界（OQ-007）。七轮评审均为 CHANGES_REQUESTED；本修订版
落实第七轮 P0-1/P0-2/P0-3/P0-4/P1-1/P1-2/P1-3/P2 全部要求。

## 决策

1. **依赖管理**：采用 `io.agentscope:agentscope-bom:2.0.1` 为全部 AgentScope 模块版本唯一来源。
2. **集成方式**：不采用官方 `agentscope-spring-boot-starter:2.0.1`（编译依赖 SB 4.0.1）；
   手工 Bean 装配内嵌 Harness/Core（SB 4.1.0 已验证）。
3. **Harness 安全边界**：禁用 FilesystemTools/ShellTool/Subagents/DynamicSubagents/
   DynamicSkills/DefaultWorkspaceSkills/MemoryTools/MemoryHooks + `enableMetaTool(false)`
   + 注册 `OtelTracingMiddleware`；构建后 `getToolkit().getToolNames()` 为权威工具面。
4. **事件流**：使用 Reactor `Sinks.many().replay().limit(256)`（`EventStreams`），
   多订阅者广播、延迟重放、恰好一次、严格顺序、背压/取消正确、有界缓存。
5. **单一终态（P1-2 强化）**：终态转移 `tryTerminal` 返回是否由本次调用完成转移；
   每个 TaskAttempt 只发布一个与最终状态一致的终态事件。取消已确认（CANCELLED）后
   迟到的普通 AgentResult 不得覆盖终态、不得发布 COMPLETED。
6. **终态事件顺序（P1-1）**：先 `tryTerminal` 推进状态、再发布终态事件；
   流完成兜底先更新状态再关闭事件流；SSE 终态回调内 Task 状态已一致。
7. **FAILED 业务事件（P0-3）**：模型/Agent 异常时先 `tryTerminal(FAILED)`，再发布
   **脱敏** FAILED 业务事件（摘要仅含异常类型名，满足 Blueprint `task.failed` 契约），
   然后正常结束事件流；详细异常仅保留内部诊断字段与日志。
8. **Nexus 生成事件 ID（P0-3）**：`AgentEventEnvelope.eventId` 由 Nexus 生成（UUIDv7，
   06 §6 契约格式），不依赖 AgentScope 官方事件 id；供 SSE `Last-Event-ID` 断线续传。
9. **长期 Runtime Identity（P0-1/P0-4）**：Tenant/Workspace/User/Session 业务标识经
   `AgentRuntimeIdentityMapper` 映射为 AgentScope `scopedUserId`/`scopedSessionId`——
   稳定（确定性）、无碰撞（分量独立 Base64URL 编码 + `.` 分隔，编码字母表不含分隔符）、
   路径安全（Base64URL 字母表不含 `/`、`\`、`..`、冒号与空白）。AgentScope
   AgentState/Memory 按 scoped 身份键控隔离；**原始业务标识保留在 RuntimeContext extras**
   （workspaceId/tenantId/sessionId）。`AgentExecutionRequest` 构造器与 Mapper 均
   fail-closed：workspaceId/tenantId 缺失/空白即拒绝；废除第六轮冒号拼接
   `namespacedSessionId`（null→空串 + 冒号方案不可接受）。
10. **Memory Hooks 边界（P0-1）**：`disableMemoryTools` 只移除长期记忆工具，Memory
    Flush/Consolidation Hooks 仍会按 userId 把会话写入共享 `memory/YYYY-MM-DD.md`
    台账（跨 Workspace 相同 user 共享，与 scopedSessionId 无关）。当前业务 Agent
    **不允许自动长期记忆**：`buildSecureAgent` 显式 `disableMemoryHooks()`。未来引入
    自动长期记忆必须先经 Runtime Identity 评审（scopedUserId 隔离台账路径）并更新
    本 ADR，禁止用临时方案掩盖。
11. **Resume 完整校验（P1-1）**：resume 先读取并校验持久化上下文，再解析 Secret/注册
    模型/创建 Agent；恢复内容与引用**逐项比较** taskId/userId/sessionId，错绑或篡改
    时 fail-closed（不得再声称"已完整校验"而不实现比较）。
12. **执行上下文（P0-1/P0-2/P0-3）**：`ExecutionContextState` 按 Task 隔离
    （State Store 键含 taskId）；resume 恢复不到上下文/Workspace/Tenant 时 fail-closed；
    cancel 的 RuntimeContext 携带原 Task 的 scoped Identity + 业务 extras。
13. **Secret 边界**：`SecretResolver` 端口解析 Reference；main 无伪实现、无默认 Prompt。
14. **Trace**：每 Task 独立 OTel span，`contextWrite` + `ContextPropagationOperator` 传播；
    `doFinally` 幂等结束 span；`OtelTracingMiddleware` 建立父子 span。
15. **标识模型（P1-7/第四轮）**：`AgentExecutionReference` 四标识 taskId/taskAttemptId/
    agentId/traceId + 事件 replyId。
16. **Tool 链路（第六轮 P0-1，撤回第五轮误判）**：`@Tool` 注解方法经官方 `ToolMethodInvoker`
    自动注入 `RuntimeContext` 参数，**真实执行**并读取 scoped 身份与业务 extras。
17. **executionId 与 agentId 语义（待同步）**：AgentScope 2.0.1 无独立 Execution ID；
    `AgentEventEnvelope.executionId` 承载 AgentScope AgentId（实例标识）；执行级细粒度
    标识为事件 replyId；Nexus 业务主键为 TaskAttemptId（UUIDv7）。**本决策待批准后
    原子同步 00_DECISIONS、08_AGENTSCOPE_AND_SKILL、16_GLOSSARY、领域字段与 API 契约**
    （第五轮 P0-9：ADR 批准前不单独修改 Accepted Blueprint）。

## 长期适配性

- BOM 统一版本来源；starter 成熟后平滑切换（需新 ADR）。
- EventStreams 基于官方 Reactor，语义由官方保证。
- Runtime Identity 确定性映射可在跨实例/重启/多副本重建，无需共享映射表。
- 标识模型清晰；未来官方新增 Execution ID 可无缝映射。
- SecretResolver 端口对齐 07 §7 长期架构。
- 自动长期记忆能力未来经 Runtime Identity + 新 ADR 评审后引入，不破坏现有隔离边界。

## 候选方案

- 官方 starter：SB 4.0.1 未验证；拒绝（当前阶段）。
- 自研事件流：语义难保证；拒绝（改用 Reactor Sinks）。
- 业务 Agent 保留默认工具面/默认 Memory Hooks：扩大攻击面 + 跨 Workspace 台账共享；拒绝。
- 冒号拼接 sessionId 命名空间（第六轮方案）：null→空串 + 冒号非路径安全、依赖官方
  safeSegment 有损净化、仍不阻止 Memory Hook 台账；拒绝（P0-4 废除）。
- 保留 Memory Hooks 并仅命名空间 sessionId：Memory Flush 仍写共享
  `user-*/memory/YYYY-MM-DD.md`；拒绝（P0-1，必须 disableMemoryHooks）。

## 影响

- 领域：新增 Port（SecretResolver/TaskAttemptId/ExecutionContextState）与 Runtime Identity
  Mapper（Adapter 内部），无业务 Schema。
- 安全：工具面收敛；Memory Hooks 禁用；Secret 只走 Reference；执行上下文与恢复 fail-closed。
- 测试：41 个真实兼容测试（`./mvnw clean verify` 全绿）。
- 迁移：无既有实现；后续切换需新 ADR。

## 验证

- G-01：通过（`./mvnw clean verify` 41/41）。
- G-03：**PARTIAL**（能力清单已验证大部分；真实 Provider/Redis 待补）。
- OQ-007：**PARTIAL**（JsonFile 恢复已验证；Redis/Checkpoint 待评审）。
- 第七轮 P0-1/P0-2/P0-3/P0-4/P1-1/P1-2/P1-3/P2 全部验证（证据见
  `docs/dev-0001/COMPATIBILITY_REPORT.md` §3/§5）。
- 已知局限：宿主 filesystem overlay（如实记录）。

## 未解决问题

- 官方 starter 与 SB 4.1.0 兼容性（G-02 复审）。
- RedisAgentStateStore 生产采用（OQ-007 评审）。
- OTel traceId 生产链路注入（12 §6）。
- Last-Event-ID 续传（后续持久化业务事件层）。
- **executionId/agentId 语义的 Accepted Blueprint 原子同步**（本 ADR 批准后执行）。
- **自动长期记忆（Long-Term Memory）产品决策**：当前禁用；引入时需独立 ADR。
