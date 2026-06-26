package com.example.myapplication.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.MemoryRepository
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.ProviderFactory
import com.example.myapplication.ui.MainActivity
import com.google.gson.JsonParser
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Echo 规划调度器 — 将 EchoPlan 注册到系统 AlarmManager。
 *
 * 与原有 ScheduleEngine 平行运行，使用独立的 requestCode 范围（8000+）避免冲突。
 */
object PlanScheduler {

    private const val TAG = "PlanScheduler"
    private const val REQUEST_CODE_BASE = 8000
    private const val ACTION_PLAN_ALARM = "com.example.myapplication.ECHO_PLAN_ALARM"
    private const val CHANNEL_ECHO = "echo_plans"

    /**
     * 为单个 EchoPlan 设置闹钟。
     */
    fun schedule(context: Context, plan: EchoPlan) {
        if (!plan.enabled) return

        // 拒绝过去的时间（LLM 可能算错）
        val now = System.currentTimeMillis()
        if (plan.triggerAt <= now) {
            Log.w(TAG, "Plan '${plan.title}' triggerAt is in the past (${plan.triggerAt} <= $now), not scheduling")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        createNotificationChannel(context)

        val requestCode = REQUEST_CODE_BASE + plan.id.hashCode().and(0xFFFF)
        val intent = buildIntent(context, plan)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // 没有精确闹钟权限 → 设为非精确
                alarmManager.set(AlarmManager.RTC_WAKEUP, plan.triggerAt, pendingIntent)
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(plan.triggerAt, pendingIntent),
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled plan '${plan.title}' (${plan.type}) at ${plan.triggerAt}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule plan '${plan.title}'", e)
        }
    }

