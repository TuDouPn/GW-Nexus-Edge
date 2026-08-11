# DEV-0001 — AgentScope 2.0.1 核心兼容性验证报告（第七轮评审修订版）

> 状态：PARTIALLY_VERIFIED（G-01 通过；G-03/OQ-007 为 PARTIAL；模型 Provider 生产认证未验证）
> 日期：2026-08-10（首次）；2026-08-10~11（七轮 CHANGES_REQUESTED 修订）
> 分支：`agent/DEV-0001-agentscope-compatibility`
> 关联门禁：G-01（核心依赖兼容 PoC，**通过**）、G-03（AgentScope 能力盘点，**PARTIAL**）
> 关联 Open Question：OQ-007（Persistence/Recovery 边界，**PARTIAL**）

---

## 1. 结论摘要

| 验证项 | 结论 | 证据 |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 共同构建 | **G-01 通过** | `./mvnw clean verify` BUILD SUCCESS，41 测试全绿 |
| Enforcer fail-fast（Java 21 / Maven 3.9） | **通过** | JDK 8 拒绝、JDK 21 通过 |
| Harness 安全边界 | **通过** | 工具面无危险工具；`disableMemoryHooks` 禁用 Memory Hooks |
| **真实 AgentScope Tool 执行（P0-1）** | **通过** | `@Tool` + RuntimeContext 参数注入，读取 scoped 身份与业务 extras |
| **长期 Runtime Identity（P0-1/P0-4）** | **通过** | Mapper 稳定/无碰撞/路径安全；AgentExecutionRequest fail-closed |
| **跨 Workspace Memory 隔离（P0-2）** | **通过** | 恢复 A/B 请求 Marker 正负断言；AgentState 目录隔离；无共享 memory 台账 |
| **FAILED 业务事件（P0-3）** | **通过** | 先 FAILED 状态后脱敏事件；SSE 回调状态一致；eventId 由 Nexus 生成 |
| **Resume 完整校验（P1-1）** | **通过** | taskId/userId/sessionId 逐项比较；篡改 fail-closed |
| **取消/迟到结果竞态（P1-2）** | **通过** | 每 Attempt 一个终态事件且与最终状态一致 |
| 终态事件顺序（P1-1） | **通过** | 先状态转换后发布终态事件；SSE 回调内状态已一致 |
| Trace doFinally + 父子 span | **通过** | OTel Exporter 验证父子关联 + 全部结束 |
| EventStreams（Reactor Sinks） | **通过** | 多订阅者/延迟/恰好一次/顺序/背压/取消/有界缓存 |
| workspace/tenant 持久化 + fail-closed | **通过** | 按 Task 隔离；resume fail-closed |
| Secret Reference/Value | **通过** | Reference 传递 + Authorization 头（无明文） |
| Cancel 真实中断 | **通过** | 终态互斥；删除 interrupt 对照完成 |
| Recovery 上下文内容 | **通过** | 跨实例恢复含标记；ModelRegistry 重置 |
| G-03 能力盘点 | **PARTIAL** | 能力清单已验证大部分；Redis/真实 Provider 待补 |
| OQ-007 Recovery 边界 | **PARTIAL** | JsonFile 已验证；Redis/Checkpoint 待评审 |
| DeepSeek / 企业私有模型生产认证 | **未验证（BLOCKED）** | 需要真实凭据与安全测试凭证 |

## 2. 第六轮评审修订落实情况

| 评审项 | 修正内容 | 验证证据 |
|---|---|---|
| P0-1 修复 Tool 测试 | 真实 `@Tool` + RuntimeContext 参数注入执行；撤回第五轮误判 | `AgentScopeRound5SemanticsTest.realAgentScopeToolReadsRuntimeContext`（第七轮证据见 §5.1） |
| P0-2 会话隔离 | 第六轮采用 sessionId 复合命名空间 | **第七轮 P0-1/P0-4 已升级为长期 Runtime Identity（Mapper），并删除冒号拼接方案** |
| P1-1 终态事件顺序 | 先 transitionTerminal 再发布终态事件 | `AgentScopeRound6SemanticsTest.terminalEventObservedAfterStateTransition` |
| P1-2 Resume 顺序 | 先校验持久化上下文后创建 Agent | 第七轮 P1-1 进一步逐项比较 taskId/userId/sessionId |
| P1-3 背压测试重写 | 主线程独立断言 request(2) 后追加 | `EventStreamsSemanticsTest.backpressureWithLimitedRequest` |
| P1-4 报告清理 | 37 测试、撤回误判、章节编号 | 本报告第七轮修订版 |

