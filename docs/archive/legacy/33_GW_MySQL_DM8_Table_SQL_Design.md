# [ARCHIVED] GW Nexus Edge MySQL / DM8 Table SQL Design

## 目标

业务数据库采用：

    MySQL

    兼容

    达梦 DM8

负责：

-   用户
-   租户
-   Workspace
-   Task
-   Agent
-   权限
-   Artifact 元数据

------------------------------------------------------------------------

# 表设计原则

统一字段：

    id
    tenant_id
    created_by
    created_time
    updated_by
    updated_time
    deleted

支持：

-   多租户
-   软删除
-   审计

------------------------------------------------------------------------

# 核心表

## gw_workspace

字段：

    id bigint

    tenant_id bigint

    name varchar

    type varchar

    status varchar

    created_time datetime

------------------------------------------------------------------------

## gw_task

字段：

    id

    workspace_id

    parent_id

    title

    status

    assignee_type

    assignee_id

------------------------------------------------------------------------

## gw_agent_session

字段：

    id

    workspace_id

    agent_id

    user_id

    status

    context_key

------------------------------------------------------------------------

# 索引策略

重点：

    tenant_id

    workspace_id

    created_time

    status

保证企业数据隔离和查询性能。
