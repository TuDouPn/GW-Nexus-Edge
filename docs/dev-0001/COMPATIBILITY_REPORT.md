# DEV-0001 — AgentScope 2.0.1 核心兼容性验证报告（第二轮评审修订版）

> 状态：PARTIALLY_VERIFIED（G-01 通过；G-03/OQ-007 为 PARTIAL；模型 Provider 生产认证未验证）
> 日期：2026-08-10（首次）；2026-08-10（CHANGES_REQUESTED 第一轮修订）；2026-08-10（第二轮修订）
> 分支：`agent/DEV-0001-agentscope-compatibility`
> 关联门禁：G-01（核心依赖兼容 PoC，**通过**）、G-03（AgentScope 能力盘点，**PARTIAL**）
> 关联 Open Question：OQ-007（Persistence/Recovery 边界，**PARTIAL**）

---

## 1. 结论摘要

| 验证项 | 结论 | 证据 |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 共同构建 | **G-01 通过** | `./mvnw clean verify` BUILD SUCCESS，17 测试全绿 |
| Enforcer fail-fast（Java 21 / Maven 3.9） | **通过** | JDK 8 拒绝、JDK 21 通过 |
| Harness 安全边界（P0-1） | **通过** | 构建后 `getToolkit().getToolNames()` 无危险工具（证据见 §4.1） |
| 单一执行源（P0-2） | **通过** | streamExecutionEvents 订阅前后请求数不变（证据见 §4.2） |
| Secret Provider（P0-3） | **通过（契约层）** | SecretResolver 端口；main 无伪实现、无默认 Prompt |
| Adapter 真实 Trace（P1-4） | **通过** | startExecution 返回真实 OTel traceId |
| Cancel 真实中断（P1-5） | **通过** | 终态由真实中断事件确认；互斥终态 |
| Recovery 上下文内容（P1-6） | **通过** | 跨实例恢复请求含第一次标记；ModelRegistry 重置 |
| 标识模型（P1-7） | **通过** | taskId / taskAttemptId(UUIDv7) / agentId / traceId 四标识独立 |
| 事件契约（P1-8） | **PARTIAL** | 事件携带 eventId；Last-Event-ID 续传属后续持久化层 |
| G-03 能力盘点 | **PARTIAL** | 能力清单已形成；真实 Provider 未验证 |
| OQ-007 Recovery 边界 | **PARTIAL** | JsonFile 持久化/恢复已验证；Redis/Checkpoint 待评审 |
| DeepSeek / 企业私有模型生产认证 | **未验证（BLOCKED）** | 需要真实凭据与安全测试凭证 |

## 2. 第二轮评审修订落实情况（P0×3 + P1×6）

| 评审项 | 修正内容 | 验证证据 |
|---|---|---|
| **P0-1 Harness 安全边界** | 构建时 `disableFilesystemTools() + disableShellTool() + disableSubagents() + disableDynamicSubagents() + disableDynamicSkills() + disableDefaultWorkspaceSkills()` + `enableMetaTool(false)`；**构建后**断言 `getToolkit().getToolNames()` | 工具面 `[echo_text, session_search, session_list, memory_save, memory_search, session_history, wait_async_results, memory_get]`——无 read_file/write_file/edit_file/execute；`filesystem` 不再为 ShellAwareOverlay（disableShellTool 生效） |
| **P0-2 单一执行源** | `streamEvents()` 为唯一执行源；startExecution 一次订阅同时完成事件/状态/结果/失败/取消；streamExecutionEvents 返回执行前建立的 Publisher，绝不发起第二次执行 | 订阅前后请求数均为 3（`AgentScopeToolCallingTest`） |
| **P0-3 真 Secret Provider** | 新增 `SecretResolver` 端口（07 §7）；apiKeyReference 经其解析临时值；main 无伪实现（生产装配依赖必填 Bean）；删除 main 默认"测试助手" Prompt | `AgentscopeAdapterConfiguration` 必填注入；`TestSecretResolver` 仅存 test |
| **P1-4 Adapter 真实 Trace** | `startExecution` 内创建 OTel span 并采集 traceId 注入引用 | `AgentScopeMinimalExecutionTest`/`TraceCorrelationTest` 断言真实 OTel traceId |
| **P1-5 Cancel 真实中断** | cancelExecution 只置 CANCEL_REQUESTED + interrupt；终态 CANCELLED 由真实中断事件（INTERRUPTED 恢复消息/中断异常）确认；COMPLETED/FAILED/CANCELLED 互斥 | `AgentScopeCancelTest` 3 用例（含删除 interrupt 后正常完成的对照） |
| **P1-6 Recovery 内容级** | 恢复请求必须包含第一次保存的上下文标记；`ModelRegistry.reset()` 后重注册（不依赖静态残留）；等待 resumed 完成 | `AgentScopeRecoveryCapabilityTest`（CONTEXT_MARKER 内容级断言） |
| **P1-7 标识模型** | `AgentExecutionReference` 四字段：taskId / taskAttemptId(UUIDv7) / agentId / traceId；**禁止称 agentId 为"官方 Execution ID"** | `TraceCorrelationTest` 语义断言 |
| **P1-8 事件契约** | SubmissionPublisher 执行前建立（避免订阅前丢事件）；事件携带 eventId；**Last-Event-ID 续传明确归后续持久化业务事件层** | `StreamingEventTest`；Port Javadoc 明确边界 |
| **P1-9 文档** | 本报告不写"9 项全部修正"；删除与日志相反的 Tool/JSONObject 结论；G-03/OQ-007 改 PARTIAL；Handoff 统一 17/17；git diff --check 无输出 | 本文件 + `git diff --check` |

