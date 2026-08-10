# 10 — V1 Web 与 V1.1 Desktop 用户体验规格

> 状态：Accepted

## 1. 前端技术基线

- React 19、TypeScript、Vite、Tailwind CSS、shadcn/ui；Agent 对话场景采用 assistant-ui。
- V1 唯一用户应用为 React 19 Web Workspace。
- V1 Monorepo 建立 `@gwnexus/ui`、`@gwnexus/assistant-ui`、`@gwnexus/design-tokens`、`@gwnexus/api-client`、`@gwnexus/domain-types`、`@gwnexus/agent-events` 和 i18n 包，保持客户端中立。
- V1 不包含 Tauri、Rust Desktop Runtime、Windows Desktop构建或MSI。
- V1.1 Desktop 计划使用 React + TypeScript + Tauri 2 + Rust，只扩展本地目录、文件 Hash/上传、设备身份、本地 Git 发现和系统通知。

### 1.1 V1 Web-only 门禁

经营分析与 Coding 两条 P0 必须在 Web 中独立完成创建、执行、进度、Evidence/Diff、Preview/Artifact、审核、发布、通知和管理闭环：

- V1 Work Item 不得包含 Desktop 业务页面、本地同步、Tauri构建或Windows打包；
- 不为未来 Desktop 提前分叉 API、状态机或领域模型；
- 不允许以“V1.1 Desktop 后续补齐”为由缺失 Web P0 能力；
- V1 CI、安装包与 Enterprise Pilot 均不得把 Desktop 作为通过条件或制品。

V1.1 立项后，Desktop 必须复用 V1 稳定的 React 领域模块、组件、API SDK、Domain Types 和 Agent Events，只增加浏览器无法提供的 Edge 能力。

## 2. V1.1 grok-app 复用边界

Desktop 是 Nexus Edge Monorepo 内自主维护的正式应用，不以持续跟随上游 Fork 作为产品架构。可在 MIT 合规前提下逐文件评估复用 grok-app 的 UI、媒体预览、Tauri 工程模式和交互组件。必须：

- 逐文件记录来源、原始许可证与修改。
- 移除 Grok CLI、ACP、Grok 登录/配额、Grok 自动化、YOLO/CLI Permission 和宿主 Session FSM。
- 所有 Agent 请求改为 Nexus Edge API/SSE。
- 对复用代码执行安全、依赖和维护性审查。

不得直接 Fork 后仅更换品牌名称，也不得把 grok-app 作为 Desktop 的外部 Runtime 或升级控制面。

## 3. Web P0 页面

- 企业登录与身份错误处理。
- Workspace 列表/详情/成员/数据等级。
- Resource 与解析/索引状态。
- Skill 选择与经营分析任务表单。
- Agent Task 工作台与安全进度流。
- Evidence 查看器。
- Artifact 库、预览、版本、批注、审核和发布。
- Notification Center。
- 管理 Console：身份源、模型、Policy、Skill/Template 版本、Renderer、审计。
- Coding Project列表、创建/导入、Repository/Branch/Project Root和Build Contract。
- Coding Plan审核、Agent工作台、Repository文件树、Monaco只读源码、Diff和测试结果。
- Preview、Release Candidate、Security Finding/SBOM、Deployment、Domain、日志和指标。

Web 可完整运行 Agent 任务，不要求 Desktop 在线。

## 4. Desktop V1.1 规划能力（不属于V1验收）

- 与 Web 一致的登录、Workspace、Task、Artifact 和通知核心体验。
- 本地目录选择和授权说明。
- 扫描结果、格式支持、上传进度、失败重试和冲突提示。
- 文件变更检测、版本预览和 Delete Request。
- 设备在线状态和诊断导出。
- Coding Workspace核心流程与Web一致；额外支持本地Git Repository发现和显式导入确认。

本地同步只向服务器上传，不自动把服务器文件回写本地。
Desktop不得在本机运行用户代码或Coding Agent命令，所有Coding执行进入远端Sandbox。

## 5. Agent 任务体验

Agent 对话、消息线程、Composer、流式状态、重试、附件和 Tool UI 优先采用 `@gwnexus/assistant-ui`。该包封装 assistant-ui 官方 Component/Primitive、Nexus Design Token、zh-CN 文案和 Custom Runtime Adapter；页面不得直接耦合上游 Runtime API。

