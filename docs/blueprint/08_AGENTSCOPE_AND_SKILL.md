# 08 — AgentScope 集成、经营分析 Skill 与 Coding Agent

> 状态：Accepted

## 1. Runtime 基线

- `io.agentscope` AgentScope Java 2.0.1。
- Spring Boot 4.1.0 内嵌 Harness/Core。
- 不部署 AgentScope Service 或独立 Runtime Control Plane。
- 使用官方 Agent、Event、Permission、Tool、Model Provider、Memory、Workflow、State Store 能力。
- Nexus Edge 不复制官方运行模型；Adapter 只隔离企业领域与 SDK 变化。

## 2. Adapter Port

领域层只认识以下业务能力语义，不暴露 AgentScope 类型：

- `startExecution(AgentExecutionRequest)`
- `cancelExecution(AgentExecutionReference)`
- `resumeExecution(AgentExecutionReference, ResumeReason)`
- `streamExecutionEvents(AgentExecutionReference)`（返回 JDK `Flow.Publisher`，执行前建立，不发起第二次执行）

Adapter 实现负责：

1. 解析 Published SkillVersion 与 Agent Definition。
2. 构造 AgentScope RuntimeContext：业务 Tenant/Workspace/User/Session 经 Runtime Identity
   映射为 AgentScope `scopedUserId`/`scopedSessionId`（稳定、无碰撞、路径安全），
   原始业务标识（workspaceId/tenantId/sessionId）保留在 RuntimeContext extras。
3. 注入经过 Policy 决策的 Knowledge、Tool 和 Model 配置。
4. 创建 Business Analyst Agent 与 Review Agent。
5. 订阅 AgentScope typed events，映射业务安全事件（终态事件先完成状态转移再发布）。
6. 维护四标识映射：taskId / taskAttemptId（UUIDv7）/ agentId（AgentScope Agent 实例标识）/
   traceId（OTel）；AgentScope 2.0.1 无独立 Execution ID，禁止称 agentId 为"官方 Execution ID"。
7. 将结构化结果交给 Artifact 服务。

不得自行实现 ReAct 循环、Tool Dispatcher、SubAgent Message Bus、Memory Compaction、Checkpoint 或模型 HTTP 客户端。

**自动长期记忆边界（ADR-0006）**：业务 Agent 不允许自动长期记忆——`disableMemoryTools`
只移除记忆工具，Memory Flush/Consolidation Hooks 仍会按 userId 写入共享
`memory/YYYY-MM-DD.md` 台账（跨 Workspace 共享），必须同时 `disableMemoryHooks()`。
引入自动长期记忆需独立 ADR + Runtime Identity 评审，禁止用临时方案掩盖。

## 3. Model Governance

Nexus Edge Model Governance 保存：

- Provider/Model Registration。
- AgentScope Provider Type 与 Model Identifier。
- Endpoint、能力、数据驻留和 Secret Ref。
- 允许的数据等级、Workspace、Skill 和优先级。
- Token/成本限额、超时、重试和熔断治理参数。

执行时 Resolver 输出 AgentScope 官方 Provider 可消费的配置。P0 验证：

- DeepSeek 官方 Provider：外部模型。
- AgentScope OpenAI-compatible/Ollama 等官方扩展覆盖的企业私有 Endpoint。

官方缺少 Provider 时，新实现必须作为 AgentScope Extension 隔离，不得创建 Nexus Edge 通用 `chat()/stream()` SPI。

## 4. Skill Manifest

SkillVersion 至少包含：

```yaml
apiVersion: gwnexus/v1
kind: Skill
metadata:
  id: uuidv7
  name: enterprise-business-analysis
  version: 1.0.0
spec:
  inputSchemaRef: business-analysis-input-v1
  agentDefinitionRef: business-analyst-agent-v1
  reviewAgentRef: evidence-review-agent-v1
  workflowDefinitionRef: business-analysis-workflow-v1
  promptVersionRefs: []
  allowedToolRefs: []
  knowledgeScopePolicyRef: workspace-authorized-snapshot
  modelPolicyRef: business-analysis-model-policy-v1
  contextPolicyRef: l2-minimum-context-v1
  outputSchemaRef: business-analysis-artifact-model-v1
  templateRequirements:
    - DOCX
    - PPTX
    - XLSX
```

Manifest 进入数据库时保留规范化字段和不可变原文 Hash。Published Version 不可修改。

## 5. 经营分析输入

必填：

- Workspace 与 Snapshot。
- 分析周期。
- 业务范围/项目范围。
- 报告受众。
- Word/PPT/Excel Template Version。
- 分析目标与重点关注项。

可选：对比周期、指标口径、管理层特别问题、输出章节偏好。

任务启动前验证文件格式、解析状态、模板、权限、模型与 Renderer 能力。失败必须在 Agent 启动前返回明确错误。

