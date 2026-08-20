## 描述

请简要说明本 PR 的目的（不扩大范围；一个 PR 一个工作项）。

## 关联

- Work Item：`DEV-xxxx`（docs/handoffs/active/DEV-xxxx.md）
- 关联 ADR / Open Question：`ADR-xxxx` / `OQ-xxx`（如适用）

## 变更内容

- [ ] 代码/测试
- [ ] 文档/契约
- [ ] 依赖/供应链（如涉及，已更新 THIRD-PARTY-NOTICES.md）

## 验证

- [ ] `./mvnw clean verify`（backend，Java 21）通过
- [ ] `git diff --check` 无输出（CI 对 PR 使用 merge-base/base SHA → head SHA）
- [ ] 未引入 Secret / Mock 冒充真实集成 / TODO / 占位实现
- [ ] 代码注释为中文，说明关键不变量
- [ ] 未修改已 Accepted Blueprint / 00_DECISIONS（或已附批准 ADR）

## 安全

- [ ] 本 PR 不向日志/测试/描述输出任何 Secret 值
- [ ] 未在不可信上下文执行需要凭据的测试
