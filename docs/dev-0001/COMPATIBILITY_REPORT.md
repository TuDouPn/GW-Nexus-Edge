# DEV-0001 — AgentScope 2.0.1 核心兼容性验证报告（第六轮评审修订版）

> 状态：PARTIALLY_VERIFIED（G-01 通过；G-03/OQ-007 为 PARTIAL；模型 Provider 生产认证未验证）
> 日期：2026-08-10（首次）；2026-08-10~11（六轮 CHANGES_REQUESTED 修订）
> 分支：`agent/DEV-0001-agentscope-compatibility`
> 关联门禁：G-01（核心依赖兼容 PoC，**通过**）、G-03（AgentScope 能力盘点，**PARTIAL**）
> 关联 Open Question：OQ-007（Persistence/Recovery 边界，**PARTIAL**）

---

## 1. 结论摘要

| 验证项 | 结论 | 证据 |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 共同构建 | **G-01 通过** | `./mvnw clean verify` BUILD SUCCESS，37 测试全绿 |
| Enforcer fail-fast（Java 21 / Maven 3.9） | **通过** | JDK 8 拒绝、JDK 21 通过 |
| Harness 安全边界 | **通过** | 工具面 `[echo_text, wait_async_results]`，无危险工具 |
| **真实 AgentScope Tool 执行（P0-1）** | **通过** | `@Tool` + RuntimeContext 参数注入，读取 workspaceId/tenantId |
| **AgentScope 会话隔离（P0-2）** | **通过** | sessionId 复合命名空间；跨 Workspace 不泄露 |
| **终态事件顺序（P1-1）** | **通过** | 先状态转换后发布终态事件；SSE 回调内状态已一致 |
| **Resume 顺序（P1-2）** | **通过** | 先校验持久化上下文后创建 Agent；失败无遗留 |
| Trace doFinally + 父子 span | **通过** | OTel Exporter 验证父子关联 + 全部结束 |
| EventStreams（Reactor Sinks） | **通过** | 多订阅者/延迟/恰好一次/顺序/背压/取消/有界缓存 |
| 单一终态 | **通过** | 每 TaskAttempt 只产生一个业务终态事件 |
| workspace/tenant 持久化 + fail-closed | **通过** | 按 Task 隔离；resume fail-closed |
| Secret Reference/Value | **通过** | Reference 传递 + Authorization 头（无明文） |
| Cancel 真实中断 | **通过** | 终态互斥；删除 interrupt 对照完成 |
| Recovery 上下文内容 | **通过** | 跨实例恢复含标记；ModelRegistry 重置 |
| G-03 能力盘点 | **PARTIAL** | 能力清单已验证大部分；Redis/真实 Provider 待补 |
| OQ-007 Recovery 边界 | **PARTIAL** | JsonFile 已验证；Redis/Checkpoint 待评审 |
| DeepSeek / 企业私有模型生产认证 | **未验证（BLOCKED）** | 需要真实凭据与安全测试凭证 |

## 2. 第五轮评审修订落实情况（P0×10）

| 评审项 | 修正内容 | 验证证据 |
|---|---|---|
| P0-1 上下文按 Task 隔离 | `ExecutionContextState.storeKey(taskId)`；同 user/session 不同 Task 互不覆盖 | `AgentScopeRound5SemanticsTest.parallelTaskRecoveryIsolation` |
| P0-2 resume fail-closed | 恢复不到上下文/Workspace/Tenant 抛 `IllegalStateException` | `AgentScopeRound5SemanticsTest.resumeFailClosedWithoutContext` |
| P0-3 cancel 携带上下文 | `cancelExecution` 复用 handle 的 RuntimeContext | Cancel 测试通过 |
| P0-4 AGENT_RESULT 按 GenerateReason | `INTERRUPTED`→CANCELLED，其他→COMPLETED | `AgentScopeRound5SemanticsTest.cancelEventStreamProducesSingleCancelled` |
| P0-5 取消事件流测试 | 一个 CANCELLED、零 COMPLETED | 同上 |
| P0-6 并行恢复隔离测试 | 同 user/session 不同 Workspace/Tenant 并行恢复互不覆盖 | `parallelTaskRecoveryIsolation` |
| P0-7 Tool 链路结论 | 第五轮曾误判"Tool 执行受限"——第六轮 P0-1 已撤回（见 §3） | 见第六轮落实 |
| P0-8 request(n) 背压测试 | 有限 request(n) | `EventStreamsSemanticsTest`（第六轮 P1-3 重写） |
| P0-9 回滚 Blueprint 16 | 撤销对 Accepted Blueprint 的先行修改 | `git checkout 27daca9 -- 16_GLOSSARY.md` |
| P0-10 更新 ADR-0006 | 轮次/EventStreams/测试数/待同步事项 | ADR-0006 |

