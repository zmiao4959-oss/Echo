package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.model.LifeJournalChapter
import com.example.myapplication.data.model.LifeJournalMoodPoint
import com.example.myapplication.data.model.LifeRecord
import java.text.SimpleDateFormat
import java.util.Locale

object LifeJournalPolicy {
    data class ConversationExcerpt(val title: String, val text: String)

    data class Input(
        val records: List<LifeRecord>,
        val diaries: List<DailyDiary>,
        val plans: List<EchoPlan>,
        val conversations: List<ConversationExcerpt>,
        val startMillis: Long,
        val endMillis: Long,
        val currentWeatherNote: String? = null
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
        "今天", "一个", "一些", "这个", "那个", "然后", "但是", "还是", "已经", "没有", "自己",
        "觉得", "感觉", "因为", "所以", "可以", "可能", "真的", "比较", "还有", "事情", "时候",
        "我们", "你们", "他们", "什么", "怎么", "就是", "不是", "一下", "现在", "最近", "echo",
        "the", "and", "but", "can", "could", "would", "should", "this", "that", "with", "from", "have", "has",
        "had", "just", "really", "think", "make", "made", "your", "you", "our", "are", "was", "were", "new",
        "day", "today"
    )
    private val weatherWords = listOf("晴", "阴", "多云", "下雨", "雨", "雪", "风", "雾", "雷", "闷热", "炎热", "凉爽", "寒冷")
    private val placeMarkers = listOf("公园", "医院", "学校", "公司", "办公室", "书店", "咖啡馆", "餐厅", "车站", "机场", "家里", "路上")
    private val positive = listOf("开心", "愉快", "平静", "轻松", "期待", "满足", "兴奋", "幸福", "不错", "顺利")
    private val negative = listOf("难过", "焦虑", "疲惫", "生气", "失落", "紧张", "烦", "累", "沮丧", "孤独")

