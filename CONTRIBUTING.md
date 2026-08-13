# Contributing to GW Nexus Edge

感谢你考虑为 GW Nexus Edge 贡献代码。

## 贡献授权（inbound = outbound）

向本项目提交贡献（代码、文档、测试等），即表示贡献者同意其贡献按本项目的
**Apache License 2.0** 授权（见 `LICENSE`）。

- 贡献者**不得**提交无权授权的代码、企业资料、Secret、专有模板或受限制数据。
- 第三方代码必须说明来源与许可证。
- **不允许**复制许可证不兼容或来源不明的代码。

> 本贡献基线与 CLA/DCO 不同：当前项目**未采用 CLA、DCO、贡献者协议或商标政策**；
> 不得擅自引入签署门禁或相关机器人。这些事项如需引入，将另行通过 ADR 决定。

## 开发流程

1. 每个工作项使用独立分支：`agent/DEV-xxxx-<slug>`。
2. 从 `main`（最新）创建分支，不直接从他人分支继续开发。
3. 提交 PR 合并到 `main`；PR 必须通过 `Backend CI / Verify`、`Repository Security / Scan`、
   `Workflow Security / Lint`（及适用的 `Dependency Review / Review`）检查。
4. 代码注释使用**中文**，解释关键业务规则、权限、事务、幂等、并发与第三方边界。
5. 遵循仓库规范（`AGENTS.md` 与 `docs/blueprint/13_ENGINEERING_STANDARD.md`）：
   - 不引入 Mock 冒充真实集成、固定 JSON、TODO、`UnsupportedOperationException` 或占位实现。
   - UI 组件复用 `@gwnexus/ui` / shadcn/ui / 批准组件，不擅自自研已有组件。
   - 架构/安全/契约变化须先经 ADR（`docs/adr/`）批准，再原子同步 Blueprint。
   - 工作项进度记录于 `docs/handoffs/active/DEV-xxxx.md`，含真实 Commit SHA 与测试证据。

## 本地验证（提交前必跑）

```bash
cd backend
./mvnw clean verify        # Java 21；Testcontainers 需 Docker
git diff --check           # 无尾随空格
```

- Secret 不得进入代码、日志、测试报告或 PR 描述；API Key 只经环境变量或 Secret Reference 注入。
- 新增依赖须进入 `THIRD-PARTY-NOTICES.md` 并保持许可证合规（G-07）。

## 安全

- 安全漏洞请通过 `SECURITY.md` 的 **私密**渠道报告，不要公开创建安全 Issue。