## 3. 第七轮评审修订落实情况（P0×4 + P1×3 + P2）

| 评审项 | 修正内容 | 验证证据 |
|---|---|---|
| **P0-1 长期 Memory 跨 Workspace 共用** | 形成长期 Runtime Identity 设计：Tenant/Workspace/User/Session → `AgentRuntimeIdentityMapper`（稳定/无碰撞/路径安全）→ AgentScope `scopedUserId`/`scopedSessionId`；原始业务 ID 保留在 RuntimeContext extras；业务 Agent 不允许自动长期记忆 → 显式 `disableMemoryHooks()`（边界记录于 ADR-0006 决策 9/10） | `AgentRuntimeIdentityMapper` + `buildSecureAgent().disableMemoryHooks()` + `AgentScopeRound7SemanticsTest.runtimeIdentityIsStableCollisionFreeAndPathSafe`；Memory 目录隔离证据见 §5.2 |
| **P0-2 重写跨 Workspace Memory 泄露测试** | 顺序恢复 Task A/B 并分别捕获模型请求：A 含 MARKER_A 且不含 MARKER_B；B 含 MARKER_B 且不含 MARKER_A；同时验证 AgentState 目录隔离与无共享 memory 台账；废弃 workspaceId 不相等替代内容断言 | `AgentScopeRound7SemanticsTest.crossWorkspaceMemoryIsolationWithRealMarkers`（Marker 正负断言证据见 §5.3） |
| **P0-3 补齐 FAILED 业务事件** | 模型/Agent 异常：先 `tryTerminal(FAILED)` → 发布脱敏 FAILED 业务事件（Blueprint `task.failed` 契约）→ 结束事件流；SSE FAILED 回调内 Task 状态为 FAILED；Nexus 生成事件 ID（UUIDv7），不依赖 AgentScope 事件 id | `AgentScopeRound7SemanticsTest.failedEventAfterModelError`（证据见 §5.4）+ `AgentscopeAgentExecutionAdapter.onStreamError` |
| **P0-4 Runtime Identity fail-closed** | `AgentExecutionRequest` 构造器校验 workspaceId/tenantId 非空；删除冒号拼接 + null→空串的 `namespacedSessionId`；建立正式 `AgentRuntimeIdentityMapper`（规范化、无碰撞、路径安全编码） | `AgentScopeRound7SemanticsTest.runtimeIdentityIsStableCollisionFreeAndPathSafe` |
| **P1-1 Resume 完整校验** | 逐项比较 restored 与 reference 的 taskId/userId/sessionId；状态内容错绑/篡改 fail-closed（不再声称"已完整校验"而未实现比较） | `AgentScopeRound7SemanticsTest.resumeRejectsTamperedContext` |
| **P1-2 终态转换返回实际结果** | `transitionTerminal` → `tryTerminal`（返回是否由本次调用完成转移）；避免 Task 已 CANCELLED 但迟到普通 AgentResult 又发布 COMPLETED；每个 TaskAttempt 只出现一个与最终状态一致的终态事件 | `AgentScopeRound7SemanticsTest.cancelThenLateResultEmitsSingleConsistentTerminal` |
| **P1-3 清理正式文档** | Handoff 重复 §1.1 合并、G-01 状态统一（通过）、Latest Commit/ADR 轮次更新、证据仅引用真实测试输出、`git diff --check` 无输出 | 本报告 + Handoff 第七轮修订版 |
| **P2 测试资源清理 null-safe** | 全部测试类 `@AfterAll` 对 endpoint/adapter/agent 判空，避免 `@BeforeAll` 失败后二次 NPE | 15 个测试类 teardown 统一判空 |

## 4. 版本证据与依赖分析（如实陈述）