    /**
     * 取消单个 EchoPlan 的闹钟。
     */
    fun cancel(context: Context, planId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = REQUEST_CODE_BASE + planId.hashCode().and(0xFFFF)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_PLAN_ALARM
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * 恢复所有已启用的 EchoPlan 闹钟（开机 / 应用启动时调用）。
     */
    suspend fun rescheduleAll(context: Context) {
        createNotificationChannel(context)
        val repo = PlanRepository()
        ensureDefaultPlans(context, repo)
        val plans = repo.getAllEnabled()
        Log.d(TAG, "Rescheduling ${plans.size} enabled plans")
        for (plan in plans) {
            schedule(context, plan)
        }
    }

    /**
     * 首次启动时创建默认规划模板。
     */
    private suspend fun ensureDefaultPlans(context: Context, repo: PlanRepository) {
        val prefs = context.getSharedPreferences("clawspeaker_config", Context.MODE_PRIVATE)
        if (prefs.getBoolean("echo_default_plans_v1", false)) return

        val allPlans = repo.getAll()
        val existingTitles = allPlans.map { it.title }.toSet()

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        // 明天早上 8:00
        val tomorrow8am = (now / dayMs + 1) * dayMs + 8 * 3_600_000L
        // 明天晚上 22:00
        val tomorrow10pm = (now / dayMs + 1) * dayMs + 22 * 3_600_000L
        // 下周日 21:00
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        val daysUntilSunday = (java.util.Calendar.SUNDAY - cal.get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
        val nextSunday9pm = (now / dayMs + if (daysUntilSunday == 0) 7 else daysUntilSunday) * dayMs + 21 * 3_600_000L

        val defaults = mutableListOf<EchoPlan>()

        if ("晨间问候" !in existingTitles) {
            defaults.add(EchoPlan(
                id = UUID.randomUUID().toString(),
                type = "companion_checkin",
                title = "晨间问候",
                message = "早上好。今天想以什么状态开始？",
                triggerAt = tomorrow8am,
                repeatRule = "每天",
                enabled = true,
                autoSpeak = false,
                importance = 2,
                tags = listOf("问候", "早晨"),
                createdAt = now,
                updatedAt = now,
                lastTriggeredAt = null
            ))
        }

        if ("晚间问候" !in existingTitles) {
            defaults.add(EchoPlan(
                id = UUID.randomUUID().toString(),
                type = "companion_checkin",
                title = "晚间问候",
                message = "今天有什么想留下来的吗？",
                triggerAt = tomorrow10pm,
                repeatRule = "每天",
                enabled = true,
                autoSpeak = false,
                importance = 2,
                tags = listOf("问候", "晚间"),
                createdAt = now,
                updatedAt = now,
                lastTriggeredAt = null
            ))
        }

        if ("周回顾" !in existingTitles) {
            defaults.add(EchoPlan(
                id = UUID.randomUUID().toString(),
                type = "memory_trigger",
                title = "周回顾",
                message = "这一周，你留下了不少片段。要不要一起看看？",
                triggerAt = nextSunday9pm,
                repeatRule = "每周",
                enabled = true,
                autoSpeak = false,
                importance = 1,
                tags = listOf("回顾", "每周"),
                createdAt = now,
                updatedAt = now,
                lastTriggeredAt = null
            ))
        }

        for (plan in defaults) {
            repo.add(plan)
            schedule(context, plan)
            Log.d(TAG, "Created default plan: ${plan.title}")
        }

        prefs.edit().putBoolean("echo_default_plans_v1", true).commit()
    }

    /**
     * 处理触发的 EchoPlan 闹钟。
     * 从 AlarmReceiver 调用。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleTrigger(context: Context, planId: String) {
        scope.launch {
            val repo = PlanRepository()
            val plan = repo.getById(planId) ?: return@launch

            // 更新最后触发时间
            repo.updateLastTriggered(planId, System.currentTimeMillis())

            // 如果有重复规则，重新调度
            if (plan.repeatRule != null) {
                val next = computeNextTrigger(plan)
                if (next != null) {
                    repo.update(plan.copy(triggerAt = next, updatedAt = System.currentTimeMillis()))
                    schedule(context, plan.copy(triggerAt = next))
                }
            } else {
                // 一次性规划 → 禁用
                repo.update(plan.copy(enabled = false, updatedAt = System.currentTimeMillis()))
            }

            // 主动陪伴设置检查（task_reminder 不受影响）
            val config = MyApplication.instance.appConfig
            val isCompanionType = plan.type in listOf("companion_checkin", "memory_trigger")
            if (isCompanionType && !config.companionEnabled) {
                Log.d(TAG, "Companion disabled, skipping ${plan.type} plan: ${plan.title}")
                return@launch
            }
            val suppressVoice = isCompanionType && !config.companionAllowVoice
            if (isCompanionType) {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val quietStart = config.companionQuietStart
                val quietEnd = config.companionQuietEnd
                val inQuiet = if (quietStart < quietEnd) hour in quietStart until quietEnd
                              else hour >= quietStart || hour < quietEnd
                if (inQuiet) {
                    Log.d(TAG, "In quiet hours ($quietStart-$quietEnd), skipping companion plan: ${plan.title}")
                    return@launch
                }
            }

            when (plan.type) {
                "task_reminder" -> triggerTaskReminder(context, plan)
                "companion_checkin" -> triggerCompanionCheckin(context, plan, suppressVoice)
                "memory_trigger" -> triggerMemoryTrigger(context, plan, suppressVoice)
                "auto_diary" -> triggerAutoDiary(context, plan)
            }
        }
    }

    // ── 各类型触发行为 ──

    private fun triggerTaskReminder(context: Context, plan: EchoPlan) {
        showNotification(context, plan, "⏰ ${plan.title}", plan.message)
        // 语音播报通过 ReminderService
        if (plan.autoSpeak) {
            val intent = Intent(context, ReminderService::class.java).apply {
                putExtra("plan_title", plan.title)
                putExtra("plan_message", plan.message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private suspend fun triggerCompanionCheckin(context: Context, plan: EchoPlan, suppressVoice: Boolean = false) {
        val greeting = buildDynamicGreeting(plan)
        showNotification(context, plan, "Echo 想问你", greeting)
        if (plan.autoSpeak && !suppressVoice) {
            val intent = Intent(context, ReminderService::class.java).apply {
                putExtra("plan_title", plan.title)
                putExtra("plan_message", greeting)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    /** 根据时间 + 最近记录 + 计划/日记状态生成动态问候（Phase E: 多变体） */
    private suspend fun buildDynamicGreeting(plan: EchoPlan): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val recordRepo = LifeRecordRepository()
            val diaryRepo = DiaryRepository()
            val planRepo = PlanRepository()
            val todayRecords = recordRepo.getByDate(today)
            val hasDiary = diaryRepo.hasDiary(today)
            val recordCount = todayRecords.size

            // Check for upcoming plans today
            val allPlans = planRepo.getAll()
            val pendingPlans = allPlans.filter { it.enabled && it.type == "task_reminder" && it.triggerAt > System.currentTimeMillis() }
            val hasPendingPlan = pendingPlans.isNotEmpty()

            // Deterministic variant seed: same day + same plan = same variant
            val seed = (today + plan.id).hashCode()

            return when {
                // Morning (6-11)
                hour in 6..11 -> {
                    if (recordCount > 0 && hasPendingPlan) {
                        pickMorningWithRecordsAndPlans(recordCount, seed)
                    } else if (recordCount > 0) {
                        pickMorningWithRecords(recordCount, seed)
                    } else {
                        pickMorningNoRecords(seed)
                    }
                }
                // Evening (18-23)
                hour in 18..23 -> {
                    if (recordCount > 0 && hasDiary) {
                        pickEveningWithRecordsAndDiary(recordCount, seed)
                    } else if (recordCount > 0) {
                        pickEveningWithRecords(recordCount, seed)
                    } else {
                        pickEveningNoRecords(seed)
                    }
                }
                else -> pickOtherTime(seed)
            }
        } catch (_: Exception) {
            return plan.message.ifEmpty { "今天有什么想留下来的吗？" }
        }
    }

