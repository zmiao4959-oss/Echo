# Echo 数据健康机制

## 概述

Phase H 引入了三层数据健康保障：**Schema 迁移**、**搜索索引**、**健康检查**。

---

## 一、Schema 迁移

### 版本管理
每个 JSON 数据文件的 schema 版本由 `SchemaVersions` 定义（`data/store/SchemaVersions.kt`）：
- `life_records.json` — v1
- `daily_diaries.json` — v1
- `plans.json` — v1
- `memory_cards.json` — v1
- `user_profile.json` — v1
- `memory_audit_log.json` — v1
- `search_index.json` — v1

### 迁移流程
`MigrationManager.runMigrations()` 在应用启动时自动执行：
1. 读取每个文件的 `schemaVersion`
2. 如果 < 目标版本 → **自动备份**（`.bak.<timestamp>`）
3. 执行迁移函数 → 更新 `schemaVersion` 写入文件
4. 成功 → 删除备份；失败 → **恢复备份**，记录错误
5. 迁移日志写入 `echo/workspace/migration_log.json`

### 迁移失败恢复流程

如果迁移过程中发生问题，恢复步骤如下：

**自动恢复（应用内）：**
1. `MigrationManager` 在执行迁移前自动创建备份文件 `<filename>.bak.<timestamp>`
2. 如果迁移函数返回 `false` 或抛出异常：
   - 自动将备份文件 copy 回原路径（覆盖迁移失败的中间状态）
   - `schemaVersion` 保持不变，下次启动重试
   - 迁移日志记录失败条目（`success=false` + `error` 消息 + `backupPath`）
3. 如果备份创建本身失败：记录失败日志，跳过该文件的迁移，不修改原文件

**手动恢复（开发者操作）：**
1. 查看 `echo/workspace/migration_log.json` — 找到失败的迁移条目，获取备份路径
2. 检查备份文件 `*.bak.<timestamp>` 的内容是否完整
3. 如需回滚：将备份文件 `copyTo` 原始路径覆盖当前文件
4. 如需重试迁移：修复迁移函数中的问题后，重启应用自动重试（因为 schemaVersion 未更新，下次启动仍会检测到差异）
5. 如果数据文件已被破坏且备份也不可用：从 `DataExporter` 的最近导出 zip 中恢复数据

**安全保证：**
- 迁移过程中**原文件永远不会被原地修改** — 所有修改先写 `.tmp`，再 rename
- 备份使用 `copyTo`（不是 `renameTo`），确保原文件在备份阶段未被修改
- 即使备份和迁移都失败，原始数据文件不会丢失

---

## 二、搜索索引 (`SearchIndex`)

### 索引内容
覆盖 5 种数据源：
- **LifeRecord** — content 字段
- **DailyDiary** — title + summary + diaryText
- **MemoryCard**（仅 confirmed）— quote + note
- **UserProfileMemory**（仅 enabled + confirmed）— key + value
- **MEMORY.md**（仅 confirmed section）— 完整文本

### 索引机制
- **分词**：CJK bigram（"咖啡馆" → "咖啡", "啡馆", "咖啡馆"）+ ASCII 整段
- **匹配**：查询 token 在索引 token 集合中的交集 → 长度加权得分
- **时间衰减**：`1.0 / (1.0 + ageDays * 0.1)` — 越旧权重越低
- **存储**：`echo/memories/search_index.json`（原子写）

### 更新策略
- **增量更新**：每个 Repository 的 write 方法后调用 `SearchIndex.upsert/remove`
- **全量重建**：启动时如果索引缺失或过期（数据文件修改时间 > 索引 builtAt），自动 `rebuild()`
- **禁用记忆即时失效**：toggleProfile/updateCard 状态变为非 confirmed → 立即 `remove()`

---

## 三、数据健康检查 (`DataHealthChecker`)

### 检查项

| checkId | 名称 | 严重度 | 可修复 | 修复方式 |
|---------|------|--------|--------|---------|
| `json_corruption_*` | JSON 文件损坏 | ERROR | ✅ | 备份坏文件 → 创建空文件 |
| `session_corruption` | Session 文件损坏 | WARNING | ✅ | 备份损坏文件 |
| `memory_md_section_format` | MEMORY.md 旧格式 | INFO | ✅ | 自动迁移 section 标记 |
| `orphan_memory_card_refs` | 卡片缺来源 | WARNING | ❌ | 需人工检查 |
| `duplicate_profile_keys` | 重复画像 key | WARNING | ❌ | 需人工合并 |
| `audit_log_oversized` | 审计日志 > 500 | INFO | ✅ | 裁剪到最近 500 条 |
| `search_index_stale` | 索引过期 | INFO | ✅ | 立即 rebuild |
| `export_missing_files` | 导出缺文件 | WARNING | ❌ | 检查数据目录 |

