# [ARCHIVED] GW Nexus Edge Backend Module Design

## 总体模块

    gw-backend

    ├── gw-api
    ├── gw-auth
    ├── gw-user
    ├── gw-tenant
    ├── gw-workspace
    ├── gw-resource
    ├── gw-task
    ├── gw-agentscope-adapter
    ├── gw-mcp-manager
    ├── gw-knowledge
    ├── gw-artifact
    ├── gw-skill
    ├── gw-collaboration
    └── gw-infrastructure

## 模块职责

### Workspace Module

负责：

-   Workspace 生命周期
-   Local / Cloud / Hybrid
-   成员管理
-   配置管理

### Resource Module

负责统一资源抽象：

-   文件
-   文档
-   Git
-   数据库
-   MCP

### Task Module

负责：

-   任务创建
-   状态流转
-   子任务
-   审批

### AgentScope Adapter Module

负责：

-   Agent 配置
-   Session
-   业务调度入口
-   AgentScope Adapter 调用

不负责实现 Agent Runtime、Workflow、Memory Runtime 或 Tool Calling；这些能力统一由 AgentScope Java 2 提供。

### MCP Manager Module

负责 Tool 与 MCP 的注册、权限、审批、凭证和审计治理，通过 AgentScope Tool 或 MCP 接入真实执行能力。

## 分层

    Controller

    ↓

    Application Service

    ↓

    Domain Service

    ↓

    Repository

    ↓

    MyBatis-Plus

    ↓

    Database