## 3. 版本证据（不依赖 GitHub main 分支文档）

以下结论均基于 **Maven Central 2.0.1 实际 Artifact/POM + Sources JAR + dependency:tree + 编译/运行测试**：

| Artifact | 2.0.1 存在性 | 证据 |
|---|---|---|
| `io.agentscope:agentscope-bom:2.0.1` | ✅ | Maven Central HTTP 200 |
| `agentscope-core/harness:2.0.1` | ✅ | POM + Sources JAR |
| `agentscope-extensions-model-openai:2.0.1` | ✅ | POM + Sources JAR |
| `agentscope-extensions-redis:2.0.1` | ✅ | POM + Sources JAR |
| `agentscope-extensions-session-redis/mysql:2.0.1` | ❌ **不存在** | 仅 1.x 与 2.0.0-RC1 |
| `agentscope-spring-boot-starter:2.0.1` | ✅ 存在 | 编译依赖 Spring Boot 4.0.1（optional） |

**BOM 采用**：`agentscope-bom:2.0.1`。**starter 不采用**（当前阶段）。

**依赖分析（修正）**：`org.json:json:20251224` 唯一来源 `mcp-json:0.17.0`；`android-json`（test）来源 jsonassert，包名独立。**本轮实际验证：无重复 JSONObject 冲突警告**（不夸大结论，仅陈述事实）。

## 4. 关键运行证据（第二轮）

### 4.1 P0-1 构建后工具面（真实日志）

```
=== 构建后工具面（getToolkit().getToolNames()）: [echo_text, session_search, session_list, memory_save, memory_search, session_history, wait_async_results, memory_get] ===
```

- ✅ 无 `read_file`/`write_file`/`edit_file`/`execute`/`shell`
- ✅ 无 `ShellAwareOverlay`（disableShellTool + disableFilesystemTools 生效）
- 剩余为官方 memory/session 工具（`session_*`/`memory_*`/`wait_async_results`），非危险能力
- 测试断言：工具面包含上述任一危险名即失败

### 4.2 P0-2 单一执行源（真实日志）

```
=== 单执行源证据: 完成时请求总数=3 ===
=== 单执行源证据: 订阅后请求总数=3 ===
```

- ✅ 订阅 `streamExecutionEvents` 前后请求数不变 → 不发起第二次执行

### 4.3 P1-5 Cancel（互斥终态）

- `cancelExecution` → CANCEL_REQUESTED → 真实中断事件 → CANCELLED
- 不调用 cancelExecution → COMPLETED（对照证明取消依赖真实中断）
- 已终态任务调用 cancelExecution → 拒绝（IllegalStateException）

## 5. G-03 AgentScope 2.0.1 能力清单（PARTIAL，基于 Sources JAR + 运行验证）

