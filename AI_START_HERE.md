# GW Nexus Edge — AI Agent 统一启动入口

> 适用对象：Codex、Claude、Gemini、Cursor、Copilot、Windsurf 及其他参与本项目的 AI Agent  
> 原则：Agent 可以更换，项目事实、架构决策和质量标准不能随 Agent 更换

## 1. 身份声明

你正在参与 GW Nexus Edge 企业级 Agent Workspace 平台的开发。你不是项目决策人，也不能依据自己的习惯重新设计已经冻结的架构。你的职责是基于当前仓库中的权威规格，完成一个边界清晰、可验证、可交接的工程任务。

聊天历史、模型记忆、上一位 Agent 的自然语言总结和厂商默认最佳实践，只能作为线索，不能覆盖仓库中的 Accepted 规范。

## 2. 开始工作前必须读取

按以下顺序完整读取：

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/blueprint/00_DECISIONS.md`
4. `docs/governance/OPEN_QUESTIONS.md`
5. `docs/blueprint/17_IMPLEMENTATION_READINESS_CHECKLIST.md`
6. `docs/blueprint/18_AI_AGENT_COLLABORATION.md`
7. 当前任务相关的 Blueprint、Accepted ADR、OpenAPI/JSON Schema/Flyway Migration 和测试
8. 当前开发任务的 `docs/handoffs/active/DEV-xxxx.md`；不存在时按模板创建

任务命中 Coding Workspace、Git、Sandbox、Build、Preview、Production、域名或应用运行时，必须额外完整读取：

9. `docs/blueprint/21_CODING_WORKSPACE.md`
10. `docs/blueprint/22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md`
11. `docs/blueprint/23_CODING_APPLICATION_DEPLOYMENT.md`

V1 有两条相互独立且必须同时通过的 P0 闭环：企业经营分析报告闭环，以及 Coding Workspace 前端/SSR 开发与 HTTPS 应用发布闭环。不得用其中一条的完成代替另一条，也不得把 Coding Workspace 误记为 Beta Skill。

V1 只开发和验收 Web Workspace。Windows Desktop 已由 ADR-0004 整体移入 V1.1；任何 V1 Work Item 均不得实现 Tauri/Desktop、本地目录同步、本地 Git 发现、Desktop 系统通知、Windows MSI 或 Desktop 专用 API。Windows Renderer 是独立 P0 节点，不得与 Desktop 混淆。

禁止把 `docs/archive/legacy/` 作为实现依据。

## 3. 开工前必须输出的基线确认

在修改任何文件前，向用户输出：

```text
工作项：DEV-xxxx / GitHub Issue
当前分支：
基线 Commit SHA：
工作树状态：clean / dirty
读取的权威文档：
关联 Decision ID：
关联 ADR：
关联 Open Question：
本次唯一模块：
明确排除：
计划修改文件：
Schema/API/Event影响：
测试计划：
阻塞项：
```

没有 Git 仓库时，`基线 Commit SHA` 必须写 `UNVERSIONED`，并明确指出项目尚不具备可靠的跨 Agent 版本共识。完成 Milestone 0 后，所有开发必须基于明确 Commit。

## 4. 立即停止并请求人工决策的情况

- Blueprint、Accepted ADR、契约或测试互相冲突；
- 当前需求命中 `OPEN_QUESTIONS.md` 中未解决问题；
- 需要改变核心版本、架构边界、安全策略、数据模型原则或 P0 范围；
- 工作树存在来源不明的未提交修改；
- 上一 Agent 的交接记录与实际 Git Diff 不一致；
- 需要删除、覆盖或重写他人尚未提交的工作；
- 只有通过 Mock、占位、降级或绕过门禁才能继续。
- Coding Agent 需要直接访问 Nexus Edge Host、Docker/CRI Socket、Production Secret、企业内网或核心基础设施；
- Production 发布未绑定人工批准、不可变 Commit/Artifact/Image Digest 或未通过不可绕过的安全门禁；

AI Agent 不得通过“采用常见做法”“先这样以后再改”或推测用户意图来解决上述问题。

## 5. 完成或切换前

必须更新当前工作项的 Handoff，至少记录：

- 基线和最新 Commit；
- 已完成、部分完成和未开始内容；
- 实际变更文件和重要 Diff；
- Schema、API、事件和状态机变化；
- 执行过的测试及原始结果；
- 已知失败、风险、临时环境和外部依赖；
- 不得重复或不得覆盖的工作；
- 下一位 Agent 的唯一建议动作。

不得只在聊天中说“已完成”。没有进入 Git、Handoff、契约、测试或 ADR 的信息，不视为可继承的项目事实。

## 6. 给任何新 Agent 的最短启动提示

```text
你现在接管 GW Nexus Edge。先完整读取根目录 AI_START_HERE.md 和 AGENTS.md，并严格执行其中的接管协议。读取当前 Handoff、Open Questions、相关 Blueprint 和 ADR，核对 Git 状态与实际 Diff。在输出基线确认和接管审计前，不得修改任何文件。如果任务涉及 Coding Workspace、Git、Sandbox、Build、Preview、Production、域名或应用运行时，必须完整读取 Blueprint 21、22、23。如果发现冲突或未决问题，停止并请求人工决策。
```
