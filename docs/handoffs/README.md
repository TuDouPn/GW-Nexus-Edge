# AI Agent Handoff 记录

本目录保存跨 AI Agent 的开发交接记录。每个开发 Work Item 使用一个活动文件：

```text
docs/handoffs/active/DEV-0001.md
```

完成并合并后可移动到：

```text
docs/handoffs/completed/DEV-0001.md
```

Git 历史保存每次 Agent 切换的修改过程，不需要为每次对话创建新文件。禁止多个 Agent 同时编辑同一个 Handoff。

## 使用规则

- 开工时从 [`TEMPLATE.md`](TEMPLATE.md) 创建活动记录。
- 切换前必须更新，额度不足时优先更新“当前状态、Diff、测试、下一步”。
- 新 Agent 必须将记录与 Git 实际状态核对，不能盲目信任。
- Handoff 只记录工作事实和进度，不得用来修改架构决策。
- 架构变化写 ADR，规范空白写 Open Questions，公共契约写 Contracts。
- Handoff 内禁止 Secret、企业原文、完整 Prompt 和敏感数据。
