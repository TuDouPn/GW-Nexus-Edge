# [ARCHIVED] GW Nexus Edge Spring Boot 4 API Design

## API分层

    /api/user

    /api/workspace

    /api/task

    /api/agent

    /api/knowledge

    /api/artifact

------------------------------------------------------------------------

# Workspace API

创建：

    POST /workspace

查询：

    GET /workspace/{id}

------------------------------------------------------------------------

# Task API

创建任务：

    POST /task

状态：

    GET /task/{id}/status

------------------------------------------------------------------------

# Agent Streaming

使用：

    SSE

    /WebSocket

事件：

    thinking

    tool_call

    tool_result

    artifact_created

    completed
