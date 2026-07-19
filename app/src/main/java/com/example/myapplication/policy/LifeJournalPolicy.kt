package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.model.LifeJournalChapter
import com.example.myapplication.data.model.LifeJournalMoodPoint
import com.example.myapplication.data.model.LifeRecord
import kotlin.math.roundToInt

object LifeJournalPolicy {
    data class ConversationExcerpt(val title: String, val text: String, val date: String = "")
    data class WeatherObservation(
        val date: String,
        val temperatureC: Float? = null,
        val label: String = ""
    )

    data class Input(
        val records: List<LifeRecord>,
        val diaries: List<DailyDiary>,
        val plans: List<EchoPlan>,
        val conversations: List<ConversationExcerpt>,
        val startMillis: Long,
        val endMillis: Long,
        val currentWeatherNote: String? = null,
        val weatherObservations: List<WeatherObservation> = emptyList()
    )

    data class Output(
        val titleSeed: String,
        val overview: String,
        val chapters: List<LifeJournalChapter>,
        val moodPoints: List<LifeJournalMoodPoint>,
        val keywords: List<String>,
        val weatherNotes: List<String>,
        val sourceCounts: Map<String, Int>
    )

    private val stopWords = setOf(
        "今天", "一个", "一些", "这个", "那个", "然后", "但是", "还是", "已经", "没有", "自己", "觉得", "感觉",
        "因为", "所以", "可以", "可能", "真的", "比较", "还有", "事情", "时候", "我们", "你们", "他们", "什么",
        "怎么", "就是", "不是", "现在", "最近", "echo", "the", "and", "but", "with", "from", "have", "this", "that"
    )
    private val weatherWords = listOf("晴", "阴", "多云", "阵雨", "下雨", "暴雨", "雷雨", "雪", "风", "雾", "闷热", "炎热", "凉爽", "寒冷")
    private val topicWords = listOf(
        "工作", "项目", "学习", "考试", "写作", "创作", "阅读", "读书", "摄影", "画画", "音乐", "电影", "游戏",
        "旅行", "出差", "搬家", "整理", "做饭", "饮食", "睡眠", "失眠", "运动", "跑步", "健身", "散步", "看病",
        "治疗", "家庭", "关系", "朋友", "独处", "压力", "焦虑", "情绪", "计划", "复盘", "面试", "交稿", "毕业"
    )
    private val positive = listOf("开心", "愉快", "平静", "轻松", "期待", "满足", "兴奋", "幸福", "不错", "顺利", "自在", "踏实")
    private val negative = listOf("难过", "焦虑", "疲惫", "生气", "失落", "紧张", "烦", "累", "沮丧", "孤独", "压抑", "崩溃")

