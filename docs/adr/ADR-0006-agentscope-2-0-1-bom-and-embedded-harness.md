# ADR-0006 — 采用 AgentScope 2.0.1 官方 BOM 并内嵌 Harness/Core（第五轮评审修订版）

> 状态：**Proposed（草案，待产品架构负责人批准）**
> 日期：2026-08-10（首次）；2026-08-11（第五轮修订）
> 决策人：（待产品架构负责人）
> 关联决策/Issue：DEV-0001、G-01、G-03、OQ-007
> 关联 ADR：ADR-0001、ADR-0002、ADR-0004

## 背景

DEV-0001 验证 Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 组合（G-01）、盘点能力
（G-03）、明确 Recovery 边界（OQ-007）。五轮评审均为 CHANGES_REQUESTED；本修订版
落实第五轮 P0×10 全部要求。

## 决策

1. **依赖管理**：采用 `io.agentscope:agentscope-bom:2.0.1` 为全部 AgentScope 模块版本唯一来源。
2. **集成方式**：不采用官方 `agentscope-spring-boot-starter:2.0.1`（编译依赖 SB 4.0.1）；
   手工 Bean 装配内嵌 Harness/Core（SB 4.1.0 已验证）。
3. **Harness 安全边界**：禁用 FilesystemTools/ShellTool/Subagents/DynamicSubagents/
   DynamicSkills/DefaultWorkspaceSkills/MemoryTools + `enableMetaTool(false)` + 注册
   `OtelTracingMiddleware`；构建后 `getToolkit().getToolNames()` 为权威工具面。
4. **事件流**：使用 Reactor `Sinks.many().replay().limit(256)`（`EventStreams`），
   多订阅者广播、延迟重放、恰好一次、严格顺序、背压/取消正确、有界缓存。
5. **单一终态**：仅 `AGENT_RESULT` 映射业务终态事件，且按 `GenerateReason` 区分
   COMPLETED/CANCELLED；`AGENT_END` 映射 PROGRESS；业务事件终态与 Task 状态一致。
6. **执行上下文（P0-1/P0-2/P0-3）**：`ExecutionContextState` 按 Task 隔离
   （State Store 键含 taskId）；resume 恢复不到上下文/Workspace/Tenant 时 fail-closed；
   cancel 的 RuntimeContext 携带原 Task workspaceId/tenantId。
7. **Secret 边界**：`SecretResolver` 端口解析 Reference；main 无伪实现、无默认 Prompt。
8. **Trace**：每 Task 独立 OTel span，`contextWrite` + `ContextPropagationOperator` 传播；
   `doFinally` 幂等结束 span；`OtelTracingMiddleware` 建立父子 span。
9. **标识模型（P1-7/第四轮）**：`AgentExecutionReference` 四标识 taskId/taskAttemptId/
   agentId/traceId + 事件 replyId。
10. **Tool 链路如实降级（P0-7）**：AgentScope 2.0.1 HarnessAgent 在 `disableMemoryTools`
    + 白名单配置下，工具被决策（POST_REASONING tool_call）但执行阶段（POST_ACTING）
    未触发——**工具执行链路验证受限**。workspace/tenant 验证改走 AgentScope
    RuntimeContext 读取路径。不称"Tool 链路已验证"。
11. **executionId 与 agentId 语义（待同步）**：AgentScope 2.0.1 无独立 Execution ID；
    `AgentEventEnvelope.executionId` 承载 AgentScope AgentId（实例标识）；执行级细粒度
    标识为事件 replyId；Nexus 业务主键为 TaskAttemptId（UUIDv7）。**本决策待批准后
    原子同步 00_DECISIONS、08_AGENTSCOPE_AND_SKILL、16_GLOSSARY、领域字段与 API 契约**
    （第五轮 P0-9：ADR 批准前不单独修改 Accepted Blueprint）。

## 长期适配性

- BOM 统一版本来源；starter 成熟后平滑切换（需新 ADR）。
- EventStreams 基于官方 Reactor，语义由官方保证。
- 标识模型清晰；未来官方新增 Execution ID 可无缝映射。
- SecretResolver 端口对齐 07 §7 长期架构。

## 候选方案

- 官方 starter：SB 4.0.1 未验证；拒绝（当前阶段）。
- 自研事件流：语义难保证；拒绝（改用 Reactor Sinks）。
- 业务 Agent 保留默认工具面：扩大攻击面；拒绝。
- 执行上下文不隔离：同 user/session 不同 Task 覆盖；拒绝（P0-1）。

## 影响

- 领域：新增 Port（SecretResolver/TaskAttemptId/ExecutionContextState），无业务 Schema。
- 安全：工具面收敛；Secret 只走 Reference；执行上下文 fail-closed。
- 测试：35 个真实兼容测试（`./mvnw clean verify` 全绿）。
- 迁移：无既有实现；后续切换需新 ADR。

## 验证

- G-01：通过（`./mvnw clean verify` 35/35）。
- G-03：**PARTIAL**（能力清单已验证大部分；真实 Provider/Redis 待补；Tool 执行链路受限）。
- OQ-007：**PARTIAL**（JsonFile 恢复已验证；Redis/Checkpoint 待评审）。
- 第五轮 P0×10 全部验证（证据见 `docs/dev-0001/COMPATIBILITY_REPORT.md` §2/§4）。
- 已知局限：宿主 filesystem overlay；Tool 执行阶段受限（如实记录）。

## 未解决问题

- 官方 starter 与 SB 4.1.0 兼容性（G-02 复审）。
- RedisAgentStateStore 生产采用（OQ-007 评审）。
- OTel traceId 生产链路注入（12 §6）。
- Last-Event-ID 续传（后续持久化业务事件层）。
- **Tool 执行阶段受限的根因**（HarnessAgent 在 disableMemoryTools + 白名单下 POST_ACTING
  未触发）——需在后续 Phase 3 以真实业务 Tool 验证；当前如实降级。
- **executionId/agentId 语义的 Accepted Blueprint 原子同步**（本 ADR 批准后执行）。
