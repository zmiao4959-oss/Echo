# 小爪 (Echo) — 开发者交接文档

这是 AI 助手参与开发时留下的项目指南。正文涵盖最需要知道的，细节链接到具体文件。
写在最前面的话：你是目前项目的开发者，dev-handover文件夹是一个历届开发者自主维护的文档，你觉得有需要补充到文档里面的信息一定要补充进来，正是大家所有人的贡献，才能创造出不一样的东西！

---

## 一、项目概览

Android AI 伴侣 App，Kotlin + MVVM，minSdk 24，targetSdk 36。

**核心能力：** LLM 对话（OpenAI 兼容，SSE 流式）→ 工具调用（Function Calling）→ 操作本地数据（生活记录/日记/规划/记忆）。

**关键依赖：** OkHttp 4.12（网络）、Gson（序列化）、Coroutines（异步）、Material 3（UI）。

---

## 二、架构分层

```
UI (Fragment/Activity/ViewModel)
  → Agent (系统提示 + 工具调用循环)
    → LLMProvider (OkHttp → OpenAI API)  ← ProviderFactory + 共享 HttpClient
  → Tools (ToolRegistry → 20+ 工具)
    → Repository → FileStore/JsonAtomicWriter → JSON 文件持久化
  → MemoryRepository → UserProfileMemory / MemoryCard / LifeRecord / DailyDiary
  → PlanScheduler / ScheduleEngine → AlarmManager 定时触发
  → diagnostics/ServiceHealth → 失败记录与诊断
```

**每个 Fragment 对应一个 ViewModel + 一个 Adapter（RecyclerView）。** 静态卡片（MaterialCardView）在 Fragment 中直接管理。

**核心数据流：**
- LLM 网络层：`ProviderFactory` → `OpenAICompatProvider`(共享 `HttpClient`) → OkHttp
- 会话持久化：`SessionManager.save()` 原子写入（tmp → rename → copyTo 兜底）
- 结构化记忆：4 层模型（LifeRecord → DailyDiary → UserProfileMemory → MemoryCard），通过 `JsonAtomicWriter` 原子写入
- 文本记忆：`FileStore` 管理 `workspace/MEMORY.md`，通过 `remember_user_fact` 工具写入

详见：[架构详解](./architecture.md)

---

## 三、最关键的事项（请务必先读）

### 3.1 卡片背景系统

项目有**三层背景**，互不冲突：

| 层级 | 管理器 | 应用对象 | 设置入口 |
|------|--------|---------|---------|
| 页面背景 | `BackgroundManager` | Activity 根 View | 设置页 |
| 页面纹理 | `PageTextureManager` | Fragment 根 View | 个人中心→页面纹理 |
| 卡片纹理 | `CardTextureManager` | MaterialCardView | 个人中心→卡片纹理 |

**关键实现细节：**
- `CardTextureManager.apply()` 在 `MaterialCardView` 内插入一个普通 `View` 子元素（tag = `"card_texture_bg"`）并设其 background 为 `CenterCropDrawable`。这样才能承载纹理图片 —— `MaterialCardView` 继承自 `CardView`，内部覆写了 `setBackgroundDrawable()` 为空操作，外部 `card.background = xxx` 会被静默拦截
- `PageTextureManager.remove()` 恢复 `?attr/echoBackground` 颜色，不能只设 null（否则透明露出后面背景）
- 卡片纹理按 5 类分：LIFE_RECORD / DIARY / PLAN / MEMORY / CHAT
- 页面纹理按 6 类分：today_page / diary_page / plan_page / memory_page / top_bar / bottom_bar
- 自定义纹理持久化在 `card_textures.json`，图片存在 `card_textures/` 目录

### 3.2 对话记忆系统

**会话持久化：** JSON 文件存在 `sessions/` 目录。每个 Session 包含 `chatId`、消息列表、metadata。

