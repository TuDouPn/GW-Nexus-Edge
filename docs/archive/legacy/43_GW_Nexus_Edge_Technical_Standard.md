# [ARCHIVED] GW Nexus Edge Technical Standard

## 目标

定义 GW Nexus Edge 全局工程规范。

------------------------------------------------------------------------

# 技术栈

    Java 21+

    Spring Boot 4

    AgentScope Java 2

    MyBatis-Plus

    Sa-Token

    Redis

    MySQL / DM8

    PostgreSQL + pgvector

    MinIO

    Vue3

    Tauri 2

------------------------------------------------------------------------

# Maven规范

GroupId:

    com.nexusedge

示例：

    com.nexusedge.server
    com.nexusedge.knowledge
    com.nexusedge.agent

------------------------------------------------------------------------

# 服务命名

统一：

    nexus-edge-xxx

例如：

    nexus-edge-server

    nexus-edge-knowledge

    nexus-edge-agent-adapter

------------------------------------------------------------------------

# 数据库规范

业务：

    nx_xxx

知识：

    knowledge_xxx

------------------------------------------------------------------------

# Docker镜像

    nexus-edge-server:v1.0

    nexus-edge-agent:v1.0
