# [ARCHIVED] GW Nexus Edge Workspace Core Design

## 目标

Workspace 是 GW Nexus Edge 平台的核心业务模型。

它不是代码目录，而是：

-   文件空间
-   知识空间
-   任务空间
-   Agent 工作空间
-   团队协作空间

核心链路：

    Resource
       ↓
    Workspace
       ↓
    Task
       ↓
    Agent + Human
       ↓
    Artifact

------------------------------------------------------------------------

# 核心实体

## Organization

企业或个人组织。

字段：

    id
    name
    owner
    members
    policy

------------------------------------------------------------------------

## Workspace

Workspace 是所有工作的容器。

类型：

    LOCAL
    CLOUD
    HYBRID

字段：

    id
    organization_id
    name
    type
    provider
    status
    created_at

------------------------------------------------------------------------

## Resource

Agent 操作对象。

类型：

    FILE
    DOCUMENT
    CODE
    DATABASE
    KNOWLEDGE
    API
    MCP
    EMAIL
    CALENDAR

字段：

    id
    workspace_id
    type
    provider
    uri
    metadata
    permission

------------------------------------------------------------------------

# Task

Task 是执行单位。

例如：

-   分析报告
-   修改文档
-   编写代码
-   检查合同

字段：

    id
    workspace_id
    parent_task
    assignee
    status
    input
    output

状态：

    PENDING
    RUNNING
    WAITING_APPROVAL
    COMPLETED
    FAILED

------------------------------------------------------------------------

# Artifact

最终交付物。

类型：

    CODE
    DOCUMENT
    PPT
    EXCEL
    REPORT
    DATASET
    APPLICATION
    KNOWLEDGE_ENTRY

支持：

-   版本
-   评论
-   审核
-   分享

------------------------------------------------------------------------

# Workspace Provider

统一抽象：

    Workspace
        |
        ├── LocalWorkspace
        ├── CloudWorkspace
        └── HybridWorkspace

目标：

Agent 不关心资源在哪里。