    fun build(input: Input): Output {
        val factSources = input.records.map {
            LifeJournalEntityPolicy.Source("record:${it.id}", it.date, it.content, it.tags)
        } + input.diaries.map {
            LifeJournalEntityPolicy.Source("diary:${it.id}", it.date, listOf(it.title, it.summary, it.diaryText).joinToString(" "), it.tags)
        }
        val allTexts = factSources.map { it.text } + input.conversations.map { it.text } +
            input.plans.flatMap { listOf(it.title, it.message) }
        val allTags = factSources.flatMap { it.tags }
        val keywords = extractKeywords(allTexts, allTags)
        val weather = mergeWeather(input)
        val entities = LifeJournalEntityPolicy.extract(factSources)
        val triggered = input.plans.filter { (it.lastTriggeredAt ?: Long.MIN_VALUE) in input.startMillis..input.endMillis }
        val active = input.plans.filter { it.enabled && it !in triggered }
        val fragments = selectFragments(input)
        val entityNames = (entities.people + entities.places).map { it.name.lowercase() }.toSet()
        val recurring = keywords.filter { keyword ->
            keyword.lowercase() !in entityNames && factSources.map { it.date }.distinct().count { date ->
                factSources.filter { it.date == date }.any { source ->
                    source.text.contains(keyword, ignoreCase = true) || source.tags.any { it.contains(keyword, ignoreCase = true) }
                }
            } >= 2
        }

        val chapters = mutableListOf(
            LifeJournalChapter(
                "fragments", "CHAPTER 01", "片段",
                if (fragments.isEmpty()) "这一期还没有留下可编入的生活片段。" else "从不同日期里挑出最能保留现场感的文字；长句只在自然停顿处收束，不把半句话硬切开。",
                fragments,
                listOf("${input.diaries.size} 篇日记", "${input.records.size} 个生活片段")
            )
        )

        if (entities.people.isNotEmpty() || entities.places.isNotEmpty()) {
            val coordinateItems = entities.people.map { "人物｜${it.name} · ${evidenceLabel(it)}" } +
                entities.places.map { "地点｜${it.name} · ${evidenceLabel(it)}" }
            val summary = buildList {
                if (entities.people.isNotEmpty()) add("${entities.people.size} 位人物")
                if (entities.places.isNotEmpty()) add("${entities.places.size} 个地点")
            }.joinToString("和")
            chapters += LifeJournalChapter(
                "coordinates", "CHAPTER ${chapterNumber(chapters)}", "人物与地点",
                entityChapterBody(entities, summary),
                coordinateItems,
                listOf("只读取日记与生活片段", "宁可留白，不猜普通词")
            )
        }
        if (recurring.isNotEmpty()) {
            chapters += LifeJournalChapter(
                "thoughts", "CHAPTER ${chapterNumber(chapters)}", "反复出现的念头",
                "这些线索至少出现在两个不同日期。它们不是性格结论，只是值得回看的回声：${recurring.joinToString("、")}。",
                recurring,
                listOf("至少跨两个日期的日记或生活片段")
            )
        }
        if (input.plans.isNotEmpty()) {
            chapters += LifeJournalChapter(
                "plans", "CHAPTER ${chapterNumber(chapters)}", "计划与进度",
                "本期有 ${triggered.size} 项留下提醒触发记录，${active.size} 项仍处于启用状态。提醒被触发不等于任务已经完成，因此这里不替你判断完成与否。",
                (triggered.map { "本期已提醒｜${it.title}" } + active.map { "仍在启用｜${it.title}" }).take(10),
                listOf("只表达提醒状态，不推断任务完成")
            )
        }
        if (input.conversations.isNotEmpty()) {
            chapters += LifeJournalChapter(
                "echo", "CHAPTER ${chapterNumber(chapters)}", "与 Echo 的谈话",
                "选入 ${input.conversations.size} 段本期活跃会话。它们呈现你在谈什么，但不会被当作已经发生的现实事件。",
                input.conversations.map { "${it.title}｜${excerpt(it.text, 90)}" }.take(8),
                listOf("按会话活跃时间归档", "只引用用户表达")
            )
        }

        return Output(
            titleSeed = titleSeed(input, keywords),
            overview = buildOverview(input, keywords, triggered, active),
            chapters = chapters,
            moodPoints = moodPoints(input.records, input.diaries, weather),
            keywords = keywords,
            weatherNotes = weather.mapNotNull { item ->
                val parts = listOfNotNull(
                    item.label.takeIf(String::isNotBlank),
                    item.temperatureC?.let { "${it.roundToInt()}℃" }
                )
                parts.takeIf { it.isNotEmpty() }?.let { "${item.date.takeLast(5).replace('-', '.')} ${it.joinToString(" ")}" }
            }.distinct().take(8),
            sourceCounts = linkedMapOf(
                "日记" to input.diaries.size,
                "片段" to input.records.size,
                "计划" to input.plans.size,
                "Echo 对话" to input.conversations.size,
                "天气" to weather.count { it.temperatureC != null }
            )
        )
    }