**关键 bug 已修（不要回退）：**
1. `TodayFragment.enterChat()` 必须复用 `last_chat_id`（SharedPreferences），不能每次生成新 chatId
2. `Session.fromJsonFile()` 必须反序列化 `tool_calls`（不能设 null），否则重进对话后 API 报 400 错误
3. ChatActivity 顶部栏标题可点击 → 对话列表（切换/重命名/删除）

**"记住关于我的信息" 路由：**
- 用户说个人信息 → `remember_user_fact` 工具（写 MEMORY.md）
- 用户说生活片段 → `create_life_record`（写 LifeRecord JSON）
- 用户说"记住这句话" → `create_memory_card`（写 MemoryCard）

详见：[对话系统详解](./chat-system.md)

### 3.3 规划/提醒时间精度

**LLM 不擅长算术。** `create_plan` 工具优先级：
1. **`triggerTime`**（ISO 字符串如 `"2026-06-27T21:00"`）— LLM 最擅长，app 用 `SimpleDateFormat` 解析
2. **`minutesFromNow`** — LLM 算相对分钟数
3. **`triggerAt`** — Unix 毫秒时间戳（最后一招）

`parseTimestamp()` 无效输入返回 null（不静默回退）。`PlanScheduler.schedule()` 拒绝过期时间。

### 3.4 天气系统

- 主页天气条在顶部栏内（`weather_bar`），layout 中居中
- 用 wttr.in JSON API（`/Shanghai?format=j1`），从 `nearest_area[0].areaName[0].value` 取城市名
- 城市在设置页手动填入，存入 `AppConfig.weatherCity`
- 缓存 30 分钟，城市变更立即失效缓存（比对 `weather_cache_city`）

---

## 四、主题与颜色

5 种主题（warm_tea / forest / ocean / twilight / dark），属性定义在 `res/values/attrs.xml`。

**卡片颜色：** `echoSurface`（主卡面）和 `echoSurfaceVariant`（次卡面）。所有卡片用 `app:cardBackgroundColor="?attr/echoSurface"`。

详见：[主题系统](./themes.md)

---

## 五、文件路径速查

| 功能 | 路径 |
|------|------|
| 主入口 | `MyApplication.kt` |
| 主页 + 天气 | `ui/MainActivity.kt` |
| 对话 UI | `ui/ChatActivity.kt` |
| 对话列表 | `ui/ConversationListActivity.kt` |
| Agent 核心 | `agent/Agent.kt` |
| 系统提示构建 | `agent/Agent.kt` → `buildSystemPrompt()` |
| 工具注册 | `tools/ToolRegistry.kt` |
| 卡片纹理管理 | `ui/CardTextureManager.kt` |
| 页面纹理管理 | `ui/PageTextureManager.kt` |
| 记忆管理 | `ui/memory/MemoryManageActivity.kt` |
| 会话管理 | `memory/SessionManager.kt` |
| 会话数据模型 | `memory/Session.kt` |
| 记忆检索 | `memory/MemorySearch.kt` |
| 记忆仓库 | `data/repository/MemoryRepository.kt` |
| LLM 对接 | `llm/OpenAICompatProvider.kt` |
| LLM 工厂 | `llm/ProviderFactory.kt` |
| 共享 HTTP | `net/HttpClient.kt` |
| 全局配置 | `config/AppConfig.kt` |
| 工作区文件 | `memory/FileStore.kt` |
| 规划调度 | `schedule/PlanScheduler.kt` / `schedule/ScheduleEngine.kt` |
| 天气工具 | `tools/WeatherTools.kt` |
| 时间解析 | `domain/TimeParser.kt` |
| 诊断记录 | `diagnostics/ServiceHealth.kt` |
| 原子 JSON | `data/store/JsonAtomicWriter.kt` |
| 测试示例 | `app/src/test/` 下有 5 个测试文件（41 个用例） |
| 验收文档 | `dev-handover/manual-qa.md` |

