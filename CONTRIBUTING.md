# Contributing to GW Nexus Edge

感谢你考虑为 GW Nexus Edge 贡献代码。

## 贡献授权（inbound = outbound）

向本项目提交贡献，即表示贡献者同意其贡献按本项目 Apache License 2.0 授权。

- 贡献者不得提交无权授权的代码、企业资料、Secret、专有模板或受限制数据。
- 第三方代码必须说明来源与许可证。
- 不允许复制许可证不兼容或来源不明的代码。

本规则是当前贡献基线（inbound=outbound），**不等同于 CLA 或 DCO**。当前项目未采用 CLA、DCO 或商标政策；不得擅自引入签署门禁或相关机器人。这些事项如需引入，将另行通过 ADR 决定。

## 开发流程

1. 每个工作项使用独立分支：`agent/DEV-xxxx-<slug>`。
2. 从 `main`（最新）创建分支，不直接从他人分支继续开发。
3. 提交 PR 合并到 `main`。PR 必须通过以下 Job Display Name（不是 YAML 文件名）：
   - `Backend CI / Verify`
   - `Repository Security / Scan`
   - `Workflow Security / Lint`
   - `Dependency Review / Review`（仅在该检查已于 PR 真实成功并被设为 Required 后强制）
4. 代码注释使用**中文**，解释关键业务规则、权限、事务、幂等、并发与第三方边界。
5. 遵循仓库规范（`AGENTS.md` 与 `docs/blueprint/13_ENGINEERING_STANDARD.md`）。

## 本地验证（提交前必跑）

```bash
cd backend
./mvnw clean verify
git diff --check
bash scripts/ci/check-uncommitted.sh
```

公开仓库 CI 对 PR 会用 merge-base/base SHA 到 head SHA 执行 `git diff --check`，不是无参数检查。

- Secret 不得进入代码、日志、测试报告或 PR 描述。
- 新增依赖须进入 `THIRD-PARTY-NOTICES.md` 与 `docs/dev-0005/license-baseline.json`，并保持许可证取证（G-07）。

## 安全

安全漏洞请通过 `SECURITY.md` 的 **私密**渠道报告，不要公开创建安全 Issue。
