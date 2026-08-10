# [ARCHIVED] GW Nexus Edge Event Driven Architecture

## 事件模型

    AgentStarted

    TaskCreated

    ToolCalled

    ArtifactCreated

    ApprovalRequired

    TaskCompleted

## 技术选择

可使用：

-   Redis Stream
-   Kafka
-   RabbitMQ

用途：

-   Agent状态
-   异步任务
-   通知
-   审计
