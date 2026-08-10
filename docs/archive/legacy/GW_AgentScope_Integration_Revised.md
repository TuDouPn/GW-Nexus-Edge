# [ARCHIVED] GW Nexus Edge AgentScope Integration Design Revised

## 定位

AgentScope 是 GW Nexus Edge 的 AI Runtime。

GW Nexus Edge 通过 Adapter 接入。

结构：

    GW Nexus Edge Agent Service

    ↓

    AgentScope Adapter

    ↓

    AgentScope Runtime

------------------------------------------------------------------------

# Agent配置

GW Nexus Edge保存：

    agent_definition

    id

    name

    skill

    prompt

    tool_config

    knowledge_scope

运行时：

GW Nexus Edge创建AgentScope Agent实例。

------------------------------------------------------------------------

# 执行流程

    User Request

    ↓

    GW Nexus Edge Task Service

    ↓

    Agent Service

    ↓

    AgentScope Runtime

    ↓

    Agent

    ↓

    Artifact

------------------------------------------------------------------------

# Adapter职责

负责：

-   参数转换
-   Context注入
-   权限校验
-   结果转换

不负责：

-   Agent执行逻辑
