# DEV-0001 — AgentScope 2.0.1 核心兼容性验证报告（第三轮评审修订版）

> 状态：PARTIALLY_VERIFIED（G-01 通过；G-03/OQ-007 为 PARTIAL；模型 Provider 生产认证未验证）
> 日期：2026-08-10（首次）；2026-08-10（三轮 CHANGES_REQUESTED 修订）
> 分支：`agent/DEV-0001-agentscope-compatibility`
> 关联门禁：G-01（核心依赖兼容 PoC，**通过**）、G-03（AgentScope 能力盘点，**PARTIAL**）
> 关联 Open Question：OQ-007（Persistence/Recovery 边界，**PARTIAL**）

---

## 1. 结论摘要

| 验证项 | 结论 | 证据 |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 共同构建 | **G-01 通过** | `./mvnw clean verify` BUILD SUCCESS，23 测试全绿 |
| Enforcer fail-fast（Java 21 / Maven 3.9） | **通过** | JDK 8 拒绝、JDK 21 通过 |
| Harness 安全边界（P0-1） | **通过** | 构建后 `getToolkit().getToolNames()` = `[echo_text, wait_async_results]` |
| Trace 异步上下文隔离（P0-1） | **通过** | contextWrite 传播；并发 Task traceId 隔离 |
| 事件订阅前丢失（P0-2） | **通过** | EventSink replay；延迟订阅收到完整事件序列 |
| Secret Reference/Value（P0-3） | **通过** | Reference 记录 + Authorization 头验证 |
| Spring 测试顺序独立（P0-4） | **通过** | 独立运行 + 全局状态重置 |
| 单一执行源（P0-2） | **通过** | 订阅前后请求数不变 |
| Cancel 真实中断（P1-5） | **通过** | 终态互斥；删除 interrupt 对照完成 |
| Recovery 上下文内容（P1-6） | **通过** | 跨实例恢复含标记；ModelRegistry 重置 |
| 标识模型（P1-7） | **通过** | taskId/taskAttemptId/agentId/traceId + 执行级 replyId |
| Workspace/Tenant 上下文（P1-9） | **通过** | RuntimeContext.put 注入 |
| G-03 能力盘点 | **PARTIAL** | 能力清单已验证大部分；Redis/真实 Provider 待补 |
| OQ-007 Recovery 边界 | **PARTIAL** | JsonFile 已验证；Redis/Checkpoint 待评审 |
| DeepSeek / 企业私有模型生产认证 | **未验证（BLOCKED）** | 需要真实凭据与安全测试凭证 |

## 2. 第三轮评审修订落实情况（P0×4 + P1×6）

| 评审项 | 修正内容 | 验证证据 |
|---|---|---|
| **P0-1 Trace 异步上下文泄漏** | 每 Task 独立 OTel span；`contextWrite` + `ContextPropagationOperator.storeOpenTelemetryContext` 传播到异步链（不再 makeCurrent 于调用线程）；span 在完成/错误/取消时统一结束 | `AgentScopeConcurrentTraceIsolationTest`：并发 Task A/B traceId 不同（`5ddeb51c...` vs `83251fc3...`） |
| **P0-2 事件订阅前丢失** | 新增 `EventSink`（replay 语义）：执行前建立，缓冲全部事件，延迟订阅者收到完整序列 | `AgentScopeDelayedSubscriptionTest`：执行完成后订阅仍收到含 STARTED/COMPLETED 的完整序列 |
| **P0-3 Secret Reference/Value 同值** | `TestSecretResolver` 记录 reference、按 reference 区分值；端点记录 Authorization 头 | `AgentScopeSecretResolutionTest`：reference 被传递、解析值进入请求头 |
| **P0-4 Spring 测试顺序依赖** | `@BeforeAll` 重置 ModelRegistry + OTel；上下文自包含 | `AgentScopeSpringContextIntegrationTest` 独立运行通过 |
| **P1 Execution 标识** | `AgentEventEnvelope.executionId` 语义澄清为 Agent 实例标识；事件携带执行级 replyId（AgentEndEvent） | `TraceCorrelationTest.eventsCarryExecutionLevelReplyId`：replyId=`ac30382f...` |
| **P1 AgentEvent 元数据误判** | `doOnComplete` 兜底区分：CANCEL_REQUESTED→CANCELLED、STARTED→COMPLETED；修复重复 end(trace) | `AgentScopeCancelTest` 3/3 |
| **P1 Harness/JSONObject 文档失真** | 本文档如实描述默认 filesystem 行为与 JSONObject 实测结论（见 §3/§4.1） | 本文档 |
| **P1 工具 Allowlist** | 新增 `disableMemoryTools()`；工具面收敛为 `[echo_text, wait_async_results]` | `AgentScopeToolCallingTest` 真实工具面日志 |
| **P1 Workspace/Tenant 上下文** | `RuntimeContext.put("workspaceId"/"tenantId")` 注入 | `AgentScopeWorkspaceTenantContextTest` |
| **P1 Handoff 状态冲突** | Handoff 同步为 READY_FOR_REVIEW（第三轮修正后） | 见 Handoff |

