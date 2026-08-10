# [ARCHIVED] GW Nexus Edge AgentScope Java 2 核心架构修正版

## 核心原则

GW Nexus Edge 不自研 Agent Framework。

采用：

    AgentScope Java 2
    +
    Spring Boot 4
    +
    企业业务能力

定位：

AgentScope 负责 AI 执行能力。

GW Nexus Edge 负责企业工作空间和治理能力。

------------------------------------------------------------------------

# 总体架构

    Client

    ↓

    GW Nexus Edge Application Layer

    Workspace
    Task
    Skill
    Knowledge
    Artifact
    Permission

    ↓

    AgentScope Java 2

    Agent
    Workflow
    Memory
    Tool

    ↓

    Model / MCP / Enterprise System

------------------------------------------------------------------------

# GW Nexus Edge 不负责

以下能力直接使用 AgentScope：

-   Agent Runtime
-   Agent 生命周期
-   Workflow Engine
-   Tool Calling
-   Message Communication
-   Agent Memory Runtime

------------------------------------------------------------------------

# GW Nexus Edge 负责

企业级能力：

-   Workspace
-   Task Management
-   Skill
-   Permission
-   Knowledge Service
-   Artifact
-   Collaboration
-   Audit
-   Model Governance
