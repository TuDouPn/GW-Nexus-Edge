# DEV-0001 — AgentScope 2.0.1 核心兼容性验证报告

> 状态：PARTIALLY_VERIFIED（模型 Provider 生产认证未验证）
> 日期：2026-08-10
> 分支：`agent/DEV-0001-agentscope-compatibility`
> 关联门禁：G-01（核心依赖兼容 PoC）、G-03（AgentScope 能力盘点）
> 关联 Open Question：OQ-007（AgentScope Persistence/Recovery 边界）

---

## 1. 结论摘要

| 验证项 | 结论 | 证据 |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 共同构建 | **通过** | Maven BUILD SUCCESS，17 测试全绿 |
| Spring Boot 4.1.0 内嵌 AgentScope Harness/Core | **通过** | `AgentScopeSpringContextIntegrationTest` |
| 最小真实 Agent 执行 | **通过** | `AgentScopeMinimalExecutionTest`（官方 HarnessAgent.call） |
| 官方 Model Provider（OpenAI-compatible）/ 测试端点 | **通过（协议层）** | 真实 HTTP 请求到达受控端点 |
| Streaming Event | **通过** | `AgentScopeStreamingEventTest`（官方 streamEvents） |
| Tool Calling | **通过** | `AgentScopeToolCallingTest`（@Tool → 官方 Toolkit） |
| Structured Output | **通过** | `AgentScopeStructuredOutputTest`（call + Class） |
| Cancel（官方 interrupt） | **通过** | `AgentScopeCancelTest`（delegate.interrupt(RuntimeContext)） |
| Error Propagation | **通过** | `AgentScopeErrorPropagationTest`（500 → RetryExhaustedException） |
| Persistence/Recovery 能力边界（OQ-007） | **能力矩阵已建立** | `AgentScopeRecoveryCapabilityTest` |
| Task / Execution / Trace 关联 | **通过** | `AgentScopeTraceCorrelationTest` |
| DeepSeek / 企业私有模型生产认证 | **未验证（BLOCKED）** | 需要真实凭据与安全提供的测试凭证 |

**G-01 判定：通过**（核心三版本组合可共同构建、启动、运行）。
**G-03 判定：能力清单已形成**（见 §4），部分能力仍需真实 Provider 验证。
**OQ-007 判定：证据已产生**（见 §5），Redis 恢复测试待能力矩阵评审后决定是否启用。

## 2. 版本证据（不依赖 GitHub main 分支文档）

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
理由：BOM 官方维护、统一 core/harness/扩展版本、避免版本漂移。已在 `backend/pom.xml` 通过 `import` 使用。

**starter 采用决策**：**不采用** `agentscope-spring-boot-starter:2.0.1`（当前阶段）。
理由：该 starter 编译依赖 Spring Boot 4.0.1；DEV-0001 通过手工 Bean 装配验证了 SB 4.1.0 内嵌
Harness/Core 可行，避免引入未经兼容验证的自动装配层。此决策待 G-02 外围依赖冻结时复审。

## 3. 依赖树关键结论

```
agentscope-harness 2.0.1 ──→ agentscope-core 2.0.1
agentscope-extensions-model-openai 2.0.1 → reactor-core 3.8.6, jackson 2.21.4
agentscope-extensions-redis 2.0.1 → jedis 7.4.1, redisson 4.2.0, lettuce 7.5.2
org.springframework.boot:spring-boot-starter 4.1.0 → spring-core 7.0.8
```

无版本冲突；Maven 依赖仲裁均解析为预期版本。

## 4. G-03 AgentScope 2.0.1 能力清单（基于 Sources JAR 盘点）

### 4.1 Agent API
- `HarnessAgent`（harness 包）：`call`/`stream`/`streamEvents`/`interrupt`/`getDelegate`/`close`
- `ReActAgent.Builder`：`name`/`sysPrompt`/`model(String|Model)`/`toolkit`/`workspace`/`stateStore`/`maxIters`
- **解析时机证据**：`model(String)` 在 builder 调用时立即通过 `ModelRegistry.resolve` 解析——
  必须先注册模型再构建 Agent。

### 4.2 事件模型（`io.agentscope.core.event`）
- `AgentEvent` 抽象基类：`getType`/`getId`/`getCreatedAt`/`getSource`/`getMetadata`，
  含 `METADATA_TASK_ID = "taskId"` 常量（Task 关联注入点）
- `AgentEventType` 枚举：`AGENT_START`/`AGENT_END`/`AGENT_RESULT`/`MODEL_CALL_*`/
  `TEXT_BLOCK_*`/`THINKING_BLOCK_*`/`TOOL_CALL_*`/`TOOL_RESULT_*`/`EXCEED_MAX_ITERS`/`REQUEST_STOP`/`CUSTOM`

### 4.3 Runtime Context
- `RuntimeContext.builder().sessionId().userId().build()`：会话级隔离
- **线程安全证据**：官方文档明确 Agent 单实例不保证并发调用安全 → Adapter 为每个 Task
  构建独立 HarnessAgent（已实现）

