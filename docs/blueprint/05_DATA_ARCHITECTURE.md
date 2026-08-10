# 05 — 数据架构与持久化规格

> 状态：Accepted

## 1. 存储职责

| 存储 | 权威数据 | 非职责 |
|---|---|---|
| MySQL | 身份映射、组织、Workspace、权限、Skill、Policy、Task、Artifact 元数据、Approval、Audit、Outbox | 大文件、Embedding |
| DM8 | MySQL 业务模型兼容认证 | V1 唯一生产基线 |
| PostgreSQL + pgvector | Knowledge Document、Chunk、Embedding、检索元数据与 ACL | 企业业务事务主库 |
| MinIO | 原始文件版本、模板、Artifact Model、正式 Artifact、预览 | 权限决策真相源 |
| Redis | Cache、Session 辅助、分布式锁、Redis Streams | 永久业务记录、审计、Agent Checkpoint 真相源 |
| Harbor | Coding Application OCI Image、SBOM、Signature、Provenance | Git源代码真相源、业务数据库 |
| Git Provider | Repository、Branch、Commit、Pull Request | Nexus Edge业务权限、Deployment状态 |

## 2. 通用字段

业务表至少包含：

| 字段 | 类型/规则 |
|---|---|
| id | UUIDv7，API 字符串 |
| tenant_id | UUIDv7，非空并参与关键索引 |
| created_at/updated_at | UTC timestamp |
| created_by/updated_by | UUIDv7 |
| version | 乐观锁版本号 |
| deleted_at | 可空 UTC timestamp；需要软删除的表使用 |

禁止用 `deleted = 0/1` 替代删除时间和删除责任人。敏感表还需 `deleted_by`、`delete_reason`。

## 3. MySQL 表目录

### Identity/Organization

- `gw_tenant`
- `gw_department`
- `gw_user`
- `gw_identity_source`
- `gw_identity_mapping`
- `gw_directory_group`
- `gw_directory_group_member`
- `gw_role_assignment`
- `gw_permission_grant`

### Workspace/Resource

- `gw_workspace`
- `gw_workspace_member`
- `gw_workspace_policy_binding`
- `gw_resource`
- `gw_resource_version`
- `gw_knowledge_snapshot`
- `gw_snapshot_resource_version`

V1.1 Desktop 计划新增 `gw_sync_source`、`gw_sync_cursor` 等本地同步实体；V1 Flyway Migration不得提前创建这些表，具体Schema在V1.1 ADR中冻结。

### Skill/Policy/Model

- `gw_skill`
- `gw_skill_version`
- `gw_prompt_version`
- `gw_workflow_definition_version`
- `gw_template`
- `gw_template_version`
- `gw_model_registration`
- `gw_model_policy`
- `gw_context_policy`
- `gw_embedding_policy`
- `gw_tool_registration`
- `gw_tool_permission`

### Task/Artifact/Approval

- `gw_task`
- `gw_task_attempt`
- `gw_task_event`
- `gw_idempotency_record`
- `gw_artifact`
- `gw_artifact_version`
- `gw_artifact_evidence_link`
- `gw_approval`
- `gw_render_node`
- `gw_render_job`

### Notification/Audit/Operations

- `gw_notification`
- `gw_notification_delivery`
- `gw_audit_event`
- `gw_model_usage`
- `gw_outbox_event`
- `gw_retention_policy`
- `gw_purge_job`

### Coding Project/Git/Context

- `gw_coding_project`
- `gw_repository_binding`
- `gw_git_provider_registration`
- `gw_git_credential_reference`
- `gw_build_contract`
- `gw_build_contract_version`
- `gw_code_index_generation`
- `gw_coding_plan`
- `gw_coding_plan_version`
- `gw_change_set`
- `gw_change_set_version`
- `gw_git_operation`

### Sandbox/Build/Supply Chain

