# ADR-0006 — 采用 AgentScope 2.0.1 官方 BOM 并内嵌 Harness/Core（第二轮评审修订版）

> 状态：**Proposed（草案，待产品架构负责人批准）**
> 日期：2026-08-10（首次）；2026-08-10（第一轮修订）；2026-08-10（第二轮修订）
> 决策人：（待产品架构负责人）
> 关联决策/Issue：DEV-0001、G-01、G-03、OQ-007
> 关联 ADR：ADR-0001、ADR-0002、ADR-0004

## 背景

DEV-0001 验证 Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 组合（G-01）、盘点能力
（G-03）、明确 Recovery 边界（OQ-007）。两轮评审均为 CHANGES_REQUESTED；本修订版
落实第二轮 P0×3 + P1×6 全部要求。

## 决策

1. **依赖管理**：采用 `io.agentscope:agentscope-bom:2.0.1` 为全部 AgentScope 模块版本唯一来源。
2. **集成方式**：不采用官方 `agentscope-spring-boot-starter:2.0.1`（编译依赖 SB 4.0.1）；
   手工 Bean 装配内嵌 Harness/Core（SB 4.1.0 已验证）。
3. **Harness 安全边界（P0-1）**：业务 Agent 构建时显式禁用
   `FilesystemTools / ShellTool / Subagents / DynamicSubagents / DynamicSkills /
   DefaultWorkspaceSkills` 并 `enableMetaTool(false)`；**构建后**以
   `getToolkit().getToolNames()` 为权威工具面断言，禁止危险工具。
4. **单一执行源（P0-2）**：`streamEvents()` 为唯一执行源；一次订阅同时完成事件/状态/
   结果/失败/取消；`streamExecutionEvents` 返回执行前建立的 Publisher，绝不二次执行。
5. **Secret 边界（P0-3）**：main 定义 `SecretResolver` 端口；apiKeyReference 经其解析
   临时值；生产装配为必填 Bean，main 无伪实现、无默认 Prompt；测试值仅存 test。
6. **Trace（P1-4）**：`startExecution` 真实链路创建 OTel span 并返回 traceId。
7. **Cancel（P1-5）**：终态由真实中断事件确认；COMPLETED/FAILED/CANCELLED 互斥。
8. **Recovery（P1-6）**：resume 基于官方 State Store 同会话恢复，验证上下文内容；
   `ModelRegistry.reset()` 后重注册，不依赖静态残留。
9. **标识模型（P1-7）**：`AgentExecutionReference` 四标识：taskId / taskAttemptId(UUIDv7)
   / agentId(AgentScope) / traceId(OTel)。**agentId 不是"官方 Execution ID"**——
   AgentScope 2.0.1 无独立 Execution ID 概念。
10. **事件契约（P1-8）**：Publisher 执行前建立；事件携带 eventId；**Last-Event-ID 续传
    归后续持久化业务事件层**，本 ADR 不承诺续传能力。

## 长期适配性

- BOM 统一版本来源；starter 成熟后平滑切换（需新 ADR）。
- 安全不变量显式收敛（disable* 全开），不随 SDK 默认漂移。
- Port/Adapter 分离；标识模型清晰，未来官方新增 Execution ID 可无缝映射。
- SecretResolver 端口对齐 07 §7 长期架构。

## 候选方案

- 官方 starter：SB 4.0.1 未验证；拒绝（当前阶段）。
- 业务 Agent 保留默认工具面：扩大攻击面；拒绝。
- call + stream 双执行：浪费调用且语义分裂；拒绝。
- apiKeyReference 直接当 apiKey：泄露 Secret 语义；拒绝。
- Redis 默认恢复存储：`session-redis` 无 2.0.1；未验证前拒绝。

## 影响

- 领域：新增 Port（含 SecretResolver/TaskAttemptId），无业务 Schema。
- 安全：工具面收敛；Secret 只走 Reference；终态互斥。
- 测试：17 个真实兼容测试（`./mvnw clean verify` 全绿）。
- 迁移：无既有实现；后续切换需新 ADR。

## 验证

- G-01：通过（`./mvnw clean verify` 17/17）。
- G-03：**PARTIAL**（能力清单已形成；真实 Provider/Redis/生产 OTel 注入待补）。
- OQ-007：**PARTIAL**（JsonFile 恢复已验证；Redis/Checkpoint 待评审）。
- P0-1~P1-8 全部验证（证据见 `docs/dev-0001/COMPATIBILITY_REPORT.md` §2/§4）。

## 未解决问题

- 官方 starter 与 SB 4.1.0 兼容性（G-02 复审）。
- RedisAgentStateStore 生产采用（OQ-007 评审）。
- OTel traceId 生产链路注入（12 §6）。
- Last-Event-ID 续传（后续持久化业务事件层）。
