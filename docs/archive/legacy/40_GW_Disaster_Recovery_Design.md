# [ARCHIVED] GW Nexus Edge Disaster Recovery Design

## 目标

企业可靠性。

------------------------------------------------------------------------

# 数据备份

MySQL:

-   定时备份
-   Binlog

PostgreSQL:

-   WAL

对象存储:

-   MinIO Replication

------------------------------------------------------------------------

# 高可用

    Load Balancer

    ↓

    Spring Boot Cluster

    ↓

    Database Cluster

------------------------------------------------------------------------

# 恢复目标

关注：

RPO

RTO
