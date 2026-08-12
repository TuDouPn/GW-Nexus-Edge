# DEV-0004 — AgentScope Recovery Capability 核验报告

> 状态：**PARTIALLY_VERIFIED**（官方 Recovery 能力组合已实证；任意崩溃点/Tool 栈/Token 级续跑 NOT_VERIFIED/NOT_SUPPORTED；
> 真实 Provider BLOCKED_BY_CREDENTIAL；G-03 保持 PARTIAL）
> 日期：2026-08-12
> 分支：`agent/DEV-0004-agentscope-checkpoint-capability`
> 关联：ADR-0009（Proposed）、G-03（PARTIAL）、OQ-007（Checkpoint 待评审）
> 证据来源：AgentScope 2.0.1 官方 Maven Artifact + 官方 sources jar + 可复现实验（真实 Redis Testcontainers / 真实临时目录）

---

## 结论总览（能力分级 + V1 产品承诺）

| 能力项 | 技术状态 | V1 产品承诺 |
|---|---|---|
| 跨 call 的 AgentState 恢复 | **VERIFIED**（CP-2） | IN_SCOPE（经营分析会话恢复） |
| 优雅 session interrupt 后 call 结束保存 AgentState、下一 call 恢复上下文 | **VERIFIED**（CP-3，不归因 bindStateSaver） | IN_SCOPE（业务步骤级恢复） |
| shutdownInterrupted 字段持久化 | **VERIFIED**（CP-4）；自动续跑 **NOT_VERIFIED** | 字段属内部标记，不作为 V1 承诺 |
| Local/Redis SandboxSnapshot **payload 持久化往返** | **VERIFIED**（CP-5/CP-6） | 不承诺（Snapshot 非 V1 经营分析方案） |
| 完整 Sandbox 文件系统**跨 call 自动恢复** | **NOT_VERIFIED** | NOT_COMMITTED（Coding 权威恢复 = Commit/Patch 重放） |
| 当前 Tool 调用栈恢复 | **NOT_VERIFIED**（无官方明确"不支持"，不得据此标 NOT_SUPPORTED） | OUT_OF_SCOPE |
| 任意进程崩溃点精确恢复 | **NOT_VERIFIED**（同上） | OUT_OF_SCOPE |
| Token 级断点恢复 | **NOT_VERIFIED**（同上） | OUT_OF_SCOPE |
| Nexus 业务步骤级恢复 | **Nexus 职责**（非 AgentScope Checkpoint API） | IN_SCOPE（MySQL 权威 + AgentStateStore 会话恢复） |

> 说明：Tool 栈/任意崩溃点/Token 级续跑——官方未明确声明"不支持"，故技术事实统一标记 **NOT_VERIFIED**；
> V1 产品承诺列另标 **OUT_OF_SCOPE / NOT_COMMITTED**。**不得用"不属于 V1 承诺"反推官方技术能力为 NOT_SUPPORTED。**

---

## CP-1：精确依赖与源码证据（VERIFIED）

- **依赖闭包（`dependency:tree`，2026-08-12）**：`io.agentscope:agentscope-core:2.0.1`、
  `agentscope-harness:2.0.1`、`agentscope-extensions-model-openai:2.0.1`（adapter + compat）、
  `agentscope-extensions-redis:2.0.1`（adapter）。
- **sources jar（Maven Central 下载，非 main/latest 分支）**：四个 Artifact 的 2.0.1 sources jar 均下载并解压。
- **关键源码位置（本次实证引用）**：
  - `io/agentscope/core/state/AgentState.java`：字段 sessionId/userId/summary/context/replyId/curIter/
    `shutdownInterrupted`（`@JsonProperty("shutdown_interrupted")`）/permissionContext/toolContext/tasksContext/
    planModeContext；`transient volatile InterruptControl interruptControl`（不序列化）。
  - `io/agentscope/core/ReActAgent.java`：`shutdownManager.bindStateSaver(... store.save(userId, sessionId,
    "agent_state", agentState))`（中断时持久化精确 per-(userId, sessionId) 会话，注释"persist that session
    directly"）；`saveStateToSession(...)`（call 结束保存，同步 toolkit activeGroups）。
  - `io/agentscope/core/shutdown/GracefulShutdownManager.java`：stateSavers 按 agentId 注册；shutdown 时调用
    saver 持久化 AgentState；检测 shutdown 中断标志供客户端重试（避免重复用户 prompt）。
  - `io/agentscope/harness/agent/sandbox/snapshot/LocalSandboxSnapshot.java`：persist 原子写
    `{basePath}/{id}.tar`；restore 读同一文件；`LocalSnapshotSpec(Path/String basePath)`。
  - `io/agentscope/extensions/redis/snapshot/RedisSnapshotSpec.java`：`RedisSnapshotSpec(UnifiedJedis, keyPrefix,
    Integer ttlSeconds)` extends RemoteSnapshotSpec（委托 RedisRemoteSnapshotClient）。
