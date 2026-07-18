package com.example.myapplication.data.store

import android.content.Context
import android.util.Log
import com.example.myapplication.MyApplication
import java.io.File

/**
 * Echo 数据目录管理。
 * 在 App 私有 filesDir 下维护 /files/echo/ 结构。
 */
object EchoFileStore {

    private const val TAG = "EchoFileStore"

    /** Echo 数据根目录 */
    val echoDir: File
        get() = MyApplication.instance.filesDir.resolve("echo")

    /** 各数据子目录 */
    val recordsDir: File get() = echoDir.resolve("records")
    val diariesDir: File get() = echoDir.resolve("diaries")
    val plansDir: File get() = echoDir.resolve("plans")
    val memoriesDir: File get() = echoDir.resolve("memories")
    val workspaceDir: File get() = echoDir.resolve("workspace")

    /** JSON 数据文件 */
    val lifeRecordsFile: File get() = recordsDir.resolve("life_records.json")
    val dailyDiariesFile: File get() = diariesDir.resolve("daily_diaries.json")
    val plansFile: File get() = plansDir.resolve("plans.json")
    val memoryCardsFile: File get() = memoriesDir.resolve("memory_cards.json")
    val userProfileFile: File get() = memoriesDir.resolve("user_profile.json")
    val searchIndexFile: File get() = memoriesDir.resolve("search_index.json")
    val foreshadowsFile: File get() = memoriesDir.resolve("foreshadows.json")

    /**
     * 初始化 Echo 目录结构。
     * 在 Application.onCreate 中调用，确保所有目录存在。
     */
    fun init(context: Context) {
        Log.d(TAG, "Initializing Echo directory structure...")
        val dirs = listOf(echoDir, recordsDir, diariesDir, plansDir, memoriesDir, workspaceDir)
        for (dir in dirs) {
            if (!dir.exists()) {
                val ok = dir.mkdirs()
                Log.d(TAG, "  mkdir ${dir.absolutePath}: $ok")
            }
        }
        // 确保 workspace 下的基础文件存在
        ensureWorkspaceFiles()
        Log.d(TAG, "Echo directory structure ready")
    }

    /**
     * 确保 workspace 下的 echo_profile.md 等基础文件存在。
     * 保留现有 notes.md / memory.md（由 MemoryInitializer 管理），
     * 这里只负责 echo 特有的文件。
     */
    private fun ensureWorkspaceFiles() {
        val echoProfile = workspaceDir.resolve("echo_profile.md")
        if (!echoProfile.exists()) {
            echoProfile.writeText(DEFAULT_ECHO_PROFILE, Charsets.UTF_8)
        }
    }

    /** 默认 Echo 角色设定 */
    private val DEFAULT_ECHO_PROFILE = """
# Echo 角色设定

你是 Echo，一个私人生活记录 AI 伙伴。你的核心任务不是单纯回答问题，而是帮助用户：
1. 记录生活片段
2. 整理每日经历
3. 形成日记
4. 规划未来提醒和主动问候
5. 从过去记忆中唤起重要片段

## 说话风格
- 温柔
- 克制
- 真诚
- 不说教
- 不强行鼓励
- 不像客服
- 不要过度鸡汤
- 用户表达混乱时，先帮他接住情绪，再轻轻整理结构

## 四种身份
今日：倾听者
日记：整理者
规划：提醒者
回忆：陪伴者

## 生活记录规则
当用户表达明显生活片段、情绪、经历、项目进展、想法转折时，可以调用 create_life_record。
不要把所有闲聊都保存。
保存前后的回复要自然，不要机械。

## 规划规则
当用户表达未来时间、提醒、问候、自动整理、回顾需求时，可以调用 create_plan。
任务提醒可以自动播报。
主动问候默认不自动播报，除非用户明确要求。
回忆触发默认通知，不默认强制语音播报。

## 日记规则
日记不是流水账，而是温柔整理用户一天的真实状态。
不要编造用户没说过的事。
可以根据记录提炼主题、情绪和关键词。

## 回忆规则
回忆不是打扰，而是在合适的时候把过去重要的片段带回来。
    """.trimIndent()
}
