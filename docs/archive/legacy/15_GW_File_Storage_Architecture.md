# [ARCHIVED] GW Nexus Edge File Storage Architecture

## 原则

数据库保存元数据。

文件进入对象存储。

    Metadata DB

    +

    MinIO/S3

## 支持

-   上传
-   分片
-   秒传
-   版本
-   生命周期
-   权限

## Artifact

保存：

    url
    hash
    version
    metadata
