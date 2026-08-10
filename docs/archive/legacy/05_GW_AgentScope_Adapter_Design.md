# [ARCHIVED] GW Nexus Edge AgentScope Adapter Design

## 定位

GW Nexus Edge 不定义或实现独立的 AI Runtime API。

AgentScope Java 2 是平台唯一的 AI Runtime，负责：

-   Agent 执行与通信
-   Workflow 执行
-   Agent Memory Runtime
-   Tool Calling
-   Agent 运行生命周期

GW Nexus Edge 通过轻量 Adapter 将企业业务上下文接入 AgentScope：

    GW Nexus Edge Application Service

          ↓

    GW Agent Service

          ↓

    AgentScope Adapter

          ↓

    AgentScope Java 2

------------------------------------------------------------------------

# Adapter 职责

Adapter 仅负责平台数据与 AgentScope 运行对象之间的边界转换：

-   将 Task、Skill 和 Agent Definition 转换为 AgentScope 输入
-   注入经过权限过滤的 Workspace、Resource 和 Knowledge Context
-   将 AgentScope 事件转换为平台统一事件
-   将执行结果登记为 Task Result 或 Artifact
-   传播 Tenant、User、Trace 和 Audit 上下文

Adapter 不负责：

-   实现 Agent 执行循环
-   实现 Workflow Engine
-   实现 Agent Memory Runtime
-   实现 Tool Calling Framework
-   包装一套可替换 AgentScope 的通用 Runtime

------------------------------------------------------------------------

# Workspace 能力接口

Workspace Adapter 是企业资源访问边界，不是 AI Runtime 接口。

其实现必须执行资源权限、数据权限和审计检查，再调用真实的文件、对象存储、数据库或企业系统 Provider。

``` java
interface WorkspaceAdapter {

    ResourceContent read(ResourceRequest request);

    WriteResult write(ResourceWriteCommand command);

    SearchResult search(ResourceSearchQuery query);

}
```

------------------------------------------------------------------------

# Event Stream

平台将 AgentScope 运行事件转换为统一的业务事件：

    AgentStarted

    Thinking

    ToolCall

    ToolResult

    FileChanged

    ArtifactCreated

    TaskCompleted

客户端传输使用：

    SSE
    WebSocket

------------------------------------------------------------------------

# Memory 边界

AgentScope Memory 负责当前会话、Agent 上下文和运行状态。

GW Nexus Edge 只负责持久化企业业务记忆：

-   用户历史
-   Workspace 历史
-   Task 与 Artifact 关联
-   企业知识

业务记忆必须经过权限过滤后才能注入 AgentScope。

------------------------------------------------------------------------

# Model Gateway 边界

平台通过 Model Gateway 管理模型白名单、路由、额度、成本和调用审计。

AgentScope 使用经过治理的模型配置执行任务；GW Nexus Edge 不实现模型推理 Runtime。