- `gw_resource_profile`
- `gw_resource_profile_version`
- `gw_runtime_profile`
- `gw_host_node`
- `gw_host_lease`
- `gw_sandbox`
- `gw_sandbox_checkpoint_ref`
- `gw_build`
- `gw_build_attempt`
- `gw_security_scan`
- `gw_security_finding`
- `gw_security_exception`
- `gw_sbom_reference`
- `gw_signature_reference`
- `gw_provenance_reference`

### Environment/Release/Deployment/Domain

- `gw_project_environment`
- `gw_environment_config_version`
- `gw_environment_secret_binding`
- `gw_release_candidate`
- `gw_deployment`
- `gw_deployment_attempt`
- `gw_application_access_policy`
- `gw_preview_share_token`
- `gw_domain_binding`
- `gw_domain_verification`
- `gw_certificate_reference`
- `gw_destination_policy`
- `gw_egress_audit`

## 4. 关键约束与索引

- 所有 Workspace 子表建立 `(tenant_id, workspace_id, id)` 或匹配访问模式的组合索引。
- `gw_workspace`：Tenant 内 name 可按业务规则唯一；状态和 department_id 建索引。
- `gw_resource`：`(workspace_id, logical_path)` 唯一；Windows 路径需规范化大小写和分隔符。
- `gw_resource_version`：`(resource_id, version_no)` 唯一；`content_hash` 索引用于去重但不得跨 Tenant 泄露存在性。
- `gw_task`：`idempotency_key` 在 Tenant/调用者/用例范围唯一；status/created_at 建索引。
- `gw_task_attempt`：`(task_id, attempt_no)` 唯一；AgentScope Execution ID 唯一且可空到启动成功。
- `gw_artifact_version`：`(artifact_id, version_no)` 唯一；storage_key 不可复用。
- `gw_approval`：同一 ArtifactVersion、stage 只能有一个活动请求。
- `gw_outbox_event`：status、next_attempt_at、created_at 组合索引；event_id 全局唯一。
- `gw_audit_event`：按 event_time、actor_id、workspace_id、resource_id、task_id 可检索；应用层禁止 UPDATE/DELETE。
- `gw_coding_project`：`(workspace_id, name)`与状态索引；Repository/ProjectRoot唯一性由业务规则校验。
- `gw_repository_binding`：保存Provider、Remote URL规范化Hash、默认分支、Project Root；禁止保存Token/SSH Key明文。
- `gw_change_set_version`：`(change_set_id, version_no)`唯一，Base/Candidate Commit和Diff Hash不可修改。
- `gw_release_candidate`：`(project_id, release_no)`唯一；Commit、Contract Hash、Image Digest和Gate Snapshot不可修改。
- `gw_deployment`：Environment/Project/Status索引；Idempotency Scope内Key唯一；Active Production同一Project只允许一个流量主版本。
- `gw_domain_binding`：FQDN规范化后全局/实例内唯一；绑定Project Production Environment；Ownership状态与Certificate状态分离。
- `gw_security_exception`：Finding、Commit/Image Digest、Scope、expires_at和Approver不可空；过期后不参与Gate。
- `gw_host_node`：Node Pool、OS、Architecture、Runtime Profile、Capacity和Heartbeat索引；Credential只保存引用。

## 5. PostgreSQL RAG Schema

- `knowledge_base(id, tenant_id, workspace_id, name, status, acl_version, ...)`
- `knowledge_document(id, tenant_id, workspace_id, resource_version_id, parser_version, status, ...)`
- `document_chunk(id, tenant_id, workspace_id, document_id, ordinal, content, content_hash, locator jsonb, metadata jsonb, ...)`
- `chunk_acl(chunk_id, principal_type, principal_id, permission)`
- `embedding_index(id, chunk_id, model_registration_id, model_version, dimension, embedding, created_at)`
- `retrieval_trace(id, task_id, execution_id, query_hash, policy_version, result_refs, created_at)`
- `code_index(id, tenant_id, workspace_id, project_id, repository_binding_id, commit_sha, project_root, index_version, status, ...)`
- `code_file(id, code_index_id, normalized_path, blob_hash, language, size, index_status, sensitivity_level, ...)`
- `code_symbol(id, code_file_id, symbol_kind, qualified_name, locator jsonb, content_hash, ...)`
- `code_embedding(id, code_file_id, chunk_ordinal, content_hash, model_registration_id, dimension, embedding, locator jsonb, ...)`

