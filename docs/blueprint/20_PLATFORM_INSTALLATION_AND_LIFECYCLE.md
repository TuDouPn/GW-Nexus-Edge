# 20 — GW Nexus Edge V1 平台安装与生命周期规格

> 状态：Accepted  
> 优先级：V1 P0  
> 生产基线：Ubuntu Server 24.04 LTS x86_64 + Docker Compose  
> 稳定入口：`nexus-edge-ctl`

> 术语修正：本文描述的是 Nexus Edge 平台自身的安装、升级、诊断与恢复，不是 Coding Workspace 将用户应用“一键发布”到域名的产品能力。应用发布见 `23_CODING_APPLICATION_DEPLOYMENT.md`。

## 1. 产品目标

企业交付人员拿到经过签名和校验的发布包后，应通过一个稳定入口完成环境检查、安装、初始化、启动和验收，不需要理解每个容器、手工拼接环境变量、逐个执行SQL或登录数据库修复状态。

平台安装能力解决的是交付一致性和可恢复性，不是把企业安全配置隐藏起来，也不等同于 Coding Workspace 的应用一键发布。域名、TLS、Secret、LDAP、模型Provider、邮件和Renderer许可仍需企业提供真实配置，但系统必须通过Schema、预检和向导明确收集，而不是散落在脚本和文档中。

## 2. 长期架构决策

采用稳定Deployment CLI +声明式配置+版本化Release Manifest：

```text
Signed Release Package
        ↓
nexus-edge-ctl
        ├─ deployment.schema.json
        ├─ deployment.yaml
        ├─ Secret References
        ├─ Release Manifest / Image Digests
        ├─ Docker Compose Profiles
        ├─ Flyway Migrations
        └─ Health / Backup / Diagnostic Contracts
```

`nexus-edge-ctl`是长期产品接口，不是未来必然被替换的脚本。内部可以调用Docker Compose、Flyway、Backup Provider和企业Secret Provider，但对交付人员暴露一致命令、状态和错误模型。

## 3. 用户与用例

| 用户 | 核心用例 |
|---|---|
| 企业交付工程师 | 预检、在线/离线安装、初始化、生成安装报告 |
| 系统管理员 | 查询状态、诊断、配置验证、Renderer配对 |
| 运维人员 | 升级、备份、恢复、证书轮换、故障处理 |
| 安全审计员 | 查询安装/升级/恢复记录和包校验信息 |
| 开发人员/AI Agent | 一条命令启动真实依赖的本地集成环境 |

## 4. 命令契约

P0命令：

```text
nexus-edge-ctl version
nexus-edge-ctl preflight
nexus-edge-ctl install
nexus-edge-ctl status
nexus-edge-ctl doctor
nexus-edge-ctl backup
nexus-edge-ctl restore
nexus-edge-ctl upgrade
nexus-edge-ctl renderer registration-token
nexus-edge-ctl dev up
nexus-edge-ctl dev status
nexus-edge-ctl dev down
```

命令必须支持人类可读输出和`--output json`机器输出。退出码、错误码和阶段ID必须稳定，以便GitHub Actions、企业运维和其他Agent自动化调用。

## 5. Preflight

检查项：

- Ubuntu版本、x86_64、内核和时钟同步；
- CPU、内存、磁盘、inode和文件系统权限；
- Docker Engine、Compose Plugin和Daemon状态；
- 端口占用、主机名、DNS、NTP、代理和防火墙提示；
- TLS证书、私钥引用、有效期、SAN和信任链；
- 安装目录、数据目录、备份目录和挂载点；
- 在线Registry可达性或离线镜像包完整性；
- MySQL/PostgreSQL/Redis/MinIO配置与Secret引用；
- LDAP、SMTP、外部模型/Embedding Endpoint可达性按Profile检查；
- Coding Profile 启用时，检查 Sandbox/Build/Preview/Production Host Pool、gVisor、Rootless BuildKit、Harbor、Traefik、Envoy Egress、受控 DNS 和 Host Agent 的版本、身份与隔离边界；
- 当前版本到目标版本的兼容路径。

检查结果分为`PASS`、`WARN`、`FAIL`。安全、数据损坏或必需依赖风险必须是FAIL，不能用通用Force绕过。

## 6. Install

安装必须具有：

- 幂等：相同配置重复执行不会重复初始化或破坏数据；
- 可恢复：失败后从安全阶段继续或准确回滚未提交步骤；
- 可观察：显示阶段、进度、耗时、结果和诊断位置；
- 可审计：记录包版本、Digest、操作者、目标实例和结果；
- 可验证：最终执行业务级健康检查，不以容器Running代替可用；
- 安全默认：随机或外部Secret、TLS、最小权限、无默认弱口令；
- 离线确定性：不访问未声明公网地址。

成功状态分级：

| 状态 | 含义 |
|---|---|
| PLATFORM_READY | Server核心服务健康，基础初始化完成 |
| PLATFORM_READY_RENDERER_PENDING | Server健康，但尚无通过门禁并完成配对的Renderer |
| ENTERPRISE_READY | LDAP/模型/SMTP/Renderer等企业必需配置验证完成 |
| INSTALL_FAILED | 任一必需阶段失败，系统不得宣称Ready |

## 7. 配置与Secret体验

