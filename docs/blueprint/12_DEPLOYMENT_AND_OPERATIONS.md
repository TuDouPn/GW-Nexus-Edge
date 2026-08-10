# 12 — 部署、可观测、备份与运行规格

> 状态：Accepted

## 1. 首验平台

- Server OS：Ubuntu Server 24.04 LTS x86_64。
- Deployment：Docker Compose。
- Renderer：专用 Windows 节点 + 已验证 WPS Office 环境。
- 浏览器：企业支持策略中列出的现代 Chromium 浏览器。
- V1 不以 Kubernetes、裸机多发行版或国产 CPU 为生产验收基线。

## 2. Docker Compose 服务

```text
nexus-edge-nginx
nexus-edge-api
nexus-edge-worker
nexus-edge-outbox-publisher
nexus-edge-sandbox-broker
nexus-edge-deployment-control
nexus-edge-egress-policy
mysql
postgresql-pgvector
redis
minio
otel-collector
prometheus
grafana
loki
```

AgentScope Harness/Core 内嵌于 `nexus-edge-api/worker` 的受控执行组件，不部署独立控制面。进程拆分可以为了资源隔离，但不改变模块化单体代码边界。

Coding基础设施不与Core Compose混在同一Docker Daemon，按独立Host Profile交付：

```text
Sandbox Host Pool: Rootless Docker + gVisor + Host Agent
Build Worker Pool: Rootless BuildKit + Host Agent
Preview Host Pool: Hardened OCI Runtime + Host Agent
Production Host Pool: Hardened OCI Runtime + Host Agent
Registry: Harbor
Ingress: Traefik Application Gateway
Egress: Envoy Gateway + Controlled DNS
```

## 3. 网络分区

### 内网首验

```text
User LAN → Nginx TLS → Web/API
                       ├─ Internal DB/Redis/MinIO
                       ├─ LDAP/AD
                       ├─ Model Endpoint/API Egress
                       └─ Renderer mTLS/TLS
```

### 可选 DMZ

```text
Internet → Enterprise WAF/ZTNA/VPN → DMZ Gateway → Internal Nginx/API
```

- 数据库、Redis、MinIO、AgentScope 和 Renderer 不直接暴露公网。
- WAF、VPN、ZTNA 和 MFA 可对接企业基础设施；V1 不自研。
- DMZ 模板、TLS、Header、限流和安全说明必须交付，但公网现场验收不是 P0。

### Coding Application网络

```text
Internet
  → Enterprise WAF/LB (optional)
  → Traefik Application Gateway
  → Preview/Production Application
  → Envoy Egress Gateway
  → approved Internet/API destinations
```

Application Host不得与Core、DB、Redis、MinIO、AgentScope或Renderer共享主机/Daemon。Host Agent使用mTLS主动注册；Core不持有通用SSH。Preview和Production必须分池。

## 4. 配置与 Secret

- 非敏感配置使用版本化模板和环境 Profile。
- Secret 通过受限文件/Docker Secret/环境主密钥注入。
- Compose 包不得携带真实密码或默认弱密码。
- 首次启动生成初始化指引，不自动创建可预测管理员口令。
- 生产配置变更记录操作者、时间、旧值摘要、新值摘要和原因；Secret 不记录值。

## 5. 离线安装包

包含：

- 固定版本 Docker 镜像 tar 与 SHA-256。
- Docker Compose、环境模板和 Secret 示例占位。
- Flyway Migration。
- 安装、初始化、升级、备份、恢复、安全和故障排查手册。
- SBOM、LICENSE、NOTICE、THIRD-PARTY-NOTICES。
- Windows Renderer Installer、签名/校验说明；Desktop Installer进入V1.1交付包。

安装程序在无公网环境不得隐式下载依赖。外部模型模式需要企业允许的 Egress；私有模型模式不需要公网模型访问。

## 5.1 一键部署入口

`nexus-edge-ctl` 是V1长期保留的部署CLI和自动化契约。它不是验证后删除的临时Shell脚本，也不是第二套应用控制面。Docker Compose、Flyway、Secret Provider和健康端点仍是实际执行能力，CLI负责以一致、幂等、可诊断的方式编排它们。

生产入口：

