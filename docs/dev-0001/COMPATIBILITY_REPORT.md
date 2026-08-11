# DEV-0001 — AgentScope 2.0.1 核心兼容性验证报告（第四轮评审修订版）

> 状态：PARTIALLY_VERIFIED（G-01 通过；G-03/OQ-007 为 PARTIAL；模型 Provider 生产认证未验证）
> 日期：2026-08-10（首次）；2026-08-10~11（四轮 CHANGES_REQUESTED 修订）
> 分支：`agent/DEV-0001-agentscope-compatibility`
> 关联门禁：G-01（核心依赖兼容 PoC，**通过**）、G-03（AgentScope 能力盘点，**PARTIAL**）
> 关联 Open Question：OQ-007（Persistence/Recovery 边界，**PARTIAL**）

---

## 1. 结论摘要

| 验证项 | 结论 | 证据 |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 共同构建 | **G-01 通过** | `./mvnw clean verify` BUILD SUCCESS，30 测试全绿 |
| Enforcer fail-fast（Java 21 / Maven 3.9） | **通过** | JDK 8 拒绝、JDK 21 通过 |
| Harness 安全边界 | **通过** | 工具面 `[echo_text, wait_async_results]`，无危险工具 |
| Trace 异步上下文隔离 | **通过** | contextWrite 传播；并发 Task traceId 隔离 |
| **Trace doFinally + 父子 span** | **通过** | OTel Exporter 验证父子关联 + 全部 span 结束 |
| **EventStreams（Reactor Sinks）** | **通过** | 多订阅者/延迟重放/恰好一次/严格顺序/背压/取消/有界缓存 |
| **单一终态** | **通过** | 每 TaskAttempt 只产生一个 COMPLETED |
| **workspace/tenant 持久化** | **通过** | 存入 State Store，resume 恢复；从 RuntimeContext 读取 |
| Secret Reference/Value | **通过** | Reference 传递 + Authorization 头（断言消息无明文） |
| Spring 测试顺序独立 | **通过** | 独立运行 + 全局状态重置 + JSONObject 重复排除 |
| 单一执行源 | **通过** | 订阅前后请求数不变 |
| Cancel 真实中断 | **通过** | 终态互斥；删除 interrupt 对照完成 |
| Recovery 上下文内容 | **通过** | 跨实例恢复含标记；ModelRegistry 重置 |
| 标识模型（P1-7） | **通过** | taskId/taskAttemptId/agentId/traceId/replyId 独立 |
| G-03 能力盘点 | **PARTIAL** | 能力清单已验证大部分；Redis/真实 Provider 待补 |
| OQ-007 Recovery 边界 | **PARTIAL** | JsonFile 已验证；Redis/Checkpoint 待评审 |
| DeepSeek / 企业私有模型生产认证 | **未验证（BLOCKED）** | 需要真实凭据与安全测试凭证 |

## 2. 第四轮评审修订落实情况

| 评审项 | 修正内容 | 验证证据 |
|---|---|---|
| **替换自研 EventSink** | 删除自研 `EventSink`，改用 Reactor `Sinks.many().replay().limit(256)`（`EventStreams`）；多订阅者广播、延迟重放、恰好一次、严格顺序、独立背压/取消、有界缓存 | `EventStreamsSemanticsTest` 4/4 |
| **单一终态事件** | `mapEvent` 仅 `AGENT_RESULT`→COMPLETED，`AGENT_END`→PROGRESS；`transitionTerminal` 保证终态唯一 | `AgentScopeRound4SemanticsTest.singleTerminalEventPerAttempt` |
| **workspace/tenant 持久化** | `ExecutionContextState` 存入 State Store（start），resume 从 Store 恢复注入 RuntimeContext；`executionContext(taskId)` 从 **AgentScope RuntimeContext extras** 读取（非 Request 自证） | `AgentScopeRound4SemanticsTest.workspaceTenantReadableFromTool` |
| **Trace doFinally + Exporter** | span 在 `doFinally` 幂等结束（覆盖 complete/error/cancel/dispose）；注册 `OtelTracingMiddleware`；`InMemorySpanExporter` 验证父子 span | `AgentScopeRound4SemanticsTest.traceParentChildRelationshipAndCompletion` |
| **JSONObject 如实记录** | 真实记录 Spring `DuplicateJsonObjectContextCustomizer` 警告（org.json + android-json）；**排除 android-json**（jsonassert 运行时可用 org.json） | 排除后警告消失，Spring 测试通过 |
| **删除 Secret 明文** | 断言消息不再输出 Authorization/解析值 | 测试源码扫描无明文 |
| **executionId/agentId 语义** | ADR-0006 决策 + Blueprint 16 术语表新增 `AgentScope AgentId`/`Nexus TaskAttemptId` 条目与禁止混用规则 | 本报告 + ADR-0006 + Blueprint 16 |