- `deployment.yaml`只保存非敏感配置和Secret引用。
- `deployment.schema.json`提供类型、必填、枚举、格式和条件校验。
- 首验支持受限Secret文件、Docker Secret和环境注入。
- CLI可以生成强随机Bootstrap Secret，但只允许一次性安全展示或写入受限Secret Provider。
- 不得把密码、API Key、LDAP Bind凭证或SMTP凭证写入YAML、命令历史、安装报告和日志。
- 修改生产配置必须先`validate`，再以受控Apply操作生效并记录审计。

## 8. 在线与离线

在线模式从批准Registry获取固定Digest镜像。离线包必须包含：

- 所有服务镜像和Digest；
- Compose、Schema、Profile和Migration；
- `nexus-edge-ctl`及其校验文件；
- SBOM、NOTICE、许可证和漏洞扫描结果；
- Renderer安装包或经过校验的独立介质清单；Desktop安装资产进入V1.1；
- Coding Profile 所需的 Host Agent、Runtime Catalog、Harbor、Gateway/Egress 配置、签名公钥与固定 Digest 制品；
- 安装、升级、备份、恢复和排障文档。

离线安装测试必须在阻断公网的环境执行。发现隐式下载即判定失败。

## 9. Windows交付边界

Server和Renderer分属不同安全与操作系统边界，不能用“一个按钮跨机器安装”制造不安全自动化。

- Renderer使用独立签名MSI，安装Worker服务、受限目录、证书和注册组件。
- WPS Office由企业依据许可独立安装；Nexus Edge不重新分发来源或许可不明的WPS安装包。
- Renderer注册Token短时、单次、绑定Instance和Node，注册完成后立即失效。
- Server的`status`统一展示Renderer健康状态。Desktop版本兼容与安装管理进入V1.1。

## 10. Upgrade

升级流程：

```text
VERIFY_TARGET
→ CHECK_COMPATIBILITY
→ PREFLIGHT_CAPACITY
→ CREATE_BACKUP
→ LOAD_IMAGES
→ APPLY_COMPATIBLE_MIGRATION
→ SWITCH_SERVICES
→ VERIFY_HEALTH
→ COMMIT_UPGRADE
```

- 任何失败均保留原始日志和阶段状态。
- Schema兼容时允许应用版本回退；不兼容时禁止盲目回退。
- Migration失败优先前向修复或使用已验证备份恢复。
- Skill、Prompt、Policy和Template版本不因应用升级被覆盖。
- 升级完成后验证登录、Workspace、Knowledge、Task、Artifact、Audit和Renderer。
- Coding Profile 启用时还必须验证 Git Provider、Sandbox Broker、Build Worker、Registry、Preview、Production、Domain/Certificate 与回滚链路。

## 11. Backup、Restore与Doctor

- `backup`统一编排MySQL、PostgreSQL、MinIO、Audit、配置和AgentScope官方持久化备份。
- `restore`要求目标检查、备份校验、明确RPO点和恢复后业务验证。
- `doctor`生成脱敏诊断包，包括版本、健康、容量、Compose状态、Migration、队列、Renderer和最近错误摘要。
- 诊断包不得包含Secret、原始Prompt、文档正文和未经授权的文件名。
- RPO≤24小时、RTO≤4小时必须通过真实恢复演练证明。

## 12. Uninstall与Purge

- 卸载默认保留企业数据、备份、审计和Secret Provider内容。
- 删除数据使用独立Purge流程，显示精确资源、保留策略和不可恢复影响。
- Purge需要高权限、二次确认、审计和完成报告。
- 禁止使用未解析环境变量、宽泛目录或递归命令识别删除目标。

## 13. 测试矩阵

至少覆盖：

| 场景 | 预期 |
|---|---|
| 全新在线安装 | 一次命令达到正确Ready状态 |
| 全新离线安装 | 零隐式公网访问，完整安装 |
| 重复install | 幂等，无重复初始化和数据损坏 |
| 端口占用/磁盘不足 | Preflight明确阻止 |
| Secret缺失/证书错误 | 安全失败，不回显Secret |
| 镜像损坏 | Digest校验失败，不启动错误版本 |
| Flyway失败 | 停止、保留证据、可安全处置 |
| Worker/Renderer缺失 | Server可分级Ready，不谎报完整能力 |
| 服务重启 | 安装状态和数据保持 |
| 兼容升级 | 备份、迁移、切换和验证完成 |
| 不兼容回退 | 被明确阻止并给出恢复路径 |
| Restore演练 | RPO/RTO与业务验证通过 |

## 14. 完成定义

平台安装与生命周期 P0 只有在以下条件全部满足时完成：

- 稳定CLI、配置Schema、Release Manifest和Compose Profile进入版本控制；
- 干净Ubuntu环境E2E通过；
- 在线与离线包均通过；
- 重复安装和失败恢复通过；
- Windows Renderer Installer、Renderer注册和状态展示通过；
- 升级、备份、恢复和诊断通过；
- Secret、TLS、数据保护和审计门禁通过；
- 安装报告可供企业交付签字；
- 文档和实际命令完全一致。

单独提供一个只能启动容器的脚本，不满足本规格，也不得对外称为企业级平台安装能力。Coding Workspace 的应用一键发布必须独立满足 `23_CODING_APPLICATION_DEPLOYMENT.md`，不能用本 CLI 的安装成功替代。