    private fun titleSeed(input: Input, keywords: List<String>): String {
        input.diaries.firstOrNull { it.title.isNotBlank() }?.title?.take(10)?.let { return it }
        keywords.firstOrNull { it.any { ch -> ch.code in 0x4E00..0x9FFF } }?.let { return it.take(8) }
        val english = keywords.filter { it.all(Char::isLetter) }.take(2)
        if (english.isNotEmpty()) return english.joinToString(" ") { it.uppercase() }.take(18)
        return if (input.records.isEmpty()) "此刻存档" else "日常切片"
    }

    private fun buildOverview(input: Input, keywords: List<String>, triggered: List<EchoPlan>, active: List<EchoPlan>): String {
        val diaryAnchors = input.diaries.sortedBy { it.date }.map { excerpt(it.summary.ifBlank { it.diaryText }, 64) }
            .filter(String::isNotBlank).take(4)
        val recordAnchors = input.records.sortedWith(compareByDescending<LifeRecord> { it.importance }.thenBy { it.createdAt })
            .map { excerpt(it.content, 64) }.filter(String::isNotBlank).take((5 - diaryAnchors.size).coerceAtLeast(1))
        val facts = (diaryAnchors + recordAnchors).distinct()
        if (facts.isEmpty()) return "这一期暂时没有足够的文字材料。生活志把空白保留下来，等待你继续记录。"
        val themeLine = keywords.take(4).takeIf { it.isNotEmpty() }?.joinToString("、", prefix = "反复出现的线索有：", postfix = "。") ?: ""
        val planLine = if (input.plans.isEmpty()) "" else "计划里，${triggered.size} 项本期有提醒记录，${active.size} 项仍在启用；这不代表完成状态。"
        return excerpt(
            "这一期留下了 ${input.diaries.size} 篇日记和 ${input.records.size} 个片段。真正被写下的事情包括：${facts.joinToString("；")}。$themeLine$planLine",
            420
        )
    }

