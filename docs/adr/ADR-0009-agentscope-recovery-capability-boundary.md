# ADR-0009 — AgentScope Recovery Capability 边界（Checkpoint 能力核验）

> 状态：**Accepted（2026-08-12 经产品架构负责人批准）**
> 日期：2026-08-12（草案）；2026-08-12（批准）
> 决策人：产品架构负责人（2026-08-12 批准）
> 批准范围：AgentScope 2.0.1 Recovery Capability 边界（无独立公开 Execution Checkpoint API；官方恢复能力
> 组合 AgentStateStore/AgentState/interrupt/SandboxSnapshot/DistributedStore；Nexus 业务步骤级恢复边界；
> 经营分析与 Coding Workspace 恢复策略；Token/Tool 栈/任意崩溃点续跑 NOT_VERIFIED + OUT_OF_SCOPE；
> 禁止 Nexus 自研第二套 Agent Checkpoint Runtime）。
> 关联决策/Issue：DEV-0004、G-03、OQ-007
> 关联 ADR：ADR-0001、ADR-0006、ADR-0008（Accepted）；ADR-0007（Proposed）

## 背景

现有 Accepted Blueprint 含 **"AgentScope 保存官方 Execution Checkpoint"** 这一**未经实证且可能错误**
的表述（03_SYSTEM_ARCHITECTURE §6、08_AGENTSCOPE_AND_SKILL §9）。DEV-0004 对当前 Maven 构建解析的
AgentScope 2.0.1 依赖闭包（agentscope-core/harness/extensions-model-openai/extensions-redis 均 2.0.1）
完成实证，需要修正 Recovery/Checkpoint 边界。

## 决策

1. **不存在独立公开 Execution Checkpoint API**：AgentScope Java 2.0.1 未发现独立公开的
   Execution Checkpoint API（依赖闭包扫描实证）；官方恢复能力由 **AgentStateStore、AgentState、
   优雅中断状态、SandboxSnapshot 与 DistributedStore 组合提供**。
2. **组件职责**：
   - `AgentStateStore`：Session/Agent State 持久化（会话恢复；Redis 实现 DEV-0003 已验证）；
   - `AgentState`：**按 userId/sessionId 寻址的 per-session 可变状态；每次 call 开始时加载、
     结束或受支持的中断路径中保存**。不得把 AgentState 描述为 Task、TaskAttempt 或某次模型调用的
     独立 Execution Checkpoint；
   - `interrupt`（优雅中断）：ReActAgent `interrupt(ctx)` 存在（javap 实证）。
     **Session interrupt 后结束本次调用、AgentState 持久化、下一次调用恢复上下文：VERIFIED**（CP-3，
     不归因 bindStateSaver）；**JVM/进程终止后的自动恢复：NOT_VERIFIED**；
   - `SandboxSnapshot`：沙箱文件系统快照（`persist(InputStream)/restore()`），Local/Remote/Redis 实现。
     **Snapshot payload 的 persist/restore 及 Local/Redis 往返：VERIFIED**（CP-5/CP-6）；
     **完整 Sandbox 文件系统跨调用自动恢复：NOT_VERIFIED**（未启动完整 Sandbox）；
   - `DistributedStore`：远程执行组合（远程文件系统/消息总线/异步工具/沙箱快照），Coding 取向。
3. **Nexus 业务步骤级恢复边界**：Nexus Task/TaskAttempt（MySQL 权威）+ AgentStateStore 会话恢复组合
   承担业务步骤级恢复（03 §6）；**不属于 AgentScope Checkpoint API**。
4. **经营分析恢复策略**：本地 HarnessAgent + AgentStateStore 会话恢复；**不需要沙箱快照**。
5. **Coding Workspace 恢复策略**：**权威恢复 = 全新 Sandbox + 不可变 Base Commit + Hash 校验的
   Commit/Patch/ChangeSet 重放**（22 §6）；**SandboxSnapshot 仅作为同一 Attempt 内跨 call 的性能优化
   和工作区延续能力，不是代码权威源、业务恢复权威源或 Production Artifact**；**不得让 RedisSnapshot
   保存大型 Node Workspace 成为 V1 默认架构**。
6. **Token/Tool 栈/任意崩溃点续跑**：技术证据状态统一为 **NOT_VERIFIED**（官方未明确"不支持"，
   不得以"搜不到类名"标 NOT_SUPPORTED）；V1 产品承诺状态单独为 **OUT_OF_SCOPE / NOT_COMMITTED**；
   **禁止使用 "NOT_SUPPORTED/NOT_VERIFIED" 混合表述**。
