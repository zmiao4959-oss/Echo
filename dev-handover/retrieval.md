# 检索引擎架构 — Phase I + I-Fix

## 当前状态

**默认行为未改变。** 检索模式默认 `rule_only`，与 Phase I 前行为完全一致。

**Phase I-Fix 后：**
- `MemoryContextBuilder` 已接入 `HybridRetrievalEngine`，设置页切换检索模式真实生效。
- 语义检索已接入豆包 Embedding API（`DoubaoEmbeddingProvider`），需在设置页配置 API Key。
- 未配置 Embedding API 时，semantic 自动 no-op，`semantic_experiment` 模式自动回退规则结果。
- 非 `rule_only` 模式在设置页显示语义服务状态提示。

## 架构概览

```
Agent.processMessageStream()
  → MemoryContextBuilder.build()
    → 收集数据（repos + SearchIndex）
    → 加载引擎（RuleBasedRetrievalEngine + SemanticRetrievalEngine）
    → HybridRetrievalEngine.retrieve()  ← AppConfig.retrievalMode 控制
      ├── RuleBasedRetrievalEngine (规则引擎 — 兜底)
      │     └── MemoryRetrievalPolicy + SearchIndex
      └── SemanticRetrievalEngine (语义引擎 — 实验)
            └── DoubaoEmbeddingProvider → 豆包 multimodal_embeddings API
```

## 检索模式

| 模式 | 配置值 | 行为 | 适用场景 |
|------|--------|------|---------|
| 规则检索（默认） | `rule_only` | 仅规则引擎，与 Phase I 前一致 | 生产环境 |
| 混合检索 | `hybrid` | 规则优先 + 语义补充，去重合并 | 开发体验 |
| 语义实验 | `semantic_experiment` | 仅语义引擎，语义空时自动回退规则结果 | 效果评估 |

设置入口：设置页 → 检索模式（开发者）→ Spinner 选择。

**切换后即时生效**（保存设置时调用 `MyApplication.refreshEmbeddingProvider()`，下一轮对话使用新模式）。

非 `rule_only` 模式：
- 显示隐私/费用警告
- 显示语义服务状态：已配置 (模型名) 或 "将自动回退规则检索"

## Embedding API 配置（豆包）

设置页 → Embedding API 配置（豆包）：

| 字段 | 说明 | 默认值 |
|------|------|--------|
| API Key | 火山引擎 API Key | 空（不启用） |
| Base URL | multimodal_embeddings 端点 | `https://ark.cn-beijing.volces.com/api/v3` |
| Model | Embedding 模型 | `doubao-embedding-vision-251215` |

未配置 API Key 时，`DoubaoEmbeddingProvider` 不创建，语义引擎退回 no-op。

## 接口定义

### RetrievalEngine

统一检索引擎接口，位于 `policy/RetrievalEngine.kt`。

```
输入：RetrievalRequest(query, limit, sourceTypes?)
输出：List<RetrievalResult>
```

每条 `RetrievalResult` 包含：
- `sourceType` — 数据来源类型（profile/memory_card/memory_md/life_record/diary）
- `sourceId` — 来源唯一标识
- `snippet` — 匹配片段（≤200 字符）
- `explain` — 人类可读的匹配解释
- `score` — 归一化分数/置信度
- `timestamp` — 来源时间戳（毫秒），可为 null

### 引擎实现

| 文件 | 类 | 说明 |
|------|-----|------|
| `policy/RetrievalEngine.kt` | `RetrievalEngine` | 接口定义 |
| `policy/RuleBasedRetrievalEngine.kt` | `RuleBasedRetrievalEngine` | 规则引擎，包装 MemoryRetrievalPolicy |
| `policy/SemanticRetrievalEngine.kt` | `SemanticRetrievalEngine` | 语义引擎，实验层 |
| `policy/HybridRetrievalEngine.kt` | `HybridRetrievalEngine` | 混合引擎，路由+去重+统一过滤 |
| `llm/DoubaoEmbeddingProvider.kt` | `DoubaoEmbeddingProvider` | 豆包 Embedding API 适配器 |
| `memory/MemoryContextBuilder.kt` | `MemoryContextBuilder` | 统一记忆注入入口（Phase I-Fix 接入引擎） |

所有引擎位于 `policy/` 包，纯 Kotlin，零 Android 依赖，JVM 可测。

## 记忆治理过滤永远优先

**confirmed/enabled/disabled/pending 过滤保证：**

1. 规则引擎：`MemoryContextBuilder` 在 `collectAllHits()` 时只收集 confirmed + enabled 数据
2. 语义引擎：`collectAllCandidates()` 只收集 confirmed + enabled 数据；`retrieve()` 内部再次 `filterConfirmedEnabled()`
3. 混合引擎：`retrieve()` 中统一过滤

**任何新引擎接入时必须遵守此合约。**

## 接入 Embedding API 的步骤

### 已完成（Phase I-Fix）

1. ✅ `DoubaoEmbeddingProvider` — OkHttp → 豆包 multimodal_embeddings API
2. ✅ 设置页 Embedding API 配置（API Key + URL + Model）
3. ✅ 检索模式真实切换（保存→刷新→下一轮生效）
4. ✅ `semantic_experiment` 空时自动回退规则结果
5. ✅ `EmbeddingCacheEntry` 缓存数据结构

### 待完成

1. 缓存持久化到 `embedding_cache.json`
2. 速率限制 + 重试机制
3. A/B 对比规则检索 vs 语义检索的召回率
4. 用户可感知的语义检索质量指标

## 测试

48 个 JVM 测试（Phase I 41 + I-Fix 7），覆盖：
- 接口合约（所有字段存在 + 默认值）
- 规则引擎（排序/评分/过滤/explain/limit/sourceTypes）
- 规则引擎与 MemoryRetrievalPolicy 一致性
- 语义 no-op 不报错
- disabled/pending 过滤
- Embedding 缓存 roundtrip
- 余弦相似度数学正确性
- 混合去重（规则优先）
- 三种模式切换正确
- explain 不丢失
- 默认 mode 为 rule_only
- **I-Fix3**: semantic_experiment 无 provider 时回退规则
- **I-Fix3**: disabled/pending 在三种模式下均排除
- **I-Fix3**: explain/source 在三种模式下均保留
- **I-Fix3**: hybrid 合并去重

全部测试在 `test/.../policy/RetrievalEngineTest.kt`。
