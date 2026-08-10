# 06 — API、SSE 与业务事件契约

> 状态：Accepted

## 1. REST 基线

- Base Path：`/api/v1`
- 资源使用复数名词。
- JSON 字段使用 `camelCase`。
- ID 使用 UUIDv7 字符串。
- 时间使用 UTC ISO 8601，例如 `2026-08-10T08:30:00Z`。
- 创建类写操作接受 `Idempotency-Key` Header。
- 所有响应返回或透传 `X-Trace-Id`。
- API 以 OpenAPI 3.1 文件为权威契约，CI 校验破坏性变化。

## 2. 统一响应与错误

成功响应直接返回资源或分页 Envelope，不再嵌套无意义的 `code=200`。

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

错误使用 Problem Details 风格：

```json
{
  "type": "https://docs.gwnexusedge.com/problems/workspace-access-denied",
  "title": "无权访问工作空间",
  "status": 403,
  "code": "WORKSPACE_ACCESS_DENIED",
  "detail": "当前用户不具备该工作空间的读取权限。",
  "traceId": "...",
  "timestamp": "2026-08-10T08:30:00Z",
  "fieldErrors": []
}
```

错误码不可复用或依赖中文文本解析。

## 3. P0 资源 API

### Identity/Organization

- `POST /auth/local/login`：仅 Break Glass 管理员。
- `POST /auth/ldap/login`
- `POST /auth/logout`
- `GET /users/me`
- `GET /departments`
- `POST /admin/identity-sources`
- `POST /admin/directory-sync-jobs`

### Workspace/Resource

- `POST /workspaces`
- `GET /workspaces`
- `GET /workspaces/{workspaceId}`
- `PATCH /workspaces/{workspaceId}`
- `DELETE /workspaces/{workspaceId}`：软删除。
- `POST /workspaces/{workspaceId}/members`
- `POST /workspaces/{workspaceId}/resources/uploads:initiate`
- `PUT /upload-sessions/{uploadSessionId}/parts/{partNo}`
- `POST /upload-sessions/{uploadSessionId}:complete`
- `GET /resources/{resourceId}/versions`
- `POST /resources/{resourceId}:request-delete`
- `POST /workspaces/{workspaceId}/snapshots`

### Skill/Task

- `GET /skills?status=PUBLISHED`
- `GET /skills/{skillId}/versions/{versionId}`
- `POST /tasks`
- `GET /tasks/{taskId}`
- `POST /tasks/{taskId}:cancel`
- `POST /tasks/{taskId}:retry`
- `GET /tasks/{taskId}/events`：SSE。

创建 Task 请求必须包含 workspaceId、skillVersionId、knowledgeSnapshotId 或创建策略、参数和输出要求。服务端解析并固化全部 Policy/Template 版本。

### Artifact/Approval

- `GET /artifacts/{artifactId}`
- `GET /artifacts/{artifactId}/versions`
- `GET /artifact-versions/{versionId}/preview`
- `GET /artifact-versions/{versionId}/evidence`
- `POST /artifacts/{artifactId}/versions`：人工修改上传新版本。
- `POST /artifact-versions/{versionId}:submit-business-review`
- `POST /approvals/{approvalId}:approve`
- `POST /approvals/{approvalId}:return`
- `POST /artifact-versions/{versionId}:publish`
- `POST /artifacts/{artifactId}:archive`

### Admin/Audit

- `POST /admin/model-registrations`
- `POST /admin/model-policies`
- `POST /admin/context-policies`
- `POST /admin/embedding-policies`
- `POST /admin/skills/{skillId}/versions`
- `POST /admin/skill-versions/{versionId}:publish`
- `POST /admin/templates/{templateId}/versions`
- `GET /audit-events`
- `POST /audit-exports`

### Coding Project/Repository

- `POST /workspaces/{workspaceId}/coding-projects`
- `GET /coding-projects`
- `GET /coding-projects/{projectId}`
- `PATCH /coding-projects/{projectId}`
- `POST /coding-projects/{projectId}:request-decommission`
- `POST /coding-project-decommission-requests/{requestId}:approve`
- `POST /coding-projects/{projectId}:restore`
- `POST /coding-projects/{projectId}/repository-bindings`
- `POST /repository-bindings/{bindingId}:fetch`
- `GET /repository-bindings/{bindingId}/branches`
- `GET /repository-bindings/{bindingId}/status`
- `POST /admin/git-provider-registrations`
- `POST /git-provider-registrations/github:begin-installation`
- `POST /git-provider-registrations/{providerId}/repositories`

