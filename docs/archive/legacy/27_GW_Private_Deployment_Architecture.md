# [ARCHIVED] GW Nexus Edge Private Deployment Architecture

## 政企私有化部署

目标：

企业内部完整运行。

------------------------------------------------------------------------

# 部署架构

    Nginx

    ↓

    Spring Boot Cluster

    ↓

    AgentScope Java 2 Runtime

    ↓

    Database

    ↓

    Storage

------------------------------------------------------------------------

# 基础设施

    Docker

    Kubernetes

    MySQL/DM8

    PostgreSQL

    Redis

    MinIO

------------------------------------------------------------------------

# 数据隔离

支持：

-   单租户
-   多租户

------------------------------------------------------------------------

# 企业集成

支持：

-   LDAP
-   SSO
-   OA
-   ERP
-   CRM
