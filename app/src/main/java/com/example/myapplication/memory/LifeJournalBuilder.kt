package com.example.myapplication.memory

import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.data.model.LifeJournalPeriod
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeJournalRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.ProviderFactory
import com.example.myapplication.policy.LifeJournalPolicy
import com.example.myapplication.ui.ThemeManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

object LifeJournalBuilder {
    data class DateWindow(val startDate: String, val endDate: String, val startMillis: Long, val endMillis: Long)

    fun dateWindow(period: LifeJournalPeriod, now: Long = System.currentTimeMillis()): DateWindow {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (period == LifeJournalPeriod.WEEK) cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        else cal.set(Calendar.DAY_OF_MONTH, 1)
        val startMillis = cal.timeInMillis
        val startDate = DATE.format(cal.time)
        if (period == LifeJournalPeriod.WEEK) cal.add(Calendar.DAY_OF_YEAR, 7) else cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        return DateWindow(startDate, DATE.format(cal.time), startMillis, cal.timeInMillis)
    }

    suspend fun build(period: LifeJournalPeriod, refineCopy: Boolean = false): LifeJournalIssue = withContext(Dispatchers.IO) {
        val window = dateWindow(period)
        val records = runCatching { LifeRecordRepository().getByDateRange(window.startDate, window.endDate) }.getOrDefault(emptyList())
        val diaries = runCatching { DiaryRepository().getByDateRange(window.startDate, window.endDate) }.getOrDefault(emptyList())
        val plans = runCatching { PlanRepository().getAll().filter { plan ->
            plan.createdAt <= window.endMillis && (plan.enabled || (plan.lastTriggeredAt ?: Long.MIN_VALUE) in window.startMillis..window.endMillis)
        } }.getOrDefault(emptyList())
        val sessions = loadConversationExcerpts(window)
        val input = LifeJournalPolicy.Input(records, diaries, plans, sessions, window.startMillis, window.endMillis, loadCurrentWeather(window))
        val localOutput = LifeJournalPolicy.build(input)
        val output = if (refineCopy) withTimeoutOrNull(12_000L) { refineWithLLM(localOutput, input) } ?: localOutput else localOutput
        val app = MyApplication.instance
        val theme = ThemeManager.specFor(app.appConfig.themeKey)
        val now = System.currentTimeMillis()
        val existing = LifeJournalRepository().getForPeriod(window.startDate, window.endDate)
        val issueLabel = if (period == LifeJournalPeriod.MONTH) {
            SimpleDateFormat("yyyy · MM", Locale.CHINA).format(window.startMillis)
        } else {
            "WEEK ${SimpleDateFormat("ww", Locale.CHINA).format(window.startMillis)}"
        }
        val periodWord = if (period == LifeJournalPeriod.MONTH) "月刊" else "周刊"
        val issue = LifeJournalIssue(
            id = existing?.id ?: UUID.randomUUID().toString(),
            period = period,
            startDate = window.startDate,
            endDate = window.endDate,
            issueLabel = issueLabel,
            title = "${output.titleSeed} · 生活志",
            subtitle = "私人$periodWord / ${window.startDate.replace('-', '.')}—${window.endDate.replace('-', '.')}",
            themeKey = theme.key,
            primaryColor = diaries.mapNotNull { it.color?.takeIf(::isHexColor) }
                .groupingBy { it.uppercase(Locale.US) }.eachCount().maxByOrNull { it.value }?.key
                ?: String.format("#%06X", 0xFFFFFF and theme.previewColors[1]),
            overview = output.overview,
            chapters = output.chapters,
            moodPoints = output.moodPoints,
            keywords = output.keywords,
            weatherNotes = output.weatherNotes,
            sourceCounts = output.sourceCounts,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            revision = (existing?.revision ?: 0) + 1
        )
        LifeJournalRepository().save(issue)
        issue
    }

    private suspend fun loadConversationExcerpts(window: DateWindow): List<LifeJournalPolicy.ConversationExcerpt> {
        val dir = MyApplication.instance.filesDir.resolve("sessions")
        val manager = SessionManager(dir, { 24_000 }, { 12 })
        return runCatching { manager.getAllSessions() }.getOrDefault(emptyList())
            .filter { it.lastActive in window.startMillis..window.endMillis }
            .mapNotNull { session ->
                val userText = session.messages.filter { it.role == "user" }
                    .joinToString(" ") { it.content.replace(Regex("\\[Memory Search Results].*?\\n\\n", RegexOption.DOT_MATCHES_ALL), "") }
                    .replace(Regex("\\s+"), " ").trim().take(500)
                userText.takeIf { it.isNotBlank() }?.let {
                    LifeJournalPolicy.ConversationExcerpt(session.title.takeUnless { title -> title == "对话" } ?: session.autoTitle(), it)
                }
            }.take(12)
    }

