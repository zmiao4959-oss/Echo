# 对话系统详解

## 对话生命周期

```
enterChat() (TodayFragment)
  → 读取 last_chat_id (SharedPreferences)
  → 如果存在且有效 → 复用（继续之前对话）
  → 否则 → 生成新 chatId
  → 存入 last_chat_id
  → startActivity(ChatActivity)

ChatActivity.onCreate()
  → 接收 chatId
  → viewModel.loadSession(chatId)
    → SessionManager.resolveSession(chatId)
      → 从 sessions/*.json 找匹配 chatId 的会话
      → 找不到 → getOrCreate() → 创建新 Session
  → 显示历史消息

ChatActivity 内
  → 用户发消息 → viewModel.sendMessage()
    → Agent.processMessageStream()
      → 构建系统提示（echo_profile + MEMORY.md + 工具列表）
      → 发送 [system + history + user] 给 LLM
      → 收到 tool_calls → ToolRegistry.execute()
      → 结果回传 LLM → 循环直到 LLM 返回纯文本
    → 每轮循环后 sessionManager.save(session)
  → 新对话按钮 → startNewChat()
    → 生成新 chatId → 更新 last_chat_id → loadSession
```

## 对话列表

点击 ChatActivity 顶部标题可管理：
- **切换** — 加载历史对话，更新 last_chat_id
- **重命名** — 修改 session.metadata["title"]，持久化
- **删除** — 删除 sessions/ 下对应 JSON 文件

标题自动从第一条用户消息取前 30 字，用户可手动重命名。

## Session 序列化

**保存时的完整结构：**
```json
{
  "sessionId": "android:...",
  "chatId": "android:...",
  "messages": [
    {"role": "user", "content": "...", "tool_call_id": "", "tool_calls": [], "name": ""},
    {"role": "assistant", "content": "...", "tool_calls": [
      {"id": "...", "type": "function", "function": {"name": "...", "arguments": "..."}}
    ]},
    {"role": "tool", "content": "...", "tool_call_id": "...", "name": "..."}
  ],
  "createdAt": 1740407700000,
  "lastActive": 1740407700000,
  "metadata": {"title": "对话标题"}
}
```

**反序列化关键：**
- `tool_calls` 必须从 JSON 正确恢复（`deserializeToolCalls()`）
- 数字字段可能以 `Double` 返回，需 `(value as? Number)?.toLong()`

## 长期记忆（MEMORY.md）

工具 `remember_user_fact` 专门写 MEMORY.md：
- 格式：`- [yyyy-MM-dd HH:mm] [category] fact`
- 追加到 `## Echo 记住的关于你的事` 段落下
- 系统提示每次都会注入 MEMORY.md 全文（标记为"## 用户长期记忆"）

## 上下文压缩

`SessionManager.compact()` — 当 `estimateTokens() > maxContextTokens * 0.8` 时触发：
1. 保留最后 N 条消息（compactionKeepMessages）
2. 把之前的消息发给 LLM 做摘要
3. 替换为 `[system] 摘要` + 保留的消息
