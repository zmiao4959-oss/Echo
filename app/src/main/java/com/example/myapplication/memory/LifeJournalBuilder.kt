package com.example.myapplication.memory

import android.content.Context
import com.example.myapplication.MyApplication
import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.data.model.LifeJournalPeriod
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeJournalRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.data.store.WeatherHistoryStore
import com.example.myapplication.llm.LLMMessage
import com.example.myapplication.llm.ProviderFactory
import com.example.myapplication.policy.LifeJournalClimateScale
import com.example.myapplication.policy.LifeJournalPolicy
import com.example.myapplication.ui.ThemeManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object LifeJournalBuilder {
    data class DateWindow(val startDate: String, val endDate: String, val startMillis: Long, val endMillis: Long)

    fun dateWindow(period: LifeJournalPeriod, now: Long = System.currentTimeMillis()): DateWindow {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (period == LifeJournalPeriod.WEEK) cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek) else cal.set(Calendar.DAY_OF_MONTH, 1)
        val startMillis = cal.timeInMillis
        val startDate = DATE.format(cal.time)
        if (period == LifeJournalPeriod.WEEK) cal.add(Calendar.DAY_OF_YEAR, 7) else cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        return DateWindow(startDate, DATE.format(cal.time), startMillis, cal.timeInMillis)
    }

    suspend fun build(period: LifeJournalPeriod, refineCopy: Boolean = true): LifeJournalIssue = withContext(Dispatchers.IO) {
        val window = dateWindow(period)
        val records = runCatching { LifeRecordRepository().getByDateRange(window.startDate, window.endDate) }.getOrDefault(emptyList())
        val diaries = runCatching { DiaryRepository().getByDateRange(window.startDate, window.endDate) }.getOrDefault(emptyList())
        val plans = runCatching {
            PlanRepository().getAll().filter { plan ->
                plan.type == "task_reminder" && plan.createdAt <= window.endMillis &&
                    (plan.enabled || (plan.lastTriggeredAt ?: Long.MIN_VALUE) in window.startMillis..window.endMillis)
            }
        }.getOrDefault(emptyList())
        val conversations = loadConversationExcerpts(window)
        val currentWeather = loadCurrentWeather(window)
        val observations = WeatherHistoryStore.read(MyApplication.instance, window.startDate, window.endDate).map {
            LifeJournalPolicy.WeatherObservation(it.date, it.temperatureC, it.description)
        }
        val input = LifeJournalPolicy.Input(
            records = records,
            diaries = diaries,
            plans = plans,
            conversations = conversations,
            startMillis = window.startMillis,
            endMillis = window.endMillis,
            currentWeatherNote = currentWeather,
            weatherObservations = observations
        )
        val localOutput = LifeJournalPolicy.build(input)
        val output = if (refineCopy) {
            withTimeoutOrNull(35_000L) { refineWithEcho(localOutput, input, period) } ?: localOutput
        } else {
            localOutput
        }

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
        val manuallyEdited = existing?.manuallyEditedPages.orEmpty().toSet()
        val existingChapters = existing?.chapters.orEmpty().associateBy { it.id }
        val mergedChapters = output.chapters.map { generated ->
            if (generated.id in manuallyEdited) existingChapters[generated.id] ?: generated else generated
        } + existing?.chapters.orEmpty().filter { old ->
            old.id in manuallyEdited && output.chapters.none { it.id == old.id }
        }
        val issue = LifeJournalIssue(
            id = existing?.id ?: UUID.randomUUID().toString(),
            period = period,
            startDate = window.startDate,
            endDate = window.endDate,
            issueLabel = issueLabel,
            title = if ("cover" in manuallyEdited) existing?.title.orEmpty() else "${output.titleSeed} · 生活志",
            subtitle = if ("cover" in manuallyEdited) existing?.subtitle.orEmpty() else "私人$periodWord / ${window.startDate.replace('-', '.')}—${window.endDate.replace('-', '.')}",
            themeKey = theme.key,
            primaryColor = diaries.mapNotNull { it.color?.takeIf(::isHexColor) }
                .groupingBy { it.uppercase(Locale.US) }.eachCount().maxByOrNull { it.value }?.key
                ?: String.format("#%06X", 0xFFFFFF and theme.previewColors[1]),
            overview = if ("overview" in manuallyEdited) existing?.overview.orEmpty() else output.overview,
            chapters = mergedChapters,
            moodPoints = output.moodPoints,
            keywords = output.keywords,
            weatherNotes = output.weatherNotes,
            sourceCounts = output.sourceCounts,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            revision = (existing?.revision ?: 0) + 1,
            manuallyEditedPages = manuallyEdited.toList()
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
                    .replace(Regex("\\s+"), " ").trim().take(600)
                userText.takeIf(String::isNotBlank)?.let {
                    LifeJournalPolicy.ConversationExcerpt(
                        session.title.takeUnless { title -> title == "对话" } ?: session.autoTitle(),
                        it,
                        DATE.format(Date(session.lastActive))
                    )
                }
            }.take(12)
    }

    private fun loadCurrentWeather(window: DateWindow): String? {
        val app = MyApplication.instance
        val prefs = app.getSharedPreferences("clawspeaker_config", Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong("weather_cache_time", 0L)
        if (cachedAt !in window.startMillis..window.endMillis || System.currentTimeMillis() - cachedAt > 6 * 60 * 60 * 1000L) return null
        val description = prefs.getString("weather_desc_cn", null)?.takeIf(String::isNotBlank) ?: return null
        val temperature = prefs.getString("weather_info_v3", null).orEmpty()
        val fullDate = DATE.format(Date(cachedAt))
        LifeJournalClimateScale.extractTemperature(temperature)?.let {
            WeatherHistoryStore.record(app, description, it, capturedAt = cachedAt)
        }
        return "${fullDate.takeLast(5).replace('-', '/')} $description ${temperature.trim()}".trim()
    }

    private data class RefinedChapter(val id: String = "", val title: String = "", val body: String = "")
    private data class RefinedCopy(val title: String = "", val overview: String = "", val chapters: List<RefinedChapter> = emptyList())

    private suspend fun refineWithEcho(
        local: LifeJournalPolicy.Output,
        input: LifeJournalPolicy.Input,
        period: LifeJournalPeriod
    ): LifeJournalPolicy.Output {
        val config = MyApplication.instance.appConfig
        if (!config.isLLMConfigured || input.records.isEmpty() && input.diaries.isEmpty()) return local
        return runCatching {
            val query = buildString {
                append(if (period == LifeJournalPeriod.MONTH) "本月生活回顾" else "本周生活回顾")
                local.keywords.take(6).forEach { append(' ').append(it) }
                input.diaries.takeLast(4).forEach { append(' ').append(it.title) }
            }.take(360)
            val memory = runCatching { MemoryContextBuilder.build(query) }.getOrNull()
            val facts = buildString {
                input.diaries.sortedBy { it.date }.take(31).forEach {
                    append("[日记 ${it.date}] 标题=${it.title}；摘要=${it.summary}；心情=${it.mood}；心情色=${it.color ?: "未记录"}；正文=${it.diaryText.take(320)}\n")
                }
                input.records.sortedBy { it.date }.take(100).forEach {
                    append("[片段 ${it.date}] 心情=${it.mood ?: "未记录"}；${it.content.take(240)}\n")
                }
                input.plans.take(30).forEach {
                    val state = if ((it.lastTriggeredAt ?: Long.MIN_VALUE) in input.startMillis..input.endMillis) "本期有提醒触发记录（不代表完成）" else if (it.enabled) "提醒仍启用" else "提醒已停用"
                    append("[计划] ${it.title}；${it.message.take(140)}；状态=$state\n")
                }
                input.conversations.take(12).forEach { append("[用户与 Echo 的会话 ${it.date}] ${it.title}；用户说：${it.text.take(320)}\n") }
                input.weatherObservations.forEach { append("[天气 ${it.date}] ${it.label} ${it.temperatureC?.let { value -> "$value℃" } ?: ""}\n") }
            }.take(16_000)
            val memoryContext = buildString {
                memory?.profileSummary?.takeIf(String::isNotBlank)?.let { append("[长期画像]\n").append(it).append('\n') }
                memory?.structuredProfileInjection?.takeIf(String::isNotBlank)?.let { append("[确认过的个人资料]\n").append(it).append('\n') }
                memory?.relevantMemorySection?.takeIf(String::isNotBlank)?.let { append("[检索到的过去记忆]\n").append(it).append('\n') }
            }.take(5_000).ifBlank { "（本次没有检索到可用的长期记忆）" }
            val skeleton = local.chapters.joinToString("\n") { "${it.id}: ${it.title}｜${it.body}" }
            val prompt = """
                你是 Echo。你长期陪伴同一位用户，现在要主编一本只给 TA 看的私人生活志。

                先回答“这一${if (period == LifeJournalPeriod.MONTH) "月" else "周"}真正发生了什么”：从事实材料里找出事件的推进、生活重心的变化、反复出现但尚未解决的线索。不要逐条罗列，不要写成统计报告，也不要使用空泛疗愈话术。

                规则：
                1. 本期事实只来自“本期事实材料”。过去记忆只能帮助你认出延续的线索，绝不能写成这期新发生的事实。
                2. Echo 的回复不等于用户事实。不要虚构人物关系、地点、天气、动机、因果或心理诊断。
                   不得根据“计划未完成”推断用户的习惯、生活重心或执行状态；不得评价 Echo 是否回应，因为提供的会话材料只含用户文字。
                3. 语气像了解用户的私人编辑：克制、具体、有判断，但允许保留“不确定”。使用第二人称“你”，不要冒充用户说“我”。
                4. overview 写 220—360 个汉字，形成完整叙事。每章 body 写 80—180 个汉字；材料不足就诚实留白。
                5. 原文片段会由版式单独呈现，body 不要伪造引语，也不要重复堆砌原句。
                6. 人物与地点页只能解释“可信编辑骨架”中已经确认的条目，不得增加人物、关系或地点；计划页只能描述提醒状态，不得把“提醒触发”写成“已经完成”。

                返回严格 JSON，不要 Markdown：
                {"title":"2—8 字刊名，不含生活志三字","overview":"本期叙事","chapters":[{"id":"原 id","title":"标题","body":"正文"}]}
                chapters 必须保留下面全部原 id，顺序不变。

                [可信编辑骨架]
                $skeleton

                [本期事实材料]
                $facts

                [仅供识别延续关系的过去记忆]
                $memoryContext
            """.trimIndent()
            val response = ProviderFactory.createLLMProvider().chat(
                messages = listOf(
                    LLMMessage("system", "你是 Echo，也是这本私人生活志的主编。只做有来源的编辑判断，绝不猜测，输出严格 JSON。"),
                    LLMMessage("user", prompt)
                ),
                tools = null,
                temperature = .32f,
                maxTokens = 2200
            ).content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val refined = Gson().fromJson(response, RefinedCopy::class.java)
            val refinedById = refined.chapters.associateBy { it.id }
            local.copy(
                titleSeed = refined.title.trim().take(10).ifBlank { local.titleSeed },
                overview = refined.overview.trim().take(560).ifBlank { local.overview },
                chapters = local.chapters.map { chapter ->
                    if (chapter.id in setOf("coordinates", "plans")) return@map chapter
                    refinedById[chapter.id]?.let { copy ->
                        chapter.copy(
                            title = copy.title.trim().take(18).ifBlank { chapter.title },
                            body = copy.body.trim().take(320).ifBlank { chapter.body }
                        )
                    } ?: chapter
                }
            )
        }.getOrDefault(local)
    }

    private val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun isHexColor(value: String): Boolean = Regex("^#[0-9A-Fa-f]{6}$").matches(value)
}
