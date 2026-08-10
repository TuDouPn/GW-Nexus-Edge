# [ARCHIVED] GW Nexus Edge Workflow Design Revised

## 原则

不开发Workflow Engine。

使用：

AgentScope Workflow能力。

------------------------------------------------------------------------

# GW Nexus Edge保存业务流程定义

例如：

    合同审核流程

    财务分析流程

    研发Review流程

保存：

    workflow_definition

------------------------------------------------------------------------

# 执行

    Skill

    ↓

    Workflow Definition

    ↓

    AgentScope Workflow

    ↓

    Execution

------------------------------------------------------------------------

# GW Nexus Edge关注

-   流程版本
-   权限
-   发布
-   审批

AgentScope关注：

-   节点执行
-   Agent协作