7. **禁止 Nexus 自研第二套 Agent Checkpoint Runtime**：官方能力不足时如实标记，恢复退化为业务步骤级；
   不得自行实现 Checkpoint 机制。

## 验证

- 依赖闭包扫描（dependency:tree，2026-08-12）：agentscope-core/harness/extensions-model-openai/
  extensions-redis 均 2.0.1；sources jar 已下载核验（非 main/latest）。
- 可复现实验（真实 Redis Testcontainers / 真实临时目录；`RecoveryCapabilityEvidenceTest` 5/5）：
  - **AgentState 跨 call/跨实例恢复：VERIFIED**（官方 AgentStateStore 序列化往返，含 context/字段）；
  - **session interrupt 后下一 call 上下文恢复：VERIFIED**（cancelExecution→interrupt→call 结束保存→
    下一 call 恢复；**不归因 bindStateSaver**，process graceful shutdown 自动恢复 NOT_VERIFIED）；
  - **shutdownInterrupted 字段持久化：VERIFIED**（JSON 往返）；**自动续跑：NOT_VERIFIED**；
  - **Local/Redis SandboxSnapshot payload 往返：VERIFIED**（存储原语字节 + Hash 一致）；
    **完整 Sandbox 文件系统跨 call 自动恢复：NOT_VERIFIED**（未启动完整 Sandbox）；
  - **Tool 栈、任意崩溃点、Token 级续跑：NOT_VERIFIED**（官方未明确"不支持"，不以"搜不到类名"标
    NOT_SUPPORTED），且 **不属于 V1 产品承诺（OUT_OF_SCOPE / NOT_COMMITTED）**。
- 无法实证项标记 NOT_VERIFIED；不用 Mock 冒充官方能力。

## 影响

- 修正 03 §6 / 08 §9 "AgentScope 保存官方 Execution Checkpoint"未经实证表述（已在 ADR 批准后原子同步）。
- G-03：Checkpoint"能力盘点"子项完成后可关闭；真实 Provider 仍 BLOCKED_BY_CREDENTIAL → G-03 保持 PARTIAL。
- OQ-007：**已 RESOLVED（关联 ADR-0008/0009）**；Checkpoint 部分定级见 §验证（NOT_VERIFIED/NOT_COMMITTED）。

## 未解决问题

- **完整 Sandbox 文件系统跨 call 自动恢复：NOT_VERIFIED**——本 Work Item 仅验证 Snapshot 存储原语
  payload 往返；完整 Docker Sandbox 集成验证属 Coding/G-09 范围，不在本项。
- **进程 graceful shutdown 自动恢复：NOT_VERIFIED**——bindStateSaver 源码存在，无调用证据；
  session interrupt 保存路径 VERIFIED 但不归因。
- **shutdownInterrupted 自动续跑：NOT_VERIFIED**（仅字段持久化 VERIFIED）。
- **Tool 栈 / 任意崩溃点 / Token 级续跑：NOT_VERIFIED（官方未明确"不支持"），OUT_OF_SCOPE（V1 承诺）。**
- 官方"Execution Checkpoint"表述已在 03 §6 / 08 §9 完成原子同步修正（ADR-0009 批准后执行）。

## 已完成原子同步（ADR-0009 批准后执行）

- 03_SYSTEM_ARCHITECTURE.md §6、08_AGENTSCOPE_AND_SKILL.md §9：Checkpoint/Recovery 表述已按本 ADR 修正
  （无独立公开 Execution Checkpoint API；恢复能力组合；Token/Tool 栈/任意崩溃点 NOT_VERIFIED + OUT_OF_SCOPE；
  Coding 权威恢复 = 新 Sandbox + Base Commit + Commit/Patch/ChangeSet 重放）。
- 00_DECISIONS.md：新增 A-008（Recovery Capability 边界）。
- 16_GLOSSARY.md：新增 SandboxSnapshot（沙箱快照）术语。
- 17_IMPLEMENTATION_READINESS_CHECKLIST.md：G-03 行（Checkpoint 能力盘点完成）。
- docs/governance/OPEN_QUESTIONS.md：OQ-007 标记 RESOLVED（关联 ADR-0008/0009）。
