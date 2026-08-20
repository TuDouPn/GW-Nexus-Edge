# DEV-0005 CI 供应链清单

> 规则必须拆开：GitHub Action 引用钉 40 位 commit SHA；二进制制品钉 Release 版本并校验官方 SHA256。
> 禁止 `curl ... | sh`。禁止把两条规则写成一句“固定版本 + commit SHA”。

## 1. GitHub Action 引用

| Action | 版本标签（仅注释） | Commit SHA（uses 钉选） |
|---|---|---|
| actions/checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| actions/setup-java | v5.7.0 | `b6effb05e454b25005698d916606bdc6ffcbf961` |
| actions/cache | v6.1.0 | `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` |
| actions/upload-artifact | v7.0.1 | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` |
| actions/download-artifact | v8.0.1 | `3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c` |
| actions/dependency-review-action | v5.0.0 | `a1d282b36b6f3519aa1f3fc636f609c47dddb294` |
| advanced-security/maven-dependency-submission-action | v5.0.0 | `b275d12641ac2d2108b2cbb7598b154ad2f2cee8` |
| github/codeql-action/upload-sarif | v4.37.7 | `ff2f1c621b7f889edc0d3c761ac2e6a3f8cdb0dd` |

校验：`scripts/ci/workflow-security-check.py` 拒绝非 40 位 SHA、可变 tag、缺少版本注释。

## 2. 二进制制品

来源均为各项目 GitHub Releases。安装脚本：`scripts/ci/install-binary.sh`。钉选文件：`scripts/ci/pins.env`。

| 工具 | 版本 | 来源 | Linux amd64 制品 | 官方 SHA256 |
|---|---|---|---|---|
| Gitleaks | 8.30.1 | https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1 | `gitleaks_8.30.1_linux_x64.tar.gz` | `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb` |
| Trivy | 0.74.0 | https://github.com/aquasecurity/trivy/releases/tag/v0.74.0 | `trivy_0.74.0_Linux-64bit.tar.gz` | `2ae6fe3ee734b7fdf11335663e18c75ea12dccc76062f09f164a3b0f8be4371a` |
| actionlint | 1.7.12 | https://github.com/rhysd/actionlint/releases/tag/v1.7.12 | `actionlint_1.7.12_linux_amd64.tar.gz` | `8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8` |

本地 Darwin 校验和见 `scripts/ci/pins.env`。校验方式：下载归档后与仓库钉死的 SHA256 比对，不匹配则失败。

## 3. Maven 插件（构建期调用，非 Action / 非独立二进制）

| 插件 | 版本 | 调用 |
|---|---|---|
| org.cyclonedx:cyclonedx-maven-plugin | 2.9.3 | `makeAggregateBom -DoutputFormat=all` |

## 4. 明确不使用的路径

- 不以 `gitleaks/gitleaks-action` 或 `aquasecurity/trivy-action` 执行扫描（避免 Fork PR 读取 Secret，并避免把 Action SHA 与工具二进制版本混为一谈）。
- 不把 Trivy filesystem scan 描述为 Container Image Scan。
