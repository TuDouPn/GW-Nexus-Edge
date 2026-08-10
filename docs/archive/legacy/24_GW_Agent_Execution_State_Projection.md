# [ARCHIVED] GW Nexus Edge Agent Execution State Projection

> 本文定义平台用于查询、治理和审计的状态投影。Agent 的真实执行生命周期、Workflow 状态推进和恢复由 AgentScope Java 2 负责；GW Nexus Edge 不实现 Agent Runtime 状态机。

## Agent 状态

    CREATED

    ↓

    INITIALIZING

    ↓

    RUNNING

    ↓

    WAITING_TOOL

    ↓

    WAITING_HUMAN

    ↓

    COMPLETED

    ↓

    ARCHIVED

------------------------------------------------------------------------

# Task 状态

    PENDING

    ↓

    RUNNING

    ↓

    REVIEWING

    ↓

    WAITING_APPROVAL

    ↓

    COMPLETED

异常：

    FAILED

    CANCELLED

------------------------------------------------------------------------

# Approval 状态

    REQUESTED

    ↓

    APPROVED

    ↓

    EXECUTED

或者：

    REJECTED

------------------------------------------------------------------------

# 长任务恢复

AgentScope Runtime 负责运行态 Checkpoint、暂停、恢复与重试。

GW Nexus Edge 只保存业务恢复所需的关联信息：

-   AgentScope execution/session 标识
-   Task 与 Workflow Definition 版本
-   已授权的 Context 引用，不复制 AgentScope 内部 Memory
-   Tool 结果与 Artifact 引用
-   面向用户查询的执行状态投影

恢复请求必须通过 GW Agent Service 和 AgentScope Adapter 转交 AgentScope，不得在平台业务模块中重复实现调度或 Checkpoint 逻辑。
