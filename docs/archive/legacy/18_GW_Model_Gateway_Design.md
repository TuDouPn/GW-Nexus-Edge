# [ARCHIVED] GW Nexus Edge Model Gateway Design

## 目标

统一管理模型。

支持：

-   GPT
-   Claude
-   Gemini
-   DeepSeek
-   Qwen
-   Local Model

架构：

    Agent

    ↓

    Model Gateway

    ↓

    Provider

    ↓

    Model

能力：

-   路由
-   降级
-   成本控制
-   Token统计
