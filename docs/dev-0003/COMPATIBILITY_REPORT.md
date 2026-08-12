# DEV-0003 — AgentScope Redis Persistence/Recovery 验证报告

> 状态：**PARTIALLY_VERIFIED**（Redis Persistence/Recovery 已验证；Provider BLOCKED_BY_CREDENTIAL；
> Checkpoint 待评审；OQ-007 Redis 部分 RESOLVED）
> 日期：2026-08-12
> 分支：`agent/DEV-0003-agentscope-persistence-recovery`
> 核心版本（固定）：Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1（未降级）
> 关联：G-03（建议状态 PARTIAL）、OQ-007（Redis 部分解决，Checkpoint 待评审）、ADR-0008（Proposed）

---

## 1. 已验证 / 未验证 / BLOCKED 严格区分

| 类别 | 内容 |
|---|---|
| **已验证（真实 Redis 12/12）** | C-1~C-12：scoped 写入、跨实例恢复、Tenant/Workspace 隔离、篡改/缺失/损坏 fail-closed、重复恢复唯一 taskAttemptId（attemptNo 依引用递增）、四标识关联、Redis 中断如实失败且异常不含 Secret（sentinel 脱敏）、不误删共享 Session、受控 Session 删除、共享客户端生命周期、keyPrefix 校验 |
| **已验证（代码/接口实证）** | `agentscope-extensions-redis:2.0.1` 组件与键结构（sources/javap）；`normalizeUser` 仅 null/blank；`AgentStateStore` 三参 delete 为 no-op 默认方法；官方实现不设 TTL |
| **Redis 客户端（评审唯一实现复审 P0）** | **Jedis 7.4.1：VERIFIED**（真实 Redis C-1~C-12，生产验证客户端）；**Lettuce 7.5.2：NOT_VERIFIED**（未来如采用必须单独完成兼容测试并经 ADR 决策；已删除 lettuce 构造入口，禁止 URI 拼接口令） |
| **未验证** | Checkpoint（沙箱快照）对 V1 经营分析的适用性（当前不采用）；真实 Provider 调用（无凭证） |
| **BLOCKED** | Provider 真实验证 = **BLOCKED_BY_CREDENTIAL**（无 DEEPSEEK_*/OPENAI_* 凭证；provider-smoke Profile fail-closed 实证） |

## 2. 官方 API 实证（Maven Central jar + sources，非猜类名）

- `RedisAgentStateStore implements AgentStateStore`（Builder: keyPrefix + jedis/lettuce/redisson）。
- 键结构 `{prefix}{userId}/{sessionId}:{key}`（含 `:list`/`:list:_hash`/`:_keys`），默认前缀
  `agentscope:session:`；**normalizeUser 仅处理 null/blank，不净化路径字符**——键安全来自
  Nexus scoped identity（base64url，DEV-0002）。
- **官方实现不设 TTL**；**三参 `delete(userId, sessionId, stateKey)` 为接口 no-op 默认方法
  （不删除任何数据）；两参 delete 为 Session 级**。
- `DistributedStore`/`RedisDistributedStore`/`RedisSnapshotSpec`/`RedisRemoteSnapshotClient` =
  沙箱快照（Checkpoint，Coding 取向）；V1 经营分析不引入。

## 3. 关键设计落实（P0/P1 修正全部落实 + 唯一实现复审收尾）

1. **跨 Scope（P0）**：`AgentExecutionReference` 增加 tenantId/workspaceId（fail-closed）；
   start 用 scoped 分区保存；resume 按四 scope 字段重算 scoped、从 scoped slot 读取、六字段逐项校验；
   Port 注释明确"恢复请求只能来自 MySQL 授权数据"。
2. **会话误删除（P0）**：不实现 Task 终态自动 Session 清理；不直接 EXPIRE/操作官方内部键；
   Session 删除归独立生命周期服务（后续工作项）；C-9 验证"Task 完成不误删共享 Session 状态"。
3. **数据权威边界**：Redis = 运行时持久化机制；MySQL = 长期权威源；ExecutionContextState = 运行期恢复投影。
4. **唯一实现复审收尾**：
   - **删除 Lettuce 构造入口**（避免未验证且存在 Secret URI 拼接风险）；生产验证客户端仅
     **Jedis 7.4.1（VERIFIED）**；Lettuce 7.5.2 NOT_VERIFIED（未来单独测试 + ADR）；
   - **C-8 增加 sentinel Secret 脱敏断言**（连接失败异常链不含 Secret）；
   - 修复 `agentscope-extensions-redis` 重复依赖（只保留一处 compile）；
   - **Provider 原子配置选择**（A~E 规则，禁止跨 Provider 混配）+ 纯配置单元测试；
   - **C-6 如实口径**：Adapter 每次 resume 产生唯一 taskAttemptId；attemptNo 依传入引用递增；
     同一旧引用重复调用得到相同 attemptNo 但不同 taskAttemptId；真正的并发串行化、幂等与连续
     Attempt 编号由后续 MySQL Task Application Service 负责——本 Adapter 不声称已完成业务 Task 幂等持久化。

## 4. 测试证据

| 测试类 | 用例 | 结果 |
|---|---|---|
| RedisPersistenceRecoveryTest（DEV-0003 新增） | 12（C-1~C-12） | ✅ 真实 Redis（Testcontainers GenericContainer redis:7.4.2；Jedis 7.4.1） |
| ProviderSmokeConfigTest（评审唯一实现复审新增） | 7 | ✅ 原子配置选择（DeepSeek/OpenAI/部分缺失/双组未选择/不混配/不含 Secret） |
| ProviderSmokeTest（provider-smoke Profile 专属） | 1 | BLOCKED_BY_CREDENTIAL（fail-closed 实证：缺凭证 → 测试失败，不冒充通过） |
| 既有 AgentScope 测试（DEV-0001，含 firstAttempt 适配） | 41 | ✅ 无回归 |
| DEV-0002 compatibility-test | 14 | ✅ 无回归 |
| **合计（默认构建）** | **74** | **0 失败** |

`./mvnw clean verify`（JDK 21）：EXIT=0，BUILD SUCCESS，DuplicateJsonObject 警告 0。

**测试环境**：colima Docker + `DOCKER_HOST=unix://~/.colima/default/docker.sock` +
`TESTCONTAINERS_RYUK_DISABLED=true`（colima socket 挂载限制的官方配置规避，与版本无关）。

## 5. 结论与建议状态

- **G-03：PARTIAL**（Redis Agent State Store 行已验证；Provider 行 BLOCKED_BY_CREDENTIAL；
  Checkpoint 行未验证/待评审；不夸大为通过）。
- **OQ-007：Redis 会话持久化/恢复（跨实例）RESOLVED**（决策链接 ADR-0008）；Checkpoint 待评审
  → 状态更新为 **PARTIAL（Redis 部分解决）**，经产品负责人确认后落库 OPEN_QUESTIONS.md。
- **ADR-0008：Proposed**（未批准前不更新 Accepted Blueprint / 00_DECISIONS.md）。
