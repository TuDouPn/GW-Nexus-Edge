# [ARCHIVED] GW Nexus Edge Agent Orchestration Design

## 目标

构建多 Agent 协作系统。

核心：

    User
     ↓
    Main Agent
     ↓
    Planner
     ↓
    Specialized Agents
     ↓
    Artifact

------------------------------------------------------------------------

# Main Agent

职责：

-   理解用户目标
-   创建计划
-   调度 Agent
-   管理上下文
-   汇总结果

不是具体执行者。

------------------------------------------------------------------------

# Agent 类型

## Coding Agent

负责：

-   Code
-   Git
-   Shell
-   Debug
-   Test

------------------------------------------------------------------------

## Document Agent

负责：

-   Word
-   WPS
-   PPT
-   PDF

------------------------------------------------------------------------

## Data Agent

负责：

-   Excel
-   SQL
-   数据分析

------------------------------------------------------------------------

## Knowledge Agent

负责：

-   企业知识库
-   RAG
-   搜索

------------------------------------------------------------------------

## Reviewer Agent

负责：

-   质量检查
-   风险发现
-   审核

------------------------------------------------------------------------

# Task Graph

任务应该形成 DAG。

例如：

    需求分析
       ↓
    数据分析
       ↓
    报告生成
       ↓
    审核

------------------------------------------------------------------------

# Agent Context

每个 Agent 独立 Context。

避免：

一个 Agent 读取整个 Workspace 导致上下文污染。

------------------------------------------------------------------------

# AgentScope Runtime 集成

统一使用：

    AgentScope Java

负责：

-   Agent 生命周期
-   Tool Calling
-   Memory
-   Workflow
-   SubAgent

GW Nexus Edge 不实现 Agent Runtime、Workflow Engine、Memory Runtime 或 Tool Calling Framework。

平台仅通过 AgentScope Adapter 注入 Task、Workspace、Permission 和 Knowledge Context，并接收执行结果与事件。
