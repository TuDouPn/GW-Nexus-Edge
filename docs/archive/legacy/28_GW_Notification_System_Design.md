# [ARCHIVED] GW Nexus Edge Notification System Design

## 通知中心

事件：

    Task完成

    Agent等待

    审批请求

    Artifact生成

    知识更新

------------------------------------------------------------------------

# 通道

支持：

-   WebSocket
-   Mobile Push
-   Email
-   企业IM
-   Webhook

------------------------------------------------------------------------

# 架构

    Event Bus

    ↓

    Notification Service

    ↓

    Channel Provider
