# [ARCHIVED] GW Nexus Edge Memory Architecture Revised

## 分层设计

## AgentScope Memory

负责：

-   当前会话
-   Agent上下文
-   运行状态

## GW Nexus Edge Memory Service

负责：

-   用户历史
-   Workspace历史
-   Artifact关联
-   企业知识

结构：

    AgentScope Memory

    +

    GW Nexus Edge Business Memory

    +

    Knowledge Base

------------------------------------------------------------------------

# 原则

不重复建设Agent Memory Framework。
