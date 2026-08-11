# 04 — 领域模型与状态机

> 状态：Accepted

## 1. 聚合关系

```text
Tenant
 ├─ Department
 ├─ User / Identity Mapping
 └─ Workspace
     ├─ WorkspaceMember
     ├─ Resource
     │   └─ ResourceVersion
     ├─ KnowledgeBase
     │   ├─ KnowledgeDocument
     │   └─ DocumentChunk
     ├─ SkillBinding
     ├─ Task
     │   └─ TaskAttempt → AgentScope Agent（agentId）
     └─ Artifact
         ├─ ArtifactVersion
         ├─ EvidenceLink
         └─ Approval
     └─ CodingProject
         ├─ RepositoryBinding / BuildContractVersion / CodeIndexGeneration
         ├─ CodingPlan / CodingPlanVersion
         ├─ CodingTask → TaskAttempt → AgentScope Agent（agentId）→ Sandbox
         ├─ ChangeSet / ChangeSetVersion
         ├─ ReleaseCandidate / SecurityFinding / SupplyChainAttestation
         ├─ Deployment / DeploymentAttempt
         └─ DomainBinding / CertificateReference
```

## 2. 核心不变量

### Tenant/Workspace

- 每条业务数据必须具有 tenant_id；Workspace 数据还必须具有 workspace_id。
- V1 一个部署只有一个活动 Tenant，但不得省略 Tenant 维度。
- Workspace 不得跨 Tenant，成员必须来自当前 Tenant。
- Workspace 默认数据等级不可由普通用户降低。
- 被删除或冻结的 Workspace 不接受新 Task。

### Resource/Version/Snapshot

- MinIO ResourceVersion 是权威内容；本地文件只负责采集。
- ResourceVersion 创建后内容、Hash、大小和来源不可修改。
- 同步新内容创建新版本，不覆盖旧对象。
- Knowledge Snapshot 固定引用 ResourceVersion 集合与解析版本。
- 运行 Task 必须绑定 Snapshot；后续文件变化不影响历史 Task 和 Artifact。

### Skill/Policy

- Task 只能使用 Published SkillVersion。
- SkillVersion、Prompt、Workflow、Tool Policy、Model Policy 和 Template Version 必须固化到 TaskAttempt。
- 已被任务引用的版本不可物理覆盖；修改必须发布新版本。
- 普通用户不能修改运行时配置。

### Artifact

- ArtifactVersion 不可覆盖。
- AI 生成和人工上传版本必须标记 creator_type 与 previous_version_id。
- Published Artifact 默认永久保留，只能归档。
- 发布前必须通过业务审核和最终批准。

### Coding Project / Repository

- 一个Project绑定一个权威Repository和一个版本化Project Root。
- 远程Git是代码真相源；Nexus Edge只保存Binding、Commit、Diff、Plan、结果和供应链证据。
- 同一Repository可以服务多个Project Root；Task不能跨多个Repository修改。
- Coding Plan批准前不得创建可写Sandbox。
- ChangeSet、Release和Deployment必须绑定Base/Candidate Commit与Project Root。
- Production只能使用已Push、已审核、已签名且Gate通过的不可变Image Digest。
- Agent不能执行Production发布；APPROVER不能绕过Security Gate。

## 3. Task 状态机

```text
CREATED → QUEUED → RUNNING → WAITING_RENDER → COMPLETED
                ↘ RETRY_WAIT → QUEUED
CREATED/QUEUED/RUNNING/WAITING_RENDER → CANCEL_REQUESTED → CANCELLED
RUNNING/WAITING_RENDER/RETRY_WAIT → FAILED
```

| 状态 | 语义 |
|---|---|
| CREATED | Task 及输入已验证并持久化 |
| QUEUED | 等待 AgentScope 启动或重新尝试 |
| RUNNING | 已关联 AgentScope Agent 实例（agentId） |
| WAITING_RENDER | Agent 已产生 Artifact Model，等待正式 Renderer |
| COMPLETED | Task 执行与必要渲染结束；审核由 Artifact 状态表达 |
| RETRY_WAIT | 可恢复失败，等待退避重试 |
| CANCEL_REQUESTED | 已请求取消，等待 AgentScope/Worker 确认 |
| CANCELLED | 取消完成，不再推进 |
| FAILED | 达到重试上限或不可恢复失败 |

Task 不包含 `READY_FOR_REVIEW`。Task 状态不是 AgentScope Agent 执行状态副本（AgentScope 2.0.1 无独立 Execution ID，见 16 术语表）。

## 4. Artifact 状态机

```text
GENERATED_DRAFT
      ↓ submit
BUSINESS_REVIEW
   ├─ return → RETURNED ── new version + resubmit event ─→ BUSINESS_REVIEW
   └─ approve → BUSINESS_APPROVED
                       ↓
                FINAL_APPROVAL
                  ├─ return → RETURNED
                  └─ approve/publish → PUBLISHED → ARCHIVED
```

- `RESUBMITTED` 是事件，不是持久状态。
- `PURGED` 只表示按 Retention Policy 完成物理清理，不是正常业务状态。
- 退回必须填写原因；重新生成或人工修改均创建新版本。
- 领导批准和发布可以在同一事务用例中完成，但必须分别记录 ApprovalDecision 与 PublishEvent。