用户可见：

- 任务目标、绑定 Snapshot/Skill/Template。
- 当前业务阶段和预计状态。
- 安全处理后的执行计划。
- Tool 名称、输入类别、状态和结果摘要。
- 使用的模型与选择原因。
- Evidence 收集进度、冲突和缺失资料。
- Renderer 状态、审批状态和通知。

禁止展示隐藏思维链、Secret、完整外发 Prompt 或无权限内容。

assistant-ui 的 Reasoning、Tool UI 或 Inline Approval 能力不得改变该规则：

- Reasoning 区域只能显示 Nexus Server 产生的安全业务阶段和摘要，不显示 Chain of Thought；
- Tool UI 只能消费经过权限、Context Policy 和脱敏处理的 Agent Event；
- 取消、重试、审批、发布等操作必须调用 Nexus `/api/v1`，由服务端重新授权并推进状态机；
- assistant-ui 客户端状态不是 Task、Message、Approval 或 Execution 的权威存储；断线后通过 REST 状态与 SSE `Last-Event-ID` 恢复。

## 6. 长任务

- 提交后客户端可关闭，服务器继续执行。
- 页面断线重连通过 REST 状态 + SSE Last-Event-ID 恢复。
- 支持取消，展示 `CANCEL_REQUESTED` 到 `CANCELLED` 过程。
- 超过 30 分钟不视为超时，只要不超过复杂任务上限且进度持续可见。
- 完成、失败、审批和退回通过站内、Web实时通知和邮件通知；Desktop系统通知进入V1.1。

## 7. 审核体验

- 平台内只提供预览、Evidence、批注、通过和退回。
- 不提供在线 Office 编辑。
- 用户下载后在 WPS/Office 修改，重新上传创建新 ArtifactVersion。
- 页面同时展示 AI Generated、Human Modified、Template/Renderer Version 和 Evidence coverage。
- 退回必须填写原因，并允许人工修改或“根据意见重新生成”。

## 8. Design System

必须定义：颜色、字体、间距、圆角、阴影、状态颜色、表格、表单、对话框、Toast、进度、Empty/Error/Loading、Agent Thread、Message、Composer、Tool UI、Agent Event、Evidence 和 Diff 组件。

### 8.1 组件复用优先级

普通管理、表单、表格与业务页面必须按照以下顺序选择组件；V1.1 Desktop 沿用同一规则：

1. 优先使用 `@gwnexus/ui` 已发布组件，保证两端交互和视觉一致。
2. 共享库没有时，使用项目已引入的 shadcn/ui 组件。
3. 单个 shadcn/ui 组件不足时，优先采用 shadcn/ui 官方组合方式或其底层无障碍 Primitive 进行组合。
4. 现有技术栈确实无法满足时，先评估成熟、可维护、许可兼容的第三方组件。
5. 只有前述方案均不可用并获得产品架构负责人书面批准后，才允许开发新的通用基础组件。

Agent 对话界面采用专用顺序：

1. `@gwnexus/assistant-ui` 中已治理的 Nexus Agent 交互组件。
2. 经 G-14 验证并锁定版本的 assistant-ui 官方 Component、Headless Primitive 和 Tool UI。
3. `@gwnexus/ui`、shadcn/ui 及其 Primitive 补充普通控件。
4. 经产品架构负责人批准的其他成熟组件。
5. 前述能力均不可用且完成例外流程后，才允许开发新的 AI 对话基础组件。

页面和业务 Feature 不得直接导入 `@assistant-ui/react`，也不得直接运行 `assistant-ui init` 后把生成代码散落到业务目录。确需采用 assistant-ui Registry 源码时，只能进入 `@gwnexus/assistant-ui`，记录上游来源/版本/许可证，并接受与共享组件相同的评审、测试和升级治理。

“组件复用优先”是强制规范，不是开发建议。任何 AI Agent 和开发人员不得为了快速、视觉偏好或减少依赖调用而擅自重写现有通用组件。

### 8.2 禁止重复实现的组件

未获得批准时，禁止自行实现以下类别：

