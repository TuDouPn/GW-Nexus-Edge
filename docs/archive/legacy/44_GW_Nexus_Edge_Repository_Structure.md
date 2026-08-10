# [ARCHIVED] GW Nexus Edge Repository Structure

## Monorepo建议

    nexus-edge

    ├── backend

    ├── frontend

    ├── desktop

    ├── knowledge

    ├── agent-adapter

    ├── mcp

    ├── deploy

    └── docs

------------------------------------------------------------------------

# Backend

    backend

    ├── nexus-edge-server

    ├── nexus-edge-common

    ├── nexus-edge-auth

    ├── nexus-edge-workspace

    ├── nexus-edge-task

------------------------------------------------------------------------

# Desktop

    desktop

    Vue3

    +

    Tauri2

负责：

-   本地Runtime
-   文件访问
-   云端同步