## 3. 版本证据与依赖分析（如实陈述）

### 3.1 Artifact 版本（Maven Central 实测）

| Artifact | 2.0.1 | 证据 |
|---|---|---|
| agentscope-bom/core/harness/model-openai/redis | ✅ | POM + Sources JAR + dependency:tree |
| agentscope-extensions-session-redis/mysql | ❌ 不存在 | 仅 1.x 与 2.0.0-RC1 |
| agentscope-spring-boot-starter | ✅ 存在 | 编译依赖 SB 4.0.1（optional），未采用 |

### 3.2 JSONObject 依赖（第四轮：真实警告 + 排除结论）

- **真实警告（Spring 测试）**：`DuplicateJsonObjectContextCustomizer` 报告
  `org.json.JSONObject` 在 classpath 出现两次：
  - `org/json/json-20251224.jar`（compile，来源 mcp-json）；
  - `com/vaadin/external/google/android-json-0.0.20131108.vaadin1.jar`（test，来源 jsonassert）。
- **排除结论**：在 `spring-boot-starter-test` 排除 `android-json`；验证 jsonassert
  运行时使用 classpath 中唯一剩余的 `org.json`，Spring 测试通过、警告消失。
- **边界**：android-json 仅在 test scope；生产 classpath 不含重复 JSONObject。

### 3.3 Harness 默认行为（如实描述，含已知局限）

- 默认 filesystem 为 `LocalFilesystemSpec` → `LocalFilesystemWithShell`（ShellAwareOverlay，
  project 默认 `${user.dir}`）。
- 本适配器：`disableFilesystemTools/disableShellTool/disableSubagents/disableDynamicSubagents/
  disableDynamicSkills/disableDefaultWorkspaceSkills/disableMemoryTools` + `enableMetaTool(false)`
  + 注册 `OtelTracingMiddleware`。
- **已知局限**：底层 filesystem 仍是本地 overlay；生产业务 Agent 需显式
  `filesystem(LocalFilesystemSpec)` 限定工作区（Phase 3）；Coding Sandbox 完整隔离由 22 文档承担。

## 4. 关键运行证据（第四轮）

### 4.1 EventStreams 语义（Reactor Sinks replay，有界缓存 256）

- 多订阅者：两个订阅者都收到全部事件；延迟订阅收到历史。
- 恰好一次：事件 id 无重复；严格顺序：与发布一致。
- 有界缓存：超过 256 时延迟订阅只收到最近 256 个（最旧丢弃）。
- 取消订阅：不影响其他订阅者。

### 4.2 单一终态

- `AGENT_RESULT`→COMPLETED（恰好一次），`AGENT_END`→PROGRESS。
- 每 TaskAttempt 只有一个业务终态事件（`singleTerminalEventPerAttempt`）。

### 4.3 Trace 父子 span（OTel Exporter，真实导出）

```
nexus-edge.agent.execution (spanId=3c56...) ← invoke_agent nexus-edge-compat-agent (parent=3c56...) ← chat test-model
```

- 同一 traceId；我们的 span 为根，AgentScope 的 `invoke_agent` 为子，`chat` 为孙。
- 全部 span 已结束导出（doFinally 幂等）。

### 4.4 workspace/tenant 持久化

- start 存入 State Store（`ExecutionContextState`）；resume 从 Store 恢复注入。
- `executionContext(taskId)` 从 **AgentScope RuntimeContext extras** 读取（非 Request 自证）。

## 5. G-03 AgentScope 2.0.1 能力清单（PARTIAL）

