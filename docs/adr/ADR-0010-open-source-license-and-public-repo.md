# ADR-0010 — 公开仓库与 Apache-2.0 开源许可基线

> 状态：**Accepted**
> 日期：2026-08-12（批准）；2026-08-20（DEV-0005 设计修订同步 inbound=outbound 贡献基线）
> 决策人：产品架构负责人（2026-08-12 明确批准公开仓库与 Apache-2.0）
> 关联决策/Issue：DEV-0005、G-06、G-07
> 取代：此前的“私有 GitHub 仓库 + 闭源商业软件”冻结结论（00_DECISIONS、13_ENGINEERING_STANDARD 相关表述）

## 背景

V1 决策基线曾将本项目定位为闭源商业软件、代码托管于私有 GitHub。产品架构负责人已明确批准：仓库保持 PUBLIC，源代码采用 Apache License 2.0。该变化属于许可模式与源码交付策略，必须经 ADR 记录后原子同步 Accepted Blueprint。

## 决策

1. **仓库公开**：GW Nexus Edge GitHub 仓库保持 **PUBLIC**。
2. **源代码许可**：项目源代码采用 **Apache License 2.0** 开源。
3. **取代旧定位**：不再使用“闭源商业软件”“私有 GitHub 仓库”作为正式定位。
4. **Apache-2.0 授权范围**：允许使用、修改、分发、再许可组合和商业使用，但须遵守许可证及 NOTICE 要求。
5. **不自动授权**：GW Nexus Edge 名称、Logo、商标、客户数据、企业模板、测试数据、商业服务合同 **不因代码许可证自动授权**；第三方依赖继续遵守各自许可证。
6. **商业活动边界**：商业发行、托管服务、私有部署、支持、行业 Skill 和定制服务可以继续存在，但**不得将 Apache-2.0 代码描述为专有闭源代码**。私有部署交付物以镜像、配置、迁移与运维文档为主；完整源码通过公开仓库按 Apache-2.0 提供。
7. **当前贡献基线（inbound=outbound）**：向本项目提交贡献，即表示贡献者同意其贡献按本项目 Apache License 2.0 授权。贡献者不得提交无权授权的代码、企业资料、Secret、专有模板或受限制数据；第三方代码必须说明来源与许可证；不允许复制许可证不兼容或来源不明的代码。
8. **本基线不等同于 CLA/DCO**：第 7 条是 Apache-2.0 项目的 inbound=outbound 声明，**不是** Contributor License Agreement，也**不是** Developer Certificate of Origin。不得声称它提供与 CLA/DCO 相同的法律效果。
9. **未决定事项**：CLA、DCO 和商标政策当前均不得擅自决定，不得引入签署机器人或签署门禁；需要时另行 ADR。

## 长期适配性

公开仓库 + Apache-2.0 是可持续的开源基线；企业私有部署、商标和客户数据边界通过“代码许可 ≠ 商标/数据授权”保持。贡献政策先用 inbound=outbound 文本约束，避免在未决定 CLA/DCO 时形成事实门禁。

## 候选方案

| 方案 | 结论 |
|---|---|
| 维持私有仓库 + 闭源 | 已由产品架构负责人否决 |
| 公开仓库 + 非 Apache 许可证 | 未采用；负责人明确批准 Apache-2.0 |
| 公开仓库 + Apache-2.0 + 立即引入 CLA/DCO | 未采用；CLA/DCO 未决定 |

## 影响

- 00_DECISIONS（CI 行与 §6）、13_ENGINEERING_STANDARD §10、15、17、18、19 等 Accepted 现行决策中的“私有/闭源”表述原子修正。
- 历史 Handoff 中“私有仓库/等待授权”为当时事实，标记“历史状态，已被 ADR-0010 取代”，不批量改写。
- 开源基础文件与公开仓库 CI 安全模型由 DEV-0005 落实。

## 迁移与回退

无需数据迁移。若未来改变许可证或仓库可见性，必须新 ADR，不得静默改回“闭源/私有”。

## 验证

- 仓库可见性：PUBLIC。
- LICENSE = Apache License 2.0 官方全文。
- CONTRIBUTING.md 含 inbound=outbound 原文，并声明不等同 CLA/DCO。
- 全仓搜索后，Accepted Blueprint 现行决策不再将本项目描述为闭源私有仓库。

## 未解决问题

- CLA / DCO / 商标政策：未决定。
- 第三方依赖中 `REVIEW_REQUIRED` 许可证的法律兼容结论：留给 G-07 逐项人工核验，不由本 ADR 裁定。