    // -- Morning variants (3 each) --

    private fun pickMorningWithRecordsAndPlans(count: Int, seed: Int): String {
        return listOf(
            "早上好。今天已经有 ${count} 条记录了，还有计划等着你。",
            "早。${count} 条记录，看来状态不错。今天也有计划要完成。",
            "早上好。已经记了 ${count} 条，今天还有待办事项，慢慢来。"
        )[Math.abs(seed) % 3]
    }

    private fun pickMorningWithRecords(count: Int, seed: Int): String {
        return listOf(
            "早上好。今天已经有 ${count} 条记录了，状态不错。",
            "早。看到你今天已经记了 ${count} 条，挺有意思的。",
            "今天的 ${count} 条记录看起来很丰富。有什么想补充的吗？"
        )[Math.abs(seed) % 3]
    }

    private fun pickMorningNoRecords(seed: Int): String {
        return listOf(
            "早上好。新的一天开始了，有什么计划吗？",
            "早。今天会有什么想记录的呢？",
            "新的一天。先喝杯水，慢慢来。"
        )[Math.abs(seed) % 3]
    }

    // -- Evening variants (2 each) --

    private fun pickEveningWithRecordsAndDiary(count: Int, seed: Int): String {
        return listOf(
            "今天记录了 ${count} 个片段，日记也写好了。好好休息。",
            "日记和 ${count} 条记录都在了，今天挺充实的。晚安。"
        )[Math.abs(seed) % 2]
    }

    private fun pickEveningWithRecords(count: Int, seed: Int): String {
        return listOf(
            "今天记录了 ${count} 个片段。还有什么想留下的吗？",
            "${count} 条记录。如果有想补充的，现在还来得及。"
        )[Math.abs(seed) % 2]
    }

    private fun pickEveningNoRecords(seed: Int): String {
        return listOf(
            "今天过得怎么样？有什么想记录下来的吗？",
            "一天快结束了，有什么想留下的吗？"
        )[Math.abs(seed) % 2]
    }

    // -- Other times (2) --

    private fun pickOtherTime(seed: Int): String {
        return listOf(
            "今天有什么想留下来的吗？",
            "有什么想记录的吗？随时都可以。"
        )[Math.abs(seed) % 2]
    }

