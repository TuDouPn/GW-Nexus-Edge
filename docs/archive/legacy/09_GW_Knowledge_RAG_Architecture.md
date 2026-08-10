# [ARCHIVED] GW Nexus Edge Knowledge RAG Architecture

## 架构

    Document

    ↓

    Parser

    ↓

    Chunk

    ↓

    Embedding

    ↓

    PostgreSQL + pgvector

    ↓

    Retriever

    ↓

    Rerank

    ↓

    LLM

## 技术

数据库：

PostgreSQL + pgvector

存储：

MinIO

## 能力

-   企业知识库
-   权限检索
-   混合搜索
-   增量更新
-   文档版本

## 权限

检索前过滤：

    User Permission

    ↓

    Knowledge Permission

    ↓

    Search Result
