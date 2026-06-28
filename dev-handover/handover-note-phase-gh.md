# 接力笔记 — Phase G~H-Fix

接手人你好。我是负责 Phase G 到 H-Fix 的开发者。以下是代码之外你需要知道的东西。

---

## 我做了什么

**Phase G — 长期成长体验（~6 个新页面/组件）：**
- 成长轨迹页：`GrowthTimelineActivity` + ViewModel + Adapter + `TimelineItem` 密封类（6 种子类型），类型筛选 chip，倒序分页
- 记忆沉淀策略：`MemoryConsolidationPolicy`（纯 Kotlin object，规则版主题提取 + 实体检测 + 情绪趋势，confidence 上限 0.6）
- 周回顾产品化：高光/低谷片段、Echo 克制总结、保存为日记/卡片
- 记忆治理中心升级：筛选 chip + 搜索 + audit log 展示 + 禁用恢复 + 记忆来源详情弹窗
- 数据导出增强：新增 audit log / weekly review / timeline 到导出 zip + README.txt

**Phase G-Fix — 语音便签（重写）：**
- 从 Google SpeechRecognizer → MediaRecorder 本地录音（m4a/AAC）
- 从自动转录 → 用户先录音再点"记录"提交
- 从今日页不可听 → 成长轨迹也能播放（MediaPlayer）
- 今日片段倒序（`sortedByDescending { it.createdAt }`）

**Phase H — 性能护城河：**
- 成长轨迹分页：PAGE_SIZE=30，RecyclerView scrollListener 懒加载
- 轻量搜索索引：`SearchIndex`（CJK bigram + token 匹配 + 时间衰减，5 种数据源，增量更新 + 全量重建）
- 数据健康检查：`DataHealthChecker`（8 项检查 info/warning/error 三级 + 一键修复）
- Schema 迁移：`SchemaVersions` + `MigrationManager`（启动备份 → 迁移 → 日志，失败回滚）
- 4 个 Repository 加了 `SearchIndex.upsert/remove` 钩子

**Phase H-Fix — 测试补齐（+39 测试）：**
- `SearchIndexTest`（15 测试）、`DataHealthCheckerTest`（15 测试）、`MigrationManagerTest`（9 测试）
- `rebuildIfStale()` 索引一致性兜底
- 251 测试 / 24 文件

---

## 你需要注意的坑

### 1. JVM 测试中 android.util.Log 会炸

任何代码路径触到 `android.util.Log.*` 在 `app/src/test/` 的 JVM 测试中都会抛 `RuntimeException: Method w in android.util.Log not mocked`。我的处理方式是在测试里 `try-catch (RuntimeException)` 降级验证。如果你要加新测试涉及 `SearchIndex.loadFromDisk()` / `JsonAtomicWriter.backupCorrupted()` / `AuditLogStore` 的故障路径——注意这一点。

### 2. MyApplication.instance 是 JVM 测试杀手

`FileStore`、`EchoFileStore`、`AuditLogStore` 的路径解析都经过 `MyApplication.instance.filesDir`。纯 JVM 测试中这个 `lateinit var` 没初始化 → `UninitializedPropertyAccessException`。我在 `DataHealthChecker`、`SearchIndex`、`MigrationManager` 里加了测试注入点（`setTestDataDir` / `setTestIndexFile` / `setTestLogDir`），但还有很多组件没覆盖。后续如果要大规模测试，最该做的事是把 `FileStore` 从单例改成可注入——见 `data-health.md` §7.2。

### 3. 索引一致性靠两个东西兜底

`SearchIndex` 的增量更新依赖每个 Repository 的 write 方法后调 `upsert/remove`。如果以后加新的 Repository 或新的数据路径，**一定要加索引钩子**，否则索引会漏。启动时的 `rebuildIfStale()` 是最后兜底——比较 `builtAt` 和各数据文件的 `lastModified()`。

### 4. 语音便签的音频文件在 `echo/records/audio/`

`LifeRecord.audioPath` 存绝对路径。`TodayRecordAdapter` 和 `GrowthTimelineAdapter` 都通过检查 `File(audioPath).exists()` 决定是否显示播放按钮。如果以后加数据迁移或清理逻辑，注意音频文件不能丢。

### 5. 不要破坏 TimelineItem 的倒序排序

`GrowthTimelineViewModel.buildTimeline()` 返回 `sortedByDescending { it.timestamp }`。任何新增 TimelineItem 子类型都按此排序。分页逻辑依赖这个顺序——`loadMore()` 按 timestamp 降序追加。

---

## 我认为下一步该做什么

按优先级：

1. **FileStore / EchoFileStore 解耦**（最高优先级）—  当前它们是硬依赖 `MyApplication.instance` 的单例，导致大量测试无法写。改成可注入后，MEMORY.md 修复链路、索引重建等都能在 JVM 完整测试。

2. **向量检索 / 语义搜索** — `SearchIndex` 现在是关键词匹配（CJK bigram + 时间衰减），覆盖面够用但召回率有限。下一步可以加 embedding 向量检索（不需要换掉现有索引，叠加就行）。数据集够大时这是体验提升最大的点。

3. **搜索索引覆盖会话内容** — 目前 5 种数据源里没有 Session。用户在对话里说过的重要信息（通过工具调用存储的除外）不在索引里。如果要加，Session 内容 tokenize 后进索引即可。

4. **增量索引的性能优化** — 每次 `upsert` 都全量写到磁盘（`writeToFile` 写全部 entries）。数据量大了以后考虑分段写或异步批处理。

5. **数据清理/归档** — 当前没有自动清理逻辑。LiveRecord 无限增长，audit log 只做了截断。考虑加：30 天前的记录自动归档、90 天前的记录可选清理。

---

## 文档入口

- `dev-handover/README.md` — 项目总览 + 架构 + 关键坑 + 文件路径速查
- `dev-handover/architecture.md` — 架构详解
- `dev-handover/chat-system.md` — 对话系统（session/tool_calls/流式）
- `dev-handover/themes.md` — 主题与颜色
- `dev-handover/data-health.md` — 索引/迁移/健康检查/已知限制
- `dev-handover/manual-qa.md` — 人工验收步骤（1-26 节）

记得按照 README.md 开头的约定：**你觉得有需要补充的信息一定要补充进来**。