### 4.1 Artifact 版本（Maven Central 实测）

| Artifact | 2.0.1 | 证据 |
|---|---|---|
| agentscope-bom/core/harness/model-openai/redis | ✅ | POM + Sources JAR + dependency:tree |
| agentscope-extensions-session-redis/mysql | ❌ 不存在 | 仅 1.x 与 2.0.0-RC1 |
| agentscope-spring-boot-starter | ✅ 存在 | 编译依赖 SB 4.0.1（optional），未采用 |

### 4.2 JSONObject 依赖（真实警告 + 排除结论）

- **真实警告（Spring 测试）**：`DuplicateJsonObjectContextCustomizer` 报告 `org.json.JSONObject`
  出现两次（`org/json/json-20251224.jar` compile 来源 mcp-json；`android-json` test 来源 jsonassert）。
- **排除结论**：在 `spring-boot-starter-test` 排除 `android-json`；jsonassert 运行时使用
  classpath 中唯一剩余的 `org.json`，Spring 测试通过、警告消失。

### 4.3 Harness 默认行为（如实描述，含已知局限）

- 默认 filesystem 为 `LocalFilesystemSpec` → `LocalFilesystemWithShell`（ShellAwareOverlay，
  project 默认 `${user.dir}`）。
- 本适配器：`disableFilesystemTools/disableShellTool/disableSubagents/disableDynamicSubagents/
  disableDynamicSkills/disableDefaultWorkspaceSkills/disableMemoryTools/disableMemoryHooks`
  + `enableMetaTool(false)` + 注册 `OtelTracingMiddleware`。
- **已知局限**：底层 filesystem 仍是本地 overlay；生产业务 Agent 需显式
  `filesystem(LocalFilesystemSpec)` 限定工作区（Phase 3）；Coding Sandbox 完整隔离由 22 文档承担。

## 5. 关键运行证据（第七轮，均来自真实测试输出）

### 5.1 真实 AgentScope Tool 执行（P0-1）

```
=== 真实 Tool 执行证据（OBSERVED）: {dGVuYW50LXI1LXRvb2w.d29ya3NwYWNlLXI1LXRvb2w.dXNlci1yNS10b29s/c2Vzc2lvbi1yNS10b29s=workspace-r5-tool|tenant-r5-tool} ===
```

- ✅ `@Tool` 注解方法经 `ToolMethodInvoker` 自动注入 `RuntimeContext`，真实执行。
- OBSERVED 键 = scopedUserId/scopedSessionId（Base64URL 编码，路径安全）；
  值 = RuntimeContext extras 中的业务 `workspace-r5-tool|tenant-r5-tool`
  （原始业务 ID 保留在 extras，P0-1）。

### 5.2 AgentState/Memory 目录隔离证据（P0-2）

```
=== AgentState/Memory 目录隔离证据（scopedIdentityA=dGVuYW50LVI3LUE.d29ya3NwYWNlLVI3LUE.dXNlci1yNw / c2Vzc2lvbi1yNw,
                                              scopedIdentityB=dGVuYW50LVI3LUI.d29ya3NwYWNlLVI3LUI.dXNlci1yNw / c2Vzc2lvbi1yNw） ===
=== State Store 文件清单:
[dGVuYW50LVI3LUE.d29ya3NwYWNlLVI3LUE.dXNlci1yNw/c2Vzc2lvbi1yNw/agent_state.json,   ← Task A 会话状态（scopedA 目录）
 user-r7/session-r7/nexus_execution_context:task-r7-b.json,                           ← Nexus 执行上下文（按 taskId 键）
 user-r7/session-r7/nexus_execution_context:task-r7-a.json,
 dGVuYW50LVI3LUI.d29ya3NwYWNlLVI3LUI.dXNlci1yNw/c2Vzc2lvbi1yNw/agent_state.json]   ← Task B 会话状态（scopedB 目录） ===
```

- ✅ Task A/B 的 AgentScope 会话状态落在**不同** scoped 目录（`dGVuYW50LVI3LUE…` 与
  `dGVuYW50LVI3LUI…`），无碰撞。
