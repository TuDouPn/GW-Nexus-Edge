# [ARCHIVED] GW Nexus Edge AgentScope Integration Design

## 定位

AgentScope Java 2 位于 AI Runtime 层。

    Spring Boot 4

    ↓

    Agent Service

    ↓

    AgentScope Runtime

    ↓

    Agent

## 生命周期

以下是平台对 AgentScope 执行状态的业务投影，不是 Nexus Edge 自行实现的 Agent 生命周期引擎：

    CREATE

    ↓

    INITIALIZE

    ↓

    RUNNING

    ↓

    WAITING_TOOL

    ↓

    WAITING_HUMAN

    ↓

    COMPLETED

状态推进、暂停和恢复由 AgentScope Runtime 负责；平台负责持久化可查询的业务状态、关联 Task 与 Artifact，并记录审计事件。

## 调度流程

    User Request

    ↓

    Main Agent

    ↓

    Planner

    ↓

    Sub Agent

    ↓

    Tool

    ↓

    Artifact

## Context

上下文来源：

-   User
-   Workspace
-   Task
-   Resource
-   Knowledge
-   Memory

不能一次加载全部 Workspace。