- **未扫描的官方 Artifact**（未解析进本构建闭包）不声称"不存在相关能力"。

## CP-2：AgentState 跨 call / 跨实例恢复（VERIFIED）

- **Artifact/源码**：agentscope-core 2.0.1 `AgentState`/`AgentStateStore`；sources 见 CP-1。
- **实验**：`./mvnw -pl nexus-edge-agentscope-adapter test -Dtest=RecoveryCapabilityEvidenceTest`
  → `agentStateFieldPersistenceRoundTrip`。
- **断言**：AgentState（含 MARKER context + shutdownInterrupted=true）经官方 JsonFileAgentStateStore 保存 →
  新 store 实例读回 → context 含 MARKER（跨实例恢复）；RedisAgentStateStoreFactory 版同断言；
  键 `nexus:test:agentscope-session:u-cp2/s-cp2:agent_state`（官方键结构）；`interruptControl` 为 transient
  （反射断言）。
- **结果**：5/5 通过。
- **字段范围**：AgentState 保存 per-session 可变状态（会话消息/上下文、replyId、curIter、shutdownInterrupted、
  权限/Tool/Task/Plan 上下文）；**不保存**：transient InterruptControl（运行时中断控制）、任意进程崩溃点的
  JVM 堆/线程栈、当前 Tool 调用栈。
- **适用边界**：同 scoped (userId, sessionId) + 同一 AgentStateStore 实现即可跨实例恢复会话；跨实现（JsonFile→Redis）
  不保证（序列化契约同，但未做混合验证）。
- **影响**：经营分析会话恢复已满足（DEV-0003 Redis 已验证）。

## CP-3：优雅 session interrupt（VERIFIED call 结束保存 + 下一 call 上下文恢复；不归因 bindStateSaver）

- **Artifact/源码**：agentscope-core 2.0.1 `ReActAgent`（interrupt）与 `GracefulShutdownManager`（bindStateSaver
  存在，源码实证）。**归因（评审修正 2）**：本实验验证的是 **session interrupt 路径**
  （cancelExecution → agent.interrupt → call 结束 → AgentState 持久化 → 下一 call 恢复上下文）；
  **具体保存由 interrupt handler 还是 call-finalization 路径完成，本实验不归因**；
  **不得声称 bindStateSaver（process graceful shutdown 路径）是本次 interrupt 触发的保存路径**
  （无调用证据）。进程 shutdown 的自动恢复：**NOT_VERIFIED**。
- **实验**：`interruptPersistsAgentStateAndNextCallRecovers`。
- **断言**：start（endpoint delay）→ cancelExecution（interrupt）→ CANCELLED → scoped slot 中 agent_state
  存在（call 结束保存）→ resume 同会话 → 模型请求含首次 MARKER（下一 call 恢复上下文）。
- **结果**：通过。
- **区分**：**"优雅中断后下一次 call 恢复上下文"= VERIFIED**；**"在原调用栈原位置继续执行"= NOT_VERIFIED**。

## CP-4：shutdownInterrupted（字段持久化 VERIFIED；自动续跑 NOT_VERIFIED）

- **源码**：`AgentState.shutdownInterrupted`（boolean）+ `@JsonProperty("shutdown_interrupted")`（可序列化）；
  Builder `shutdownInterrupted(boolean)`；getter `isShutdownInterrupted()`；GracefulShutdownManager 读取该标志
  供客户端重试检测（避免重复 prompt）。
- **实验**：`agentStateFieldPersistenceRoundTrip`——JsonFile 与 Redis 往返后 `isShutdownInterrupted()` 仍 true。
- **结果**：通过。
- **结论**：**字段持久化 VERIFIED**；**自动续跑 NOT_VERIFIED**（本实验只证明字段往返，不证明字段触发任何
  自动续跑执行）。

## CP-5：LocalSandboxSnapshot payload 持久化往返（VERIFIED；完整工作区跨 call 自动恢复 NOT_VERIFIED）

- **Artifact/源码**：agentscope-harness 2.0.1 `LocalSnapshotSpec(Path basePath)`/`LocalSandboxSnapshot`
  （persist 原子写 `{basePath}/{id}.tar`；restore 读回）。
- **实验**：`localSandboxSnapshotRoundTrip`——真实临时目录，persist(payload 字节流) → restore() → 字节与
  SHA-256 一致；isRestorable true；目标文件存在。
- **结果**：通过。
- **证据等级（评审修正 1）**：本实验只验证 **Snapshot 存储原语的 payload persistence round-trip**
  （任意字节流，非"真实 tar 工作区归档"）；**未启动真实 Sandbox、未验证 Sandbox Manager 在下一 call
  自动恢复工作区** → **完整 Sandbox 文件系统跨 call 自动恢复 = NOT_VERIFIED**。官方文档/源码表明其设计
  用途为沙箱快照（Coding 取向），但本 Work Item 不新增完整 Docker Sandbox 集成测试（避免扩大到 G-09）。

