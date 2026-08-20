# 18 — AI Agent 协作、共识与交接协议

> 状态：Accepted  
> 适用范围：所有由 AI Agent 参与的分析、编码、测试、文档、部署和审查任务  
> 目标：允许在不同模型和厂商之间切换，而不丢失决策、上下文、工程状态和责任边界

## 1. 核心原则

项目不要求多个 AI Agent 在观点上彼此认同，而要求它们对同一组版本化事实保持一致。

共识不是：

- 上一个 Agent 在聊天里说了什么；
- 某个模型记住了什么；
- 某个 Agent 认为“最佳实践”是什么；
- 多个 Agent 投票选择方案。

共识是：

```text
同一个 Git Commit
 + 同一套 Accepted Blueprint/ADR
 + 同一份 OpenAPI/Schema/Migration/Event Contract
 + 同一组自动化测试和质量门禁
 + 同一个 Development Work Item/Handoff
```

Agent 可以不同，模型可以不同，但输入事实和验收函数必须相同。

## 2. 权威性顺序

发生不一致时按以下顺序处理：

1. 人类产品架构负责人当前明确决策，并要求落入 ADR/Blueprint。
2. `AGENTS.md` 开发宪章。
3. `00_DECISIONS.md` 与已同步完成的 Accepted ADR。
4. 当前正式 Blueprint。
5. 版本化 OpenAPI、JSON Schema、Event Schema、Flyway Migration。
6. 自动化测试、Golden Dataset 和企业验收用例。
7. GitHub Issue/PR 的当前工作范围与批准记录。
8. Handoff 中记录的工作进度和操作事实。
9. Agent 聊天输出仅供参考，不具备规范效力。

如果第 3 至第 6 层互相冲突，不能简单选择优先级较高者继续编码；必须停止、登记 Open Question，并由人类负责人通过 ADR/规格修订消除冲突。

## 3. 工作单元

每次开发使用唯一 `DEV-xxxx` Work Item，并映射到一个 GitHub Issue。一个 Work Item 只包含一个业务模块或一个明确的技术门禁。

必须记录：

- 目标与验收标准；
- 范围内和范围外内容；
- 关联 Decision/ADR/Open Question；
- 所有者和当前 Agent；
- 基线 Commit 与工作分支；
- Schema/API/Event/State Machine 影响；
- 测试计划与证据；
- 当前状态。

开发状态使用：`PROPOSED`、`READY`、`IN_PROGRESS`、`BLOCKED`、`READY_FOR_REVIEW`、`DONE`。这些是工程 Work Item 状态，不得与 Nexus Edge 产品中的 Task/Artifact 状态混用。

## 4. Git 是工作状态真相源

公开 GitHub 仓库（ADR-0010）建立后：

- `main` 只接受 Pull Request，不允许 Agent 直接推送；
- 每个 Work Item 使用独立分支，例如 `agent/DEV-0001-agentscope-compatibility`；
- 开工和交接必须记录基线 Commit SHA；
- Agent 开工前先确认分支和工作树，不覆盖来源不明的修改；
- 合并以 CI、人工 Review 和批准记录为准，不以 Agent 自评为准；
- 架构决策、Handoff、测试和代码在同一 PR 中可追溯。

禁止两个 Agent 同时修改同一 Work Item 或同一组核心文件。需要并行时，只能拆成依赖和文件边界清晰的 Work Item，并先冻结公共契约。

## 5. 接管流程

新 Agent 必须执行：

### 5.1 指令和规格发现

读取 `AI_START_HERE.md`、`AGENTS.md`、正式索引、Decision、Open Questions、实施清单、相关 Blueprint/ADR/Contracts 和当前 Handoff。

Coding Workspace相关任务还必须同时读取：

- `21_CODING_WORKSPACE.md`；
- `22_CODING_SANDBOX_AND_SUPPLY_CHAIN.md`；
- `23_CODING_APPLICATION_DEPLOYMENT.md`。

任一Agent不得仅凭“一键部署”“Sandbox”或“支持任意前端”等自然语言自行扩展含义。

### 5.2 仓库审计

检查：

- 当前目录、分支、HEAD SHA 和工作树；
- 未提交、未跟踪和暂存文件；
- Handoff 声明的变更是否与 Diff 一致；
- 依赖锁、Migration、Contracts 和测试是否同步；
- 最近 CI 或本地验证结果是否仍适用于当前 Commit。

