# [ARCHIVED] GW Nexus Edge Tool MCP Architecture Revised

## 原则

Tool执行能力使用：

-   AgentScope Tool
-   MCP

GW Nexus Edge负责治理。

------------------------------------------------------------------------

# 架构

    AgentScope Agent

    ↓

    Tool Interface

    ↓

    MCP Server

    ↓

    Enterprise System

------------------------------------------------------------------------

# GW Nexus Edge Tool Manager

负责：

-   Tool注册
-   权限
-   审批
-   审计

不负责：

Tool Runtime开发。
