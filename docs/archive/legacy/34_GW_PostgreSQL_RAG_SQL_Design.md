# [ARCHIVED] GW Nexus Edge PostgreSQL RAG SQL Design

## 数据库

    PostgreSQL

    +

    pgvector

------------------------------------------------------------------------

# knowledge_base

知识库：

    id

    workspace_id

    name

    permission_policy

------------------------------------------------------------------------

# knowledge_document

文档：

    id

    knowledge_base_id

    file_name

    file_type

    storage_path

------------------------------------------------------------------------

# document_chunk

核心：

    id

    document_id

    content text

    embedding vector(1536)

    metadata jsonb

------------------------------------------------------------------------

# 向量索引

使用：

    HNSW

    IVFFlat

优化：

-   相似搜索
-   大规模知识库

------------------------------------------------------------------------

# 检索流程

    Query

    ↓

    Embedding

    ↓

    Vector Search

    ↓

    Metadata Filter

    ↓

    Rerank

    ↓

    LLM