## 3. 第六轮评审修订落实情况

| 评审项 | 修正内容 | 验证证据 |
|---|---|---|
| **P0-1 修复 Tool 测试** | 真实 AgentScope Tool（`@Tool` + RuntimeContext 参数注入）执行验证 workspaceId/tenantId；**撤回第五轮"Tool 执行受限"误判**（根因是共享端点 toolCallRounds 未重置的测试缺陷，非 HarnessAgent 限制） | `AgentScopeRound5SemanticsTest.realAgentScopeToolReadsRuntimeContext`：`OBSERVED={user-r5-tool/session-r5-tool=workspace-r5-tool\|tenant-r5-tool}`；日志含 `TOOL_RESULT_TEXT: ["上下文已读取"]` |
| **P0-2 AgentScope 会话隔离** | sessionId 复合命名空间 `{workspaceId}:{tenantId}:{sessionId}`，AgentScope AgentState/Memory 按 Workspace 隔离；跨 Workspace 相同 user/session 不共享 | `AgentScopeRound6SemanticsTest.crossWorkspaceMemoryIsolation`（分别恢复 Task A/B，上下文互不覆盖） |
| **P1-1 终态事件顺序** | `AgentResultEvent` 先 `transitionTerminal` 再发布终态事件；流完成兜底先更新状态再关闭事件流 | `AgentScopeRound6SemanticsTest.terminalEventObservedAfterStateTransition`（SSE COMPLETED 回调内 Task 状态已 COMPLETED） |
| **P1-2 Resume 顺序** | 先读取并完整校验持久化上下文（taskId/userId/sessionId/workspaceId/tenantId），再解析 Secret/注册模型/创建 Agent；失败路径无未关闭 Agent | `AgentScopeRound5SemanticsTest.resumeFailClosedWithoutContext` + P1-2 重排 |
| **P1-3 背压测试重写** | 主线程独立断言 request(2) 后恰好 2 条，再追加 request 验证剩余；不在 onNext 回调内追加请求 | `EventStreamsSemanticsTest.backpressureWithLimitedRequest`（P1-3 版） |
| **P1-4 报告清理** | 更新 37 测试、撤回 Tool 误判、A/B 恢复描述、删除 Blueprint 16 过期描述、修正章节编号 | 本报告 + `git diff --check` |

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
  disableDynamicSkills/disableDefaultWorkspaceSkills/disableMemoryTools` + `enableMetaTool(false)`
  + 注册 `OtelTracingMiddleware`。
- **已知局限**：底层 filesystem 仍是本地 overlay；生产业务 Agent 需显式
  `filesystem(LocalFilesystemSpec)` 限定工作区（Phase 3）；Coding Sandbox 完整隔离由 22 文档承担。

## 5. 关键运行证据（第六轮）

### 5.1 真实 AgentScope Tool 执行（P0-1，撤回误判）

```
TOOL_RESULT_TEXT: ["上下文已读取"]
OBSERVED={user-r5-tool/session-r5-tool=workspace-r5-tool|tenant-r5-tool}
```

- ✅ `@Tool` 注解方法经 `ToolMethodInvoker` 自动注入 `RuntimeContext`，真实执行读取
  workspaceId/tenantId。第五轮"HarnessAgent Tool 执行受限"结论已撤回——根因是共享
  端点 `toolCallRounds` 未重置的测试隔离缺陷（`resetToolCallRounds` + `setToolCallName`
  后工具正常执行）。

### 5.2 AgentScope 会话隔离（P0-2）

- sessionId 复合命名空间 `{workspaceId}:{tenantId}:{sessionId}`。
- 跨 Workspace 相同 user/session 的 Task A（workspace-R6-A）与 Task B（workspace-R6-B）
  分别恢复，上下文互不覆盖（无内容泄露）。

### 5.3 终态事件顺序（P1-1）

- `AgentResultEvent` 先 `transitionTerminal` 再发布终态事件。
- SSE COMPLETED 回调内 `statusOf` 已为 COMPLETED（先状态后事件）。

### 5.4 Trace 父子 span（持续验证）

- `nexus-edge.agent.execution ← invoke_agent ← chat`，同一 traceId，全部结束。

### 5.5 背压（P1-3）

- `request(2)` 后主线程独立断言恰好 2 条；追加 request 后收齐剩余。

## 6. G-03 AgentScope 2.0.1 能力清单（PARTIAL）

| 能力 | 状态 | 说明 |
|---|---|---|
| HarnessAgent API | ✅ | call/stream/streamEvents/interrupt/getToolkit |
| Builder disable* 系列 | ✅ | 含 disableMemoryTools |
| Tool（@Tool + RuntimeContext 注入） | ✅ | `ToolMethodInvoker` 自动注入，真实执行读取上下文 |
| ModelRegistry / OpenAIChatModel | ✅ | 协议链路 |
| State Store（JsonFile） | ✅ | 持久化/恢复 + 内容级 + 上下文 + 会话隔离 |
| OtelTracingMiddleware | ✅ | 父子 span 已验证 |
| State Store（Redis） | ❌ 未验证 | 待评审 |
| 官方 spring-boot-starter | ❌ 未采用 | SB 4.0.1 未保证 |
| DeepSeek / 企业私有 Provider | ❌ BLOCKED | 需真实凭证 |

## 7. OQ-007 Recovery 能力矩阵（PARTIAL）

| 维度 | 结论 |
|---|---|
| Session 持久化 | JsonFile 已验证（内容级 + 复合命名空间） |
| resume 语义 | 同 (userId, sessionId) + 同 store 重建（跨实例） |
| 上下文恢复 | workspaceId/tenantId 按 Task 隔离存储/恢复 |
| 会话隔离 | 跨 Workspace 相同 user/session 不泄露（复合 sessionId） |
| cancel 语义 | 会话级优雅中断（已验证） |
| Redis Store | 扩展存在，未启用 |
| Checkpoint | 官方组件待盘点 |

## 8. 测试证据（37/37 全绿，第六轮修订后）

| 测试类 | 用例 | 结果 | 关键断言 |
|---|---|---|---|
| AgentScopeMinimalExecutionTest | 2 | ✅ | 异步引用 + 真实 traceId |
| **AgentScopeRound6SemanticsTest** | 2 | ✅ | **跨 Workspace 隔离 + 终态顺序** |
| AgentScopeWorkspaceTenantContextTest | 1 | ✅ | workspace/tenant 注入 |
| **AgentScopeRound5SemanticsTest** | 4 | ✅ | **真实 Tool 执行、取消单一 CANCELLED、fail-closed、并行隔离** |
| AgentScopeCancelTest | 3 | ✅ | 真实中断终态 + 互斥 |
| AgentScopeDelayedSubscriptionTest | 1 | ✅ | 延迟订阅完整事件 |
| AgentScopeToolCallingTest | 2 | ✅ | 工具面 + 单执行源 |
| AgentScopeSecretResolutionTest | 2 | ✅ | Secret Reference + 请求头（无明文） |
| AgentScopeTraceCorrelationTest | 4 | ✅ | 四标识 + replyId |
| AgentScopeSpringContextIntegrationTest | 1 | ✅ | SB 4.1.0 内嵌 + JSONObject 排除 |
| AgentScopeConcurrentTraceIsolationTest | 1 | ✅ | 并发 Trace 隔离 |
| AgentScopeErrorPropagationTest | 1 | ✅ | 500 → RetryExhaustedException |
| AgentScopeRecoveryCapabilityTest | 3 | ✅ | 内容级恢复 + 复合命名空间 |
| AgentScopeRound4SemanticsTest | 3 | ✅ | 单一终态 + RuntimeContext + 父子 span |
| **EventStreamsSemanticsTest** | 5 | ✅ | **多订阅者/延迟/唯一/顺序/取消/缓存边界/背压** |
| AgentScopeStructuredOutputTest | 1 | ✅ | 对象解析 |
| AgentScopeStreamingEventTest | 1 | ✅ | 事件同源 + 单执行源 |
| **合计** | **37** | **0 失败** | `./mvnw clean verify` |

## 9. 明确边界与未验证项

1. **模型 Provider 生产认证：BLOCKED**。
2. **宿主 filesystem overlay**（如实记录）：disable* 移除工具，但底层 overlay 仍在；
   生产需显式 `filesystem(LocalFilesystemSpec)`（Phase 3）。
3. **G-03/OQ-007：PARTIAL**（Redis/真实 Provider 待补）。
4. **Last-Event-ID 续传**：归后续持久化业务事件层。
5. **executionId/agentId 语义同步**：ADR-0006 批准后原子同步
   00_DECISIONS/08_AGENTSCOPE_AND_SKILL/16_GLOSSARY/领域字段/API 契约（P0-9）。

## 10. 提交记录

第六轮修正 Commit SHA 见 `docs/handoffs/active/DEV-0001.md` §6（`git log --oneline` 可核验）。
