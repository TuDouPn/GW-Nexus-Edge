# ADR-0006 — 采用 AgentScope 2.0.1 官方 BOM 并内嵌 Harness/Core（兼容性验证结论，评审修订版）

> 状态：**Proposed（草案，待产品架构负责人批准）**
> 日期：2026-08-10（首次）；2026-08-10（CHANGES_REQUESTED 修订）
> 决策人：（待产品架构负责人）
> 关联决策/Issue：DEV-0001、G-01、G-03、OQ-007
> 关联 ADR：ADR-0001、ADR-0002、ADR-0004

## 背景

DEV-0001 需要验证 Java 21 + Spring Boot 4.1.0 + AgentScope Java 2.0.1 组合是否可作为
V1 长期后端基线（G-01），盘点 AgentScope 2.0.1 官方 API 能力（G-03）、明确
Persistence/Recovery 边界（OQ-007）。验证前不得基于 AgentScope GitHub main 分支文档
假设 2.0.1 API；不得假设官方 spring-boot-starter、RedisAgentStateStore、Provider 扩展
或 Recovery API 必然兼容。

首次评审结论 CHANGES_REQUESTED，要求：Tool 白名单、真实生命周期/ID、真实 resume、
重做事件契约、fail-fast 配置、修正测试与构建。本修订版已全部落实。

## 决策

1. **依赖管理**：采用 `io.agentscope:agentscope-bom:2.0.1` 作为全部 AgentScope 模块版本唯一来源。
2. **集成方式**：不采用 `agentscope-spring-boot-starter:2.0.1`（编译依赖 Spring Boot 4.0.1），
   采用 Spring Bean 手工装配内嵌 Harness/Core（已验证 SB 4.1.0 可行）。
3. **Tool 安全边界**：业务 Agent 只注入显式白名单只读工具（官方 Toolkit + `enableMetaTool(false)`）；
   **不授予任何 Shell / 文件系统 / Host execute 工具**。Coding Agent 的执行型 Tool
   只能经 Sandbox Broker 在 Task 独立 Sandbox 执行（22 文档，属后续 Coding 工作项）。
4. **执行生命周期**：`startExecution` 异步启动并立即返回真实引用；executionId 来自官方
   `getAgentId()`（构建时 UUID）；记录真实 userId/sessionId；`cancelExecution` 触发官方
   interrupt 并验证状态推进 CANCELLED（不抛异常 ≠ 取消成功）。
5. **事件契约**：`streamExecutionEvents` 返回 JDK 内置 `Flow.Publisher<AgentEventEnvelope>`
   （零第三方依赖，适合长期异步/SSE）；事件携带官方 eventId（Last-Event-ID 断线续传）。
6. **resume 语义**：使用官方 State Store（JsonFile/未来 Redis）同 (userId, sessionId) 重建
   Agent 继续会话；返回新 Attempt 的真实 Execution ID；无 State Store 时 fail-fast。
7. **Execution/Trace ID 边界**：AgentScope 2.0.1 **无独立 Execution ID 概念**，Adapter 以官方
   `getAgentId()` 作为执行标识；Nexus Attempt ID（attemptNo）与 AgentScope 标识的映射
   由 Nexus Edge 持久化（A-004）。traceId 来自 OpenTelemetry（`TracerRegistry` +
   `OtelTracingMiddleware`），禁止 "unassigned" 等伪造值。
8. **生产配置 fail-fast**：main 代码无任何默认值（无 test-key/test-model/localhost）；
   Secret 只允许 Reference/Provider（07 §7）；测试值仅存 test source/test profile。
9. **测试端点定位**：受控 OpenAI-compatible 端点仅是下游模型 Test Double，只能证明
   运行时与协议集成；不得用于宣称 DeepSeek 或企业私有模型已通过生产认证。

## 长期适配性

- BOM 统一版本来源；手工 Bean 装配可在 starter 成熟后平滑切换（需新 ADR）。
- Tool 白名单是强制安全不变量：业务 Agent 工具面显式收敛，不随 SDK 默认变化漂移。
- Flow.Publisher 契约客户端中立、零依赖；适配多种传输（SSE/本地）。
- Port/Adapter 分离：领域层不感知 AgentScope SDK。
- Nexus Attempt ID 与 AgentScope 标识的映射：Nexus 持久化，未来 Execution ID 标准化
  （若官方新增）可无缝替换。

## 候选方案

- **使用 agentscope-spring-boot-starter:2.0.1**：编译依赖 SB 4.0.1，未验证；拒绝（当前阶段）。
- **不使用 BOM 逐模块固定版本**：版本漂移风险；拒绝。
- **自研模型调用 SPI**：违反 A-003；拒绝。
- **业务 Agent 授予默认内置工具**：扩大攻击面，违背最小权限；拒绝。
- **Stream 事件契约**：`java.util.stream.Stream` 不适合长期异步/SSE；拒绝。
- **Redis 作为默认恢复存储**：`session-redis` 无 2.0.1；未验证前拒绝。

## 影响

- 产品：无直接产品影响；建立技术基线。
- 领域：新增 `nexus-edge-domain-agentscope-port`（纯领域，零 io.agentscope 依赖）。
- 数据：无 Schema 变化。
- API：无公共 API 变化（Port 为内部契约）。
- 安全：Tool 白名单收敛；Secret 只走 Reference；生产配置 fail-fast。
- 运维：无部署变化。
- 测试：16 个真实兼容测试纳入 CI（G-06 建立后）。
- 迁移与回退：无既有实现；后续切换 starter 需新 ADR。
- 许可：AgentScope Apache-2.0，进入第三方许可清单（G-07）。

## 验证

- G-01：Java 21 + SB 4.1.0 + AgentScope 2.0.1 共同构建 ✅（`./mvnw clean verify` 16/16）
- Enforcer：JDK 8 构建被拒绝、JDK 21 通过 ✅
- G-03：能力清单见 `docs/dev-0001/COMPATIBILITY_REPORT.md` §4 ✅
- OQ-007：能力矩阵见报告 §5（跨实例 resume 已验证）✅
- 16/16 测试全绿（2026-08-10，本地 Maven Wrapper）
- 待补：真实 Provider Smoke Test（需安全凭证）、GitHub Actions CI（G-06）

## 未解决问题

- 官方 `agentscope-spring-boot-starter` 与 SB 4.1.0 兼容性：G-02 复审。
- `RedisAgentStateStore` 是否作为生产恢复存储：OQ-007 评审后决定。
- OTel traceId 在生产链路的注入：需在 OTel 中间件启用后接入（12 §6）。
