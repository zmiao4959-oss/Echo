<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="112" alt="小爪应用图标" />
  <h1>小爪（Echo）</h1>
  <p>一个会陪你记录生活、整理日记，并在时间里慢慢理解你的 Android AI 伴侣。</p>
</div>

> [!NOTE]
> 项目仍在持续开发中，部分功能和数据格式可能发生变化。

## 项目简介

小爪是一款本地优先的 Android AI 伴侣应用。它把零散的生活片段、每日回顾、长期记忆和计划提醒连接在一起，让 AI 不只回答当前的问题，也能在用户许可的范围内理解一段持续的生活上下文。

应用支持接入 OpenAI 兼容接口，通过流式对话和 Function Calling 操作本地数据。生活记录、日记、计划、记忆卡片与会话均保存在设备本地，不依赖自建后端服务。

## 核心功能

- **流式 AI 对话**：支持 OpenAI 兼容接口、SSE 流式响应、工具调用、多轮工具循环和长上下文压缩。
- **生活片段**：通过文字或本地录音留下当天的瞬间，并获得简短、克制的“微回声”。
- **每日日记**：把一天的记录整理为日记，支持回看、编辑和日记闭环提示。
- **计划与提醒**：创建任务提醒、主动问候、回忆触发和自动日记计划。
- **长期记忆**：管理用户画像、回忆卡片和文本记忆；记忆可确认、禁用、恢复、编辑或丢弃。
- **多源检索**：在生活记录、日记、画像、回忆卡片和 `MEMORY.md` 之间进行规则检索；可选启用远程 Embedding 和混合排序。
- **成长轨迹与周回顾**：以时间线回看内容，聚合一周的关键词、高光、低谷与总结。
- **记忆治理**：记录记忆状态变化和审计信息，并解释对话使用了哪些类型的记忆。
- **本地数据保护**：采用原子写入、Schema 迁移、数据健康检查、搜索索引重建和数据导出机制。
- **个性化体验**：支持主题、背景、页面纹理、卡片纹理、字体、触感、提示音、天气和可选 TTS。

## 界面结构

主界面由四个核心页面组成：

| 页面 | 作用 |
| --- | --- |
| 今日 | 快速记录、今日状态、天气、微回声与对话入口 |
| 日记 | 浏览和整理每日日记 |
| 规划 | 管理任务、提醒和自动化计划 |
| 记忆 | 浏览长期画像与回忆卡片 |

应用还提供 AI 对话、对话列表、周回顾、成长轨迹、记忆治理、数据诊断和个性化设置等独立页面。

## 技术架构

```text
UI（Activity / Fragment / ViewModel）
        │
        ├── Agent ── LLMProvider ── OpenAI 兼容 API
        │     │
        │     └── ToolRegistry ── 生活 / 日记 / 计划 / 记忆 / Web / 天气工具
        │
        ├── Policy ── 检索 / 排序 / 聚合 / 记忆治理 / 内容过滤
        │
        └── Repository ── JsonAtomicWriter ── 本地 JSON / Markdown
                         ├── Schema 迁移
                         ├── 搜索索引
                         └── 数据健康检查
```

项目采用单应用模块和传统 Android View 体系，主要技术包括：

- Kotlin
- Android SDK（minSdk 24，targetSdk 36）
- MVVM、ViewModel、Lifecycle
- Kotlin Coroutines / Flow
- OkHttp、SSE
- Gson
- Material Components、RecyclerView、ConstraintLayout
- JUnit 与 AndroidX Test

策略层尽量保持为纯 Kotlin，使检索、聚合、提醒和记忆治理逻辑可以脱离 Android 框架进行单元测试。

## 记忆模型

小爪使用逐层沉淀的记忆结构：

```text
生活片段 LifeRecord
    ↓ 整理
每日日记 DailyDiary
    ↓ 提取稳定信息
用户画像 UserProfileMemory
    ↓ 保存值得重访的内容
回忆卡片 MemoryCard
```

候选长期记忆默认需要确认。禁用或待确认的记忆不会进入正常检索结果，相关操作会写入本地审计日志。

## 本地数据

主要数据位于应用私有目录中：

```text
files/
├── echo/
│   ├── life_records.json
│   ├── daily_diaries.json
│   ├── plans.json
│   ├── memory_cards.json
│   ├── user_profile.json
│   ├── memory_audit_log.json
│   └── memories/
│       ├── search_index.json
│       └── embedding_cache.json
├── sessions/
│   └── <sessionId>.json
└── workspace/
    ├── SOUL.md
    ├── IDENTITY.md
    ├── AGENTS.md
    └── MEMORY.md
```