## 3. 版本证据与依赖分析（如实陈述）

### 3.1 Artifact 版本（Maven Central 实测）

| Artifact | 2.0.1 | 证据 |
|---|---|---|
| agentscope-bom/core/harness/model-openai/redis | ✅ | POM + Sources JAR + dependency:tree |
| agentscope-extensions-session-redis/mysql | ❌ 不存在 | 仅 1.x 与 2.0.0-RC1 |
| agentscope-spring-boot-starter | ✅ 存在 | 编译依赖 SB 4.0.1（optional），未采用 |

### 3.2 JSONObject 依赖（实测结论，不夸大）

- `org.json:json:20251224`（compile）：唯一来源 `mcp-json:0.17.0`。
- `com.vaadin.external.google:android-json`（test）：来源 jsonassert（Spring Test）。
- **实测：Maven 构建未报告重复 JSONObject 冲突**。两者包名空间独立，无版本冲突。

### 3.3 Harness 默认行为（如实描述，含已知局限）

- **默认 filesystem 机制**：未显式配置时 `HarnessAgent` 使用 `LocalFilesystemSpec.toFilesystem`，
  project 默认 `${user.dir}`（宿主当前目录），产生 `LocalFilesystemWithShell` 实例
  （日志中的 `ShellAwareOverlay` 即此类型）。
- **本适配器修正**：通过 `disableFilesystemTools() + disableShellTool()` 移除了
  FilesystemTool 与 ShellExecuteTool（日志不再注册 read_file/write_file/edit_file/execute），
  并 `disableSubagents/disableDynamicSubagents/disableDynamicSkills/disableDefaultWorkspaceSkills/disableMemoryTools`。
- **已知局限（如实记录）**：底层 filesystem 对象仍是本地 overlay（宿主可写目录被 overlay），
  `disableShellTool` 阻止了 shell 执行工具，但**未消除宿主路径 overlay 本身**——Coding
  Sandbox 的完整隔离由 22 文档的 Sandbox Broker + gVisor 承担，DEV-0001 业务 Agent
  仅保证"无 shell/文件系统工具、无 subagent、无 memory 工具"。生产业务 Agent 的
  filesystem 隔离需在 Phase 3 以显式 `filesystem(LocalFilesystemSpec)` 限定工作区
  目录为唯一根（当前 DEV-0001 聚焦兼容性验证，不伪装已解决宿主 overlay 问题）。

## 4. 关键运行证据（第三轮）

### 4.1 构建后工具面（真实日志，Allowlist 后）

```
=== 构建后工具面（getToolkit().getToolNames()）: [echo_text, wait_async_results] ===
```

- ✅ 无 read_file/write_file/edit_file/execute/shell/session_*/memory_*
- ✅ `disableMemoryTools()` 生效（memory/session 工具移除）
- 剩余 `wait_async_results` 为官方异步工具（无副作用）

### 4.2 P0-1 并发 Trace 隔离（真实日志）

```
Task task-iso-a ... traceId=5ddeb51c9536df58bdc0c3fa31759eba
Task task-iso-b ... traceId=83251fc3708234903da8d6481da5ce4a
```

- ✅ 两个并发 Task traceId 不同（隔离，无泄漏交叉）

### 4.3 P0-2 延迟订阅完整事件（真实）

- 执行完成后订阅 `streamExecutionEvents` → 收到含 STARTED/COMPLETED 的完整序列
- 订阅前后请求数一致（不发起第二次执行）

### 4.4 P0-3 Secret 请求头（真实）

