package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord

/**
 * Builds a small, local summary of what today's records are beginning to form.
 *
 * This deliberately prefers silence over weak guesses: themes only come from
 * explicit tags, well-known life topics, or phrases repeated across records.
 */
object TodayFormationPolicy {

    data class Formation(
        val recordCount: Int,
        val themes: List<String>,
        val mood: String?,
        val observation: String
    )

    private data class MoodRule(
        val label: String,
        val keywords: List<String>,
        val positive: Boolean = false
    )

    private val ignoredTags = setOf(
        "echo", "life_records", "life", "record", "记录", "生活", "片段", "今日", "今天"
    )

    private val topicRules = linkedMapOf(
        "工作" to listOf("工作", "上班", "下班", "项目", "需求", "会议", "客户", "同事", "代码", "开发", "加班"),
        "学习" to listOf("学习", "上课", "课程", "考试", "复习", "作业", "论文", "背单词"),
        "运动" to listOf("运动", "跑步", "健身", "游泳", "瑜伽", "骑行", "打球", "散步"),
        "饮食" to listOf("吃饭", "早餐", "午餐", "晚餐", "做饭", "咖啡", "奶茶", "餐厅", "好吃"),
        "家人" to listOf("家人", "爸妈", "爸爸", "妈妈", "父母", "孩子", "家里"),
        "朋友" to listOf("朋友", "聚会", "见面", "聊天", "同学"),
        "出行" to listOf("出门", "旅行", "旅游", "机场", "高铁", "地铁", "开车", "堵车", "到达"),
        "睡眠" to listOf("睡觉", "睡眠", "失眠", "早起", "熬夜", "午睡", "做梦"),
        "阅读" to listOf("读书", "阅读", "看书", "小说", "书店"),
        "创作" to listOf("写作", "画画", "摄影", "创作", "设计", "剪辑", "音乐", "练琴"),
        "健康" to listOf("身体", "健康", "医院", "医生", "生病", "感冒", "发烧", "头疼", "吃药"),
        "娱乐" to listOf("电影", "追剧", "游戏", "演出", "音乐会", "综艺"),
        "宠物" to listOf("猫咪", "小猫", "狗狗", "小狗", "宠物"),
        "天气" to listOf("下雨", "晴天", "天气", "降温", "下雪", "刮风")
    )

    private val moodRules = listOf(
        MoodRule("愉快", listOf("开心", "高兴", "快乐", "幸福", "惊喜", "愉快", "好棒", "喜欢"), positive = true),
        MoodRule("轻松", listOf("轻松", "放松", "惬意", "悠闲", "舒服"), positive = true),
        MoodRule("平静", listOf("平静", "安静", "宁静", "踏实", "安心"), positive = true),
        MoodRule("有成就感", listOf("完成", "搞定", "进展", "突破", "达成", "顺利"), positive = true),
        MoodRule("期待", listOf("期待", "盼望", "迫不及待", "兴奋"), positive = true),
        MoodRule("疲惫", listOf("疲惫", "好累", "累了", "很累", "困了", "好困", "乏力")),
        MoodRule("有些焦虑", listOf("焦虑", "担心", "紧张", "不安", "害怕", "着急", "压力")),
        MoodRule("低落", listOf("不开心", "难过", "伤心", "失落", "沮丧", "郁闷", "糟糕", "崩溃", "烦躁"))
    )

    fun build(records: List<LifeRecord>): Formation {
        val nonBlankRecords = records.filter { it.content.isNotBlank() }
        val recordsForAnalysis = nonBlankRecords.take(40)
        return Formation(
            recordCount = nonBlankRecords.size,
            themes = extractThemes(recordsForAnalysis, topN = 3),
            mood = extractMood(recordsForAnalysis),
            observation = observationFor(nonBlankRecords.size)
        )
    }

    fun extractThemes(records: List<LifeRecord>, topN: Int = 3): List<String> {
        if (topN <= 0) return emptyList()

        val explicitTags = records.asSequence()
            .flatMap { it.tags.asSequence() }
            .map { it.trim().removePrefix("#") }
            .filter { it.length in 2..10 && it.lowercase() !in ignoredTags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

        val combined = records.joinToString("\n") { it.content.take(300) }
        val topicLabels = topicRules.mapNotNull { (label, keywords) ->
            val score = keywords.count { combined.contains(it, ignoreCase = true) }
            label.takeIf { score > 0 }?.let { it to score }
        }.sortedByDescending { it.second }.map { it.first }

        val repeatedPhrases = repeatedPhrases(records.map { it.content.take(300) })
        return (explicitTags + topicLabels + repeatedPhrases)
            .distinct()
            .take(topN)
    }

    fun extractMood(records: List<LifeRecord>): String? {
        val explicit = records.mapNotNull { it.mood?.trim()?.takeIf(String::isNotEmpty) }
        if (explicit.isNotEmpty()) {
            return explicit.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }

        val text = records.joinToString("\n") { it.content.take(300) }
        return moodRules.map { rule ->
            val score = rule.keywords.count { keyword ->
                text.contains(keyword, ignoreCase = true) &&
                    (!rule.positive || !isNegated(text, keyword))
            }
            rule.label to score
        }.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first
    }

    private fun repeatedPhrases(texts: List<String>): List<String> {
        if (texts.size < 2) return emptyList()
        val perRecordPhrases = texts.map { text ->
            val clean = listOf("今天", "刚刚", "然后", "后来")
                .fold(text) { result, filler -> result.replace(filler, "") }
            buildSet {
                for (length in 4 downTo 2) {
                    for (start in 0..clean.length - length) {
                        val phrase = clean.substring(start, start + length)
                        if (phrase.all(::isCjk) && phrase.none { it in cjkNoiseChars }) add(phrase)
                    }
                }
            }
        }

        val counts = mutableMapOf<String, Int>()
        perRecordPhrases.forEach { phrases -> phrases.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { it.key.length }
                .thenBy { it.key })
            .map { it.key }
            .filter { candidate ->
                counts.keys.none { longer ->
                    longer.length > candidate.length && longer.contains(candidate) && (counts[longer] ?: 0) >= 2
                }
            }
            .take(2)
    }

    private fun isNegated(text: String, keyword: String): Boolean =
        listOf("不$keyword", "没$keyword", "没有$keyword").any { text.contains(it) }

    private fun observationFor(count: Int): String = when (count) {
        0 -> "从第一条片段开始，今天会慢慢有轮廓。"
        1 -> "一个瞬间被认真留下，今天已经有了开头。"
        2 -> "两个片段开始彼此照应，今天的线索正在浮现。"
        in 3..4 -> "几个分散的瞬间，正在连成今天的轮廓。"
        in 5..7 -> "今天已经积累出一条清晰的生活轨迹，晚些时候回看会更完整。"
        else -> "今天被你记录得很具体，这些片段会让回看更有温度。"
    }

    private val cjkNoiseChars = setOf('的', '了', '呢', '吗', '吧', '啊', '是', '我', '你', '他', '她', '它', '和', '与', '在', '又', '也', '都', '很')

    private fun isCjk(char: Char): Boolean = char in '\u3400'..'\u9FFF'
}