### Coding Plan/Task/ChangeSet

- `POST /coding-projects/{projectId}/coding-plans`
- `GET /coding-plans/{planId}/versions`
- `POST /coding-plan-versions/{versionId}:approve`
- `POST /coding-plan-versions/{versionId}:reject`
- `POST /coding-projects/{projectId}/coding-tasks`
- `GET /coding-tasks/{taskId}`
- `GET /coding-tasks/{taskId}/events`：SSE。
- `POST /coding-tasks/{taskId}:cancel`
- `GET /change-sets/{changeSetId}/versions`
- `GET /change-set-versions/{versionId}/diff`
- `POST /change-set-versions/{versionId}:approve`
- `POST /change-set-versions/{versionId}:reject`
- `POST /change-set-versions/{versionId}:push`
- `POST /change-set-versions/{versionId}:resolve-conflict`
- `POST /change-set-versions/{versionId}:create-pull-request`

### Environment/Secret/Resource

- `GET /coding-projects/{projectId}/environments`
- `POST /coding-projects/{projectId}/environments/{environment}/config-versions`
- `POST /coding-projects/{projectId}/environments/{environment}/secret-bindings`
- `DELETE /environment-secret-bindings/{bindingId}`
- `POST /coding-projects/{projectId}/resource-profile-bindings`
- `POST /admin/resource-profiles`
- `POST /admin/runtime-profiles`
- `POST /admin/destination-policies`

### Preview/Build/Supply Chain

- `POST /coding-projects/{projectId}/previews`
- `GET /deployments/{deploymentId}`
- `GET /deployments/{deploymentId}/events`：SSE。
- `POST /preview-deployments/{deploymentId}:extend`
- `POST /preview-deployments/{deploymentId}:stop`
- `POST /preview-deployments/{deploymentId}/share-tokens`
- `DELETE /preview-share-tokens/{tokenId}`
- `POST /coding-projects/{projectId}/release-candidates`
- `GET /release-candidates/{releaseCandidateId}`
- `GET /release-candidates/{releaseCandidateId}/security-findings`
- `GET /release-candidates/{releaseCandidateId}/sbom`
- `POST /security-findings/{findingId}/exceptions`
- `POST /security-exceptions/{exceptionId}:approve`
- `POST /security-exceptions/{exceptionId}:reject`

### Production/Domain

- `POST /release-candidates/{releaseCandidateId}:submit-approval`
- `POST /release-candidates/{releaseCandidateId}:approve-and-deploy`
- `POST /deployments/{deploymentId}:rollback`
- `POST /deployments/{deploymentId}:stop`
- `GET /coding-projects/{projectId}/releases`
- `GET /coding-projects/{projectId}/deployments`
- `POST /coding-projects/{projectId}/domain-bindings`
- `GET /domain-bindings/{domainBindingId}`
- `POST /domain-bindings/{domainBindingId}:verify`
- `POST /domain-bindings/{domainBindingId}:activate`
- `DELETE /domain-bindings/{domainBindingId}`
- `POST /domain-bindings/{domainBindingId}/certificate-references`
- `GET /coding-projects/{projectId}/runtime-logs`
- `GET /coding-projects/{projectId}/runtime-metrics`

## 4. 幂等

- Task 创建、上传完成、Artifact 渲染、审批决策、发布和通知发送必须幂等。
- Idempotency 作用域至少包含 tenant、actor/client、operation 和 key。
- 相同 Key、相同请求 Hash 返回首次结果；相同 Key、不同请求 Hash 返回 `409 IDEMPOTENCY_CONFLICT`。
- 幂等记录不得先于业务事务提交。

## 5. SSE 契约

响应：`Content-Type: text/event-stream`。每条事件包含：

```text
id: 01J...
event: task.progress
data: {"schemaVersion":"1.0","taskId":"...","executionId":"...","occurredAt":"...","payload":{...}}
```

P0 事件类型：