- ✅ **不存在**任何 `memory/YYYY-MM-DD.md` 台账文件（`disableMemoryHooks` 生效，
  Memory Flush/Consolidation Hooks 未写入共享台账）。
- ✅ Nexus 执行上下文按 `user-r7/session-r7/nexus_execution_context:{taskId}` 键隔离
  （业务分区 + taskId 键，互不覆盖）。

### 5.3 恢复 A/B 模型请求 Marker 正负断言（P0-2）

```
=== 恢复 A 模型请求 Marker 断言: 请求数=2, 含 MARKER_A=true, 含 MARKER_B=false ===
=== 恢复 B 模型请求 Marker 断言: 请求数=2, 含 MARKER_B=true, 含 MARKER_A=false ===
```

- ✅ 恢复 A 的历史内容延续（含 MARKER_A）且**不含** B 的内容（MARKER_B）；
  恢复 B 对称验证——真实历史内容级断言，非 workspaceId 不相等替代。

### 5.4 FAILED 业务事件（P0-3）

```
=== FAILED 事件证据: 状态=FAILED, FAILED 摘要=[Agent 执行失败（脱敏）：RetryExhaustedException] ===
```

- ✅ 模型端点 500 → 官方 Provider 重试耗尽（`RetryExhaustedException`）→ Task 状态 FAILED。
- ✅ SSE FAILED 回调内 `statusOf` 已为 FAILED（先状态后事件）。
- ✅ FAILED 事件摘要脱敏（仅异常类型名，不含 Secret/原始异常细节）。
- ✅ 事件 ID 由 Nexus 生成（UUIDv7 格式断言通过），不依赖 AgentScope 事件 id。

### 5.5 Runtime Identity 设计（P0-1/P0-4）

- `scopedUserId = b64(tenant) + '.' + b64(workspace) + '.' + b64(user)`；
  `scopedSessionId = b64(session)`（Base64 URL 安全、无填充）。