    fun build(input: Input): Output {
        val allTexts = input.records.map { it.content } +
            input.diaries.flatMap { listOf(it.title, it.summary, it.diaryText) } +
            input.conversations.map { it.text } + input.plans.flatMap { listOf(it.title, it.message) }
        val keywords = extractKeywords(allTexts)
        val weather = (listOfNotNull(input.currentWeatherNote) + weatherWords.filter { word -> allTexts.any { it.contains(word) } }).distinct().take(5)
        val locations = extractLocations(allTexts, input.records.flatMap { it.tags } + input.diaries.flatMap { it.tags })
        val people = extractPeople(allTexts, input.records.flatMap { it.tags } + input.diaries.flatMap { it.tags })
        val completed = input.plans.filter { (it.lastTriggeredAt ?: Long.MIN_VALUE) in input.startMillis..input.endMillis }
        val pending = input.plans.filter { it.enabled && it !in completed }
        val fragments = (input.records.sortedWith(compareByDescending<LifeRecord> { it.importance }.thenByDescending { it.createdAt })
            .map { it.content } + input.diaries.sortedByDescending { it.date }.map { it.summary })
            .map { clean(it, 96) }.filter { it.isNotBlank() }.distinct().take(8)
        val overview = buildOverview(input, keywords, completed, pending)
        val recurring = keywords.filter { key -> allTexts.count { it.contains(key, ignoreCase = true) } >= 2 }

        val chapters = listOf(
            LifeJournalChapter(
                id = "fragments", eyebrow = "CHAPTER 01", title = "片段",
                body = if (fragments.isEmpty()) "这一期还没有留下可编入的生活片段。" else "这些句子构成了本期生活最清晰的切面。它们保持原意，不替你补写情节。",
                fragments = fragments,
                sourceLabels = input.records.take(8).map { "生活片段 · ${it.date}" }
            ),
            LifeJournalChapter(
                id = "people", eyebrow = "CHAPTER 02", title = "人物",
                body = if (people.isEmpty()) "本期记录里没有足够明确的人物线索，因此这一页保持留白。" else "反复被写到或被标签标记的人：${people.joinToString("、")}。",
                fragments = people,
                sourceLabels = listOf("仅依据正文称呼与人物标签")
            ),
            LifeJournalChapter(
                id = "places", eyebrow = "CHAPTER 03", title = "地点",
                body = if (locations.isEmpty()) "本期没有可确认的地点线索。" else "生活在这些具体空间里展开：${locations.joinToString("、")}。",
                fragments = locations,
                sourceLabels = listOf("仅依据正文地点词与地点标签")
            ),
            LifeJournalChapter(
                id = "thoughts", eyebrow = "CHAPTER 04", title = "反复出现的念头",
                body = if (recurring.isEmpty()) "还没有形成足够稳定的重复主题。" else "这些词在不同记录中再次出现：${recurring.joinToString("、")}。它们不是结论，只是值得回看的回声。",
                fragments = recurring,
                sourceLabels = listOf("跨日记、片段、计划与对话的重复词")
            ),
            LifeJournalChapter(
                id = "plans", eyebrow = "CHAPTER 05", title = "完成与未完成",
                body = "本期完成 ${completed.size} 项，仍在进行 ${pending.size} 项。未完成不是负面判断，只保留为下一期的开放线索。",
                fragments = (completed.map { "已完成 · ${it.title}" } + pending.map { "进行中 · ${it.title}" }).take(10),
                sourceLabels = listOf("计划触发记录")
            ),
            LifeJournalChapter(
                id = "echo", eyebrow = "CHAPTER 06", title = "与 Echo 的谈话",
                body = if (input.conversations.isEmpty()) "本期没有可确认的 Echo 会话。" else "选入 ${input.conversations.size} 段本期活跃会话，只保留用户表达，不把 Echo 的回应当作你的事实。",
                fragments = input.conversations.map { "${it.title}｜${clean(it.text, 90)}" }.take(8),
                sourceLabels = listOf("会话按本期活跃时间归档")
            )
        )

        return Output(
            titleSeed = titleSeed(input, keywords),
            overview = overview,
            chapters = chapters,
            moodPoints = moodPoints(input.records, input.diaries),
            keywords = keywords,
            weatherNotes = weather,
            sourceCounts = linkedMapOf(
                "日记" to input.diaries.size,
                "片段" to input.records.size,
                "计划" to input.plans.size,
                "Echo 对话" to input.conversations.size
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

    private fun buildOverview(input: Input, keywords: List<String>, completed: List<EchoPlan>, pending: List<EchoPlan>): String {
        val anchors = input.diaries.sortedBy { it.date }.map { clean(it.summary.ifBlank { it.diaryText }, 58) }
            .filter { it.isNotBlank() }.take(4)
        val recordAnchors = input.records.sortedWith(compareByDescending<LifeRecord> { it.importance }.thenBy { it.createdAt })
            .map { clean(it.content, 58) }.filter { it.isNotBlank() }.take((4 - anchors.size).coerceAtLeast(1))
        val facts = (anchors + recordAnchors).distinct()
        if (facts.isEmpty()) return "这一期暂时没有足够的文字材料。生活志已经把空白保留下来，等待你继续记录。"
        val opening = "这一期留下了 ${input.diaries.size} 篇日记和 ${input.records.size} 个片段。"
        val happening = facts.joinToString("；")
        val themes = if (keywords.isEmpty()) "" else " 反复出现的线索是：${keywords.take(4).joinToString("、")}。"
        val planLine = if (input.plans.isEmpty()) "" else " 计划里，${completed.size} 项已完成，${pending.size} 项仍在继续。"
        return clean("$opening 真正被写下的事情包括：$happening。$themes$planLine", 360)
    }

    fun extractKeywords(texts: List<String>): List<String> {
        val counts = linkedMapOf<String, Int>()
        texts.forEach { text ->
            Regex("[\\p{IsHan}]{2,6}|[A-Za-z][A-Za-z0-9_-]{2,}").findAll(text.lowercase()).forEach { match ->
                val token = match.value.trim()
                if (token !in stopWords && weatherWords.none { token == it } && token.length in 2..8) {
                    counts[token] = (counts[token] ?: 0) + 1
                }
            }
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }.take(8)
    }

    private fun extractLocations(texts: List<String>, tags: List<String>): List<String> {
        val fromText = placeMarkers.filter { marker -> texts.any { it.contains(marker) } }
        val fromTags = tags.filter { tag -> tag.startsWith("地点:") || tag.startsWith("地点：") }
            .map { it.substringAfter(':').substringAfter('：').trim() }
        return (fromText + fromTags).filter { it.isNotBlank() }.distinct().take(8)
    }

    private fun extractPeople(texts: List<String>, tags: List<String>): List<String> {
        val fromTags = tags.filter { it.startsWith("人物:") || it.startsWith("人物：") }
            .map { it.substringAfter(':').substringAfter('：').trim() }
        val fromText = texts.flatMap { text ->
            Regex("(?:和|跟|与|见到|遇到)([\\p{IsHan}]{2,4})").findAll(text).map { it.groupValues[1] }.toList()
        }.filterNot { it in stopWords || placeMarkers.any(it::contains) }
        return (fromTags + fromText).filter { it.isNotBlank() }.distinct().take(8)
    }

    private fun moodPoints(records: List<LifeRecord>, diaries: List<DailyDiary>): List<LifeJournalMoodPoint> {
        val byDate = linkedMapOf<String, MutableList<String>>()
        records.forEach { byDate.getOrPut(it.date) { mutableListOf() }.add(it.mood.orEmpty() + " " + it.content) }
        diaries.forEach { byDate.getOrPut(it.date) { mutableListOf() }.add(it.mood + " " + it.summary) }
        return byDate.toSortedMap().map { (date, values) ->
            val joined = values.joinToString(" ")
            val pos = positive.count { joined.contains(it) }
            val neg = negative.count { joined.contains(it) }
            val score = ((pos - neg).coerceIn(-3, 3) / 3f)
            val label = when { score > .34f -> "明亮"; score < -.34f -> "低沉"; else -> "平静" }
            LifeJournalMoodPoint(date, label, score)
        }
    }

    private fun clean(value: String, max: Int): String = value.replace(Regex("\\s+"), " ").trim().take(max)
}