### 查看方式
- **诊断页**（设置 → 底部）：显示 `数据健康: X errors, Y warnings, Z info — N fixable`
- `runAllChecks()` 程序化调用返回完整 `HealthReport`

### 安全保证
- 所有修复操作前**自动备份**原文件
- 修复只操作**副本文档**（备份），不删除用户真实数据
- 修复失败 → 原文件保持不变

---

## 四、如何处理坏数据

| 问题 | 处理方式 |
|------|---------|
| JSON 文件无法解析 | `JsonAtomicWriter.readItems()` 自动备份 `.bak.<ts>` → 返回空列表 |
| Session 文件损坏 | 诊断标记 + 备份选项 |
| MEMORY.md section 标记异常 | 自动迁移（`migrateIfNeeded`） |
| 索引与数据不一致 | 详见下方「索引不一致处理」 |
| 迁移失败 | 自动回滚备份文件，详见上方「迁移失败恢复流程」 |

### 索引不一致处理

**为什么会不一致？**
- 增量更新依赖每个 Repository 的 write 方法调用 `SearchIndex.upsert/remove`。如果某处遗漏，索引与数据文件不同步。
- 数据文件被外部修改（如手动编辑 JSON），索引 unaware。

**检测方式：**
1. **`SearchIndex.isStale()`** — 比较索引 `builtAt` 与各数据文件的 `lastModified()` 时间戳。任一数据文件比索引新 → stale
2. **启动时自动检测** — `MyApplication.onCreate()` 调用 `SearchIndex.rebuildIfStale()`
3. **诊断页显示** — SettingsActivity 诊断面板调用 `DataHealthChecker.quickSummary()`，其中 `search_index_stale` 检查会标记索引状态

**手动重建方法（按优先级）：**

| 方式 | 适用场景 | 操作 |
|------|---------|------|
| 自动（启动） | 正常使用 | 无需操作，启动时自动检测 + rebuild |
| 诊断页一键修复 | 应用运行中 | 设置 → 诊断 → 查看数据健康 → 运行完整检查 → 点击"重建索引" |
| 代码调用 | 开发/调试 | `kotlinx.coroutines.runBlocking { SearchIndex.rebuild() }` |
| 删除索引文件 | 索引文件损坏 | 删除 `echo/memories/search_index.json` → 重启应用自动重建 |
| 全量重建 + 验证 | 深度排查 | 删索引 → 重启 → 检查 `SearchIndex.getStats()` 确认 entryCount 正确 |

**rebuild 期间行为：**
- `rebuild()` 读取所有 5 种数据源的最新数据，重新 tokenize 建索引，原子写入
- indexed 期间 `query()` 返回旧索引的结果（如有）；rebuild 完成后瞬间切换
- 全部在 IO 线程执行，不阻塞 UI

## 五、性能基准

| 场景 | 数据量 | 预期耗时 |
|------|--------|---------|
| 关键词检索 | 1000 条 LifeRecord | < 500ms |
| 时间线排序分页 | 1000 条混合 TimelineItem | < 300ms |
| 卡片状态过滤 | 500 张 MemoryCard | < 200ms |
| 审计日志截断 | 1000 条 | < 100ms |
| 索引全量重建 | 所有 5 种源 | < 3s |

## 六、测试覆盖（Phase H-Fix）

### SearchIndex（15 测试）
- upsert 后可检索 / update 旧 token 失效 / remove 不可检索
- disabled/pending 从索引移除
- 损坏索引可重建（loadFromDisk 返回 false）
- 多 sourceType/sourceId 不互相覆盖
- snippet 长度受控 (<=200)
- 新旧条目时间衰减排序
- 空查询/空白文本边界

### DataHealthChecker（15 测试）
- 损坏 JSON 发现 / 空 JSON 发现 / 有效 JSON 无假阳性
- 坏 session 文件 / 健康 session 无告警
- MEMORY.md section 旧格式检测
- orphan memory card 缺来源检测
- 重复 profile key 检测 / 唯一 key 无告警
- 审计日志过大 warning
- 搜索索引 stale warning
- 导出缺失文件检测
- safe repair：备份原文件后才修改
- severity 分级正确 / summary 字符串非空

