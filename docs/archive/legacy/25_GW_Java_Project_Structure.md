# [ARCHIVED] GW Nexus Edge Java Project Structure

## Maven 多模块

    gw-platform

    ├── gw-common

    ├── gw-gateway

    ├── gw-auth

    ├── gw-workspace

    ├── gw-task

    ├── gw-agentscope-adapter

    ├── gw-knowledge

    ├── gw-artifact

    ├── gw-mcp-manager

    ├── gw-infrastructure

------------------------------------------------------------------------

# 模块结构

    module

    ├── controller

    ├── application

    ├── domain

    ├── infrastructure

    ├── mapper

    ├── entity

    └── dto

------------------------------------------------------------------------

# 原则

Controller:

只处理请求

Application:

业务编排

Domain:

核心规则

Repository:

数据访问

Infrastructure:

外部依赖