- 稳定：确定性映射，跨实例/重启/多副本可重建；无碰撞：Base64URL 单射 + `.` 分隔符
  不在编码字母表；路径安全：字母表不含 `/`、`\`、`..`、冒号、空白。
- fail-closed：workspaceId/tenantId 缺失/空白时 `AgentExecutionRequest` 构造器与
  Mapper 均拒绝；已删除第六轮冒号拼接 `namespacedSessionId`。

### 5.6 Trace 父子 span（持续验证）

- `nexus-edge.agent.execution ← invoke_agent ← chat`，同一 traceId，全部结束。

### 5.7 背压（P1-3）

- `request(2)` 后主线程独立断言恰好 2 条；追加 request 后收齐剩余。

## 6. G-03 AgentScope 2.0.1 能力清单（PARTIAL）

| 能力 | 状态 | 说明 |
|---|---|---|
| HarnessAgent API | ✅ | call/stream/streamEvents/interrupt/getToolkit |
| Builder disable* 系列 | ✅ | 含 disableMemoryTools/disableMemoryHooks |
| Tool（@Tool + RuntimeContext 注入） | ✅ | `ToolMethodInvoker` 自动注入，真实执行读取 scoped 身份与 extras |
| Runtime Identity（scopedUserId/scopedSessionId） | ✅ | Mapper 稳定/无碰撞/路径安全，会话按 Tenant/Workspace 隔离 |
| ModelRegistry / OpenAIChatModel | ✅ | 协议链路 |
| State Store（JsonFile） | ✅ | 持久化/恢复 + 内容级 + scoped 目录隔离 |
| FAILED 业务事件 | ✅ | 先状态后脱敏事件；SSE 回调一致；Nexus 生成 eventId |
| OtelTracingMiddleware | ✅ | 父子 span 已验证 |
| State Store（Redis） | ❌ 未验证 | 待评审 |
| 官方 spring-boot-starter | ❌ 未采用 | SB 4.0.1 未保证 |
| DeepSeek / 企业私有 Provider | ❌ BLOCKED | 需真实凭证 |

## 7. OQ-007 Recovery 能力矩阵（PARTIAL）

| 维度 | 结论 |
|---|---|
| Session 持久化 | JsonFile 已验证（内容级 + scoped 身份目录隔离） |
| resume 语义 | 同 (userId, sessionId) + 同 store 重建（跨实例）；逐项校验 taskId/userId/sessionId |
| 上下文恢复 | workspaceId/tenantId 按 Task 隔离存储/恢复；篡改 fail-closed |
| 会话隔离 | 跨 Workspace 相同 user/session 不泄露（scopedUserId 目录隔离 + Marker 正负断言） |
| Memory 台账 | 自动长期记忆禁用（disableMemoryHooks），无共享 memory 台账 |
| cancel 语义 | 会话级优雅中断（已验证）；迟到结果不发布冲突终态 |
| Redis Store | 扩展存在，未启用 |
| Checkpoint | 官方组件待盘点 |

## 8. 测试证据（41/41 全绿，第七轮修订后）

| 测试类 | 用例 | 结果 | 关键断言 |
|---|---|---|---|
| AgentScopeMinimalExecutionTest | 2 | ✅ | 异步引用 + 真实 traceId |
| **AgentScopeRound7SemanticsTest** | 5 | ✅ | **Runtime Identity、Marker 正负断言、FAILED 事件、篡改 fail-closed、取消/迟到竞态** |
| AgentScopeWorkspaceTenantContextTest | 1 | ✅ | workspace/tenant 注入 |
| **AgentScopeRound5SemanticsTest** | 4 | ✅ | 真实 Tool 执行、取消单一 CANCELLED、fail-closed、并行隔离 |
| AgentScopeCancelTest | 3 | ✅ | 真实中断终态 + 互斥 |
| AgentScopeDelayedSubscriptionTest | 1 | ✅ | 延迟订阅完整事件 |
| AgentScopeToolCallingTest | 2 | ✅ | 工具面 + 单执行源 |
| AgentScopeSecretResolutionTest | 2 | ✅ | Secret Reference + 请求头（无明文） |
| AgentScopeTraceCorrelationTest | 4 | ✅ | 四标识 + replyId |
| AgentScopeSpringContextIntegrationTest | 1 | ✅ | SB 4.1.0 内嵌 + JSONObject 排除 |
| AgentScopeConcurrentTraceIsolationTest | 1 | ✅ | 并发 Trace 隔离 |
| AgentScopeErrorPropagationTest | 1 | ✅ | 500 → RetryExhaustedException |
| AgentScopeRecoveryCapabilityTest | 3 | ✅ | 内容级恢复 + scoped 身份 |
| AgentScopeRound4SemanticsTest | 3 | ✅ | 单一终态 + RuntimeContext + 父子 span |
| AgentScopeRound6SemanticsTest | 1 | ✅ | 终态事件顺序（SSE 回调一致） |
| **EventStreamsSemanticsTest** | 5 | ✅ | 多订阅者/延迟/唯一/顺序/取消/缓存边界/背压 |
| AgentScopeStructuredOutputTest | 1 | ✅ | 对象解析 |
| AgentScopeStreamingEventTest | 1 | ✅ | 事件同源 + 单执行源 |
| **合计** | **41** | **0 失败** | `./mvnw clean verify` |

## 9. 明确边界与未验证项

1. **模型 Provider 生产认证：BLOCKED**。
2. **宿主 filesystem overlay**（如实记录）：disable* 移除工具，但底层 overlay 仍在；
   生产需显式 `filesystem(LocalFilesystemSpec)`（Phase 3）。
3. **G-03/OQ-007：PARTIAL**（Redis/真实 Provider 待补）。
4. **自动长期记忆（Long-Term Memory）**：当前业务 Agent 禁用（disableMemoryHooks）；
   引入时需独立 ADR 并经 Runtime Identity 评审。
5. **Last-Event-ID 续传**：归后续持久化业务事件层（Nexus 生成 eventId 已就绪）。
6. **executionId/agentId 语义同步**：ADR-0006 批准后原子同步
   00_DECISIONS/08_AGENTSCOPE_AND_SKILL/16_GLOSSARY/领域字段/API 契约（P0-9）。

## 10. 提交记录

第七轮修正 Commit SHA 见 `docs/handoffs/active/DEV-0001.md` §6（`git log --oneline` 可核验）。
