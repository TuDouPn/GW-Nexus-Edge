# ADR-0004 — Windows Desktop 整体移出 V1 并进入 V1.1

> 状态：Accepted  
> 日期：2026-08-10  
> 决策人：产品架构负责人  
> 取代：[ADR-0003](ADR-0003-web-first-client-delivery.md)  
> 关联规格：Blueprint 00、01、02、03、10、12、13、14、15、17、18、19、20、21、23

## 背景

ADR-0003 已确定 Web-first，但仍要求 Desktop 在 V1 最终 Enterprise Pilot 前交付。当前 V1 同时承担经营分析和 Coding Workspace 两条 P0，继续把 Desktop 安装、同步、本地 Git、设备能力和系统通知纳入同一版本，会增加 Windows/Tauri、安全、签名、兼容和双端测试负担，并分散 Web 完整闭环的研发资源。

## 决策

1. GW Nexus Edge V1 的唯一用户工作入口为 React 19 Web Workspace。
2. Windows Desktop 整体移出 V1，进入 V1.1；V1 不开发、不构建、不打包、不部署、不验收 Desktop。
3. 本地目录扫描/Hash/单向同步、设备身份、Desktop 系统通知、本地 Git Repository 发现、Tauri 2 + Rust 应用与 Windows MSI 均属于 V1.1。
4. V1 经营分析通过 Web 文件上传进入 Managed Workspace；V1 Coding Workspace 通过 Web 创建新项目或导入远程 Git Repository。
5. V1 后端、OpenAPI、SSE、Domain Types、Design Tokens 和 UI 领域组件保持客户端中立，为 V1.1 Desktop 复用提供长期契约，但不得提前实现 Desktop 专用 API 或产品功能。
6. Windows Renderer 仍是 V1 独立 P0 基础设施节点，不属于 Desktop，范围不受本决策影响。

## 长期适配性

该决策不放弃 Edge 架构，而是把 Desktop 建立在已经稳定的 Web 业务模型和版本化契约上。V1.1 Desktop 仍采用 React + TypeScript + Tauri 2 + Rust，作为本地能力增强端，不承载独立 Agent 或第二套业务状态机。延后实现减少返工，不引入未来必须替换的临时客户端。

## 候选方案

- Web 与 Desktop 都在 V1：覆盖完整，但范围和验证成本过高，拒绝。
- ADR-0003 的“Web 完成后仍在 V1 开发 Desktop”：顺序正确但仍阻塞 V1 最终签字，拒绝。
- 永久取消 Desktop：会失去本地目录同步与设备能力，不符合长期 Edge 定位，拒绝。
- V1 制作简化 Desktop 壳：属于将被替换的临时方案，违反长期架构原则，拒绝。

## 影响

- V1 PRD、P0、验收、路线图、CI、部署包与测试矩阵只包含 Web。
- V1 不包含 `apps/desktop` 的产品实现、Tauri Windows Build 或 MSI。
- 站内、Web 实时通知和邮件仍为 V1 P0；Desktop 系统通知进入 V1.1。
- Desktop 目录同步不再是 V1 数据入口；Web 上传必须达到生产级文件版本、续传和权限要求。
- V1.1 启动 Desktop 时必须复用 V1 稳定契约，不得反向复制业务逻辑。

## 迁移与回退

当前无实现代码和生产数据，不需要代码或数据迁移。若要把 Desktop 重新纳入 V1，必须新增 Superseding ADR，并重新评估日期、CI、签名、安装、权限和双端 E2E；不得只修改路线图文字。

## 验证

- V1 两条 P0 在无 Desktop 的条件下完成全部 E2E 与 Enterprise Pilot。
- 活跃 V1 文档、CI 和部署清单不存在 Desktop P0、Tauri Build、MSI 或 Desktop 通知门禁。
- Web 文件上传覆盖原本由 Desktop 同步承担的 V1 数据入口。
- V1.1 Desktop 立项时单独建立兼容、安全、签名和双端契约门禁。

## 未解决问题

V1.1 Desktop 的详细排期与验收样本在 V1 通过后冻结，不阻断 V1。
