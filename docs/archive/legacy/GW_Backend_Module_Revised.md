# [ARCHIVED] GW Nexus Edge Backend Module Revised

## Spring Boot 4 模块

    gw-backend

    ├── gw-workspace

    ├── gw-task

    ├── gw-skill

    ├── gw-knowledge

    ├── gw-artifact

    ├── gw-permission

    ├── gw-agentscope-adapter

    ├── gw-mcp-manager

    └── gw-infrastructure

------------------------------------------------------------------------

# AI Runtime 边界

后端模块只保留 AgentScope Adapter 和 Agent Configuration Management。

Agent、Workflow、Memory Runtime 与 Tool Calling 的执行能力统一由 AgentScope Java 2 提供；业务模块不得建设平行的 AI 执行框架。

------------------------------------------------------------------------

# Agent模块

组成：

    AgentScope Adapter

    +

    Agent Configuration Management
