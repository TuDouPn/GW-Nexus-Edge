# 02 — V1 范围、容量与验收标准

> 状态：Accepted

## 1. 目标环境

- 一个私有部署实例对应一家企业和一个 Tenant。
- 企业目录最多同步 500 个账号。
- 目标 50 DAU、20 人同时在线。
- 同时运行最多 5 个业务 Agent Task。
- 一个经营分析 Task 通常包含主 Agent、Review Agent 和专业 Tool/Workflow。

## 1.1 客户端范围

V1 是 Web-only 产品版本。经营分析和 Coding 两条 P0 必须均可在浏览器中完成全部闭环：

1. Managed Workspace 的 V1 数据入口为 Web 文件上传。
2. Coding Project 的 V1 入口为 Web 创建新项目或导入远程 Git Repository。
3. V1 不开发、不构建、不部署、不验收 Tauri/Windows Desktop、目录同步、本地 Git 发现、设备能力、Desktop 系统通知或 MSI。
4. Windows Renderer 是独立 Artifact 基础设施节点，不属于 Desktop。

Web 验收必须满足：P0 页面全部可用；两条 P0 Web E2E 通过；响应式、无障碍、权限、SSE 重连、错误恢复、组件复用和核心前端覆盖率门禁通过；不存在依赖 Desktop 的业务步骤。

## 2. 容量基线

| 指标 | V1 目标 |
|---|---:|
| 用户账号 | 500 |
| 同时在线 | 20 |
| 并发 Agent Task | 5 |
| 单 Workspace 文件数 | 5,000 |
| 单 Workspace 权威文件容量 | 100 GB |
| 正式支持单文件上限 | 100 MB |
| Knowledge Chunk | 单企业 1,000,000 |
| 标准经营分析初稿 | ≤30 分钟 |
| 复杂任务最大时间 | ≤60 分钟 |
| Coding Project | 50 |
| 同时在线 Preview | 20 |
| Production Application | 20 |
| 并发 Coding Sandbox | 5 |
| 并发 OCI Build | 3 |
| 并发 Application Deployment | 2 |

超过 100 MB 的文件在 V1 默认拒绝进入 Knowledge Pipeline；视频等大文件不属于范围。

## 3. 业务成果标准

### 3.1 Word

- 使用企业 `.docx` 模板。
- 保留 Logo、页眉页脚、字体、标题样式、表格和图片槽位。
- 生成正确 Heading 层级和可更新目录。
- 关键事实、数字和推断具有 Evidence 关联。
- 用户不需要重新排版，人工修改量目标不超过约 20%。

### 3.2 PowerPoint

- 使用企业 `.pptx` 母版、主题、页面比例、Logo 和页脚。
- 生成封面、目录、背景、数据、问题、建议、计划和结束页等业务结构。
- 图表必须是可编辑 Office Chart，不得以截图冒充。
- 控制页面文字密度，突出结论和数据。
- 人工顺序和观点调整目标不超过约 20%–30%。

### 3.3 Excel

- 使用企业 `.xlsx` 模板并保留 Sheet、格式、颜色和公式区域。
- 派生指标写入可复算公式，不只写最终数字。
- 图表保持可编辑。
- 记录源文件、Sheet、区域/字段、计算公式和 Snapshot。

## 4. 可信度硬门槛

| 指标 | 要求 |
|---|---:|
| 关键数字来源 | 100% 可追溯 |
| 派生指标公式 | 100% 记录并可复算 |
| 关键事实 Evidence | 100% 存在 |
| 无来源数字 | 0 容忍 |
| 虚构文件、页码或引用 | 0 容忍 |
| 冲突数据 | 必须显式提示，不得自行选值 |
| 证据不足 | 必须说明缺少资料，不得补写事实 |
| 推断结论 | 必须标为 Inference，并展示依据 |

报告可信度评分只能作为辅助指标，不得掩盖未引用事实。任何 P0 可信度失败都阻止 Artifact 发布。

## 5. Enterprise Pilot 验收

### 5.1 样本与成功率

- 至少执行 20 次完整经营分析任务。
- 完整任务覆盖 Task 创建、AgentScope 执行、RAG/Tool、Renderer、业务审核、最终批准和发布。
- 端到端成功率至少 90%；用户主动取消不计失败。
- 模型或 Renderer 临时故障经自动恢复后完成可计成功；系统异常或错误结果计失败。

### 5.2 效率与满意度

- 标准初稿 ≤30 分钟，复杂任务 ≤60 分钟。
- 人工二次加工时间相较原流程降低至少 70%。
- 业务分析员、负责人和审批人参与真实验收。
- 满意用户比例 ≥80%，或平均评分 ≥4/5。

### 5.3 安全零事故

- Workspace/File/Knowledge/Artifact 越权访问：0。
- 未授权 L3 数据外发：0。
- 关键操作审计缺失：0。
- Secret、原始 Prompt 或敏感上下文进入日志：0。

## 6. Production Readiness Gate

经营分析 Skill 进入生产验收前，试点企业必须提供：