- `task.created`
- `task.queued`
- `task.started`
- `task.progress`
- `agent.plan.updated`
- `tool.started`
- `tool.completed`
- `tool.failed`
- `evidence.collected`
- `artifact.model.created`
- `render.waiting`
- `render.started`
- `render.completed`
- `approval.required`
- `task.completed`
- `task.retrying`
- `task.cancelled`
- `task.failed`
- `stream.heartbeat`
- `coding.plan.ready`
- `coding.plan.approval.required`
- `sandbox.allocating`
- `sandbox.ready`
- `coding.validation.completed`
- `changeset.review.required`
- `changeset.conflict`
- `changeset.pushed`
- `preview.deploying`
- `preview.ready`
- `preview.expiring`
- `build.started`
- `build.completed`
- `security.gate.blocked`
- `release.approval.required`
- `deployment.pulling`
- `deployment.verifying`
- `deployment.activating`
- `deployment.active`
- `deployment.rollback.started`
- `deployment.rolled_back`
- `domain.verification.updated`
- `certificate.renewal.warning`

不得向客户端暴露模型隐藏思维链。`agent.plan.updated` 和进度事件只展示经过安全处理的目标、步骤、Tool、证据和结果摘要。

SSE 事件写入可回放存储；客户端使用 `Last-Event-ID` 恢复。服务端保留期限必须覆盖活跃任务与合理重连窗口。

`@gwnexus/assistant-ui` 负责把本节稳定的 REST/SSE DTO 映射为 assistant-ui Custom Runtime 所需的客户端状态。公共 API 不得直接暴露 assistant-ui 内部类型、上游消息存储格式或特定版本的 Runtime 事件；assistant-ui 升级不得迫使后端公共契约同步破坏性变更。Tool UI 的 Action 必须调用本文件定义的 Nexus 业务 API，不能仅修改客户端 Thread 状态来模拟取消、重试、审批或发布成功。

## 6. Business Event Envelope

```json
{
  "eventId": "uuidv7",
  "eventType": "RESOURCE_VERSION_STORED",
  "schemaVersion": "1.0",
  "tenantId": "...",
  "workspaceId": "...",
  "aggregateType": "RESOURCE",
  "aggregateId": "...",
  "occurredAt": "...",
  "traceId": "...",
  "actor": {"type":"USER","id":"..."},
  "payload": {}
}
```

- Event Schema 必须版本化并向后兼容。
- Consumer 按 eventId 幂等。
- 不在事件中放文件正文、Secret、完整 Prompt 或大对象。

## 7. Outbox + Redis Streams

- 业务事务同时写 Aggregate 与 `gw_outbox_event`。
- Publisher 使用抢占/租约机制批量发布 Redis Streams。
- Consumer Group ACK 成功事件；失败使用指数退避。
- 超过最大重试进入 Dead Letter Stream，并生成告警和审计。
- P0 Stream：file-parse、notification、render、knowledge-cleanup。
- Coding业务可使用：security-scan、build-result、deployment-event、domain-check和preview-expiration；命令执行与Agent生命周期不通过Stream调度。
- Agent Execution 不进入上述 Stream 调度。

## 8. Coding API 安全契约

- Coding API必须同时校验Tenant、Workspace、Project、Role、Scope、Environment和当前聚合状态。
- Git Credential、Secret Value、Certificate Private Key和完整模型Context不得出现在请求回显或响应中。
- Push、Release Candidate创建、Production发布、Rollback、Domain Activation和Decommission必须支持Idempotency-Key。
- Production发布请求必须携带客户端已查看的Release Candidate Version/ETag；版本过期返回`409 RELEASE_CANDIDATE_STALE`。
- Diff下载、Preview分享、Runtime Log和SBOM均使用短期授权；跨Project ID猜测必须返回一致的拒绝模型。
- 所有长操作先持久化业务资源再返回`202 Accepted`与Location；进度通过REST+SSE恢复。
- GitHub Webhook必须验证签名、安装范围和事件重放；Webhook不得直接触发Agent或Production。

## 9. V1.1 Desktop预留边界

本节不属于V1 API，不得在V1 OpenAPI、Controller或SDK中提前实现。V1.1预计评审以下能力：

- Workspace本地目录Sync Source注册与Cursor；
- 本地Git Repository发现、用户确认和远程Repository绑定；
- Desktop设备注册、系统通知Channel和诊断；
- Desktop版本兼容、更新和撤销。

具体路径、Schema、权限和幂等契约必须在V1.1 ADR中重新冻结，本文不预先承诺`sync-sources`或`import-local`等路径名称。
