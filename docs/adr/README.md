# GW Nexus Edge 架构决策记录（ADR）

> 状态：Accepted  
> 作用：管理会改变长期架构、公共契约、安全边界或 V1 范围的决策

## 1. 权威关系

已冻结决策汇总在 [`../blueprint/00_DECISIONS.md`](../blueprint/00_DECISIONS.md)。该文件中的决策可视为本项目的初始 Accepted ADR 集合。

后续变更不得直接覆盖旧结论。必须新增一份 ADR，记录旧决策为何不再成立、迁移影响和替代方案；ADR 获批后，再同步修改 Blueprint、测试与实施计划。

## 2. 必须创建 ADR 的变化

- 产品定位、V1 P0/P1/P2 范围或验收门槛；
- AgentScope 与 Nexus Edge 责任边界；
- Java、Spring Boot、AgentScope、React、Tauri 等核心技术基线；
- Agent 对话组件框架、公共前端适配层或会影响 REST/SSE 交互契约的 UI Runtime 变化；
- 新增基础设施、中间件、数据库、Runtime 或跨进程服务；
- 数据权威源、Tenant、身份、权限、分类分级和数据外发规则；
- API 版本、状态机、持久化模型或一致性策略的破坏性变化；
- Renderer 生产 Provider、Office 格式承诺或高保真标准；
- 部署拓扑、RPO/RTO、加密、Secret 或审计边界；
- 许可模式、源码交付或第三方组件合规策略。

普通实现细节、无公共影响的重构和已在 Blueprint 中授权的 Provider 实现不需要 ADR。

## 3. 状态

- `Proposed`：已提交，尚未批准；不能作为实现依据。
- `Accepted`：已批准，必须同步到 Blueprint。
- `Rejected`：不采用，保留原因以防重复讨论。
- `Superseded`：被后续 ADR 取代，必须链接替代 ADR。
- `Deprecated`：仍在兼容期，但禁止新实现继续使用。

## 4. 命名

文件名使用：

```text
ADR-0001-short-kebab-case-title.md
```

编号只增不复用。标题描述决策，不使用“讨论某某问题”这类无结论表达。

## 5. 模板

```markdown
# ADR-XXXX — 决策标题

> 状态：Proposed  
> 日期：YYYY-MM-DD  
> 决策人：  
> 关联决策/Issue：

## 背景

说明现状、约束、驱动因素以及为什么现在必须决策。

## 决策

使用可验证、无歧义的语言写出最终选择和适用边界。

## 长期适配性

解释该方案为何能在目标架构中长期存在，而不是等待未来替换的临时方案；说明扩展点和不变量。

## 候选方案

列出认真评估过的方案、优缺点与淘汰原因。

## 影响

分别说明产品、领域、数据、API、安全、运维、测试、迁移、许可与成本影响。

## 迁移与回退

说明已有数据/调用方如何迁移；若无法安全回退，明确前向修复策略。

## 验证

列出 PoC、自动化测试、性能/安全/兼容门禁和验收证据。

## 未解决问题

仅记录不影响当前决策成立的问题；若核心结论仍不确定，ADR 不应进入 Accepted。
```

## 6. 当前需要优先形成的 ADR

以下事项尚未完成验证。验证结论产生后应创建正式 ADR，而不是把猜测写成依赖版本或生产承诺：

1. Spring Boot 4.1.0 + AgentScope 2.0.1 + MyBatis-Plus + Sa-Token 兼容组合；
2. AgentScope Harness/Core 的 Persistence、Recovery、Provider 与 Observability 具体接入边界；
3. WPS Office 免费版 Renderer Compatibility Gate 结论；
4. MySQL 与 DM8 双方言迁移组织和兼容认证范围；
5. Coding Workspace Host Agent、Sandbox、BuildKit、Harbor、Traefik、Envoy 与 Runtime Catalog 的组合验证结论。

## 7. 当前 ADR 索引

| ADR | 决策 |
|---|---|
| [ADR-0001](ADR-0001-v1-dual-p0-product-scope.md) | V1 采用经营分析与 Coding Workspace 双 P0 产品范围 |
| [ADR-0002](ADR-0002-coding-execution-and-deployment-boundary.md) | Coding 不可信执行、供应链与自托管应用发布边界 |
| [ADR-0003](ADR-0003-web-first-client-delivery.md) | Superseded：Web-first 但 Desktop 仍在 V1 的旧决策 |
| [ADR-0004](ADR-0004-desktop-moved-to-v1-1.md) | Windows Desktop 整体移出 V1 并进入 V1.1 |
| [ADR-0005](ADR-0005-assistant-ui-for-agent-conversation-surfaces.md) | assistant-ui 作为 Agent 对话交互组件基线，通过项目治理层接入 Nexus REST/SSE |
| [ADR-0006](ADR-0006-agentscope-2-0-1-bom-and-embedded-harness.md) | **Accepted（2026-08-11）**：采用 AgentScope 2.0.1 官方 BOM 并内嵌 Harness/Core；长期 Runtime Identity；Memory Hooks 边界；Nexus 生成事件 ID；四标识模型（taskId/taskAttemptId/agentId/traceId） |
