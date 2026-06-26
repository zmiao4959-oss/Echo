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
    → LLMProvider (OkHttp → OpenAI API)
  → Tools (ToolRegistry → 20+ 工具)
    → Repository → FileStore/JSON 文件持久化
```

**每个 Fragment 对应一个 ViewModel + 一个 Adapter（RecyclerView）。** 静态卡片（MaterialCardView）在 Fragment 中直接管理。

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
- `CardTextureManager.apply()` 用自定义 `CenterCropDrawable` 设为 `card.background`，**不注入 ImageView 子 View**（否则图片 intrinsic size 会把卡片撑大）
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
| Agent 核心 | `agent/Agent.kt` |
| 系统提示构建 | `agent/Agent.kt` → `buildSystemPrompt()` |
| 工具注册 | `tools/ToolRegistry.kt` |
| 卡片纹理管理 | `ui/CardTextureManager.kt` |
| 页面纹理管理 | `ui/PageTextureManager.kt` |
| 会话管理 | `memory/SessionManager.kt` |
| 会话数据模型 | `memory/Session.kt` |
| LLM 对接 | `llm/OpenAICompatProvider.kt` |
| 全局配置 | `config/AppConfig.kt` |
| 工作区文件 | `memory/FileStore.kt` |
| 规划调度 | `schedule/PlanScheduler.kt` / `schedule/ScheduleEngine.kt` |
| 天气工具 | `tools/WeatherTools.kt` |

---

## 六、注意事项与易踩坑

1. **不要用 `java.time.*`** — minSdk 24 不支持。用 `SimpleDateFormat` + `Date()` + `System.currentTimeMillis()`。虽然项目有 desugar，但 lint 会报错。

2. **卡片背景不要注入子 View** — `MaterialCardView` 用 `card.background = drawable`，不要 `addView(ImageView)`。

3. **AlertDialog 不支持长按** — 需要用其他方式实现"管理"操作（如点击弹出子菜单）。

4. **SharedPreferences 是单文件** — `clawspeaker_config` 被 `AppConfig` 和部分 Activity 共享，key 命名要避免冲突。

5. **ToolCall 反序列化** — `Session.fromJsonFile()` 中的 `toolCalls` 不能丢，否则带工具调用的对话恢复后 API 报错。

6. **Gson 序列化 Map 类型** — `SerializableSession` 中 `messages` 是 `List<Map<String, Any>>`，从 JSON 反序列化时数字可能是 `Double`，需要 `(value as? Number)?.toLong()` 而非直接 `as? Long`。

7. **Fragment 中 `requireActivity()` 和 `requireView()`** — `onResume` 里用 `requireView()` 没问题（已 attached），但 `onCreateView` 返回前不要调。

8. **`RoundedBitmapDrawable` 的 intrinsic size** — 如果在非卡片场景用了，需要自定义 Drawable 覆盖 `getIntrinsicWidth/Height` 返回 -1。

---

## 七、未来方向建议

- **语义搜索替换关键词搜索** — `MemorySearch.keywordSearch()` 目前是简单的子串匹配，可换用向量检索
- **多轮对话记忆增强** — 系统提示注入 `MEMORY.md`，但目前靠关键词匹配，可加语义检索
- **对话列表** — 现在是弹窗，可改成独立 Activity 或侧滑
- **自定义纹理的批量管理** — 目前逐个操作，可加多选删除
- **Emoji 天气映射** — `weatherEmoji()` 覆盖有限，可扩展