    private fun loadCurrentWeather(window: DateWindow): String? {
        val app = MyApplication.instance
        val prefs = app.getSharedPreferences("clawspeaker_config", android.content.Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong("weather_cache_time", 0L)
        if (cachedAt !in window.startMillis..window.endMillis || System.currentTimeMillis() - cachedAt > 6 * 60 * 60 * 1000L) return null
        val desc = prefs.getString("weather_desc_cn", null)?.takeIf { it.isNotBlank() } ?: return null
        val temperature = prefs.getString("weather_info_v3", null).orEmpty()
        val day = SimpleDateFormat("M/d", Locale.CHINA).format(cachedAt)
        return "$day $desc${temperature.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"
    }

    private data class RefinedChapter(val id: String = "", val title: String = "", val body: String = "")
    private data class RefinedCopy(val title: String = "", val overview: String = "", val chapters: List<RefinedChapter> = emptyList())

    private suspend fun refineWithLLM(
        local: LifeJournalPolicy.Output,
        input: LifeJournalPolicy.Input
    ): LifeJournalPolicy.Output {
        val config = MyApplication.instance.appConfig
        if (!config.isLLMConfigured || input.records.isEmpty() && input.diaries.isEmpty()) return local
        return runCatching {
            val facts = buildString {
                input.diaries.sortedBy { it.date }.take(31).forEach { append("[日记 ${it.date}] ${it.title}｜${it.summary}｜${it.diaryText.take(260)}\n") }
                input.records.sortedBy { it.date }.take(80).forEach { append("[片段 ${it.date}] ${it.content.take(220)}\n") }
                input.plans.take(30).forEach { append("[计划] ${it.title}｜${it.message.take(120)}｜完成时间:${it.lastTriggeredAt ?: "无"}\n") }
                input.conversations.take(12).forEach { append("[用户与Echo会话] ${it.title}｜${it.text.take(260)}\n") }
            }.take(12_000)
            val skeleton = local.chapters.joinToString("\n") { "${it.id}: ${it.title}｜${it.body}" }
            val prompt = """
                你是一位克制的私人刊物编辑。请只根据“事实材料”润色生活志，不得补写事件、因果、人物关系、地点、天气、动机或心理诊断。
                Echo 的回答不是用户事实。材料不足时直接写“本期没有足够明确的线索”。叙述使用第一人称刊物语气，但不要替用户下结论。
                返回严格 JSON，不要 Markdown：
                {"title":"2至8字刊名，不含生活志三字","overview":"180至320字","chapters":[{"id":"原id","title":"标题","body":"60至160字"}]}
                chapters 必须保留全部原 id，顺序不变。原文片段会另行排版，不要在 body 中虚构引语。

                现有可信骨架：
                $skeleton

                事实材料：
                $facts
            """.trimIndent()
            val response = ProviderFactory.createLLMProvider().chat(
                messages = listOf(
                    LLMMessage("system", "你只做有来源的编辑整理，绝不猜测。输出严格 JSON。"),
                    LLMMessage("user", prompt)
                ), tools = null, temperature = .35f, maxTokens = 1400
            ).content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val refined = Gson().fromJson(response, RefinedCopy::class.java)
            val refinedById = refined.chapters.associateBy { it.id }
            local.copy(
                titleSeed = refined.title.trim().take(10).ifBlank { local.titleSeed },
                overview = refined.overview.trim().take(420).ifBlank { local.overview },
                chapters = local.chapters.map { chapter ->
                    refinedById[chapter.id]?.let { copy ->
                        chapter.copy(
                            title = copy.title.trim().take(18).ifBlank { chapter.title },
                            body = copy.body.trim().take(220).ifBlank { chapter.body }
                        )
                    } ?: chapter
                }
            )
        }.getOrDefault(local)
    }

    private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun isHexColor(value: String): Boolean = Regex("^#[0-9A-Fa-f]{6}$").matches(value)
}
