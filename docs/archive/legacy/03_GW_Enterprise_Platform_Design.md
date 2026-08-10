# [ARCHIVED] GW Nexus Edge Enterprise Platform Design

## 定位

企业级 Agent 平台。

核心：

    Organization
     ↓
    Workspace
     ↓
    Member
     ↓
    Agent
     ↓
    Knowledge
     ↓
    Task

------------------------------------------------------------------------

# Multi Tenant

支持：

    企业
     |
    部门
     |
    团队
     |
    用户

数据隔离：

-   Workspace 隔离
-   Knowledge 隔离
-   Resource 隔离

------------------------------------------------------------------------

# RBAC

角色：

    OWNER
    ADMIN
    DEVELOPER
    MEMBER
    VIEWER

权限：

-   Workspace
-   Resource
-   Knowledge
-   Tool
-   Agent

------------------------------------------------------------------------

# Skill System

Skill 是企业能力沉淀。

例如：

    合同审核 Skill

    财务分析 Skill

    Java Review Skill

包含：

    Prompt
    Workflow
    Tool Permission
    Knowledge Scope
    Output Schema

------------------------------------------------------------------------

# Knowledge Platform

流程：

    Document
     ↓
    Parser
     ↓
    Chunk
     ↓
    Embedding
     ↓
    Vector DB
     ↓
    Knowledge Base

权限必须继承企业权限。

------------------------------------------------------------------------

# Approval

高风险操作：

-   删除
-   发布
-   发邮件
-   部署
-   修改数据

需要：

    Agent Request
     ↓
    Human Approval
     ↓
    Execute

------------------------------------------------------------------------

# Audit

记录：

-   谁
-   什么时间
-   哪个 Agent
-   哪个 Tool
-   修改什么