---

## 六、注意事项与易踩坑

1. **不要用 `java.time.*`** — minSdk 24 不支持。用 `SimpleDateFormat` + `Date()` + `System.currentTimeMillis()`。虽然项目有 desugar，但 lint 会报错。

2. **卡片背景必须通过子 View 注入** — `MaterialCardView` 会拦截外部的 `setBackground()` 调用（`setBackgroundDrawable()` 被重写为空操作）。正确做法是用普通 `View`（tag = `"card_texture_bg"`）作为 `MaterialCardView` 的子元素，用 `addView(bgView, 0)` 插入底层，再将 `CenterCropDrawable` 设为该子 View 的 background。用普通 View 而非 ImageView 可避免 intrinsic size 撑大卡片。

3. **AlertDialog 不支持长按** — 需要用其他方式实现"管理"操作（如点击弹出子菜单）。

4. **SharedPreferences 是单文件** — `clawspeaker_config` 被 `AppConfig` 和部分 Activity 共享，key 命名要避免冲突。

5. **ToolCall 反序列化** — `Session.fromJsonFile()` 中的 `toolCalls` 不能丢，否则带工具调用的对话恢复后 API 报错。

6. **Gson 序列化 Map 类型** — `SerializableSession` 中 `messages` 是 `List<Map<String, Any>>`，从 JSON 反序列化时数字可能是 `Double`，需要 `(value as? Number)?.toLong()` 而非直接 `as? Long`。

7. **Fragment 中 `requireActivity()` 和 `requireView()`** — `onResume` 里用 `requireView()` 没问题（已 attached），但 `onCreateView` 返回前不要调。

8. **`RoundedBitmapDrawable` 的 intrinsic size** — 如果在非卡片场景用了，需要自定义 Drawable 覆盖 `getIntrinsicWidth/Height` 返回 -1。

9. **不要用 `runBlocking` 在 UI 线程** — 会卡界面。异步查数据用 `lifecycleScope.launch` + `withContext(Dispatchers.IO)`。

10. **禁用/待确认的记忆不会自动生效** — `UserProfileMemory.enabled=false` 和 `status="pending"` 的记忆需要 `MemoryRepository.searchAll()` 和 `Agent.buildStructuredProfileInjection()` 显式过滤。

11. **主动陪伴有静默时段** — `AppConfig` 中 `companionQuietStart/End` 控制，`task_reminder` 不受影响。

12. **所有 OkHttpClient 必须从 `HttpClient.instance` 派生** — 不要新建 `OkHttpClient.Builder()`，用 `.newBuilder()` 定制超时。

---

## 七、已完成优化（Phase A-D 摘要）

- **Phase A**: java.time.* 全部替换为 Calendar/SimpleDateFormat；Session 原子写入；卡片纹理实现修正；核心单元测试（41 用例）
- **Phase B**: OkHttpClient 共享单例 + ProviderFactory；调度恢复去重；Agent 循环拆解为 5 个方法
- **Phase C**: Session 原子写入加固（rename 失败 copyTo 兜底）；短超时派生；MemorySearch 中文支持 + 时间加权；系统提示记忆瘦身；诊断面板
- **Phase D**: MemoryManageActivity 记忆管理；置信度/待确认机制；ConversationListActivity 独立对话列表；主动陪伴 MVP（动态问候 + 智能回忆 + 静默控制）

## 八、未来方向建议

- **语义搜索升级** — `MemorySearch` 已支持中文 bigram，下一步可做向量检索
- **记忆摘要器** — 当前用规则版取前 N 行，可升级为 LLM 摘要器生成画像
- **对话列表搜索优化** — 当前是前缀匹配，可加模糊搜索
- **自定义纹理批量管理** — 目前逐个操作，可加多选删除
- **主动陪伴个性化** — 当前问候基于记录数量，可基于用户画像生成更个性化内容
