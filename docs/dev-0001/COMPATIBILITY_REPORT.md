# DEV-0001 — AgentScope 2.0.1 核心兼容性验证报告（评审修订版）

> 状态：PARTIALLY_VERIFIED（模型 Provider 生产认证未验证）
> 日期：2026-08-10（首次）；2026-08-10（CHANGES_REQUESTED 修订）
> 分支：`agent/DEV-0001-agentscope-compatibility`
> 关联门禁：G-01（核心依赖兼容 PoC）、G-03（AgentScope 能力盘点）
> 关联 Open Question：OQ-007（AgentScope Persistence/Recovery 边界）
> 评审结论：CHANGES_REQUESTED → 已按 9 项修订全部修正

---

## 1. 结论摘要

| 验证项 | 结论 | 证据 |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 共同构建 | **通过** | Maven Wrapper `clean verify` BUILD SUCCESS，16 测试全绿 |
| Enforcer fail-fast（Java 21 / Maven 3.9） | **通过** | JDK 8 下构建被拒绝；JDK 21 下通过 |
| Spring Boot 4.1.0 内嵌 AgentScope Harness/Core | **通过** | `AgentScopeSpringContextIntegrationTest` |
| 最小真实 Agent 执行（异步生命周期） | **通过** | `AgentScopeMinimalExecutionTest`：长任务完成前返回真实引用 |
| 官方 Model Provider（OpenAI-compatible）/ 测试端点 | **通过（协议层）** | 真实 HTTP 请求到达受控端点 |
| Streaming Event（Flow.Publisher 契约） | **通过** | `AgentScopeStreamingEventTest`：事件非空、同源、携带续传 id |
| Tool Calling（显式白名单） | **通过** | `AgentScopeToolCallingTest`：仅白名单工具，禁 meta tool |
| Structured Output（对象成功解析） | **通过** | `AgentScopeStructuredOutputTest`：断言 STRUCTURED_OUTPUT 元数据 |
| Cancel（官方 interrupt，验证在途中断） | **通过** | `AgentScopeCancelTest`：状态推进 CANCELLED，非"不抛异常" |
| Error Propagation | **通过** | `AgentScopeErrorPropagationTest`：500 → RetryExhaustedException |
| Persistence/Recovery（真实 resume） | **通过** | `AgentScopeRecoveryCapabilityTest`：跨实例会话恢复，真实 Execution ID |
| Task / Execution / Trace 关联（真实 ID） | **通过** | `AgentScopeTraceCorrelationTest`：拒绝空值/"unassigned" |
| DeepSeek / 企业私有模型生产认证 | **未验证（BLOCKED）** | 需要真实凭据与安全提供的测试凭证 |

**G-01 判定：通过**（核心三版本组合可共同构建、启动、运行）。
**G-03 判定：能力清单已形成**（见 §4）。
**OQ-007 判定：证据已产生**（见 §5），Redis 恢复测试待能力矩阵评审后决定是否启用。

## 2. 评审修订落实情况（9 项全数修正）

| 评审项 | 修正内容 | 验证 |
|---|---|---|
| 1. Tool 白名单/文件系统边界 | 显式白名单 Toolkit + `enableMetaTool(false)`；业务 Agent 无 Shell/File/Host execute 工具 | `AgentScopeToolCallingTest.toolSurfaceIsWhitelistOnly` |
| 2. 执行生命周期 | 异步启动立即返回真实引用；记录真实 userId/sessionId；cancel 验证在途中断（状态 CANCELLED） | `AgentScopeMinimalExecutionTest`、`AgentScopeCancelTest` |
| 3. resume 真实实现 | 删除伪 ID；用官方 State Store 同 (userId, sessionId) 重建 Agent 继续会话；无 Store 时 fail-fast | `AgentScopeRecoveryCapabilityTest` |
| 4. 事件契约 | Port 返回 `Flow.Publisher<AgentEventEnvelope>`；订阅原执行真实事件；事件非空/同源/携带续传 id | `AgentScopeStreamingEventTest` |
| 5. Execution/Trace ID 真实 | executionId=官方 `getAgentId()`（真实 UUID）；traceId=OTel 真实 span；拒绝空值/"unassigned" | `AgentScopeTraceCorrelationTest`、`TestOtel` |
| 6. 生产配置 fail-fast | 删除 main 默认值；apiKey 改 Secret Reference 语义；必填属性缺失即启动失败；测试值仅存 test | `AgentscopeAdapterConfig`、`AgentscopeAdapterConfiguration` |
| 7. 测试修正 | Structured 断言对象解析；Recovery 跨实例；Cancel 验证在途；Trace 拒绝空值；Event 非空；Error 修复恒真 | 16/16 全绿 |
| 8. 构建修正 | Maven Wrapper；Enforcer（Java21/Maven3.9）；Testcontainers 版本统一管理 | `./mvnw`、`mvn validate` 双向验证 |
| 9. 文档同步 | 本报告、ADR-0006、Handoff 更新，写入真实 Commit SHA | 见 §9 |