    private fun triggerMemoryTrigger(context: Context, plan: EchoPlan, @Suppress("UNUSED_PARAMETER") suppressVoice: Boolean = false) {
        scope.launch {
            try {
                // Weekly review: use WeeklyReviewBuilder
                if (plan.title == "周回顾" || plan.tags.contains("每周")) {
                    val review = com.example.myapplication.memory.WeeklyReviewBuilder.build()
                    val notificationText = review.summary.take(200)
                    showNotification(
                        context, plan,
                        "Echo 周回顾 ${review.dateRange}",
                        notificationText
                    )
                    return@launch
                }

                // Regular memory trigger: show a random memory card
                val memoryRepo = MemoryRepository()
                val cards = memoryRepo.getAllCards()
                if (cards.isEmpty()) {
                    showNotification(context, plan, "Echo 回忆", "还没有记忆卡片，多记录生活吧")
                    return@launch
                }
                // 优先选置顶 + 重要性高的，其次选最近没出现过的
                val sorted = cards.sortedWith(compareByDescending<com.example.myapplication.data.model.MemoryCard> { it.pinned }
                    .thenByDescending { it.createdAt })
                val prefs = context.getSharedPreferences("clawspeaker_config", android.content.Context.MODE_PRIVATE)
                val recentIds = prefs.getStringSet("recent_memory_trigger_ids", emptySet()) ?: emptySet()
                val card = sorted.firstOrNull { it.id !in recentIds } ?: sorted.first()
                // 记录最近触发的 ID（保留最近 20 个）
                val updated = (recentIds.toList() + card.id).takeLast(20).toSet()
                prefs.edit().putStringSet("recent_memory_trigger_ids", updated).apply()
                showNotification(
                    context, plan,
                    "Echo 找到一段过去的你",
                    "「${card.quote.take(80)}」\n${card.note}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Memory trigger failed", e)
                showNotification(context, plan, "Echo 回忆", plan.message)
            }
        }
    }

    private fun triggerAutoDiary(context: Context, plan: EchoPlan) {
        scope.launch {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val diaryRepo = DiaryRepository()
                val recordRepo = LifeRecordRepository()

                if (diaryRepo.hasDiary(today)) return@launch

                val records = recordRepo.getByDate(today)
                if (records.isEmpty()) return@launch

                // 调用 LLM 生成日记
                val config = MyApplication.instance.appConfig
                if (!config.isLLMConfigured) {
                    showNotification(context, plan, "日记生成失败", "LLM 未配置")
                    return@launch
                }

                val fragments = records.joinToString("\n\n") { "[${it.source}] ${it.content}" }
                val prompt = """
请根据以下生活片段生成一篇今日日记。输出纯 JSON：
{"title":"标题","summary":"摘要","diaryText":"正文","mood":"情绪","tags":["标签1","标签2"]}

生活片段：
$fragments
                """.trimIndent()

                val provider = ProviderFactory.createLLMProvider()
                val response = provider.chat(
                    listOf(
                        LLMMessage("system", "你是温柔克制的日记写作者。只输出 JSON。"),
                        LLMMessage("user", prompt)
                    ), temperature = 0.7f, maxTokens = 2048
                )

                val json = extractJson(response.content)
                val obj = JsonParser.parseString(json).asJsonObject
                val now = System.currentTimeMillis()
                val diary = DailyDiary(
                    id = UUID.randomUUID().toString(), date = today,
                    title = obj.get("title")?.asString ?: "日记",
                    summary = obj.get("summary")?.asString ?: "",
                    diaryText = obj.get("diaryText")?.asString ?: response.content,
                    mood = obj.get("mood")?.asString ?: "",
                    tags = obj.getAsJsonArray("tags")?.map { it.asString } ?: emptyList(),
                    sourceRecordIds = records.map { it.id },
                    createdAt = now, updatedAt = now
                )
                diaryRepo.add(diary)

                showNotification(
                    context, plan,
                    "今日日记已生成",
                    "${diary.title}\n${diary.summary}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Auto diary trigger failed", e)
                showNotification(context, plan, "日记生成失败", "打开日记页手动生成")
            }
        }
    }

    private fun extractJson(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```").trimStart()
            if (t.startsWith("json")) t = t.removePrefix("json").trimStart()
            if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        return if (start >= 0 && end > start) t.substring(start, end + 1) else t
    }

    // ── 通知 ──

    private fun showNotification(context: Context, plan: EchoPlan, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context, plan.id.hashCode().and(0xFFFF), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ECHO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPending)
            .build()

        val notifyId = plan.id.hashCode().and(0xFFFF)
        manager.notify(notifyId, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ECHO) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ECHO,
                    "Echo 规划提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "任务提醒、主动问候、回忆触发、自动日记"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    // ── 意图构建 ──

    private fun buildIntent(context: Context, plan: EchoPlan): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_PLAN_ALARM
            putExtra("plan_id", plan.id)
            putExtra("plan_type", plan.type)
        }
    }

    private fun computeNextTrigger(plan: EchoPlan): Long? {
        val rule = plan.repeatRule ?: return null
        val interval = when (rule.lowercase()) {
            "daily", "每天" -> 86_400_000L
            "weekly", "每周" -> 604_800_000L
            else -> return null
        }
        return plan.triggerAt + interval
    }
}