### 4.4 Tool
- `@Tool`/`@ToolParam` 注解 + `Toolkit.registerTool(Object)`（反射扫描）
- `ToolBase.Builder`：`name`/`description`/`inputSchema`/`readOnly`/`concurrencySafe`/`externalTool`
- `Toolkit.getToolSchemas(activeGroups)`：**ungrouped 工具始终可见**（实测证据）

### 4.5 Model
- `ModelRegistry.register(name, Model)`（覆盖式）/`resolve`/`registerFactory`
- `OpenAIChatModel.Builder`：`apiKey`/`modelName`/`baseUrl`/`endpointPath`/`stream`/`nativeStructuredOutput`
- OpenAI 兼容端点默认路径 `/v1/chat/completions`
- **默认重试证据**：端点 500 时官方以 `RetryExhaustedException`（2 次重试）传播错误

### 4.6 State / Persistence（OQ-007 输入）
- `AgentStateStore` 接口：`save(userId, sessionId, key, State)`/`exists`/`listSessionIds`/`delete`
- 官方实现：`InMemoryAgentStateStore`、`JsonFileAgentStateStore`（本地文件）、
  `RedisAgentStateStore`（需注入 jedis/lettuce/redisson client）
- **HarnessAgent 默认启用 Memory 中间件**：每次调用后执行 memory extraction 请求（实测证据）

## 5. OQ-007 Recovery 能力矩阵

| 维度 | 结论 |
|---|---|
| 官方 State Store 类型 | InMemory / JsonFile（本地）/ Redis（扩展）/ 分布式 Store（接口） |
| Session 持久化 | `AgentStateStore.save(userId, sessionId, ...)`，JsonFile 可跨进程重载（已验证） |
| Execution 持久化 | AgentScope 以 (userId, sessionId) 定位会话状态 |
| Memory 持久化 | Harness 默认 Memory 中间件 + JsonFile/Redis Store |
| Checkpoint | AgentScope 官方 Persistence/Recovery 组件（OQ-007 待进一步盘点） |
| cancel 语义 | 会话级优雅中断：`delegate.interrupt(RuntimeContext)`，返回恢复消息 |
| resume 语义 | 复用 (userId, sessionId) 会话恢复 |
| 进程重启恢复粒度 | 业务步骤级（03 §6）；Token 级续跑不要求 |
| Redis 官方职责 | `agentscope-extensions-redis:2.0.1` 存在，需显式注入 client；**session-redis 无 2.0.1** |
| Nexus vs AgentScope 边界 | Nexus 持久化业务 Task 状态；AgentScope 持久化 Execution/会话状态（A-004） |

**Redis 恢复测试启用建议**：待评审确认 `RedisAgentStateStore` 满足 DEV-0001 需要后，
以 Testcontainers 增加独立测试；当前保持不预设 Redis 为最终恢复存储。

## 6. 模块与 Port 边界（最终确认）

```text
nexus-edge-domain-agentscope-port  纯领域 Port（零 io.agentscope 依赖，零第三方依赖）
  └─ AgentExecutionPort（startExecution / cancelExecution / resumeExecution / streamExecutionEvents）
      └─ AgentExecutionRequest / AgentExecutionReference / AgentEventEnvelope

nexus-edge-agentscope-adapter     Adapter 实现（依赖 Port + 官方 SDK）
  └─ AgentscopeAgentExecutionAdapter implements AgentExecutionPort
      └─ AgentscopeAdapterConfig / ModelAssembler / AgentscopeAdapterConfiguration
```

- Port 不依赖任何 `io.agentscope` 类型 ✅
- Adapter 依赖 Port，领域层不依赖 Adapter ✅
- 未创建未来需迁移的临时生产接口 ✅
- Java 包名统一 `com.gwnexusedge.nexus.edge` ✅

## 7. 测试证据（17/17 全绿）

| 测试类 | 用例数 | 结果 |
|---|---|---|
| AgentScopeMinimalExecutionTest | 2 | ✅ |
| AgentScopeStreamingEventTest | 2 | ✅ |
| AgentScopeToolCallingTest | 2 | ✅ |
| AgentScopeStructuredOutputTest | 1 | ✅ |
| AgentScopeCancelTest | 2 | ✅ |
| AgentScopeErrorPropagationTest | 1 | ✅ |
| AgentScopeRecoveryCapabilityTest | 3 | ✅ |
| AgentScopeSpringContextIntegrationTest | 1 | ✅ |
| AgentScopeTraceCorrelationTest | 3 | ✅ |
| **合计** | **17** | **0 失败** |

## 8. 明确边界与未验证项

1. **模型 Provider 生产认证：BLOCKED**。测试端点是下游模型 Test Double，只证明运行时与
   协议集成；DeepSeek 官方 Provider 与企业私有模型需真实凭据 + 安全提供的测试凭证
   （独立、可选、不可在不可信 PR 运行的 Smoke Test）。
2. **GitHub Actions CI（G-06）**：本地已全部验证；CI 建立后需将本套件纳入流水线。
3. **starter 自动装配**：未采用官方 starter（见 §2），G-02 时复审。
4. **Redis 恢复测试**：未启用（见 §5）。
5. 未实现任何业务模块（Workspace/Knowledge/Skill/Artifact/Renderer/Coding/Desktop）。
6. 未使用 `docs/archive/legacy/`。
7. 未创建远程仓库、未 Push、未修改核心版本。
