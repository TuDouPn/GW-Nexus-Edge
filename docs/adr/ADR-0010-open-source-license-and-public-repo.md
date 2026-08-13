# ADR-0010 — 公开仓库与 Apache-2.0 开源许可基线

> 状态：**Accepted（2026-08-12 经产品架构负责人批准）**
> 日期：2026-08-12（批准）
> 决策人：产品架构负责人（2026-08-12 批准）
> 关联决策/Issue：DEV-0005、G-06、G-07
> 取代：此前的"私有 GitHub 仓库 + 闭源商业软件"冻结结论（00_DECISIONS、13_ENGINEERING_STANDARD 相关表述）

## 决策

1. **仓库公开**：GW Nexus Edge GitHub 仓库保持 **PUBLIC**。
2. **源代码许可**：项目源代码采用 **Apache License 2.0** 开源。
3. **取代旧定位**：不再使用"闭源商业软件""私有 GitHub 仓库"作为正式定位。
4. **Apache-2.0 授权范围**：允许使用、修改、分发、再许可组合和商业使用，但须遵守许可证及 NOTICE 要求。
5. **不自动授权**：GW Nexus Edge 名称、Logo、商标、客户数据、企业模板、测试数据、商业服务合同
   **不因代码许可证自动授权**；第三方依赖继续遵守各自许可证。
6. **商业活动边界**：商业发行、托管服务、私有部署、支持、行业 Skill 和定制服务可以继续存在，
   但**不得将 Apache-2.0 代码描述为专有闭源代码**。
7. **贡献基线（inbound=outbound）**：向本项目提交贡献即表示贡献者同意其贡献按本项目 Apache License 2.0
   授权（当前贡献基线；**不等同于 CLA/DCO**）。
8. **未决定事项**：CLA、DCO、Contributor License Agreement 和商标政策当前均不得擅自决定；需要时另行 ADR。

## 影响

- 00_DECISIONS（CI 行与 §10 相关）、13_ENGINEERING_STANDARD §10、17 G-07 通过标准等"私有/闭源"表述
  原子修正为公开 + Apache-2.0。
- 历史 Handoff 中"私有仓库/等待授权"为当时事实，标记"历史状态，已被 ADR-0010 取代"，不批量改写。
- 开源基础文件（LICENSE/NOTICE/THIRD-PARTY-NOTICES/SECURITY/CONTRIBUTING/CODE_OF_CONDUCT）与公开仓库
  CI 安全模型由 DEV-0005 落实。
- 商标/客户数据/企业模板/商业合同不因代码许可证自动授权；未来商业与商标政策需单独决策。

## 验证

- 仓库可见性：PUBLIC（gh 审计）。
- LICENSE = Apache License 2.0 官方全文（apache.org，未修改）。
- 原子同步后全仓无"私有/闭源商业软件"现行决策表述（历史 Handoff 除外，已标记历史）。
