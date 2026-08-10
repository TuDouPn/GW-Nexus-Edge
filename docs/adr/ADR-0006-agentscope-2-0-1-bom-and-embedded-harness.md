# ADR-0006 — 采用 AgentScope 2.0.1 官方 BOM 并内嵌 Harness/Core（兼容性验证结论）

> 状态：**Proposed（草案，待产品架构负责人批准）**
> 日期：2026-08-10
> 决策人：（待产品架构负责人）
> 关联决策/Issue：DEV-0001、G-01、G-03、OQ-007
> 关联 ADR：ADR-0001、ADR-0002、ADR-0004

## 背景

DEV-0001 需要验证 Java 21 + Spring Boot 4.1.0 + AgentScope Java 2.0.1 组合是否可作为
V1 长期后端基线（G-01），并盘点 AgentScope 2.0.1 官方 API 能力（G-03）、明确
Persistence/Recovery 边界（OQ-007）。在验证前，不得基于 AgentScope GitHub main 分支
文档假设 2.0.1 API；也不得假设官方 spring-boot-starter、RedisAgentStateStore、Provider
扩展或 Recovery API 必然兼容。

## 决策

1. **依赖管理**：采用 `io.agentscope:agentscope-bom:2.0.1` 作为全部 AgentScope 模块
   （core/harness/扩展）的版本唯一来源，通过 Maven `dependencyManagement` import 使用。
2. **集成方式**：不采用 `agentscope-spring-boot-starter:2.0.1`（编译依赖 Spring Boot 4.0.1），
   采用 Spring Bean 手工装配将 AgentScope Harness/Core 内嵌于 Spring Boot 4.1.0 上下文。
3. **Adapter 边界**：领域层仅依赖 `nexus-edge-domain-agentscope-port`（零 io.agentscope 依赖）；
   `nexus-edge-agentscope-adapter` 依赖 Port 与官方 SDK 并实现四个冻结业务语义。
4. **测试端点定位**：受控 OpenAI-compatible 端点仅是下游模型 Test Double，只能证明
   运行时与协议集成；不得用于宣称 DeepSeek 或企业私有模型 Provider 已通过生产认证。
5. **恢复存储**：不预设 Redis 为最终 AgentScope 恢复存储；先以 JsonFile 官方 Store 验证
   持久化/恢复边界（OQ-007），Redis 测试仅在能力矩阵评审后另行启用。

## 长期适配性

- BOM 统一版本来源：AgentScope 版本升级时只需改 BOM 版本，核心代码不因模块版本漂移而断裂。
- 手工 Bean 装配：不引入未经兼容验证的自动装配层，SB 4.1.0 升级或 starter 成熟后可平滑切换
  （切换需重新验证并形成新 ADR）。
- Port/Adapter 分离：领域层不感知 AgentScope SDK，未来 Provider 或 Runtime 替换只影响 Adapter。
- 测试端点 Test Double 边界清晰：真实 Provider 认证是独立工作项，不污染兼容性结论。

## 候选方案

- **直接使用 agentscope-spring-boot-starter:2.0.1**：上手快，但编译依赖 SB 4.0.1，
  与冻结的 4.1.0 兼容性未经官方保证；拒绝（当前阶段）。
- **不使用 BOM，逐模块固定版本**：版本易漂移，扩展新增时易遗漏；拒绝。
- **自研模型调用 SPI**：违反 A-003/08 §3"官方缺少 Provider 时作为扩展，不创建通用 SPI"；
  拒绝。
- **Redis 作为默认恢复存储**：`session-redis` 无 2.0.1，`RedisAgentStateStore` 需显式注入
  client；在能力矩阵未评审前预设为默认会引入未验证依赖；拒绝（当前阶段）。

## 影响

- 产品：无直接产品影响；本 ADR 建立技术基线。
- 领域：新增 `nexus-edge-domain-agentscope-port`（纯领域）。
- 数据：无 Schema 变化。
- API：无公共 API 变化。
- 安全：测试端点仅用于 DEV-0001；生产 Secret 不入代码（07 §7）。
- 运维：无部署变化。
- 测试：17 个真实兼容测试纳入 CI（G-06 建立后）。
- 迁移与回退：无既有实现；后续切换 starter 需新 ADR。
- 许可：AgentScope Apache-2.0，需进入第三方许可清单（G-07）。

## 验证

- G-01：Java 21 + SB 4.1.0 + AgentScope 2.0.1 共同构建、启动、运行 ✅
- G-03：能力清单见 `docs/dev-0001/COMPATIBILITY_REPORT.md` §4 ✅
- OQ-007：能力矩阵见报告 §5（证据已产生，Redis 测试待评审）✅（部分）
- 17/17 测试全绿（2026-08-10，本地 Maven）
- 待补：真实 Provider Smoke Test（需安全凭证）、GitHub Actions CI（G-06）

## 未解决问题

- 官方 `agentscope-spring-boot-starter` 与 SB 4.1.0 的兼容性：G-02 外围依赖冻结时复审。
- `RedisAgentStateStore` 是否作为生产恢复存储：OQ-007 评审后决定。
- AgentScope 官方 Checkpoint 组件明细：OQ-007 进一步盘点。