### 5.3 复述而不是推测

输出当前任务、已完成内容、未完成内容、禁止事项、开放问题和下一步计划。必须引用 Decision ID、ADR 或文件路径，不能只给抽象总结。

### 5.4 等待确认

首次接管、存在 Dirty Worktree、存在 Open Question 或准备改变公共契约时，必须等待人工确认后才能编辑。

## 6. 正常交接

切换前，当前 Agent 必须：

1. 停止扩张范围，不顺手开始下一模块。
2. 运行与变更相称的测试和静态检查。
3. 更新 Contracts、Migration、文档和 ADR。
4. 检查 Git Diff 中没有 Secret、生成垃圾或无关修改。
5. 更新 `docs/handoffs/active/DEV-xxxx.md`。
6. 明确哪些内容已验证，哪些只是推断或未运行。
7. 提供下一步单一动作，避免“继续完善”这类无边界描述。

若允许提交，应形成可构建的中间 Commit。若尚不能提交，必须在 Handoff 中逐文件说明未提交内容和安全恢复方式。

## 7. 紧急接管

额度耗尽、Agent 中断或无法生成主动交接时，新 Agent 不得假设前一 Agent 已完成。

执行顺序：

1. 不运行 `git reset --hard`、`git checkout --` 或批量覆盖命令。
2. 保存并检查 `git status`、`git diff`、暂存区和未跟踪文件。
3. 对照当前 Work Item、Blueprint 和测试重建事实清单。
4. 标记来源不明、无法证明和疑似越界的修改。
5. 运行最小非破坏性验证。
6. 创建或更新 Handoff，标记 `EMERGENCY_TAKEOVER`。
7. 向人类负责人报告后再继续。

## 8. 共识检查点

以下时点必须重新做基线确认：

- 新 Agent 接管；
- 切换分支或基线 Commit；
- Accepted ADR 新增或被取代；
- OpenAPI、Event Schema、Artifact Schema 或 Migration 变化；
- 公共共享前端包变化；
- 核心依赖版本变化；
- CI 与本地测试出现结论差异；
- 进入 READY_FOR_REVIEW 或发布候选。

## 9. 多 Agent 并行边界

只有满足以下条件才允许并行：

- 公共 API、Event 和 Schema 已冻结；
- 每个 Work Item 有不同所有者、分支和文件边界；
- 依赖方向明确，不要求两个 Agent 同时修改同一文件；
- 集成顺序和契约测试已定义；
- 人类负责人批准拆分。

推荐按纵向业务切片拆分，而不是让不同 Agent 长期各自拥有“全部后端”或“全部 Web”。Desktop 已进入V1.1，任何V1 Agent不得创建Desktop功能Work Item、Tauri构建、Windows MSI或Desktop专用API。V1.1立项前必须重新核对OpenAPI、SSE Event Schema、Auth Contract、Domain Types和共享组件API。

## 10. 禁止行为

- 让新 Agent 只阅读上一 Agent 的聊天摘要后直接编码；
- 把厂商专用 Memory/Plan 当成唯一项目记录；
- 在 Handoff 之外保留关键未决结论；
- 为适应新 Agent 的偏好更换框架或重组架构；
- 未经批准修改已经冻结的 Contract；
- 多个 Agent 在同一 Dirty Worktree 上交替覆盖；
- 使用自动合并掩盖语义冲突；
- 让 Agent 自己批准自己的 ADR、安全例外或生产门禁。
- 把Nexus Edge平台安装CLI误当成Coding Application一键发布，或反向混用两套状态和验收。
- 为赶进度在Core、AgentScope进程、用户Desktop或Production Host管理环境直接执行用户代码。
- 把Nexus Edge自身UI组件约束强加给用户Coding Project。
- 在V1中实现Desktop、Tauri、目录同步、本地Git发现、Desktop通知或Windows MSI，造成范围回流。

## 11. 人类负责人的职责

人类产品架构负责人是最终决策者，负责：

- 批准 ADR、范围和安全例外；
- 解决 Open Questions；
- 分配 Work Item 和允许并行；
- 确认交接是否完整；
- 审核 CI 不能证明的 Office/WPS、企业身份和业务质量；
- 拒绝以额度、模型差异或截止日期为由降低 P0 标准。

Agent 的共识来自相同规则，人类的责任来自最终决策和验收，两者不能互相替代。
