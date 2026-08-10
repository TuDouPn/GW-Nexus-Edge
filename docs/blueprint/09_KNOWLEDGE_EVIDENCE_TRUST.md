# 09 — Knowledge、Evidence 与可信内容规范

> 状态：Accepted

## 1. Pipeline

```text
ResourceVersion
 → Format Validation
 → Parser
 → Structured Blocks
 → Chunking
 → ACL Projection
 → Embedding Policy
 → Embedding
 → Vector/Keyword Index
 → Knowledge Snapshot
```

每一步保存 processor、version、input hash、status、warnings 和 trace。解析或索引失败不得将部分结果标记为 Ready。

## 2. Parser 基线

### DOCX

提取标题层级、段落、表格、图片引用、页眉页脚/样式元数据和可定位结构。页码若不能稳定从 OOXML 推导，Evidence 使用章节/段落/表格定位，不伪造页码。

### XLSX

提取 Workbook、Sheet、命名区域、单元格值、公式、格式和合并区域。公式缓存值与公式本身分别保存。日期、百分比、货币与空值必须保留类型语义。

### PPTX

提取 Slide、Layout、标题、文本框、表格、图表数据引用、图片引用和演讲者备注。Evidence 使用 Slide 编号和 Shape 定位。

### PDF

只对存在可靠文本层的 PDF 作生产承诺。保存页码、文本块和坐标。未检测到文本层时进入 Unsupported/OCR Required。

## 3. Chunking

- Chunk 以结构边界为先，不按固定字符粗暴切割。
- 表格、标题与正文关系不得拆散。
- 每个 Chunk 保存 parent/previous/next、locator、resource_version、parser_version。
- Chunk 参数按文档类型版本化，并通过检索 Eval 调整。
- 不在文档中硬编码唯一 Token 大小；实现必须有上限和重叠策略。

## 4. Retrieval

执行顺序：

1. Tenant/Workspace/ACL/Data Policy 预过滤。
2. Query normalization 与必要的检索计划。
3. Keyword + Vector 候选召回。
4. 去重和版本过滤，只使用 Snapshot 中版本。
5. Rerank。
6. Context Policy 最小化。
7. Evidence Registry 登记后注入 AgentScope。

禁止先召回无权数据再在应用层过滤。

## 5. Embedding Policy

- L1：管理员批准后可外部 Embedding。
- L2：默认禁止外部；管理员明确授权可信 Provider 后允许。
- L3：只允许企业内网 Embedding。
- Embedding Request 记录 tenant、workspace、resource versions、data level、provider、model/version、policy version、trace 和结果。
- 不在审计中复制全文。
- Embedding 模型变化产生新 Index Generation，禁止静默覆盖旧向量。

## 6. Evidence 类型

| 类型 | 必需字段 |
|---|---|
| SOURCE_FACT | claim、resourceVersionId、locator、excerptHash、verifiedText |
| CALCULATION | metric、inputRefs、formula、result、toolVersion、rounding |
| INFERENCE | conclusion、supportingEvidenceIds、confidence、limitations |
| USER_CONFIRMED | conflict/question、selectedValue、actor、time、reason |
| OCR | resourceVersion、page、engine/version、confidence、confirmation status |

## 7. Claim 规则

- 关键数字只能来自 Calculation 或直接 Source Evidence。
- 关键事实必须定位到固定 ResourceVersion。
- 引用展示可用文件名与位置，但内部使用不可变 ID/Hash。
- Inference 不得伪装成事实，必须使用“可能、推断、基于现有资料”等清晰标识。
- 置信度不是事实证明；没有 Evidence 时高置信度也不得发布。

## 8. 冲突

同一指标/事实存在不同值时：

1. 创建 Conflict Record。
2. 展示所有来源、版本、位置和值。
3. 应用管理员配置的来源优先级只能形成建议，不得隐式删除其他证据。
4. 允许授权用户确认口径，形成 USER_CONFIRMED Evidence。
5. 未确认时报告必须标记“待确认”，不得选择一个值冒充事实。

## 9. 证据不足

系统应输出已有资料、缺失资料、无法确认的结论和建议补充项。不得用行业常识补写企业事实。

## 10. Trust Gate

发布前计算：Evidence coverage、calculation validation、conflict count、unsupported claims、retrieval trace completeness。Gate 规则：

- unsupported critical claim > 0：阻止。
- invalid calculation > 0：阻止。
- unresolved critical conflict > 0：阻止或要求显式“待确认”并经业务审核。
- Evidence locator 不可解析：阻止对应 Claim 成为关键事实。

## 11. Eval

- Retrieval：Recall@K、MRR/nDCG、ACL leakage=0。
- Evidence：关键 Claim 覆盖率、Locator 正确率、虚构引用率=0。
- Calculation：与 Golden Result 对比，关键指标精确匹配或按口径容差。
- Report：业务评分、结构完整度、建议可用性和人工修改时间。