## 6. 推荐执行结构

```text
Business Analyst Agent
  ├─ plan via AgentScope Workflow/Prompt
  ├─ Knowledge Search Tool
  ├─ Excel Analysis Tool
  ├─ Metric Calculation Tool
  ├─ Evidence Registry Tool
  ├─ Artifact Model Tool
  └─ handoff → Review Agent
```

- Planner 不强制独立 Agent。
- Data/Knowledge 是确定性或检索 Tool，不强制独立 Agent。
- Document 生成是 Artifact Workflow，不是 Agent。
- Review Agent 独立检查证据覆盖、数字、冲突、无依据推断和输出 Schema。

只有独立上下文、权限或生命周期需要时才增加 SubAgent。

## 7. 确定性 Tool 原则

- Excel 公式、聚合、同比/环比、排序和异常阈值由 Tool 计算。
- Agent 选择分析方法、解释结果、关联知识和形成建议。
- Tool 输出包含 source、locator、formula、result、data type、warnings 和 hash。
- Agent 不得凭自然语言心算替代可复算 Tool。
- Tool 错误是结构化错误，不能以空结果伪装成功。

## 8. Review Agent Gate

Review Agent 必须检查：

1. 每个关键数字有 Calculation Evidence。
2. 每个关键事实有 Source Evidence。
3. 推断标记为 Inference，证据与置信说明存在。
4. 冲突没有被静默解决。
5. 不存在资料之外的企业事实、名称、数字和引用。
6. 输出满足 Artifact Model Schema 和模板容量约束。

Review Agent 不能替代业务负责人审核。模型 Review 失败时 Task 不得产生可提交 Draft。

## 9. 长任务

- AgentScope 管理执行状态、Checkpoint 和恢复。
- Nexus Edge 记录业务阶段、进度投影和 Attempt。
- 用户取消通过 Adapter 转交 AgentScope；确认完成后 Task 进入 CANCELLED。
- 模型暂时不可用进入 RETRY_WAIT；所有模型不可用时 Task 最终 FAILED，不降级成非 Agent 报告流水线。

## 10. Skill 治理

- 管理员可通过 Web Console 或版本化 YAML/JSON 创建、测试、发布、回滚。
- V1 不提供低代码 Agent 编排 IDE。
- Task 只使用 Published Version。
- 每次执行固化 Skill、Prompt、Workflow、Tool、Model/Context Policy、Template 和 Parser Version。
- Golden Dataset Eval 未通过不得发布新生产版本。

## 11. Coding Agent

Coding Workspace同样使用内嵌AgentScope Java 2.0.1作为唯一Runtime。Nexus Edge不为Coding自行实现Agent循环、Planning Runtime、Tool Dispatcher、Checkpoint或模型协议。

推荐结构：

```text
Coding Agent
  ├─ read-only Repository/Code Index Tools
  ├─ Coding Plan Artifact Tool
  ├─ Sandbox File/Search Tool
  ├─ Sandbox Shell/Test/Dev Build Tool
  ├─ Git ChangeSet Tool
  └─ Validation/Review Workflow
```

所有执行型Tool经Policy和Sandbox Broker进入Task独立Sandbox。Coding Agent不直接访问Host、Docker Socket、Production Secret、Production Host或企业内网。

## 12. Coding Plan 与执行

1. 只读分析绑定Repository、Project Root和Base Commit。
2. Agent生成版本化Coding Plan。
3. 用户批准Plan。
4. Nexus Edge创建可写Sandbox并启动AgentScope Execution。
5. Agent通过Tool修改、测试和开发构建。
6. 结果进入ChangeSet；用户确认后才Push远程Agent Branch。

Plan的业务状态、ChangeSet和Git Push不由AgentScope内部状态替代。AgentScope负责Execution；Nexus Edge负责人类批准、Repository权限、Diff和供应链治理。

## 13. Coding Model Capability Profile

管理员只有在模型通过以下真实测试后才能发布为Coding模型：

- 长上下文与代码检索协作；
- Tool Calling；
- 结构化输出；
- 多文件代码理解；
- Patch/ChangeSet生成；
- 错误恢复和测试反馈修正。

Fallback只允许在相同数据边界、相同或更严格安全等级且已认证的模型间发生。L3不得因模型故障外发。所有模型不可用时Task失败或等待恢复，不降级为无模型脚本流水线。

## 14. Coding长任务恢复

AgentScope保存官方Execution Checkpoint；Nexus Edge保存Base Commit、Sandbox Manifest、Checkpoint Commit/Patch、Build Contract和Tool结果。恢复时创建全新Sandbox并重放Hash校验的代码变更，不恢复旧容器文件系统。Repository、Permission、Secret或Policy变化要求重新验证或新Attempt。