要求：

- 权限、tenant_id 和 workspace_id 必须在向量/关键词检索阶段预过滤。
- 不固定 `vector(1536)`。Embedding 模型、版本、维度和索引代际必须显式记录。
- 不同维度不得混入同一物理向量索引；采用按 Embedding Profile 管理的索引表/分区或明确的迁移方案。
- Chunk 记录页码、Sheet、Slide、章节、单元格区域等 locator。
- 原文件版本删除后，Chunk/Embedding 通过可靠清理事件进入删除流程。
- Code Index严格绑定Commit；`.gitignore`、`.nexusignore`、Secret命中、二进制、依赖和构建产物不得进入Embedding。
- Code Index权限必须在全文、符号和向量召回阶段按Tenant/Workspace/Project预过滤。

## 6. MinIO 对象键

对象键必须不可变并包含逻辑隔离：

```text
tenants/{tenantId}/workspaces/{workspaceId}/resources/{resourceId}/versions/{versionId}/source
tenants/{tenantId}/templates/{templateId}/versions/{versionId}/template
tenants/{tenantId}/workspaces/{workspaceId}/artifacts/{artifactId}/versions/{versionId}/artifact
tenants/{tenantId}/render-jobs/{jobId}/temporary/{attemptId}
tenants/{tenantId}/workspaces/{workspaceId}/coding-projects/{projectId}/plans/{planVersionId}
tenants/{tenantId}/workspaces/{workspaceId}/coding-projects/{projectId}/changesets/{changeSetVersionId}
tenants/{tenantId}/workspaces/{workspaceId}/coding-projects/{projectId}/validations/{taskId}/{attemptId}
tenants/{tenantId}/workspaces/{workspaceId}/coding-projects/{projectId}/release-evidence/{releaseCandidateId}
```

- 业务表保存 object key、bucket、hash、size、media type 和 encryption metadata。
- MinIO Bucket 不直接暴露公网；下载通过授权 API 或短期预签名 URL。
- 临时对象必须有生命周期策略，成功登记后才能成为 ArtifactVersion。

## 7. Schema 生命周期

- Flyway 是唯一 Schema 迁移工具。
- `db/migration/mysql` 与 `db/migration/dm8` 独立维护同一逻辑版本。
- 禁止 Hibernate/JPA ddl-auto、自动建表或应用启动隐式迁移。
- 生产迁移必须先备份、验证可前滚、记录兼容范围和停机需求。
- “回滚”优先采用向前修复 Migration；破坏性迁移必须通过 Expand/Migrate/Contract。

## 8. 一致性

- MySQL 内部强一致业务变更使用本地事务。
- MySQL 与 Redis/MinIO/PostgreSQL 跨存储采用状态机、Outbox、幂等 Consumer 和补偿。
- 文件上传先写临时对象，Hash/大小校验后在 MySQL 登记版本，再发布解析事件。
- 解析/索引失败不删除权威文件，ResourceVersion 状态标记失败并支持重试。
- Artifact 发布事务只更新业务状态；对象必须已经验证存在且 Hash 匹配。

## 9. 保留与删除

| 数据 | 默认保留 |
|---|---|
| 软删除 Workspace | 90 天 |
| 软删除源文件版本 | 90 天 |
| Chunk/Embedding | 跟随源文件版本 |
| 未发布 Draft | 90 天 |
| Published Artifact | 默认永久，仅归档 |
| Audit | 365 天，可延长和归档 |
| Coding Sandbox / Build Cache | Sandbox销毁；Build Cache 7天 |
| Preview运行日志/到期镜像 | 7天 |
| 未发布/拒绝 Release Candidate | 90天 |
| Published Production Image/SBOM/Signature/Provenance | 默认永久归档 |
| Production运行日志 | 30天 |

物理清理必须由系统 Purge Job 执行并记录 Audit，不允许业务 Controller 直接删除跨存储数据。
