# ADR-0001 — V1 采用经营分析与 Coding Workspace 双 P0 产品范围

> 状态：Accepted  
> 日期：2026-08-10  
> 决策人：产品架构负责人  
> 关联规格：Blueprint 00、01、02、15、21、22、23

## 背景

V1 最初只以企业经营分析报告 Skill 作为生产闭环。随后产品目标增加了 Coding Workspace：用户能够让 Agent 创建新前端项目或导入现有 Git 仓库，完成理解、修改、测试、Preview，并在人工与安全门禁后把同一不可变制品发布为可直接访问的 HTTPS 应用。

这不是经营分析 Skill 的子流程，也不是平台安装脚本的别名。它拥有独立用户、领域模型、不可信执行边界、软件供应链、应用运行环境与验收指标。如果只作为“后续可选插件”记录，后续 Agent 会持续以单一业务闭环设计核心数据、权限和部署架构。

## 决策

GW Nexus Edge V1 具有两条相互独立且必须同时通过的 P0 生产闭环：

1. 企业经营分析报告 Workspace。
2. Coding Workspace 前端/SSR 开发、Preview 与 HTTPS Production 应用发布。

经营分析报告仍是 V1 唯一承担生产承诺的企业业务 Skill。Coding Workspace 是另一类 Workspace 产品能力，不增加第二个生产业务 Skill。任一闭环 P0 未通过时，整体 V1 不得通过 2026-09-01 Enterprise Pilot 验收；质量门禁优先，允许延期。

## 长期适配性

双闭环共享 Tenant、Identity、Workspace、Task、AgentScope Runtime、Policy、Secret、Audit、Notification 和 Observability，但保持领域、数据和基础设施边界独立。未来增加更多业务 Skill 或更多编程语言 Runtime 时，可以扩展各自能力，不需要把两条闭环重新拆出平台。

## 候选方案

- 只保留经营分析为 V1：无法满足已确认的 Coding 产品承诺。
- 把 Coding 作为经营分析 Skill：职责、权限、运行风险和制品完全不同，领域建模失真。
- 把 Coding 推迟到 V1.1：与用户明确冻结的 V1 P0 范围冲突。
- 将两个闭环合并成一个验收用例：会掩盖任一领域未完成或不稳定的问题。

## 影响

- 产品：V1 必须提供业务 Workspace 与 Coding Workspace 两类入口。
- 架构：增加 Sandbox、Build、Registry、Application Host、Gateway、Domain/Certificate 等长期组件。
- 安全：必须隔离企业资料处理与不可信代码执行。
- 计划：并行但独立的里程碑与验收样本；任何 P0 缺失允许延期。
- 文档：Blueprint 00—23、根协作协议与验收表必须保持双 P0 一致。

## 迁移与回退

这是实现前的范围冻结，不涉及生产数据迁移。后续若要移除或降级任一 P0，必须新增 Superseding ADR，不得直接修改本文或通过实现缺失形成事实降级。

## 验证

- 经营分析闭环按 Blueprint 02 与 14 的企业数据、可信度、Renderer、审批和安全门禁验收。
- Coding 闭环按 Blueprint 21—23 的 ≥20 次真实任务、≥90% 成功率、供应链、安全、域名、发布和回滚门禁验收。
- 整体发布检查必须验证两套 P0 均为 PASS。

## 未解决问题

精确实现契约见 `docs/governance/OPEN_QUESTIONS.md`。这些问题阻断相应实现阶段，但不改变双 P0 产品决策。
