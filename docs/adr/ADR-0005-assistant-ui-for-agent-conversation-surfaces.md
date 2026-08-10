# ADR-0005 — assistant-ui 作为 Agent 对话交互组件基线

> 状态：Accepted  
> 日期：2026-08-10  
> 决策人：产品架构负责人  
> 关联规格：Blueprint 00、10、13、14、15、16、17

## 背景

V1 Web Workspace 需要承载长任务对话、消息流、Composer、流式状态、Tool Call、取消/重试和安全审批提示。若直接在业务页面内自行实现这些交互，会重复建设成熟 AI 对话组件，并增加流式状态、键盘操作、滚动、无障碍和升级维护成本。

[assistant-ui 官方文档](https://www.assistant-ui.com/docs)提供 React AI 对话组件、Headless Primitive、Custom Runtime 和 Tool UI，能够接入自有后端，并与项目现有 React、Vite、Tailwind CSS 和 shadcn/ui 技术路线配合。

## 决策

1. GW Nexus Edge V1 将 assistant-ui 纳入 Web Agent 对话场景的正式组件基线，优先复用 Thread、Message、Composer、ThreadList、ActionBar、Attachment、Streaming/Retry 状态和 Tool UI，不得在业务页面重复实现已有对话基础能力。
2. 建立项目治理层 `@gwnexus/assistant-ui`。页面和业务模块不得直接依赖 `@assistant-ui/react`、assistant-ui Registry 代码或其内部 Runtime API；所有上游组件、主题映射、中文文案、Nexus 事件适配、安全过滤和扩展槽统一收敛于该共享包。
3. `@gwnexus/assistant-ui` 使用 assistant-ui Custom Runtime/受支持适配机制连接 Nexus Edge `/api/v1` REST、SSE 与 `Last-Event-ID` 协议。不得为接入 UI 而引入第二套 Agent 后端、Vercel AI SDK 服务端协议、Assistant Cloud 或客户端直连模型。
4. 浏览器中的 assistant-ui Runtime 只负责 UI 状态适配，不是 Agent Runtime、业务 Task 状态机、消息权威存储或恢复控制面。AgentScope Java 2 仍是唯一 Agent Runtime，Nexus Edge Server 仍是 Task、权限、审计和事件的权威源。
5. Tool UI 只能展示经过后端权限与数据策略过滤的 Tool Event。审批、取消、重试、发布等动作必须调用 Nexus Edge 业务 API 并接受服务端权限和状态机校验；前端组件不得自行改变权威状态。
6. 禁止通过 Reasoning/Chain-of-Thought 组件暴露模型隐藏思维链、完整 Prompt、Secret、未经授权的 RAG 原文或敏感 Tool 输出。UI 只展示安全处理后的业务阶段、Tool 摘要、Evidence 和可审计事件。
7. assistant-ui 精确版本、React 19/Vite/Tailwind 兼容性以及 Custom Runtime 对 Nexus SSE 的适配结果，必须通过 G-14 门禁后锁定。禁止使用 `latest` 作为可重复构建的生产依赖。
8. assistant-ui 为 MIT 依赖，必须进入 `THIRD-PARTY-NOTICES.md`、SBOM、依赖漏洞与许可证扫描。

## 长期适配性

独立的 `@gwnexus/assistant-ui` 隔离上游 API、视觉实现与 Nexus 领域协议，使 Web 与未来 V1.1 Desktop 可以共享同一 Agent 交互层，同时保持 AgentScope、Nexus API 和 UI 库三者边界稳定。上游升级或替换只影响治理包，不要求重写业务页面或后端协议。

这不是临时包装：该包长期负责 Agent 对话的设计系统映射、事件适配、安全展示策略、无障碍、i18n 和测试契约，但不复制 assistant-ui 已有 Primitive，也不承载业务状态机。

## 候选方案

- 完全自行实现对话 UI：重复建设消息、Composer、Streaming、Tool UI 和无障碍能力，拒绝。
- 页面直接散落使用 `@assistant-ui/react`：耦合上游 API，难以统一安全过滤和主题，拒绝。
- 使用 Assistant Cloud 或另一套托管 Runtime：破坏企业私有部署和 AgentScope 唯一 Runtime 边界，拒绝。
- 仅使用通用 shadcn/ui 拼装全部对话能力：基础控件可复用，但会重复建设 AI 对话状态与交互 Primitive，拒绝。

## 影响

- Monorepo 新增长期共享包 `packages/assistant-ui`，npm 名称为 `@gwnexus/assistant-ui`。
- Agent 对话页面的组件检索顺序调整为 `@gwnexus/assistant-ui` → assistant-ui 官方能力 → `@gwnexus/ui`/shadcn/ui → 批准的第三方组件。
- 普通管理、表单、表格和业务页面仍遵循 `@gwnexus/ui` → shadcn/ui 的既有顺序。
- 前端测试增加 Custom Runtime、SSE 重连、Tool UI 权限、敏感数据过滤、中文、无障碍和视觉回归矩阵。
- Coding Workspace 中用户委托 Agent 开发的目标应用不受此技术选型约束，除非用户或目标仓库明确选择 assistant-ui。

## 迁移与回退

当前仓库无前端实现代码，不存在运行数据迁移。若 G-14 证明关键兼容性不可满足，必须提交 Superseding ADR 选择替代成熟组件；不得回退为页面内临时自研聊天组件。

## 验证

- React 19 + TypeScript strict + Vite + Tailwind CSS + shadcn/ui 构建通过。
- 使用 Nexus 自有 REST/SSE Fixture 验证提交、Streaming、取消、重试、错误、断线和 `Last-Event-ID` 续传。
- Tool Call 与 Tool Result 只能展示授权、脱敏后的事件；隐藏思维链和 Secret 泄漏测试为零。
- zh-CN、键盘、焦点、ARIA、屏幕阅读、响应式和视觉回归通过。
- 经营分析与 Coding Workspace 的 Agent 对话均通过真实 E2E，且未引入第二套后端 Runtime。

## 未解决问题

精确锁定的 assistant-ui 包版本、采用的 Custom Runtime API 面和 Registry 组件清单由 OQ-013/G-14 PoC 产生证据后冻结，不影响本 ADR 对组件边界和治理方式的决定。