| 能力 | 状态 | 说明 |
|---|---|---|
| HarnessAgent API（call/stream/streamEvents/interrupt/getToolkit） | ✅ 验证 | 全部真实调用 |
| Builder disable* 系列 | ✅ 验证 | P0-1 安全边界 |
| Tool（@Tool/Toolkit） | ✅ 验证 | 白名单 + 默认 memory/session 工具 |
| ModelRegistry / OpenAIChatModel | ✅ 验证 | OpenAI-compatible 协议链路 |
| State Store（JsonFile） | ✅ 验证 | 持久化/恢复 + 内容级验证 |
| State Store（Redis） | ❌ 未验证 | 扩展存在，待评审启用 |
| 官方 spring-boot-starter | ❌ 未采用 | SB 4.0.1 兼容性未保证 |
| DeepSeek / 企业私有 Provider | ❌ BLOCKED | 需真实凭证 |
| OtelTracingMiddleware | ⚠️ PARTIAL | span 采集已验证；生产注入待接入 |

## 6. OQ-007 Recovery 能力矩阵（PARTIAL）

| 维度 | 结论 |
|---|---|
| Session 持久化 | JsonFile 已验证（内容级） |
| resume 语义 | 同 (userId, sessionId) + 同 store 重建 Agent（已验证，跨实例） |
| cancel 语义 | 会话级优雅中断（已验证） |
| Redis Store | 扩展存在，未启用（待评审） |
| Checkpoint | 官方组件待进一步盘点 |
| Nexus vs AgentScope 边界 | Nexus 持久化业务 Task/TaskAttempt；AgentScope 持久化会话（A-004） |

## 7. 模块与 Port 边界（第二轮最终）

```text
nexus-edge-domain-agentscope-port  纯领域 Port（零 io.agentscope 依赖）
  └─ AgentExecutionPort（start/cancel/resume/stream）
  └─ AgentExecutionReference（taskId/taskAttemptId/agentId/traceId/attemptNo/status）
  └─ AgentEventEnvelope（含 eventId）/ AgentExecutionRequest / SecretResolver / TaskAttemptId(UUIDv7)

nexus-edge-agentscope-adapter     Adapter 实现
  └─ AgentscopeAgentExecutionAdapter（安全 Agent、单一执行源、SecretResolver、OTel Trace、真实终态）
  └─ AgentscopeAdapterConfig（fail-fast）/ ModelAssembler / AgentscopeAdapterConfiguration / TraceSupport
```

## 8. 测试证据（17/17 全绿，第二轮修订后）

| 测试类 | 用例数 | 结果 |
|---|---|---|
| AgentScopeMinimalExecutionTest | 2 | ✅（真实 traceId + 异步引用） |
| AgentScopeCancelTest | 3 | ✅（真实中断终态 + 互斥 + 对照） |
| AgentScopeToolCallingTest | 2 | ✅（P0-1 工具面 + P0-2 单执行源） |
| AgentScopeTraceCorrelationTest | 3 | ✅（四标识语义独立） |
| AgentScopeStreamingEventTest | 1 | ✅（P0-2/P1-8 事件契约） |
| AgentScopeStructuredOutputTest | 1 | ✅（对象解析断言） |
| AgentScopeErrorPropagationTest | 1 | ✅（500 → RetryExhaustedException） |
| AgentScopeRecoveryCapabilityTest | 3 | ✅（P1-6 内容级恢复） |
| AgentScopeSpringContextIntegrationTest | 1 | ✅（SB 4.1.0 内嵌） |
| **合计** | **17** | **0 失败**（`./mvnw clean verify`） |

## 9. 明确边界与未验证项

1. **模型 Provider 生产认证：BLOCKED**。测试端点是下游模型 Test Double。
2. **G-03 PARTIAL**：能力清单已验证大部分；Redis/真实 Provider/生产 OTel 注入待补。
3. **OQ-007 PARTIAL**：JsonFile 恢复已验证；Redis/Checkpoint 待评审。
4. **Last-Event-ID 续传**：P1-8 明确归后续持久化业务事件层，DEV-0001 只保证事件携带 eventId。
5. **GitHub Actions CI（G-06）**：本地 `./mvnw` 全量验证；CI 待建立。
6. 未实现任何业务模块；未使用 `docs/archive/legacy/`；未创建远程仓库；未修改核心版本；未合并 main；未批准 ADR。

## 10. 提交记录

第二轮修正 Commit SHA 见 `docs/handoffs/active/DEV-0001.md` §6（`git log --oneline` 可核验）。