```text
nexus-edge-ctl preflight --config deployment.yaml
nexus-edge-ctl install --profile enterprise --config deployment.yaml
nexus-edge-ctl status
nexus-edge-ctl doctor --output ./diagnostics
nexus-edge-ctl backup
nexus-edge-ctl restore --backup <backup-id>
nexus-edge-ctl upgrade --package <release-package>
```

开发环境入口复用同一契约：

```text
nexus-edge-ctl dev up
nexus-edge-ctl dev status
nexus-edge-ctl dev down
```

`dev` Profile可以减少容量和可观测组件默认资源，但不得用Fake Service替换MySQL、PostgreSQL、Redis、MinIO或AgentScope真实集成。开发Profile不能作为生产验收证据。

CLI实现语言须通过工程ADR确定，但命令、配置Schema、退出码、阶段模型和日志格式是稳定公共契约，不随底层实现语言变化。

## 5.2 安装阶段

安装工作流固定为：

```text
VERIFY_PACKAGE
→ PREFLIGHT
→ LOAD_CONFIGURATION
→ INITIALIZE_SECRET_REFERENCES
→ LOAD_OR_PULL_IMAGES
→ START_DATA_SERVICES
→ RUN_FLYWAY_MIGRATIONS
→ START_PLATFORM_SERVICES
→ VERIFY_HEALTH
→ CREATE_SECURE_BOOTSTRAP_INSTRUCTION
→ WRITE_INSTALLATION_REPORT
```

每个阶段必须：

- 具有稳定阶段ID、开始/结束时间、结果和错误码；
- 可安全重试，完成标记不能仅存在于终端输出；
- 不在日志打印密码、API Key、LDAP凭证或Bootstrap Token；
- 失败时保留数据和诊断证据，不自动删除Volume；
- 对可恢复失败给出下一条准确命令。

安装完成的定义不是“容器已启动”，而是：

- MySQL、PostgreSQL、Redis、MinIO健康；
- Flyway版本与发布Manifest一致；
- Nexus Edge API、Web、Outbox、Parser和可观测链路健康；
- Secret引用可解析且没有默认弱密码；
- Bootstrap入口为一次性、短时、可审计流程；
- Renderer未配对时明确显示 `PLATFORM_READY_RENDERER_PENDING`，不得谎报完整生产就绪。

## 5.3 配置与安装状态

部署包必须包含版本化 `deployment.schema.json` 和示例 `deployment.yaml`。配置至少覆盖：

- Instance/Tenant基本标识和网络地址；
- TLS证书引用和可信根；
- MySQL、PostgreSQL、Redis、MinIO连接与Secret引用；
- LDAP、SMTP、模型Provider和Embedding Provider引用；
- 存储目录、容量和备份目标；
- 可观测Profile和保留周期；
- Renderer注册策略；
- 在线或离线镜像来源。

CLI保存非敏感安装状态、发布版本、Compose Project、Migration版本、镜像Digest和步骤结果。Secret只保存引用。状态文件和安装报告必须使用受限权限，不能成为第二个业务数据库。

## 5.4 Windows Renderer安装

Server部署不能假装跨操作系统自动安装Renderer。V1交付两类受控安装资产：

1. Server：`nexus-edge-ctl` + Compose Release Package。
2. Renderer：Windows MSI/企业软件分发包，安装Worker、服务账号权限、证书和注册向导，不捆绑来源不明的WPS安装程序。

Renderer安装包必须签名、携带版本、校验和、SBOM和卸载信息。Renderer只有完成Compatibility Gate、WPS许可确认、Server注册和健康测试后才能标记Ready。Desktop MSI、升级和企业软件分发属于V1.1。

## 5.5 升级、回退与卸载

- `upgrade`先执行包签名/Hash、兼容矩阵、容量、备份和Migration预检。
- 使用固定镜像Digest，不使用`latest`。
- Flyway采用向前迁移；应用回退只有在Schema保持向后兼容时允许。
- 不可安全应用回退时，CLI必须阻止并给出前向修复或备份恢复流程。
- 普通`uninstall`只移除应用与受控运行资源，默认保留数据库、对象、备份和审计。
- 物理删除必须使用独立`purge`流程、明确目标、二次确认和审计，不得隐藏在卸载中。
- CLI不得提供绕过TLS、Secret、Migration或数据保护门禁的通用`--force`开关。

## 6. 可观测性边界

### AgentScope

负责 Execution、Agent、Tool、Model、Memory、Workflow 和 Runtime 异常的官方事件/观测。

