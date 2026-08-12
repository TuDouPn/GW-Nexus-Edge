# ADR-0009 — AgentScope Recovery Capability 边界（Checkpoint 能力核验）

> 状态：**Proposed（草案，待产品架构负责人批准）**
> 日期：2026-08-12（草案）
> 决策人：（待产品架构负责人）
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
   - `interrupt`（优雅中断）：ReActAgent `interrupt(ctx)` 存在（javap 实证），中断后部分状态保存与
     下一 call 行为**待实证**；
   - `SandboxSnapshot`：沙箱文件系统快照（`persist(InputStream)/restore()`），Local/Remote/Redis 实现；
     真实 persist/restore 行为**待实证**；
   - `DistributedStore`：远程执行组合（远程文件系统/消息总线/异步工具/沙箱快照），Coding 取向。
3. **Nexus 业务步骤级恢复边界**：Nexus Task/TaskAttempt（MySQL 权威）+ AgentStateStore 会话恢复组合
   承担业务步骤级恢复（03 §6）；**不属于 AgentScope Checkpoint API**。
4. **经营分析恢复策略**：本地 HarnessAgent + AgentStateStore 会话恢复；**不需要沙箱快照**。
5. **Coding Workspace 恢复策略**：**权威恢复 = 全新 Sandbox + 不可变 Base Commit + Hash 校验的
   Commit/Patch/ChangeSet 重放**（22 §6）；**SandboxSnapshot 仅作为同一 Attempt 内跨 call 的性能优化
   和工作区延续能力，不是代码权威源、业务恢复权威源或 Production Artifact**；**不得让 RedisSnapshot
   保存大型 Node Workspace 成为 V1 默认架构**。
6. **Token/Tool 栈/任意崩溃点续跑不属于 V1 承诺**：模型 Token 断点、当前 Tool 调用栈、任意进程崩溃点
   的精确续跑未被证明且不属于 V1（NOT_SUPPORTED/NOT_VERIFIED）。
7. **禁止 Nexus 自研第二套 Agent Checkpoint Runtime**：官方能力不足时如实标记 NOT_SUPPORTED/BLOCKED，
   恢复退化为业务步骤级；不得自行实现 Checkpoint 机制。

## 验证

- 依赖闭包扫描（dependency:tree，2026-08-12）：agentscope-core/harness/extensions-model-openai/
  extensions-redis 均 2.0.1；无独立 Checkpoint API（jar 扫描）。
- 实现阶段可复现实验：CP-1~CP-9（跨 call AgentState 恢复、interrupt 部分状态、shutdownInterrupted、
  Local/Redis SandboxSnapshot 往返、数据面隔离、崩溃点/Tool 栈/Token 续跑不支持证明、Coding 权威恢复边界）。
- 无法实证项标记 NOT_VERIFIED/BLOCKED，不写成 VERIFIED；不用 Mock 冒充官方能力。

## 影响

- 修正 03 §6 / 08 §9 "AgentScope 保存官方 Execution Checkpoint"未经实证表述（ADR 批准后原子同步）。
- G-03：Checkpoint"能力盘点"子项完成后可关闭；真实 Provider 仍 BLOCKED_BY_CREDENTIAL → G-03 保持 PARTIAL。
- OQ-007：Checkpoint 部分以本 ADR 定级（待评审/未证明）。

## 未解决问题

- interrupt 后部分状态保存、shutdownInterrupted 持久化、SandboxSnapshot 真实行为：待实证（CP-3~CP-6）。
- 官方"Execution Checkpoint"表述在 03/08 的落点：待 ADR 批准后原子同步修正。

## 待批准后原子同步

- 03_SYSTEM_ARCHITECTURE.md §6、08_AGENTSCOPE_AND_SKILL.md §9：修正 Checkpoint 表述。
- 16_GLOSSARY.md：如需新增术语（沙箱快照 vs 会话状态）。
- 17_IMPLEMENTATION_READINESS_CHECKLIST.md：G-03 Checkpoint 子项状态（如实）。
- docs/governance/OPEN_QUESTIONS.md：OQ-007 Checkpoint 部分（如需）。