1. 脱敏但保留真实业务结构的数据集。
2. 正式 Word、PPT、Excel 模板。
3. 人工认可的 Golden Result。
4. 指标口径、来源优先级和冲突解决规则。
5. 业务负责人、分析员和审批人名单。

在材料缺失时可以完成平台、Agent、Tool、Renderer 和测试框架，但不得声明 Skill 已达到生产正确性或模板保真。

## 7. Renderer Compatibility Gate

WPS Office 免费版仅作为首个验证基线，不预设其具备企业无人值守能力。必须验证：

- 合法安装与运行方式。
- 自动化 API 可用性和授权边界。
- Word/PPT/Excel 模板保真。
- 并发、超时、僵尸进程、崩溃恢复和重复任务幂等。
- 连续样本稳定性和文件完整性。

门禁未通过时，Artifact Model 可以完成，但正式 Office Artifact 不得标记生产就绪。

## 8. 平台安装生命周期验收门槛

V1 必须提供稳定的 `nexus-edge-ctl` 部署入口。在满足前置条件的全新 Ubuntu Server 24.04 LTS x86_64 环境中，验收人员不得手工编辑数据库、逐个启动容器或人工执行 Flyway SQL。

必须验证：

- `nexus-edge-ctl preflight` 能识别 OS、CPU、内存、磁盘、端口、Docker/Compose、时间、DNS、证书和目录权限问题。
- `nexus-edge-ctl install --profile enterprise` 能幂等完成配置校验、Secret引用初始化、在线镜像拉取或离线镜像装载、服务启动、Flyway迁移和健康检查。
- 重复执行 `install` 不创建重复数据、不重置Secret、不破坏已安装实例。
- 任一步骤失败时返回非零退出码、结构化阶段、可操作错误和诊断包路径；禁止显示成功后留下不可用系统。
- `status`、`doctor`、`backup`、`restore`、`upgrade` 提供统一入口并记录操作审计。
- Windows Renderer 提供可签名安装包，完成安装后可通过受控注册与Server配对；Desktop安装包不属于V1。
- 离线安装全程不隐式访问公网，包内镜像、SBOM、迁移和校验和版本一致。
- 干净环境安装、重复安装、失败恢复、升级和备份恢复E2E全部通过。

`nexus-edge-ctl` 表示用户使用一个稳定入口驱动平台自身完整、可观测、可恢复的安装生命周期，不表示 Coding Workspace 的应用一键发布，也不表示跳过企业域名、证书、Secret、LDAP、模型凭证或Renderer许可等必要配置。

## 9. Coding Workspace Production Readiness Gate

试点企业必须提供：

1. 至少两个脱敏但结构真实的企业前端仓库。
2. 至少一个静态项目和一个 Node.js SSR 项目。
3. 真实改造任务、可验证验收条件和人工认可目标结果。
4. 测试运行方式、部署配置和非生产 Secret。
5. 至少一个具有企业真实依赖、组件体系和构建复杂度的项目。

材料缺失时可以完成平台、Sandbox、Agent、Build 和 Deployment 工程能力，但不得声明真实企业 Coding 场景生产就绪。

## 10. Coding Workspace Enterprise Pilot

### 10.1 样本

- 至少20次完整Coding闭环。
- Agent新建和导入已有Repository各不少于5次。
- 覆盖React/Vue静态应用、Next.js/Nuxt SSR应用。
- 覆盖GitHub First-Class Provider和至少一个标准Git Provider。
- 完整闭环包括Plan、Sandbox、Agent修改、Build/Test、Diff确认、Push、Preview、Security Gate、Release Candidate、人工Production、HTTPS和Audit。

### 10.2 成功指标

| 指标 | 目标 |
|---|---:|
| 端到端Coding成功率 | ≥90% |
| 自定义域名绑定 | ≥5次 |
| Production发布 | ≥10次 |
| 历史Release回滚 | ≥5次 |
| Commit/Artifact/Image Digest不一致 | 0 |
| 越权发布/门禁绕过 | 0 |
| Production Secret泄漏 | 0 |
| 生成代码横向访问核心网络 | 0 |

### 10.3 性能

| 指标 | 目标 |
|---|---:|
| Sandbox创建P95 | ≤60秒 |
| Build完成到Preview URL可用 | ≤3分钟 |
| Approved RC到Production Active | ≤5分钟 |
| 一键回滚 | ≤2分钟 |
| DNS验证到证书/Gateway激活 | ≤10分钟 |
| 实时关键通知 | ≤30秒 |
| 邮件进入发送队列 | ≤2分钟 |

Blue-Green发布不得产生计划内服务中断。目标并发运行时不得使Nexus Edge Core、业务Agent Task或既有Production应用不可用。

## 11. V1整体通过规则

经营分析 Workspace 与 Coding Workspace 是两条相互独立、均为P0的验收轨道。整体Enterprise Pilot V1只有在两条轨道各自通过Production Readiness Gate、业务/技术样本、安全零事故和恢复门禁后才能宣告通过。任一P0未达标即允许延期，不得用Mock、人工后台补数据、降低隔离、跳过发布门禁或降低Artifact质量换取日期。
