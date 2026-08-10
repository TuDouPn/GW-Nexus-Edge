# [ARCHIVED] GW Nexus Edge Tool MCP Platform Design

## Tool Architecture

    Agent

    ↓

    Tool Registry

    ↓

    Permission Check

    ↓

    AgentScope Tool / MCP Client

    ↓

    Execution

    ↓

    Audit

## Tool 类型

-   File
-   Shell
-   Git
-   Database
-   Office
-   Browser
-   Enterprise API

## MCP

接入：

-   ERP
-   OA
-   CRM
-   GitLab
-   Jira
-   Confluence

MCP需要：

-   注册
-   权限
-   凭证管理
-   审计

GW Nexus Edge 只负责 Tool 与 MCP 的注册、权限、审批、凭证和审计治理，不开发 Tool Runtime 或 Tool Calling Framework。
