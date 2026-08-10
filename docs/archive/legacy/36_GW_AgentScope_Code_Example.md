# [ARCHIVED] GW Nexus Edge AgentScope Code Example

## Spring Boot 集成

结构：

    AgentService

    ↓

    AgentScope Runtime

    ↓

    Agent

------------------------------------------------------------------------

# Agent定义

示例：

``` java
class DocumentAgent {

 execute(Task task){

 }

}
```

------------------------------------------------------------------------

# Tool调用

流程：

    Agent

    ↓

    Tool Registry

    ↓

    Tool Execute

    ↓

    Result

------------------------------------------------------------------------

# Multi Agent

    Main Agent

    ↓

    Planner

    ↓

    Sub Agents

子Agent：

-   Data Agent
-   Document Agent
-   Coding Agent
