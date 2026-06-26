# 架构详解

## 四层架构

```
┌──────────────────────────────────────────────┐
│  UI 层                                       │
│  MainActivity → 4 Fragments（今日/日记/规划/记忆）│
│  ChatActivity / SettingsActivity / Profile    │
│  ViewModel (Chat/Today/Diary/Plan/Memory)     │
├──────────────────────────────────────────────┤
│  Agent 层                                     │
│  Agent.kt: buildSystemPrompt + LLM 对话循环    │
│  AgentContext: 单次对话上下文                  │
│  工具调用循环: 用户消息 → LLM → tool_calls →   │
│    ToolRegistry.execute() → 结果回传 LLM       │
│  最多 maxToolRounds 轮（默认 10）              │
├──────────────────────────────────────────────┤
│  Tools 层                                     │
│  ToolRegistry: 工具注册表（单例）              │
│  20+ 工具: FileTools / WebTools / WeatherTools │
│           EchoLife / EchoDiary / EchoPlan /    │
│           EchoMemory / ToolDefinition          │
│  每个工具: schema(JSON Schema) + executor(λ)   │
├──────────────────────────────────────────────┤
│  Data 层                                      │
│  Repository → JSON 文件（EchoFileStore）       │
│  LifeRecordRepository / DiaryRepository /      │
│  PlanRepository / MemoryRepository             │
│  WorkspaceRepository（SOUL.md 等）             │
│  SessionManager（sessions/ 目录）              │
└──────────────────────────────────────────────┘
```

## 数据存储

**所有数据都是本地 JSON 文件，无服务器。**

| 数据类型 | 存储位置 | 格式 |
|---------|---------|------|
| LifeRecord | `echo/life_records.json` | `{"schemaVersion":1, "items":[...]}` |
| DailyDiary | `echo/daily_diaries.json` | 同上 |
| EchoPlan | `echo/plans.json` | 同上 |
| MemoryCard | `echo/memory_cards.json` | 同上 |
| UserProfile | `echo/user_profile.json` | 同上 |
| Session | `sessions/<sessionId>.json` | Session.toJson() |
| 工作区文件 | `workspace/SOUL.md`, `MEMORY.md` 等 | Markdown |
| 配置 | SharedPreferences `clawspeaker_config` | key-value |
| 自定义纹理 | `card_textures.json` + `card_textures/` | JSON + JPEG |

## 纹理系统架构

```
CardTextureManager (图片池 + 卡片纹理)
  ├── BUILTIN_TEXTURES (bg_default_1/2/3)
  ├── customTextures (card_textures.json → 用户添加)
  ├── loadBitmap() → 被 PageTextureManager 复用
  ├── apply(card, key, fallbackColorAttr) → MaterialCardView
  └── CenterCropDrawable (等比缩放填满，无 intrinsic size)

PageTextureManager (页面纹理，复用上面图片池)
  ├── apply(view, key) → 普通 View
  └── remove(view) → 恢复 echoBackground
```

**共享键设计：** 卡片纹理和页面纹理共用同一套图片池。`CardTextureManager.allTextureKeys(context)` 返回全部可用纹理。"添加纹理"在两个地方都能调用，保存后两边立即可用。