    fun extractKeywords(texts: List<String>, tags: List<String> = emptyList()): List<String> {
        val counts = linkedMapOf<String, Int>()
        texts.forEach { text ->
            val terms = linkedSetOf<String>()
            val lower = text.lowercase()
            topicWords.filter(lower::contains).forEach(terms::add)
            Regex("[A-Za-z][A-Za-z0-9_-]{2,}").findAll(lower).map { it.value }.forEach(terms::add)
            Regex("《([^》]{2,16})》").findAll(text).map { it.groupValues[1].trim() }.forEach(terms::add)
            FOCUS_PATTERN.findAll(text).map { normalizeTopic(it.groupValues[1]) }.filter(String::isNotBlank).forEach(terms::add)
            text.split(Regex("[，。！？；、,.!?;\\n]")).map(::normalizeTopic)
                .filter { it.length in 2..8 && it !in stopWords && topicWords.any(it::contains) }
                .forEach(terms::add)
            terms.filter { it !in stopWords && weatherWords.none { weather -> it == weather } }.forEach { token ->
                counts[token] = (counts[token] ?: 0) + 1
            }
        }
        tags.mapNotNull(::topicFromTag).distinct().forEach { token -> counts[token] = (counts[token] ?: 0) + 2 }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }.take(8)
    }

    private data class FragmentCandidate(val date: String, val kind: String, val text: String, val priority: Int)

    private fun selectFragments(input: Input): List<String> {
        val candidates = input.diaries.map { diary ->
            FragmentCandidate(
                diary.date,
                "日记",
                diary.summary.ifBlank { diary.diaryText.ifBlank { diary.title } },
                100
            )
        } + input.records.map { record ->
            FragmentCandidate(record.date, "片段", record.content, record.importance.coerceIn(0, 10) * 8)
        }
        val selected = mutableListOf<FragmentCandidate>()
        val perDate = mutableMapOf<String, Int>()
        val seen = mutableListOf<String>()
        candidates.sortedWith(compareByDescending<FragmentCandidate> { it.priority }.thenByDescending { it.date }).forEach { item ->
            if (selected.size >= 8 || (perDate[item.date] ?: 0) >= 2) return@forEach
            val text = item.text.replace(Regex("\\s+"), " ").trim()
            val key = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
            if (key.length < 2 || seen.any { previous -> previous == key || previous.length >= 8 && (previous.contains(key) || key.contains(previous)) }) return@forEach
            selected += item.copy(text = text)
            seen += key
            perDate[item.date] = (perDate[item.date] ?: 0) + 1
        }
        return selected.sortedWith(compareBy<FragmentCandidate> { it.date }.thenByDescending { it.priority })
            .map { "${it.date.takeLast(5).replace('-', '.')} · ${it.kind}｜${excerpt(it.text, 108)}" }
    }

    private fun evidenceLabel(entity: LifeJournalEntityPolicy.Entity): String = when {
        entity.distinctDates >= 2 -> "${entity.distinctDates} 个记录日"
        entity.tagged -> listOfNotNull(entity.dates.lastOrNull()?.takeLast(5)?.replace('-', '.'), "明确标签").joinToString(" · ")
        else -> listOfNotNull(
            entity.dates.lastOrNull()?.takeLast(5)?.replace('-', '.'),
            when {
                entity.sourceKinds == setOf("diary") -> "日记"
                entity.sourceKinds == setOf("record") -> "片段"
                else -> null
            }
        ).joinToString(" · ").ifBlank { "一次明确提及" }
    }

    private fun entityChapterBody(entities: LifeJournalEntityPolicy.Result, summary: String): String {
        fun names(values: List<LifeJournalEntityPolicy.Entity>): String = values.take(3).joinToString("、") { it.name } +
            if (values.size > 3) "等" else ""
        val facts = buildList {
            if (entities.people.isNotEmpty()) add("人物是${names(entities.people)}")
            if (entities.places.isNotEmpty()) add("地点是${names(entities.places)}")
        }.joinToString("；")
        return "本期从日记与片段中确认$summary：$facts。这里只有明确称呼、人物/地点标签，或带实际行动语境的地名；模糊代词、抽象词和句子残片不会被编入。"
    }

    private fun chapterNumber(chapters: List<LifeJournalChapter>): String =
        (chapters.size + 1).toString().padStart(2, '0')

    private fun topicFromTag(raw: String): String? {
        val tag = raw.trim().removePrefix("#").replace('：', ':')
        val prefix = tag.substringBefore(':', "").trim().lowercase()
        if (prefix in setOf("人物", "人", "与谁", "person", "people", "地点", "位置", "地方", "place", "location", "天气", "心情", "情绪")) return null
        val value = if (':' in tag) tag.substringAfter(':') else tag
        return normalizeTopic(value).takeIf { it.length in 2..12 && it !in stopWords }
    }

    private fun normalizeTopic(raw: String): String {
        var value = raw.lowercase().replace(Regex("\\s+"), " ").trim()
            .trim('，', '。', '、', '！', '？', ',', '.', '!', '?', '：', ':', '“', '”', '‘', '’')
        TOPIC_NOISE.forEach { prefix -> value = value.removePrefix(prefix) }
        return value.removeSuffix("了").removeSuffix("的").trim().take(16)
    }

    private fun mergeWeather(input: Input): List<WeatherObservation> {
        val extracted = mutableListOf<WeatherObservation>()
        fun inspect(date: String, text: String) {
            val temperature = LifeJournalClimateScale.extractTemperature(text)
            val label = weatherWords.firstOrNull(text::contains).orEmpty()
            if (temperature != null || label.isNotBlank()) extracted += WeatherObservation(date, temperature, label)
        }
        input.records.forEach { inspect(it.date, it.content) }
        input.diaries.forEach { inspect(it.date, "${it.summary} ${it.diaryText}") }
        val merged = (input.weatherObservations + extracted)
            .groupBy { it.date }
            .map { (date, values) ->
                WeatherObservation(date, values.firstNotNullOfOrNull { it.temperatureC }, values.firstOrNull { it.label.isNotBlank() }?.label.orEmpty())
            }.sortedBy { it.date }.toMutableList()
        input.currentWeatherNote?.takeIf(String::isNotBlank)?.let { note ->
            if (merged.none { note.contains(it.date.takeLast(5).replace('-', '/')) }) {
                val date = input.diaries.maxByOrNull { it.date }?.date ?: input.records.maxByOrNull { it.date }?.date
                date?.let { merged += WeatherObservation(it, LifeJournalClimateScale.extractTemperature(note), weatherWords.firstOrNull(note::contains).orEmpty()) }
            }
        }
        return merged.sortedBy { it.date }
    }

    private fun moodPoints(
        records: List<LifeRecord>,
        diaries: List<DailyDiary>,
        weather: List<WeatherObservation>
    ): List<LifeJournalMoodPoint> {
        val weatherByDate = weather.associateBy { it.date }
        val recordsByDate = records.groupBy { it.date }
        val diaryByDate = diaries.associateBy { it.date }
        val dates = (records.map { it.date } + diaries.map { it.date } + weather.map { it.date }).distinct().sorted()
        return dates.map { date ->
            val diary = diaryByDate[date]
            val dayRecords = recordsByDate[date].orEmpty()
            val rawMood = diary?.mood?.trim().orEmpty().ifBlank { dayRecords.firstNotNullOfOrNull { it.mood?.trim()?.takeIf(String::isNotBlank) }.orEmpty() }
            val text = buildString {
                append(rawMood).append(' ')
                diary?.let { append(it.summary).append(' ').append(it.diaryText) }
                dayRecords.forEach { append(' ').append(it.content).append(' ').append(it.mood.orEmpty()) }
            }
            val textScore = textMoodScore(text)
            val colorScore = LifeJournalClimateScale.colorToMoodScore(diary?.color)
            val hasMood = rawMood.isNotBlank() || diary != null || dayRecords.any { !it.mood.isNullOrBlank() }
            val score = when {
                !hasMood -> 0f
                colorScore != null && textScore != 0f -> textScore * .62f + colorScore * .38f
                colorScore != null -> colorScore
                else -> textScore
            }.coerceIn(-1f, 1f)
            val label = if (!hasMood) "" else rawMood.take(7).ifBlank { labelFor(score) }
            val observation = weatherByDate[date]
            LifeJournalMoodPoint(
                date = date,
                label = label,
                score = score,
                moodColor = diary?.color?.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) },
                temperatureC = observation?.temperatureC,
                weatherLabel = observation?.label?.takeIf(String::isNotBlank)
            )
        }
    }

    private fun textMoodScore(text: String): Float {
        val pos = positive.count(text::contains)
        val neg = negative.count(text::contains)
        return ((pos - neg).coerceIn(-4, 4) / 4f)
    }

    private fun labelFor(score: Float): String = when {
        score > .45f -> "明亮"
        score > .12f -> "舒展"
        score < -.45f -> "低沉"
        score < -.12f -> "紧绷"
        else -> "平静"
    }

    private fun excerpt(value: String, max: Int): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= max) return clean
        val window = clean.take(max)
        val boundary = window.indexOfLast { it in "。！？.!?；;" }
        return if (boundary >= (max * .55f).toInt()) {
            window.take(boundary + 1).trim()
        } else {
            window.dropLast(1).trimEnd('，', '、', ',', '；', ';', '：', ':', ' ') + "…"
        }
    }

    private val FOCUS_PATTERN = Regex(
        "(?:关于|想(?:要)?|准备|继续|开始|完成|担心|期待|决定|计划)([\\p{IsHan}A-Za-z0-9_-]{2,12}?)(?=了|的|，|。|、|！|？|\\s|$)"
    )
    private val TOPIC_NOISE = listOf("今天", "昨天", "明天", "最近", "这次", "这周", "这个月", "我觉得", "感觉", "我想", "想要", "准备", "继续", "开始")
}