- `TestSecretResolver.resolveCount(reference) >= 1`（Reference 被传递）
- 模型请求 Authorization 头包含 resolver 解析值

## 5. G-03 AgentScope 2.0.1 能力清单（PARTIAL）

| 能力 | 状态 | 说明 |
|---|---|---|
| HarnessAgent API | ✅ | call/stream/streamEvents/interrupt/getToolkit |
| Builder disable* 系列 | ✅ | 含 disableMemoryTools（Allowlist） |
| Tool（@Tool/Toolkit） | ✅ | 白名单收敛 |
| ModelRegistry / OpenAIChatModel | ✅ | 协议链路 |
| State Store（JsonFile） | ✅ | 持久化/恢复 + 内容级 |
| State Store（Redis） | ❌ 未验证 | 待评审 |
| 官方 spring-boot-starter | ❌ 未采用 | SB 4.0.1 未保证 |
| DeepSeek / 企业私有 Provider | ❌ BLOCKED | 需真实凭证 |
| OtelTracingMiddleware | ⚠️ PARTIAL | span 采集已验证；生产注入待接入 |

## 6. OQ-007 Recovery 能力矩阵（PARTIAL）

| 维度 | 结论 |
|---|---|
| Session 持久化 | JsonFile 已验证（内容级） |
| resume 语义 | 同 (userId, sessionId) + 同 store 重建（跨实例） |
| cancel 语义 | 会话级优雅中断（已验证） |
| Redis Store | 扩展存在，未启用 |
| Checkpoint | 官方组件待盘点 |
| Nexus vs AgentScope 边界 | Nexus 持久化业务 Task/TaskAttempt；AgentScope 持久化会话 |

## 7. 测试证据（23/23 全绿，第三轮修订后）

| 测试类 | 用例 | 结果 | 关键断言 |
|---|---|---|---|
| AgentScopeMinimalExecutionTest | 2 | ✅ | 异步引用 + 真实 traceId |
| AgentScopeWorkspaceTenantContextTest | 1 | ✅ | workspaceId/tenantId 注入 |
| AgentScopeCancelTest | 3 | ✅ | 真实中断终态 + 互斥 + 对照 |
| AgentScopeDelayedSubscriptionTest | 1 | ✅ | **延迟订阅完整事件（P0-2）** |
| AgentScopeToolCallingTest | 2 | ✅ | 工具面 Allowlist + 单执行源 |
| AgentScopeSecretResolutionTest | 2 | ✅ | **Secret Reference + 请求头（P0-3）** |
| AgentScopeTraceCorrelationTest | 4 | ✅ | 四标识 + 执行级 replyId |
| AgentScopeSpringContextIntegrationTest | 1 | ✅ | **SB 4.1.0 内嵌（独立运行，P0-4）** |
| AgentScopeConcurrentTraceIsolationTest | 1 | ✅ | **并发 Trace 隔离（P0-1）** |
| AgentScopeErrorPropagationTest | 1 | ✅ | 500 → RetryExhaustedException |
| AgentScopeRecoveryCapabilityTest | 3 | ✅ | 内容级恢复 + ModelRegistry 重置 |
| AgentScopeStructuredOutputTest | 1 | ✅ | 对象解析断言 |
| AgentScopeStreamingEventTest | 1 | ✅ | 事件同源 + 单执行源 |
| **合计** | **23** | **0 失败** | `./mvnw clean verify` |

## 8. 明确边界与未验证项

1. **模型 Provider 生产认证：BLOCKED**。
2. **宿主 filesystem overlay（如实记录）**：`disableShellTool/disableFilesystemTools`
   移除工具，但底层 filesystem 仍是本地 overlay；生产业务 Agent 的 filesystem 隔离
   需显式 `filesystem(LocalFilesystemSpec)` 限定（Phase 3），Coding Sandbox 完整隔离
   由 22 文档 Sandbox Broker + gVisor 承担。
3. **G-03/OQ-007：PARTIAL**（Redis/真实 Provider/生产 OTel 注入待补）。
4. **Last-Event-ID 续传**：归后续持久化业务事件层。
5. **GitHub Actions CI（G-06）**：本地 `./mvnw` 全量验证；CI 待建立。

## 9. 提交记录

第三轮修正 Commit SHA 见 `docs/handoffs/active/DEV-0001.md` §6（`git log --oneline` 可核验）。