- Button、Input、Textarea、Checkbox、Radio、Switch、Select。
- Form、Field、Validation Message、Date/Time Picker。
- Dialog、Alert Dialog、Drawer、Sheet、Popover、Tooltip。
- Dropdown Menu、Context Menu、Navigation Menu、Command Palette。
- Tabs、Accordion、Collapsible、Breadcrumb、Pagination。
- Table、Data Table、Tree、List、Card、Badge、Avatar。
- Toast、Alert、Progress、Skeleton、Spinner、Empty State。
- 焦点陷阱、键盘导航、浮层定位、滚动锁定等底层交互能力。
- Thread、Message、Composer、ThreadList、ActionBar、Attachment、Streaming/Retry State、Tool Call Fallback 和 Tool UI 等已有 AI 对话能力。

禁止通过复制粘贴、改名或在业务目录中隐藏实现的方式绕过共享组件治理。

### 8.3 允许的业务组合组件

可以开发具有 Nexus Edge 领域语义的组合组件，例如：

- WorkspaceCard、ResourceSyncStatus。
- TaskProgressPanel、AgentEventTimeline。
- AgentWorkspacePanel、SafeToolResult、AgentTaskStatusBridge。
- EvidenceViewer、ConflictBanner、TrustScorePanel。
- ArtifactPreview、ApprovalPanel、RendererStatus。

业务组合组件必须由现有基础组件构成。它可以封装领域数据、权限状态和业务交互，但不能重新实现按钮、表单、对话框、表格、焦点管理或键盘导航等基础能力。

### 8.4 自定义组件例外流程

确需新增通用组件时，Pull Request 或设计记录必须包含：

- 搜索过的 `@gwnexus/ui`、shadcn/ui、assistant-ui（AI 对话场景）和第三方候选。
- 每个候选无法满足需求的可验证原因。
- 建议组件的职责、公共 API 和不允许承载的业务逻辑。
- 键盘、焦点、ARIA、颜色对比、响应式和主题方案。
- 单元、交互、视觉回归和 Web 兼容测试；Desktop兼容测试在V1.1执行。
- 产品架构负责人批准记录。

未经批准的自定义基础组件不得合并。批准新增后，应优先进入 `@gwnexus/ui`，不得只放在单个页面目录形成不可治理的私有组件。

要求：

- 简体中文唯一 V1 验收语言。
- 文本通过 i18n key 管理，不在业务逻辑硬编码。
- 键盘可操作、焦点可见、语义标签、颜色对比和屏幕阅读基础支持。
- Responsive Web 支持领导手机查看与审批，但手机公网接力不是首验 P0。
- 错误信息必须说明发生了什么、数据是否安全、是否自动重试、用户下一步。

## 9. Coding Workspace UX

### 9.1 V1核心面板

- Project Overview与Repository状态。
- Agent对话和版本化Coding Plan Gate。
- Repository Tree与Monaco只读Source Viewer。
- Agent Event/Tool/Command Timeline。
- Test、Development Build和Validation结果。
- ChangeSet Diff、冲突、确认并Push、PR链接。
- Preview内嵌面板、访问权限、Share Link和TTL。
- Release Readiness Checklist、Security Findings、SBOM和Exception。
- Production发布、Blue-Green进度、运行日志/指标、一键回滚。
- Domain Binding、DNS记录、验证、Certificate和续期状态。

V1不提供人工在线自由编辑代码或交互式Terminal。人工深度编辑使用本地IDE和远程Git。

### 9.2 V1.1 双端一致性契约

V1.1 Desktop 与 Web 共享React领域模块、`@gwnexus/ui`、API SDK、Domain Type和Agent Event Contract。Desktop额外提供本地Repository发现、文件选择和系统通知，不产生第二套Coding业务状态。

Desktop 只能在 V1.1 Work Item 获批后消费已稳定的共享能力，不能要求 V1 Web 等待 Desktop，也不能复制一套独立页面状态和业务 Service。

### 9.3 用户项目UI自治

本文件的组件治理只约束GW Nexus Edge自身Web/Desktop代码。Coding Agent创建或修改的用户项目由用户自主决定UI框架、组件库和自定义组件策略：

- 已有项目遵循Repository现有Design System；
- 新项目按用户选择执行；
- Nexus Edge不得强制用户项目采用shadcn/ui或`@gwnexus/ui`。
