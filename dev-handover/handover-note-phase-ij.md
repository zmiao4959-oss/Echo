# 接力笔记 — Phase I~J

接手人你好。我是负责 Phase I（语义检索预研）和 Phase J（可控语义检索落地）的开发者。上一任把 `SearchIndex` + `MemoryRetrievalPolicy` 的关键词检索做到了 251 测试的基线，我的任务是在不破坏这个基线的同时，铺一条语义检索的路。

---

## 我做了什么

**Phase I — 接口铺路：**
- 定义了 `RetrievalEngine` 统一接口：`RetrievalRequest` → `List<RetrievalResult>`
- 三个引擎实现：`RuleBasedRetrievalEngine`（包装旧逻辑）、`SemanticRetrievalEngine`（no-op fallback）、`HybridRetrievalEngine`（合并去重 + 三模式路由）
- `EmbeddingProvider` 接口 + `EmbeddingCacheEntry` 缓存数据结构
- `DoubaoEmbeddingProvider`：OkHttp 调火山豆包 `POST /api/v3/embeddings/multimodal`，endpoint 坑很多（下划线/连字符/数组→对象，踩了三轮才接通）
- 设置页 Spinner：`rule_only` / `hybrid` / `semantic_experiment`

**Phase I-Fix — 接通链路：**
- `MemoryContextBuilder` 接入 `HybridRetrievalEngine`，`AppConfig.retrievalMode` 真实切换
- `semantic_experiment` 空时自动回退规则结果
- Embedding API 失败写 `ServiceHealth` 诊断 + 回退规则检索，不打断聊天
- 去掉 `memoryPrefix`（用户消息里的 `[Memory Search Results]` 前缀），消除与 System Prompt 的重复

**Phase I-RC — 提示词整理：**
- 修复 `IDENTITY.md`/`SOUL.md`/`AGENTS.md` 从未被加载到 System Prompt 的 bug（`buildStaticSystemPrompt()` 定义了但没被调用）
- 去掉 `echo_profile.md` 加载（内容与 SOUL.md + AGENTS.md 完全重复）
- `WORKSPACE_VERSION` 升到 4，精简四个文件各司其职：SOUL（身份+性格）、AGENTS（工具规则）、IDENTITY（元信息+TTS）、USER（占位）
- Runtime Info 加了星期几 + 自然语言时间（"周日 下午3点30分"）
- 对话每轮自动 dump 完整上下文到 `workspace/_last_prompt.md`，App 内可直接查看

**Phase J — 语义检索落地：**
- `EmbeddingCacheStore` 重写：LRU 1000 条、model/provider 隔离、textHash 检测变更、summary 存短摘要不存原文、文件损坏自动重建
- `HybridRankingPolicy`：多因素排序（exact match boost + source weight + pinned + recency），规则 exact match 优先于弱语义相似
- `SensitiveContentFilter`：身份证/手机号/API Key/邮箱/地址/医疗/财务 正则过滤，不发送给 Embedding API
- `DataHealthChecker` 第 9 项检查：embedding cache 过大/损坏可一键清空
- `remoteSemanticEnabled` 开关（默认 true），关闭后即使配了 API Key 也不调远程
- 30 个新增 JVM 测试，353 测试基线

---

## 你需要注意的坑

### 1. 豆包 Embedding API 的 endpoint 长这样

不是 `/embeddings`，不是 `/multimodal_embeddings`（下划线），不是 `/multimodal-embeddings`（连字符）。**是 `/embeddings/multimodal`**。响应里 `data` 是对象不是数组：`data.embedding` 直接拿向量。别踩我踩过的坑。

### 2. 语义检索三层回退，别拆

```
DoubaoEmbeddingProvider.embed() 异常
  → EmbeddingException.recordDiagnostics()  写诊断
  → SemanticRetrievalEngine catch → null   跳过该候选项
  → HybridRetrievalEngine 检测语义结果为空 → 回退 ruleEngine
```

任何一环出问题都会回退到规则检索，不会打断聊天。加了新的 error case 保持这个链条。

### 3. `remoteSemanticEnabled` 默认 true，但检索模式默认 rule_only

所以用户不手动切到 hybrid/semantic_experiment 就不会触发语义检索。两个开关是 AND 关系：
- `retrievalMode` → 控制检索路径
- `remoteSemanticEnabled` → 控制是否创建 Embedding Provider

### 4. `EmbeddingCacheStore` 的 model/provider 隔离

缓存文件存了 `model` 和 `provider` 字段。`load()` 会过滤掉不匹配的旧条目。如果用户换了 embedding 模型（比如从 `doubao-embedding-vision-251215` 换成新模型），旧缓存自动失效但不会报错。

### 5. `_last_prompt.md` 在工作区文件里可见

App 内「工作区文件」列表能看到。每次对话覆盖写入，是被我当作调试入口用。用户可能会看到里面的内容（完整 System Prompt + 消息历史），**注意别在 prompt 里塞敏感信息**。导出数据时也会包含它。

### 6. JVM 测试的 android.util.Log 问题（上一任也提过）

`EmbeddingCacheStore.cacheFile()` 里 `Log.w()` 在 JVM 测试中会炸。`DataHealthChecker.checkEmbeddingCache()` 加了 try-catch 兜底。新加代码如果走 `EchoFileStore` / `FileStore` 的路径，测试里注意。

---

## 我认为下一步该做什么

按优先级：

1. **真实 Embedding API 实测**（最高优先级）— 当前评估全是 fake provider 模拟。拿一个豆包 API Key，跑 20 条真实查询，对比 rule_only vs hybrid 的召回率差异，数据写进 `retrieval-eval.md`。当前不知道真实语义质量到底如何。

2. **Embedding 缓存淘汰补充删除逻辑** — 当前删除数据后缓存条目残留（幽灵条目），不会出错但占容量。加一个 `evictStale(currentCandidateKeys)` 在 save 前清理。

3. **HybridRankingPolicy 接真实 pin/recency 标志** — 当前从 explain 字符串里检测"置顶"/"最近"，只靠字符串匹配。上层 MemoryContextBuilder 传 SourceHit 时带了 pinned/ageDays 字段，HybridRetrievalEngine 合并时应该保留这些结构化的元信息而不是丢给 explain。

4. **敏感过滤器补分隔符变体** — 手机号 `138-1234-5678`、`+86138...`、空格分隔等格式目前拦不住。

5. **Phase I 设计的语义缓存预热** — `SemanticRetrievalEngine.loadData()` 后可以异步预计算所有候选 embedding（而不是等到检索时才逐个算），首次检索延迟会明显降低。

6. **提示词继续迭代** — `_last_prompt.md` 现在是很好的调试入口。每次改完提示词后看这个文件确认效果。SOUL.md / AGENTS.md 可以进一步根据真实对话效果微调。

---

## 文档入口

- `dev-handover/README.md` — 项目总览（已更新 Phase I + I-Fix + J）
- `dev-handover/retrieval.md` — 检索引擎架构 + Embedding API 接入步骤
- `dev-handover/retrieval-eval.md` — 语义检索评估报告（fake provider 小样本）
- `dev-handover/handover-note-phase-gh.md` — 上一任的 Phase G~H 笔记（测试/索引/迁移/语音）
- `workspace/_last_prompt.md` — 运行时生成，最近一次对话的完整上下文

353 测试，26 测试文件。记得按照 README.md 的约定：**你觉得有需要补充的信息一定要补充进来**。
