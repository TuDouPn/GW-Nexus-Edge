# Security Policy

## 报告安全漏洞

**请勿公开创建安全相关的 Issue。** 优先使用 GitHub **Private Vulnerability Reporting**：

1. 打开仓库页面的 **Security → Report a vulnerability**（Security Advisory 私密报告入口）。
2. 提供可复现的最小描述、影响范围和受影响的版本。
3. 报告内容仅维护者可见，直到漏洞被处理。

实施阶段将调用 GitHub API 启用并复验该能力。若平台无法启用 Private Vulnerability Reporting，本文件将标记 `BLOCKED_BY_PLATFORM`，并向产品架构负责人请求真实的安全联系邮箱。我们不会编造 `security@` 域名或个人邮箱作为安全渠道。

## 处理承诺

- 维护者会及时确认并处理私密报告。
- 修复前不公开漏洞细节。
- 重要修复会随版本/公告披露。

## 范围

- 本仓库源代码（Apache-2.0）的安全问题。
- 供应链（依赖、CI 工作流、Secret 处理）。
- 不覆盖：已进入生产部署的实例（请通过企业支持渠道报告）。