JSON 数据通过临时文件替换的方式原子写入。应用启动时会检查数据版本、索引状态和数据健康状况；迁移失败时会尝试从备份恢复。

## 开始使用

### 环境要求

- Android Studio（建议使用当前稳定版本及其内置 JDK）
- Android SDK 36
- Android 7.0（API 24）或更高版本的设备/模拟器

### 获取项目

```bash
git clone https://github.com/zmiao4959-oss/project1.git
cd project1
```

使用 Android Studio 打开项目，等待 Gradle 同步完成，然后选择设备运行 `app` 配置。

也可以通过 Gradle Wrapper 构建 Debug APK：

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

构建产物默认位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 配置 AI 服务

首次启动后，在应用的设置页面填写：

1. LLM Base URL
2. API Key
3. 模型名称

所选服务需要兼容 OpenAI 风格的 Chat Completions 接口。若要完整使用应用内工具，模型和服务还需要支持流式响应与 Function Calling。

以下能力为可选配置：

- Embedding API：用于远程语义检索和混合排序。
- TTS：用于语音播报和提醒。
- 天气城市：用于主页天气信息和天气工具。

所有密钥均由用户在应用内填写，项目源码不包含可用的 API Key。请勿把真实密钥写入源码、提交记录或公开的配置文件。

## 权限说明

应用会根据启用的功能申请以下权限：

| 权限 | 用途 |
| --- | --- |
| 网络访问 | 调用用户配置的 AI、Embedding、TTS、天气或网页服务 |
| 麦克风 | 录制本地语音便签 |
| 通知 | 展示计划和记录提醒 |
| 精确闹钟 / 唤醒 | 在设定时间触发计划提醒 |
| 前台服务 | 在系统限制下完成语音提醒播放 |
| 开机完成 | 设备重启后恢复计划 |

不使用相关功能时，可以拒绝对应的运行时权限。

## 隐私说明

应用的结构化记录、会话和工作区文件默认保存在设备本地。但“本地优先”不等于所有处理都离线：

- 发起 AI 对话时，对话内容和选中的记忆上下文会发送到用户配置的 LLM 服务。
- 启用远程语义检索时，经过过滤的文本可能发送到配置的 Embedding 服务。
- 使用 TTS、天气或网页工具时，相应请求会发送到对应的第三方服务。
- 敏感内容过滤只能降低意外外发风险，不能替代对第三方服务隐私政策的审查。

请仅配置你信任的服务，并根据实际部署环境检查网络安全、备份规则和密钥存储策略。

## 项目结构

```text
app/src/main/java/com/example/myapplication/
├── agent/          # Agent 循环、系统提示和工具调度
├── config/         # 应用配置
├── data/
│   ├── model/      # 数据模型
│   ├── repository/ # 领域数据读写
│   └── store/      # 原子存储、迁移、导出和审计日志
├── diagnostics/    # 数据健康与服务诊断
├── llm/            # LLM 与 Embedding Provider
├── memory/         # 会话、工作区和记忆上下文
├── policy/         # 可测试的纯 Kotlin 业务策略
├── schedule/       # 计划、闹钟与提醒
├── search/         # 本地搜索索引
├── tools/          # Agent 可调用工具
├── tts/            # 语音合成与播放
└── ui/             # Activity、Fragment、ViewModel 和 Adapter
```

面向开发者的架构说明、手工验收清单和各阶段交接记录位于 [`dev-handover/`](dev-handover/README.md)。

## 测试

运行 JVM 单元测试：

```bash
./gradlew testDebugUnitTest
```

运行设备测试：

```bash
./gradlew connectedDebugAndroidTest
```

测试主要覆盖记忆策略、混合检索、日记与周回顾策略、计划调度、搜索索引、Schema 迁移、数据健康和数据导出等模块。

## 参与开发

欢迎通过 Issue 提交问题、建议和使用反馈。提交代码前建议：

1. 先阅读 [`dev-handover/README.md`](dev-handover/README.md) 中的关键约束和已知问题。
2. 不要在代码、日志、测试数据或截图中提交真实 API Key 和私人记录。
3. 为策略层和数据迁移变更补充对应测试。
4. 涉及数据格式时，提供向后兼容的 Schema 迁移方案。

## 许可证

本仓库目前尚未添加开源许可证。在许可证明确之前，代码的复制、修改和再分发不自动获得授权。

