# [ARCHIVED] GW Nexus Edge Local Cloud Sync Design

## 目标

实现：

本地工作 → 云端接力 → 多设备继续

------------------------------------------------------------------------

# 三种模式

## Local

    Desktop
     ↓
    Local Runtime
     ↓
    Local Resource

适合：

-   私有代码
-   敏感文件

------------------------------------------------------------------------

## Cloud

    Cloud Workspace
     ↓
    Sandbox
     ↓
    Agent

适合：

-   长任务
-   多设备
-   企业协作

------------------------------------------------------------------------

## Hybrid

    Cloud Agent
          ↓
    Secure Tunnel
          ↓
    Local Runtime
          ↓
    Local Resource

------------------------------------------------------------------------

# Session Continuity

Session 不绑定设备。

模型：

    Workspace
     ↓
    Session
     ↓
    Task

电脑：

    connect(session)

手机：

    connect(session)

------------------------------------------------------------------------

# Sync

建议基于：

    Git
    +
    Artifact Version

而不是自行实现文件版本。

------------------------------------------------------------------------

# 上云流程

    扫描资源

    ↓

    生成 Workspace Snapshot

    ↓

    上传

    ↓

    创建 Cloud Workspace

    ↓

    恢复 Session

    ↓

    继续任务

------------------------------------------------------------------------

# 安全

本地资源访问必须经过：

    Local Runtime Bridge

云端不能直接访问用户机器。