## 3. 版本证据（不依赖 GitHub main 分支文档）

以下结论均基于 **Maven Central 2.0.1 实际 Artifact/POM + Sources JAR + dependency:tree + 编译/运行测试**：

| Artifact | 2.0.1 存在性 | 证据 |
|---|---|---|
| `io.agentscope:agentscope-bom:2.0.1` | ✅ | Maven Central HTTP 200，POM 检查 |
| `io.agentscope:agentscope-core:2.0.1` | ✅ | POM + Sources JAR |
| `io.agentscope:agentscope-harness:2.0.1` | ✅ | POM + Sources JAR |
| `io.agentscope:agentscope-extensions-model-openai:2.0.1` | ✅ | POM + Sources JAR |
| `io.agentscope:agentscope-extensions-redis:2.0.1` | ✅ | POM + Sources JAR（jedis/redisson/lettuce） |
| `agentscope-extensions-session-redis/mysql:2.0.1` | ❌ **不存在** | 仅 1.x 与 2.0.0-RC1 |
| `agentscope-spring-boot-starter:2.0.1` | ✅ 存在 | 编译依赖 Spring Boot **4.0.1**（optional），与 4.1.0 兼容性未经官方保证 |

**BOM 采用决策**：**采用** `agentscope-bom:2.0.1` 作为全部 AgentScope 依赖版本唯一来源。
**starter 采用决策**：**不采用** `agentscope-spring-boot-starter:2.0.1`（当前阶段），手工 Bean 装配已验证可行（G-02 复审）。

**依赖冲突分析（评审项 8）**：
- `org.json:json:20251224`（compile）：唯一来源 `io.modelcontextprotocol.sdk:mcp-json:0.17.0`（AgentScope 聚合），无重复。
- `com.vaadin.external.google:android-json`（test）：来源 `jsonassert`（Spring Test），包名与 org.json 不同，无冲突。
- **结论：无重复 JSONObject 冲突；Maven 未报告冲突警告。** 两库包名空间独立，保留现状并在本报告记录来源。

## 4. G-03 AgentScope 2.0.1 能力清单（基于 Sources JAR 盘点）

### 4.1 Agent API
- `HarnessAgent`：`call`/`stream`/`streamEvents`/`interrupt`/`getDelegate`/`getAgentId`/`close`
- `ReActAgent.Builder`：`name`/`sysPrompt`/`model(String|Model)`/`toolkit`/`workspace`/`stateStore`/`maxIters`/`enableMetaTool`
- **解析时机证据**：`model(String)` 在 builder 调用时立即通过 `ModelRegistry.resolve` 解析。
- **标识证据**：`getAgentId()` 返回构建时 `UUID.randomUUID()` 真实标识（可作为 Execution/Agent 标识）。

### 4.2 事件模型（`io.agentscope.core.event`）
- `AgentEvent`：`getType`/`getId`/`getCreatedAt`/`getSource`/`getMetadata`，含 `METADATA_TASK_ID`
- `AgentEventType`：`AGENT_START`/`AGENT_END`/`AGENT_RESULT`/`MODEL_CALL_*`/`TEXT_BLOCK_*`/`THINKING_BLOCK_*`/`TOOL_CALL_*`/`TOOL_RESULT_*`/`EXCEED_MAX_ITERS`/`REQUEST_STOP`/`CUSTOM`

### 4.3 Runtime Context
- `RuntimeContext.builder().sessionId().userId().build()`；**线程安全证据**：单实例不保证并发安全 → Adapter 每 Task 独立 HarnessAgent。

### 4.4 Tool
- `@Tool`/`@ToolParam` + `Toolkit.registerTool(Object)`；`ToolBase.Builder`；ungrouped 工具始终可见；`enableMetaTool(false)` 禁用动态工具面。

### 4.5 Model
- `ModelRegistry.register/resolve/registerFactory`；`OpenAIChatModel.Builder`（apiKey/modelName/baseUrl/stream/nativeStructuredOutput）；默认端点 `/v1/chat/completions`；**默认重试**：500 → `RetryExhaustedException`（2 次）。

### 4.6 State / Persistence（OQ-007 输入）
- `AgentStateStore`：`save/exists/listSessionIds/get/delete`；官方实现 InMemory/JsonFile/Redis（需注入 client）。
- **HarnessAgent 默认 Memory 中间件**：每次调用后执行 memory extraction 请求（实测证据）。

## 5. OQ-007 Recovery 能力矩阵