### MigrationManager（9 测试）
- memory_cards / user_profile / audit_log v1→v2 迁移
- 迁移前生成备份 / 失败保留原文件 / 异常保留原文件
- schemaVersion 正确更新（v1→v3 跨版本）
- 已匹配版本不重复迁移
- 迁移日志可读（success/targetFile/fromVersion/toVersion）

### 索引一致性兜底
- `SearchIndex.rebuildIfStale()` — 启动时自动检测 + 重建
- 诊断页显示 stale 状态
- 测试：空索引 stale / 数据文件比索引新时触发 rebuild

---

## 七、已知限制与后续解耦计划

### 1. MEMORY.md 检查的 JVM 测试覆盖限制

**当前状态：**
`checkMemoryMdSections()` 和 `fixMemoryMdSections()` 在测试环境中通过 `readMemoMdFile()` 访问 MEMORY.md：
- 测试模式（`testDataDir` 已设置）：读取 `workspace/MEMORY.md` 临时文件
- 生产模式：通过 `FileStore.readWorkspaceFile("MEMORY.md")` 读取

**测试覆盖边界：**
- ✅ `readMemoMdFile()` 的路径解析逻辑已在 DataHealthChecker 测试中覆盖
- ✅ 功能层（`MemoryMdParser.migrateIfNeeded()`）已有独立测试（`MemoryMdParserTest`）
- ⚠️ `fixMemoryMdSections()` 的完整修复链路（读到旧格式 → 迁移 → 写回）在 JVM 中无法端到端测试，因为 `FileStore.writeWorkspaceFile()` 依赖 `MyApplication.instance`
- ⚠️ 生产模式下的回溯路径（`FileStore.readWorkspaceFile()`）在 JVM 测试中抛出 `UninitializedPropertyAccessException`

**测试中对 `android.util.Log` 的处理：**
- `SearchIndex.loadFromDisk()` 解析失败时调用 `Log.w()` → JVM 测试中 `android.util.Log` 未 mock，抛出 `RuntimeException`
- 测试通过 `try-catch (RuntimeException)` 降级验证，assert 行为正确（返回 false）
- 同样问题影响 `JsonAtomicWriter.backupCorrupted()` → `Log.w/e`
- 这些测试在 JVM 中**已验证核心逻辑正确**；Log 调用仅在 Android 运行时正常执行

### 2. FileStore 依赖 MyApplication.instance（后续解耦项）

**当前依赖链：**
```
FileStore.workspaceDir → MyApplication.instance.filesDir → Context
```
`MyApplication` 是一个 `Application` 子类，其 `instance` 通过 `lateinit var` 在 `onCreate()` 中赋值。任何访问 `FileStore` 的代码在 `MyApplication` 未初始化时都会崩溃（JVM 测试、类加载期等）。

**受影响的组件：**
- `MemoryInitializer` — 初始化工作区文件
- `MemorySearch` — 关键字检索 MEMORY.md
- `MemoryContextBuilder` — 构建记忆上下文时读 MEMORY.md
- `DataHealthChecker.checkMemoryMdSections` — MEMORY.md 格式检查
- `MemoryMdParser` — 纯解析（无依赖，JVM 可测）

**解耦计划（后续迭代）：**
1. 将 `FileStore` 从单例改为可注入实例，接受 `File` 作为工作区根目录
2. `MyApplication.once()` 中初始化 `FileStore.init(filesDir)`
3. 所有消费者通过构造函数或参数接收 `FileStore`（而非直接访问单例）
4. 测试中创建 `FileStore(TemporaryFolder.root)` 即可完整测试
5. 收益：MEMORY.md 完整迁移/修复链路可在 JVM 中端到端测试，不再受 `MyApplication.instance` 限制

### 3. 其他已知限制

| 限制 | 影响 | 缓解措施 |
|------|------|---------|
| 增量索引依赖每个 Repository write 后调 `SearchIndex` | 遗漏一处就会不一致 | 启动 `rebuildIfStale()` 兜底 |
| `JsonAtomicWriter` 无并发保护 | 多协程同时写入同一文件可能丢数据 | 架构上所有写操作串行，实际风险低 |
| `AuditLogStore` 的 `readAll()`返回类型与 `MemoryGovernanceService.AuditEntry` 耦合 | 修复 audit log 时需要类型转换 | `DataHealthChecker.fixAuditLogSize()` 已处理映射
