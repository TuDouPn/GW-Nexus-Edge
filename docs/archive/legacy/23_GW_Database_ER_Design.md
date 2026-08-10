# [ARCHIVED] GW Nexus Edge Database ER Design

## 数据库分层

GW Nexus Edge 使用双数据库架构：

    业务数据库
    MySQL / 达梦 DM8

    +

    知识数据库
    PostgreSQL + pgvector

------------------------------------------------------------------------

# 业务数据库核心表

## 用户体系

    sys_user

    sys_tenant

    sys_organization

    sys_role

    sys_permission

关系：

    Tenant
     |
     Organization
     |
     User
     |
     Role
     |
     Permission

------------------------------------------------------------------------

# Workspace

    gw_workspace

    gw_workspace_member

    gw_workspace_resource

关系：

    Workspace

     ├── Member

     ├── Resource

     ├── Task

     └── Artifact

------------------------------------------------------------------------

# Agent

    gw_agent

    gw_agent_session

    gw_agent_execution

------------------------------------------------------------------------

# Task

    gw_task

    gw_task_node

    gw_task_history

    gw_task_comment

支持：

-   父子任务
-   DAG
-   审批
-   重试

------------------------------------------------------------------------

# Artifact

    gw_artifact

    gw_artifact_version

    gw_artifact_review

文件存储：

MinIO

数据库保存：

-   URL
-   Hash
-   Version

------------------------------------------------------------------------

# RAG 数据库

PostgreSQL：

    knowledge_base

    knowledge_document

    document_chunk

    embedding

核心：

    document_chunk

    content

    embedding vector

    metadata
