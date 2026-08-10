# ADR-0003 — Web-first 客户端交付顺序与 Desktop 开工门禁

> 状态：Superseded by [ADR-0004](ADR-0004-desktop-moved-to-v1-1.md)  
> 日期：2026-08-10  
> 决策人：产品架构负责人  
> 关联规格：Blueprint 00、01、02、10、15、17、18、21

> 历史说明：本文保留“Desktop 仍属于 V1”的旧决策，仅用于追溯。自 ADR-0004 生效后，不得再作为 V1 范围依据。

## 背景

V1 同时规划 Web Workspace 与 Windows Desktop。若两个客户端从项目早期并行开发，会在 API、状态机、组件、交互和验收尚不稳定时形成两套半成品，实现者容易复制业务逻辑或用 Desktop 补 Web 缺口。当前产品价值首先需要通过 Web 快速形成完整可验证闭环；Desktop 的不可替代价值主要是本地目录/Repository发现、单向同步、设备身份和系统通知。

## 决策

V1 采用严格 Web-first 顺序：

1. 经营分析与 Coding 两条 P0 均先在 Web 中完成端到端业务闭环。
2. Web Completion Gate 通过前，不开发 Desktop 业务页面、本地同步或 Desktop Coding 功能；只允许不产生产品功能的 Tauri/Windows 兼容 PoC和共享契约维护。
3. Web Gate 通过后，Desktop 复用稳定的 React 领域模块、`@gwnexus/ui`、API SDK、Domain Types、Agent Event 和 Auth Contract，并只补浏览器无法提供的 Edge 能力。
4. Desktop 仍属于 V1 最终交付范围；本决策改变实施顺序，不取消 Desktop 产品能力或最终验收责任。

## Web Completion Gate

- Web 可独立完成经营分析资料上传、Workspace、Skill、Task、Evidence、Artifact、两级审批和发布。
- Web 可独立完成 Coding Project 新建/导入、Plan、Agent执行、Diff、Preview、Release Candidate、Production、Domain 和回滚。
- 管理 Console、权限、通知、审计和错误恢复可用。
- 两条 P0 Web E2E、响应式、无障碍、SSE断线恢复、视觉回归、组件治理与前端覆盖率门禁通过。
- 不存在必须依赖 Desktop 才能完成的非本地专属业务步骤。

## 长期适配性

Web-first 不把 Desktop 降级为临时壳。两端长期共享业务契约与领域包，Desktop 保持独立 Tauri 应用入口和 Edge 安全边界。先稳定 Web 可减少重复实现；后续 Desktop、移动端或其他客户端均可消费同一版本化契约，而无需重写后端。

## 候选方案

- Web/Desktop 同步开发：反馈并行，但在契约未冻结阶段带来重复实现和漂移，拒绝。
- V1 完全取消 Desktop：会失去本地目录同步和 Edge 能力，与最终产品范围冲突，拒绝。
- 先 Desktop 后 Web：不利于企业跨设备、管理、审批和快速试点，也使本地能力过早塑造平台模型，拒绝。
- Web 先做 Demo、Desktop 才做生产：会降低 Web 质量并违背 Web 完整工作入口定位，拒绝。

## 影响

- Roadmap 必须先完成 Web 两条 P0，再排 Desktop 阶段。
- Managed Workspace 初期使用 Web 上传，Desktop 后续增加单向目录同步。
- Coding 初期使用远程 Git 导入/创建，Desktop 后续增加本地 Repository 发现。
- Desktop 团队/Agent 不得提前创建重复页面或业务 Service。
- V1 最终验收仍包含 Desktop 已冻结 P0 Edge 能力。

## 迁移与回退

当前尚无实现代码，无迁移成本。若未来需要改变顺序或移除 Desktop，必须新增 Superseding ADR；不得通过长期搁置 Desktop 或在 Web 未完成时偷偷并行开发形成事实变更。

## 验证

- Web Completion Gate 形成可审计检查记录。
- 无 Desktop 在线时完成两条 P0 Web E2E。
- Desktop 开工 Work Item 必须引用 Web Gate PASS 证据。
- Desktop 契约测试证明复用同一 API、状态机和事件，不存在第二套业务模型。

## 未解决问题

无。具体页面与组件实现按 Blueprint 10 和 17 执行。