## CP-6：RedisSnapshotSpec payload 持久化往返（VERIFIED；完整工作区跨 call 自动恢复 NOT_VERIFIED）

- **Artifact/源码**：agentscope-extensions-redis 2.0.1 `RedisSnapshotSpec(UnifiedJedis, keyPrefix, ttlSeconds)`。
- **实验**：`redisSnapshotRoundTrip`——真实 Redis（Testcontainers redis:7.4.2），persist(payload) →
  isRestorable → restore → 字节与 SHA-256 一致；JedisPooled 经 try-with-resources 关闭（评审修正 4）。
- **结果**：通过。
- **证据等级**：同 CP-5——仅 Snapshot 存储原语 payload 往返 VERIFIED；完整工作区跨 call 自动恢复
  NOT_VERIFIED；不将 RedisSnapshot 设为大型 Node Workspace 的 V1 默认方案（CP-9）。

## CP-7：双向数据面隔离（VERIFIED；黑盒观察说明）

- **实验**：`snapshotVsAgentStateDataPlaneIsolation`——
  - 方向 A：persist Snapshot → 删除 Snapshot 键 → **官方 AgentStateStore.get 真实读取** AgentState 且含 Marker
    （非仅 exists）；
  - 方向 B：重新 persist Snapshot（新 payload）→ 删除 agent_state 键 → Snapshot **isRestorable() +
    restore 内容与原 payload / SHA-256 一致**。
- **结果**：通过。
- **黑盒说明（评审修正 3）**：Redis KEYS/DEL 仅用于测试观察与受控清理，**不是生产契约**；生产不依赖
  未公开内部键（AgentState 键为官方键结构，快照键为官方 RedisSnapshotSpec keyPrefix）。

## CP-8：续跑能力边界（技术状态 + V1 承诺分列）

| 项 | 技术状态 | V1 承诺 |
|---|---|---|
| 跨 call 会话恢复 | **VERIFIED** | IN_SCOPE |
| 优雅 interrupt 后上下文恢复 | **VERIFIED** | IN_SCOPE |
| shutdownInterrupted 标记 | **字段持久化 VERIFIED / 自动续跑 NOT_VERIFIED** | 不作承诺 |
| 沙箱文件系统恢复 | **payload 往返 VERIFIED / 跨 call 自动恢复 NOT_VERIFIED** | NOT_COMMITTED |
| 当前 Tool 调用栈恢复 | **NOT_VERIFIED** | OUT_OF_SCOPE |
| 任意进程崩溃点恢复 | **NOT_VERIFIED** | OUT_OF_SCOPE |
| Token 级断点恢复 | **NOT_VERIFIED** | OUT_OF_SCOPE |

> 说明：官方未对 Tool 栈/崩溃点/Token 级明确声明"不支持"，故统一技术事实为 **NOT_VERIFIED**；
> V1 产品承诺列另标 **OUT_OF_SCOPE / NOT_COMMITTED**；**不得用"不属于 V1 承诺"反推官方能力为
> NOT_SUPPORTED**（评审修正 5）。

## CP-9：Coding 权威恢复边界（对照 22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md §6）

| 维度 | 结论 |
|---|---|
| 权威输入 | **Base Commit**（不可变，22 §6） |
| 权威变更 | **Commit/Patch/ChangeSet**（Hash 校验，22 §6） |
| 恢复方式 | **创建全新 Sandbox 后进行 Hash 校验和重放**（22 §6；不恢复旧容器/可变文件系统） |
| SandboxSnapshot 定位 | **仅可作为同一 Attempt 内跨 call 的可选优化**（性能/工作区延续），**不是代码权威源、
  业务状态权威源或 Production Artifact** |
| RedisSnapshot 定位 | **不得保存大型 Node Workspace 作为 V1 默认架构** |

## 对经营分析与 Coding Workspace 的影响

- **经营分析**：不需要 SandboxSnapshot/分布式执行；会话恢复已由 AgentStateStore（Redis，DEV-0003）满足；
  业务步骤级恢复边界不变（03 §6）。
- **Coding Workspace**：权威恢复仍为 Base Commit + Commit/Patch 重放；SandboxSnapshot 仅可选优化；
  本报告不改变 22 §6 契约。

## 测试证据

| 测试类 | 用例 | 结果 |
|---|---|---|
| RecoveryCapabilityEvidenceTest（DEV-0004 新增） | 5（CP-2~CP-7 覆盖；CP-1/8/9 为源码+文档证据） | ✅ 真实 Redis + 真实临时目录 |
| 既有 AgentScope 测试（DEV-0001/0003，含 firstAttempt 适配） | 60 | ✅ 无回归 |
| DEV-0002 compatibility-test | 14 | ✅ 无回归 |
| **合计（默认构建）** | **79** | **0 失败** |

`./mvnw clean verify`：EXIT=0，BUILD SUCCESS，DuplicateJsonObject 0；`git diff --check` 无输出。
