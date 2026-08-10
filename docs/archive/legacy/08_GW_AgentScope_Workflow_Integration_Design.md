# [ARCHIVED] GW Nexus Edge AgentScope Workflow Integration Design

## 目标

GW Nexus Edge 不开发 Workflow Engine。

复杂 Agent 工作流统一使用 AgentScope Workflow 执行。平台只管理具有企业业务语义的流程定义与治理数据：

    Skill

    ↓

    Workflow Definition

    ↓

    AgentScope Adapter

    ↓

    AgentScope Workflow

    ↓

    Execution Result

## 示例

    需求分析

    ↓

    资料搜索

    ↓

    数据分析

    ↓

    生成报告

    ↓

    人工审核

    ↓

    发布

## 职责边界

AgentScope 负责：

-   节点执行与 Agent 协作
-   DAG 调度
-   运行时重试
-   暂停、恢复与 Checkpoint
-   Agent 运行状态

GW Nexus Edge 负责：

-   Workflow Definition 的版本、发布和停用
-   Workflow 与 Skill、权限及 Knowledge Scope 的关联
-   人工审批任务的创建与结果回传
-   业务执行记录、审计和 Artifact 关联
-   将 AgentScope 运行状态投影为平台可查询状态

## 平台状态投影

以下状态用于业务查询与协作展示，不构成自研 Workflow Runtime：

    RUNNING
    PAUSED
    WAITING_APPROVAL
    FAILED
    COMPLETED

状态的真实执行与恢复由 AgentScope Workflow 完成，平台不得重复实现节点调度器或 Checkpoint Engine。
