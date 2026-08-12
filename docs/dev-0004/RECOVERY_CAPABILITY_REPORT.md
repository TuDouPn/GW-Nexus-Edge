# DEV-0004 — AgentScope Recovery Capability 核验报告

> 状态：**PARTIALLY_VERIFIED**（官方 Recovery 能力组合已实证；任意崩溃点/Tool 栈/Token 级续跑 NOT_VERIFIED/NOT_SUPPORTED；
> 真实 Provider BLOCKED_BY_CREDENTIAL；G-03 保持 PARTIAL）
> 日期：2026-08-12
> 分支：`agent/DEV-0004-agentscope-checkpoint-capability`
> 关联：ADR-0009（Proposed）、G-03（PARTIAL）、OQ-007（Checkpoint 待评审）
> 证据来源：AgentScope 2.0.1 官方 Maven Artifact + 官方 sources jar + 可复现实验（真实 Redis Testcontainers / 真实临时目录）

---

## 结论总览（能力分级）

| 能力项 | 状态 |
|---|---|
| 跨 call 的 AgentState 恢复 | **VERIFIED**（CP-2，官方 AgentStateStore + 真实 Redis/JsonFile） |
| 优雅 interrupt 后保存部分状态（agent_state） | **VERIFIED**（CP-3，bindStateSaver 源码 + 运行证据） |
| shutdownInterrupted 字段持久化 | **VERIFIED**（CP-4，字段 JSON 往返）；**自动续跑 NOT_VERIFIED**（未证明） |
| Sandbox 文件系统跨 call 恢复（Local/Redis） | **VERIFIED**（CP-5/CP-6，persist/restore 字节 + Hash 一致） |
| 任意进程崩溃点精确续跑 | **NOT_VERIFIED / NOT_SUPPORTED**（无官方 API；未证明） |
| Token 级断点续跑 | **NOT_SUPPORTED**（非 V1 承诺，03 §6） |
| 当前 Tool 调用栈恢复 | **NOT_VERIFIED**（无源码/实验证据） |
| Nexus 业务步骤级恢复 | **Nexus 职责**（MySQL 权威 + AgentStateStore 会话恢复组合，非 AgentScope Checkpoint API） |

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

## CP-3：优雅 interrupt（VERIFIED 部分状态保存 + 下一 call 上下文恢复；原调用栈续跑 NOT_VERIFIED）

- **Artifact/源码**：agentscope-core 2.0.1 `ReActAgent`/`GracefulShutdownManager`（bindStateSaver）。
- **实验**：`interruptPersistsAgentStateAndNextCallRecovers`。
- **断言**：start（endpoint delay）→ cancelExecution（interrupt）→ CANCELLED → scoped slot 中 agent_state 存在
  （中断路径保存会话，bindStateSaver 源码 + 运行证据）→ resume 同会话 → 模型请求含首次 MARKER
  （下一 call 恢复上下文）。
- **结果**：通过。
- **区分**：**"优雅中断后下一次 call 恢复上下文"= VERIFIED**；**"在原调用栈原位置继续执行"= NOT_VERIFIED**
  （无源码/实验证据支持断点续跑）。
- **影响**：业务步骤级恢复（03 §6）满足；不冒充精确断点续跑。

## CP-4：shutdownInterrupted（字段持久化 VERIFIED；自动续跑 NOT_VERIFIED）

- **源码**：`AgentState.shutdownInterrupted`（boolean）+ `@JsonProperty("shutdown_interrupted")`（可序列化）；
  Builder `shutdownInterrupted(boolean)`；getter `isShutdownInterrupted()`；GracefulShutdownManager 读取该标志
  供客户端重试检测（避免重复 prompt）。
- **实验**：`agentStateFieldPersistenceRoundTrip`——JsonFile 与 Redis 往返后 `isShutdownInterrupted()` 仍 true。
- **结果**：通过。
- **结论**：**字段持久化 VERIFIED**；**自动续跑 NOT_VERIFIED**（本实验只证明字段往返，不证明字段触发任何
  自动续跑执行）。

## CP-5：LocalSandboxSnapshot（VERIFIED）

- **Artifact/源码**：agentscope-harness 2.0.1 `LocalSnapshotSpec(Path basePath)`/`LocalSandboxSnapshot`
  （persist 原子写 `{basePath}/{id}.tar`；restore 读回）。
- **实验**：`localSandboxSnapshotRoundTrip`——真实临时目录，persist(tar 字节流) → restore() → 字节与
  SHA-256 一致；isRestorable true；目标文件存在。
- **结果**：通过。
- **边界**：快照为沙箱工作区归档字节（tar），非 Agent 会话状态；自建实现未使用（官方类直接调用）。

## CP-6：RedisSnapshotSpec（VERIFIED）

- **Artifact/源码**：agentscope-extensions-redis 2.0.1 `RedisSnapshotSpec(UnifiedJedis, keyPrefix, ttlSeconds)`。
- **实验**：`redisSnapshotRoundTrip`——真实 Redis（Testcontainers redis:7.4.2），persist → isRestorable →
  restore → 字节与 SHA-256 一致。
- **结果**：通过。
- **边界**：快照键前缀 `nexus:snap:`（本实验）；**不得将 RedisSnapshot 设为大型 Node Workspace 的 V1 默认方案**
  （Coding 权威恢复 = Base Commit + Commit/Patch 重放，见 CP-9）。

## CP-7：数据面隔离（VERIFIED）

- **实验**：`snapshotVsAgentStateDataPlaneIsolation`——AgentStateStore 键
  `nexus:test:agentscope-session:u-cp7/s-cp7:agent_state` vs RedisSnapshot 键 `nexus:snap:*`：前缀不同；
  删除 Snapshot 键不影响 AgentState；删除 AgentState 不影响 Snapshot。
- **结果**：通过。
- **边界**：两类数据语义/键空间分离；不直接操作或依赖未公开内部键作为生产契约。

## CP-8：续跑能力边界（7 项分列）

| 项 | 状态 | 依据 |
|---|---|---|
| 跨 call 会话恢复 | **VERIFIED** | CP-2（AgentStateStore 官方序列化 + 跨实例） |
| 优雅 interrupt 后上下文恢复 | **VERIFIED** | CP-3（bindStateSaver 保存 + 下一 call 恢复） |
| shutdownInterrupted 标记 | **VERIFIED（字段持久化）/ NOT_VERIFIED（自动续跑）** | CP-4 |
| 沙箱文件系统恢复 | **VERIFIED** | CP-5/CP-6（Local/Redis persist/restore + Hash） |
| 当前 Tool 调用栈恢复 | **NOT_VERIFIED** | 无源码/实验证据 |
| 任意进程崩溃点恢复 | **NOT_VERIFIED / NOT_SUPPORTED** | 无官方 API；未证明 |
| Token 级断点恢复 | **NOT_SUPPORTED** | 非 V1 承诺（03 §6）；官方未提供 |

> 说明：NOT_SUPPORTED 仅用于官方明确无能力或架构上无法实现的项目；NOT_VERIFIED 用于"未找到类名但
> 未能证明支持/不支持"的项目。**不得把"搜索不到类名"单独作为 NOT_SUPPORTED 的充分证据**。

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