| 能力 | 状态 | 说明 |
|---|---|---|
| HarnessAgent API | ✅ | call/stream/streamEvents/interrupt/getToolkit |
| Builder disable* 系列 | ✅ | 含 disableMemoryTools |
| Tool（@Tool/Toolkit/ToolBase） | ✅ | @Tool 注解路径验证；ToolBase 子类在 HarnessAgent 下工具执行有兼容限制（如实记录） |
| ModelRegistry / OpenAIChatModel | ✅ | 协议链路 |
| State Store（JsonFile） | ✅ | 持久化/恢复 + 内容级 + 上下文 |
| OtelTracingMiddleware | ✅ | 父子 span 已验证 |
| State Store（Redis） | ❌ 未验证 | 待评审 |
| 官方 spring-boot-starter | ❌ 未采用 | SB 4.0.1 未保证 |
| DeepSeek / 企业私有 Provider | ❌ BLOCKED | 需真实凭证 |

## 6. OQ-007 Recovery 能力矩阵（PARTIAL）

| 维度 | 结论 |
|---|---|
| Session 持久化 | JsonFile 已验证（内容级） |
| resume 语义 | 同 (userId, sessionId) + 同 store 重建（跨实例） |
| 上下文恢复 | workspaceId/tenantId 存入/恢复已验证 |
| cancel 语义 | 会话级优雅中断（已验证） |
| Redis Store | 扩展存在，未启用 |
| Checkpoint | 官方组件待盘点 |

## 7. 测试证据（30/30 全绿，第四轮修订后）

| 测试类 | 用例 | 结果 | 关键断言 |
|---|---|---|---|
| AgentScopeMinimalExecutionTest | 2 | ✅ | 异步引用 + 真实 traceId |
| AgentScopeWorkspaceTenantContextTest | 1 | ✅ | workspace/tenant 注入 |
| AgentScopeCancelTest | 3 | ✅ | 真实中断终态 + 互斥 |
| AgentScopeDelayedSubscriptionTest | 1 | ✅ | 延迟订阅完整事件 |
| AgentScopeToolCallingTest | 2 | ✅ | 工具面 + 单执行源 |
| AgentScopeSecretResolutionTest | 2 | ✅ | Secret Reference + 请求头（无明文） |
| AgentScopeTraceCorrelationTest | 4 | ✅ | 四标识 + replyId |
| AgentScopeSpringContextIntegrationTest | 1 | ✅ | SB 4.1.0 内嵌 + JSONObject 排除 |
| AgentScopeConcurrentTraceIsolationTest | 1 | ✅ | 并发 Trace 隔离 |
| AgentScopeErrorPropagationTest | 1 | ✅ | 500 → RetryExhaustedException |
| AgentScopeRecoveryCapabilityTest | 3 | ✅ | 内容级恢复 |
| **AgentScopeRound4SemanticsTest** | 3 | ✅ | **单一终态 + RuntimeContext 读取 + 父子 span** |
| **EventStreamsSemanticsTest** | 4 | ✅ | **多订阅者/延迟/唯一/顺序/取消/缓存边界** |
| AgentScopeStructuredOutputTest | 1 | ✅ | 对象解析 |
| AgentScopeStreamingEventTest | 1 | ✅ | 事件同源 + 单执行源 |
| **合计** | **30** | **0 失败** | `./mvnw clean verify` |

## 8. 明确边界与未验证项

1. **模型 Provider 生产认证：BLOCKED**。
2. **宿主 filesystem overlay**（如实记录）：disable* 移除工具，但底层 overlay 仍在；
   生产需显式 `filesystem(LocalFilesystemSpec)`（Phase 3）。
3. **ToolBase 子类工具在 HarnessAgent 下执行限制**（如实记录）：`@Tool` 注解路径已验证；
   ToolBase 子类 `callAsync` 在 HarnessAgent 工具执行阶段未被调用，workspace/tenant 验证
   改用 AgentScope RuntimeContext 读取。
4. **G-03/OQ-007：PARTIAL**（Redis/真实 Provider 待补）。
5. **Last-Event-ID 续传**：归后续持久化业务事件层。

## 9. 提交记录

第四轮修正 Commit SHA 见 `docs/handoffs/active/DEV-0001.md` §6（`git log --oneline` 可核验）。
