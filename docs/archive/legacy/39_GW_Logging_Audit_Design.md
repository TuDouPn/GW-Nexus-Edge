# [ARCHIVED] GW Nexus Edge Logging Audit Design

## 审计目标

记录：

谁

什么时候

哪个Agent

执行什么操作

------------------------------------------------------------------------

# Audit Event

    USER_LOGIN

    AGENT_EXECUTE

    TOOL_CALL

    RESOURCE_CHANGE

    APPROVAL

------------------------------------------------------------------------

# 日志体系

    Application Log

    Agent Log

    Business Audit

    Security Audit

------------------------------------------------------------------------

# 技术

-   OpenTelemetry
-   Loki
-   Elasticsearch