### Nexus Edge

负责 Workspace、Resource上传/解析、目录服务同步、Skill、Task、Policy、Artifact、Approval、Notification和业务审计；不包含V1.1 Desktop目录同步观测。

关联键：Task ID → AgentScope Execution ID → OpenTelemetry Trace ID。

Coding链路额外关联：Project ID → Coding Task/Execution/Sandbox → ChangeSet → Build → Image Digest → Release Candidate → Deployment → Domain。

## 7. 技术栈

- OpenTelemetry：Trace 和 Context 传播。
- Prometheus：服务、队列、任务、解析、Renderer、模型和业务指标。
- Grafana：统一 Dashboard。
- Loki：结构化应用日志。
- MySQL Audit 表：业务与安全审计。
- 不引入 Elasticsearch。

## 8. P0 指标

- HTTP latency/error/rate。
- Active/queued/retrying/failed Task。
- Agent Execution duration、model/tool errors、tokens/cost。
- Parser queue lag、failures、document throughput。
- Redis Stream lag、pending、DLQ。
- Renderer online nodes、queue lag、duration、failure and orphan process count。
- RAG latency、candidate count、rerank latency、ACL rejection。
- Notification delivery latency/failure。
- MinIO、MySQL、PostgreSQL、Redis health/capacity。
- Sandbox/Build/Preview/Production Node capacity、lease、duration、failure与resource limit。
- Harbor push/pull、scan、signature和storage capacity。
- Deployment stage、health、rollback、Traefik route和Envoy policy reject。
- Application request/error/latency、container CPU/memory/network/restart。
- Domain verification、ACME issue/renewal与certificate expiry。

指标 Label 禁止放用户输入、文件名、Prompt 或高基数全文 ID；Trace 中敏感 Attribute 必须脱敏。

## 9. SLO/告警基线

- 在线 API 服务可用性、错误率和 P95 latency 设置试点 SLO。
- Task 超过 30/60 分钟、长时间无进度、重试耗尽时告警。
- Renderer 全部离线、Render Queue 超限、DLQ 非空立即告警。
- 外部模型错误率、配额、成本异常和 Policy 拒绝激增告警。
- 存储容量达到 70/85/95% 分级告警。
- Production Gate阻断、自动回滚、Host Agent离线、Harbor签名验证失败和证书续期失败立即告警。

具体数值需在兼容性和压测阶段形成 ADR，不得用未经测试的数字伪装承诺。

## 10. Backup/Recovery

目标：RPO ≤24 小时，RTO ≤4 小时。

必须备份：

- MySQL 全量 + 可用 Binlog。
- PostgreSQL/pgvector 全量 + 可用 WAL。
- MinIO 原始文件、模板、Artifact Model、Artifact。
- Audit 数据与归档。
- 配置、Policy、迁移版本和恢复所需 Secret Reference；Secret 值按企业 Secret 系统备份。
- Coding Project/Repository Binding、Plan、ChangeSet、Release、Deployment、Domain、Resource/Runtime Profile和Environment/Secret Reference。
- Harbor中已发布Production Image、SBOM、Signature和Provenance。

Redis 缓存可重建；Streams 和必要状态开启 AOF，但不把 Redis 作为唯一恢复源。Agent Checkpoint 按 AgentScope 官方持久化方案纳入备份。

默认：每日全量、必要增量、保留 30 天。备份必须加密、校验、限制访问并定期恢复演练。只有“备份成功”日志而未实际恢复验证不算通过。

## 11. 恢复顺序

1. 恢复 Secret/配置和网络。
2. 恢复 MySQL、PostgreSQL、MinIO。
3. 启动 Redis、应用和 AgentScope 集成。
4. 恢复 Outbox/Streams 和 Renderer。
5. 验证登录、Workspace、历史 Snapshot、Artifact、Audit。
6. 对不一致的 Task 执行对账：恢复、重试或明确失败。
7. 恢复Harbor与Coding控制面，验证Image Signature/Gate/Secret/Certificate后重建Production Application并激活流量。

## 12. Upgrade

- 发布包不可使用 floating `latest`。
- 迁移遵循先备份、兼容部署、Flyway 前滚、健康验证。
- Skill/Prompt/Policy/Template 与代码独立版本化。
- AgentScope 升级必须通过官方 Release、兼容测试和 ADR；不得自动跟随最新版本。