## 5. Resource 状态

```text
DISCOVERED → UPLOADING → STORED → PARSING → INDEXED
                  ├─ UPLOAD_FAILED
                  └─ PARSE_FAILED / UNSUPPORTED
INDEXED → VERSION_SUPERSEDED
ACTIVE → SOFT_DELETED → PURGED
```

- 无文本层 PDF 在没有 OCR Provider 时进入 `UNSUPPORTED`，不得进入 INDEXED。
- 本地删除产生 DeleteRequest，不直接删除服务器权威文件。

## 6. RenderJob 状态

```text
PENDING → CLAIMED → RENDERING → VALIDATING → SUCCEEDED
             └─ RETRY_WAIT → PENDING
PENDING/RENDERING/VALIDATING → FAILED
```

- Worker 必须通过 Lease/Heartbeat 避免同一 Job 并发执行。
- 输出使用临时对象键，验证成功后原子登记为 ArtifactVersion。
- 相同 Idempotency Key 重复请求返回同一有效结果。

## 7. Approval 状态

```text
REQUESTED → APPROVED
          → RETURNED
          → CANCELLED
```

Approval 保存 stage、artifact_version_id、assignee、decision、comment、decided_at 和审计关联。

## 8. 配置资产状态

Skill、Prompt、WorkflowDefinition、ModelPolicy、ContextPolicy、Template 统一使用：

```text
DRAFT → TESTING → PUBLISHED → DEPRECATED → ARCHIVED
```

Published 版本不可修改。Rollback 是重新激活历史 Published 版本，不重写版本内容。

## 9. Coding Plan 状态

```text
DRAFT → READY_FOR_REVIEW → APPROVED
                    └────→ REJECTED
APPROVED → SUPERSEDED
```

- 只读分析阶段可以创建Plan；可写Sandbox要求APPROVED。
- 实质性范围变化创建新PlanVersion，旧版本标记SUPERSEDED但不可覆盖。
- 精简Plan仍使用相同状态机。

## 10. ChangeSet 状态机

```text
GENERATED
  → VALIDATING
  → REVIEW_REQUIRED
  → APPROVED
  → PUSHING
  → PUSHED

VALIDATING → VALIDATION_FAILED
REVIEW_REQUIRED → REJECTED
PUSHING → CONFLICT
CONFLICT → new ChangeSetVersion → REVIEW_REQUIRED
任意未Push版本 → SUPERSEDED
```

- Task只描述Agent执行；Diff审核和Git写回由ChangeSet表达。
- ChangeSetVersion不可覆盖，绑定Base Commit、Candidate Commit、Diff Hash、测试和安全结果。
- `CONFLICT`不是通用Task状态；远程分支前进或Merge冲突记录在ChangeSet。
- 冲突解决、Rebase或Merge后必须生成新版本并重新审核。

## 11. Sandbox 状态机

```text
ALLOCATING → PREPARING → READY → EXECUTING → CHECKPOINTING → TERMINATING → DESTROYED
任一活动状态 → FAILED → TERMINATING
```

- Sandbox是临时执行资源，不是代码或Agent Checkpoint真相源。
- 任务恢复创建新Sandbox并重放经过Hash校验的Commit/Patch。
- 到期、取消、失败和完成均必须进入TERMINATING并最终销毁。

## 12. Release Candidate 状态机

```text
CREATED → BUILDING → SECURITY_CHECKING → READY_FOR_APPROVAL → APPROVED → PROMOTED
BUILDING / SECURITY_CHECKING → BLOCKED
READY_FOR_APPROVAL → REJECTED
任意未发布版本 → SUPERSEDED
```

- `APPROVED`表示Production人工发布授权已经完成。
- `PROMOTED`只在Production Gateway切流成功后设置。
- BLOCKED必须关联Security Finding、Build Failure或Readiness Failure。
- 任一Commit、Lockfile、Build Contract或Base Image变化创建新Release Candidate。

## 13. Deployment 状态机

```text
REQUESTED → PULLING → STARTING → VERIFYING → ACTIVATING → ACTIVE
任一执行状态 → FAILED
ACTIVE → ROLLING_BACK → ROLLED_BACK
ACTIVE → STOPPING → STOPPED
```

- Preview复用Deployment状态，但不使用Production审批和PROMOTED语义。
- ACTIVATING表示Gateway原子切流；切流前必须完成Signature、Gate和Health验证。
- 自动/人工回滚都创建DeploymentAttempt并保留原Release证据。

## 14. Domain Binding 状态

```text
PENDING_CONFIGURATION
  → VERIFYING_OWNERSHIP
  → VERIFYING_ROUTE
  → ISSUING_CERTIFICATE
  → ACTIVE

任一验证状态 → VERIFICATION_FAILED
ACTIVE → RENEWAL_WARNING
ACTIVE / RENEWAL_WARNING → REVOKING → REVOKED
```

- 自定义域名绑定Project Production Environment，不绑定单个Release。
- 无有效HTTPS时不得进入ACTIVE或激活新的Production Traffic。
- DNS验证、Certificate和Gateway Route均需独立审计。