| 维度 | 结论 |
|---|---|
| 官方 State Store 类型 | InMemory / JsonFile（本地）/ Redis（扩展）/ 分布式 Store 接口 |
| Session 持久化 | `AgentStateStore.save(userId, sessionId, ...)`，JsonFile 可跨进程重载（已验证） |
| resume 语义 | 同 (userId, sessionId) + 同 store 重建 Agent，`getAgentState` 懒加载恢复会话上下文（已验证，跨 Adapter 实例） |
| cancel 语义 | 会话级优雅中断：`delegate.interrupt(RuntimeContext)`，返回恢复消息（已验证） |
| Execution 标识 | 官方 `getAgentId()`（UUID）；**AgentScope 无独立 Execution ID 概念** |
| Trace 标识 | `TracerRegistry` + `OtelTracingMiddleware`（GlobalOpenTelemetry）；无 SDK 时 Noop |
| 进程重启恢复粒度 | 业务步骤级（03 §6）；Token 级续跑不要求 |
| Redis 官方职责 | `agentscope-extensions-redis:2.0.1` 存在，需显式注入 client；**session-redis 无 2.0.1** |
| Nexus vs AgentScope 边界 | Nexus 持久化业务 Task 状态；AgentScope 持久化 Execution/会话状态（A-004） |

**Redis 恢复测试启用建议**：待评审确认 `RedisAgentStateStore` 满足需要后，以 Testcontainers 增加独立测试；当前保持不预设 Redis。

## 6. 模块与 Port 边界（评审后最终确认）

```text
nexus-edge-domain-agentscope-port  纯领域 Port（零 io.agentscope 依赖）
  └─ AgentExecutionPort
      └─ startExecution / cancelExecution / resumeExecution
      └─ streamExecutionEvents → Flow.Publisher<AgentEventEnvelope>（JDK 内置，适合 SSE）
      └─ AgentExecutionRequest / AgentExecutionReference（含 userId/sessionId/attemptNo/status）
         / AgentEventEnvelope（含 eventId，Last-Event-ID）

nexus-edge-agentscope-adapter     Adapter 实现（依赖 Port + 官方 SDK）
  └─ AgentscopeAgentExecutionAdapter（异步生命周期、Tool 白名单、真实 ID、resume via State Store）
      └─ AgentscopeAdapterConfig / ModelAssembler / AgentscopeAdapterConfiguration
```

- Port 不依赖任何 `io.agentscope` 类型 ✅
- Adapter 依赖 Port，领域层不依赖 Adapter ✅
- Java 包名统一 `com.gwnexusedge.nexus.edge` ✅
- 生产配置 fail-fast，无 main 默认测试值 ✅

## 7. 测试证据（16/16 全绿，评审修订后）

| 测试类 | 用例数 | 结果 | 关键断言 |
|---|---|---|---|
| AgentScopeMinimalExecutionTest | 2 | ✅ | 异步返回真实引用；executionId 为真实 UUID；COMPLETED |
| AgentScopeStreamingEventTest | 1 | ✅ | 事件非空、同 Task/Execution、eventId 续传 |
| AgentScopeToolCallingTest | 2 | ✅ | 工具定义真实发送；白名单仅 echo_text，无执行类工具 |
| AgentScopeStructuredOutputTest | 1 | ✅ | STRUCTURED_OUTPUT 元数据存在且为 Map（解析成功） |
| AgentScopeCancelTest | 2 | ✅ | 在途执行被中断；状态 CANCELLED 而非不抛异常 |
| AgentScopeErrorPropagationTest | 1 | ✅ | 500 → 结构化异常（直接断言捕获异常，非恒真） |
| AgentScopeRecoveryCapabilityTest | 3 | ✅ | 跨实例 resume；真实 Execution ID；无 Store fail-fast |
| AgentScopeSpringContextIntegrationTest | 1 | ✅ | SB 4.1.0 内嵌 AgentScope 真实执行 |
| AgentScopeTraceCorrelationTest | 3 | ✅ | 真实 OTel Trace ID；拒绝空值/"unassigned" |
| **合计** | **16** | **0 失败** | `./mvnw clean verify` |

## 8. 明确边界与未验证项

1. **模型 Provider 生产认证：BLOCKED**。测试端点是下游模型 Test Double，只证明运行时与协议集成；DeepSeek/企业私有模型需真实凭据 + 安全测试凭证（独立可选 Smoke Test）。
2. **GitHub Actions CI（G-06）**：本地 `./mvnw` 全量验证；CI 建立后纳入流水线。
3. **starter 自动装配**：未采用官方 starter（§3），G-02 时复审。
4. **Redis 恢复测试**：未启用（§5）。
5. **OTel traceId 注入到引用**：测试直接验证 OTel 可产生真实 Trace ID；Adapter 生产链路需在 OTel 中间件启用后接入（12 §6 关联）。
6. 未实现任何业务模块；未使用 `docs/archive/legacy/`；未创建远程仓库、未 Push、未修改核心版本。

## 9. 提交记录（真实 Commit SHA）

评审修订后的 Commit SHA 见 `docs/handoffs/active/DEV-0001.md` §6（`git log --oneline` 可核验）。
