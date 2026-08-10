# 11 — Artifact、模板与 Windows Renderer 规格

> 状态：Accepted  
> 重要：WPS 免费版尚未通过兼容门禁，不得把本规范当作已验证事实。

## 1. 架构

```text
AgentScope structured result
 → Artifact Model validation
 → Template binding
 → RenderJob + Outbox
 → Redis render stream
 → Windows Renderer Worker
 → WPS Provider
 → Output validation
 → ArtifactVersion registration
 → Business Review
```

正式渲染不依赖任务发起人的电脑。企业至少部署一个专用 Windows Renderer Node。

## 2. Artifact Model

Artifact Model 是结构化中间契约，不是 Markdown 拼接。至少包含：

- report metadata、title、period、audience。
- sections、headings、paragraph blocks。
- tables、cells、formats、source Evidence。
- charts、series、categories、units、source Calculation。
- key findings、risks、recommendations、action plans。
- evidence links、conflicts、limitations。
- output/template requirements。

使用 JSON Schema 版本化。模型输出必须通过 Schema 校验和 Trust Gate 后才能创建 RenderJob。

## 3. Renderer SPI

平台定义面向文档渲染的业务 Port，不暴露 WPS COM/Automation 类型：

- capability discovery。
- health/heartbeat。
- claim job with lease。
- render Artifact Model + Template。
- validate result。
- report structured failure。

Provider：

- V1 验证：WPS Office 免费版。
- 未来：WPS 企业版、Microsoft Office、服务器文档引擎。

Provider 替换不得改变 Artifact Model 和业务审批流程。

## 4. Windows Worker 安全

- 使用专用低权限服务账号和隔离工作目录。
- 只从受控 API 获取指定 Template/Artifact Model，不访问任意 Workspace 文件。
- Job 输入和输出使用短期令牌、TLS、Hash 校验和路径白名单。
- 禁止任意宏执行、外部链接自动刷新和未知插件。
- 每个 Job 清理临时文件；异常时隔离残留进程并上报。
- Worker 身份可吊销，权限仅限 RenderJob。

## 5. DOCX 要求

- 打开指定 TemplateVersion。
- 保留 Logo、页眉页脚、主题字体、样式和页面设置。
- 按占位符/Content Control 或稳定书签填充，禁止依赖模糊文本替换。
- 使用 Heading 样式并更新目录域。
- 表格结构不退化为纯文本。
- 插入图表和图片保持可编辑/可定位。
- 输出后执行打开、页数、关键样式、占位符残留和文件损坏检查。

## 6. PPTX 要求

- 继承母版、Theme、Layout、页面比例和页脚。
- 选择明确 Layout/Placeholder，不用绝对坐标堆叠所有内容。
- Chart 为可编辑对象并绑定结构化数据。
- 执行溢出、遮挡、缺字、字体替换和空页面检查。
- 一页保持结论、关键数据和有限说明，不生成大段正文。

## 7. XLSX 要求

- 保留 Workbook、Sheet 名、样式、命名区域和模板公式。
- 派生指标写入公式，记录口径和舍入。
- 原始数据、分析表和图表区域分离。
- 不启用或执行未知宏。
- 输出后重开验证公式、图表、引用、错误单元格和文件完整性。

## 8. Preview

Renderer 可生成受控 PDF/图片预览，用于 Nexus Edge Web 审核。预览不是权威 Artifact；下载始终引用原 ArtifactVersion。

## 9. 可靠性

- RenderJob 使用 UUIDv7、Idempotency Key、Lease、Heartbeat、Attempt 和最大重试。
- Worker 离线时 Task 进入 WAITING_RENDER，Artifact Model 持久保存。
- 恢复上线后继续领取，不依赖发起人设备。
- 超过重试上限进入 FAILED/DLQ，并通知管理员和任务用户。
- 不允许同一 Job 重复登记两个正式 ArtifactVersion。

## 10. 模板治理

- Template 和 TemplateVersion 版本化、不可覆盖。
- 发布前执行格式、占位符、字体、Renderer Provider 和安全检查。
- Task 固化 TemplateVersion。
- 字体必须预装到 Renderer 并通过许可检查；缺失字体阻止正式渲染，不静默替换。

## 11. Compatibility Gate

必须使用试点企业模板做连续测试，至少覆盖：

- 20 次完整报告批次。
- Worker 重启、网络断开、WPS 崩溃和重复投递。
- 页眉页脚、Logo、字体、表格、图表、公式和母版。
- 输出在 WPS 正常打开、编辑、保存；兼容验证中在 Microsoft Office 打开。

若 WPS 免费版授权或自动化能力不满足无人值守生产要求，必须通过 ADR 重新选择 Provider，不能以 GUI 宏、人工点击或隐藏降质方式绕过。

