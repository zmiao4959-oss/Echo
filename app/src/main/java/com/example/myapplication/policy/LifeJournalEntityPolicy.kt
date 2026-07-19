package com.example.myapplication.policy

/**
 * Conservative entity extraction for the life journal.
 *
 * A publication should prefer an honest blank to turning an arbitrary Chinese
 * phrase into a person or place. Structured tags are therefore strongest;
 * free text is accepted only when it appears in an explicit social/spatial
 * context. Counts are source-level rather than raw regex-hit counts.
 */
object LifeJournalEntityPolicy {
    data class Source(
        val id: String,
        val date: String,
        val text: String,
        val tags: List<String> = emptyList()
    )

    data class Entity(
        val name: String,
        val evidenceCount: Int,
        val distinctDates: Int,
        val tagged: Boolean,
        val dates: List<String> = emptyList(),
        val sourceKinds: Set<String> = emptySet()
    )

    data class Result(
        val people: List<Entity>,
        val places: List<Entity>
    )

    fun extract(sources: List<Source>, limitPerKind: Int = 8): Result {
        val people = linkedMapOf<String, Evidence>()
        val places = linkedMapOf<String, Evidence>()

        sources.forEach { source ->
            val peopleInSource = linkedMapOf<String, Mention>()
            val placesInSource = linkedMapOf<String, Mention>()

            source.tags.forEach { tag ->
                taggedValue(tag, PEOPLE_TAGS)?.let { addMention(peopleInSource, normalizePerson(it, tagged = true), tagged = true) }
                taggedValue(tag, PLACE_TAGS)?.let { addMention(placesInSource, normalizePlace(it, tagged = true), tagged = true) }
            }

            PERSON_CONTEXT.findAll(source.text).forEach { match ->
                addMention(peopleInSource, normalizePerson(match.groupValues[1], tagged = false), tagged = false)
            }
            RELATION_TERMS.filter(source.text::contains).forEach { relation ->
                addMention(peopleInSource, normalizePerson(relation, tagged = false), tagged = false)
            }

            MOVEMENT_PLACE_CONTEXT.findAll(source.text).forEach { match ->
                addMention(placesInSource, normalizePlace(match.groupValues[1], movementContext = true), tagged = false)
            }
            ARRIVAL_PLACE_CONTEXT.findAll(source.text).forEach { match ->
                addMention(placesInSource, normalizePlace(match.groupValues[1], movementContext = true), tagged = false)
            }
            LOCATIVE_PLACE_CONTEXT.findAll(source.text).forEach { match ->
                addMention(placesInSource, normalizePlace(match.groupValues[1]), tagged = false)
            }
            // Generic places are useful only when no more specific place ending
            // with the same marker was already found in this source.
            PLACE_MARKERS.filter(source.text::contains).forEach { marker ->
                if (placesInSource.keys.none { it.length > marker.length && it.endsWith(marker) }) {
                    addMention(placesInSource, normalizePlace(marker), tagged = false)
                }
            }

            peopleInSource.values.forEach { mention -> mergeEvidence(people, mention, source) }
            placesInSource.values.forEach { mention -> mergeEvidence(places, mention, source) }
        }

        return Result(
            people = ranked(people).take(limitPerKind),
            places = ranked(places).take(limitPerKind)
        )
    }

    private data class Mention(val key: String, val display: String, val tagged: Boolean)
    private data class Evidence(
        var display: String,
        var tagged: Boolean = false,
        val sourceIds: MutableSet<String> = linkedSetOf(),
        val dates: MutableSet<String> = linkedSetOf(),
        val sourceKinds: MutableSet<String> = linkedSetOf()
    )

    private fun addMention(target: MutableMap<String, Mention>, value: String, tagged: Boolean) {
        if (value.isBlank()) return
        val key = canonicalKey(value)
        if (key.isBlank()) return
        val existing = target[key]
        if (existing == null || tagged && !existing.tagged) target[key] = Mention(key, value, tagged)
    }

    private fun mergeEvidence(target: MutableMap<String, Evidence>, mention: Mention, source: Source) {
        val evidence = target.getOrPut(mention.key) { Evidence(mention.display) }
        if (mention.tagged || evidence.display.length < mention.display.length) evidence.display = mention.display
        evidence.tagged = evidence.tagged || mention.tagged
        evidence.sourceIds += source.id
        source.date.takeIf(String::isNotBlank)?.let(evidence.dates::add)
        source.id.substringBefore(':').takeIf { it in setOf("record", "diary") }?.let(evidence.sourceKinds::add)
    }

    private fun ranked(values: Map<String, Evidence>): List<Entity> = values.values
        .map { Entity(it.display, it.sourceIds.size, it.dates.size, it.tagged, it.dates.sorted(), it.sourceKinds.toSet()) }
        .sortedWith(
            compareByDescending<Entity> { it.tagged }
                .thenByDescending { it.distinctDates }
                .thenByDescending { it.evidenceCount }
                .thenBy { it.name.length }
                .thenBy { it.name }
        )

    private fun taggedValue(tag: String, acceptedPrefixes: Set<String>): String? {
        val normalized = tag.trim().removePrefix("#").replace('：', ':')
        val separator = normalized.indexOf(':')
        if (separator <= 0) return null
        val prefix = normalized.substring(0, separator).trim().lowercase()
        if (prefix !in acceptedPrefixes) return null
        return normalized.substring(separator + 1).trim().takeIf(String::isNotBlank)
    }

    private fun normalizePerson(raw: String, tagged: Boolean): String {
        val value = clean(raw)
            .removePrefix("我的")
            .removePrefix("一位")
            .removePrefix("那个")
            .trim()
        if (value.length !in 1..8) return ""
        if (value in PERSON_REJECTS || PLACE_MARKERS.any(value::contains)) return ""
        if (ACTION_WORDS.any { value.endsWith(it) }) return ""
        if (!tagged && !looksLikePerson(value)) return ""
        return value
    }

    private fun normalizePlace(raw: String, tagged: Boolean = false, movementContext: Boolean = false): String {
        var value = clean(raw)
        LEADING_NOISE.forEach { prefix -> value = value.removePrefix(prefix) }
        value = when (value) {
            "家里", "家中", "家里边", "家附近" -> "家"
            else -> value.removeSuffix("里面").removeSuffix("里边").removeSuffix("附近")
        }.trim()
        if (value.length !in 1..14 || value in PLACE_REJECTS) return ""
        if (ABSTRACT_PLACE_WORDS.any(value::contains) || ACTION_WORDS.any { value == it || value.endsWith(it) }) return ""
        if (!tagged && !movementContext && !looksLikePlace(value)) return ""
        return value
    }

    private fun looksLikePerson(value: String): Boolean {
        if (value in RELATION_TERMS) return true
        if (ENGLISH_PERSON.matches(value)) return true
        if (CHINESE_NICKNAME.matches(value)) return true
        return value.length in 2..4 && value.first() in COMMON_SURNAMES && value.all { it.code in 0x4E00..0x9FFF }
    }

    private fun looksLikePlace(value: String): Boolean = value == "家" ||
        PLACE_MARKERS.any { value == it || value.endsWith(it) } ||
        PLACE_SUFFIXES.any(value::endsWith)

    private fun canonicalKey(value: String): String = value.lowercase()
        .replace(Regex("[\\s·•・,，。.!！?？、'\"“”‘’()（）【】\\[\\]]"), "")

    private fun clean(value: String): String = value.trim()
        .trim('，', '。', '、', '！', '？', ',', '.', '!', '?', '：', ':', '“', '”', '‘', '’', '（', '）', '(', ')')

    private val PERSON_CONTEXT = Regex(
        "(?:和|跟|(?<!参)与|遇到|见到|陪|约(?:了)?|联系(?:了)?)([\\p{IsHan}A-Za-z·.'-]{1,24}?)(?=一起|去|到|在|聊|说|吃|喝|看|逛|散步|工作|开会|见面|，|。|、|！|？|\\s|$)"
    )
    private val MOVEMENT_PLACE_CONTEXT = Regex(
        "(?:去(?:了|往)?|前往|抵达|路过|离开|回到|返回|从)([\\p{IsHan}A-Za-z0-9·]{1,14}?)(?=里|中|内|附近|工作|上班|散步|休息|吃|喝|看|见|开会|出差|旅行|，|。|、|！|？|\\s|$)"
    )
    private val ARRIVAL_PLACE_CONTEXT = Regex(
        "(?:^|[，。！？；、,;\\s])(?:我|我们|随后|终于|已经|今天|昨天|后来)?到(?:了)?([\\p{IsHan}A-Za-z0-9·]{1,14}?)(?=里|中|内|附近|工作|上班|散步|休息|吃|喝|看|见|开会|出差|旅行|，|。|、|！|？|\\s|$)"
    )
    private val LOCATIVE_PLACE_CONTEXT = Regex(
        "在([\\p{IsHan}A-Za-z0-9·]{1,14}?)(?=里|中|内|附近|工作|上班|散步|休息|吃|喝|看|见|开会|出差|旅行|，|。|、|！|？|\\s|$)"
    )

    private val PEOPLE_TAGS = setOf("人物", "人", "与谁", "person", "people")
    private val PLACE_TAGS = setOf("地点", "位置", "地方", "place", "location")
    private val RELATION_TERMS = listOf(
        "妈妈", "爸爸", "母亲", "父亲", "姐姐", "哥哥", "妹妹", "弟弟", "奶奶", "爷爷", "外婆", "外公",
        "伴侣", "爱人", "室友", "同事", "朋友", "老师", "医生"
    )
    private val PLACE_MARKERS = listOf(
        "咖啡馆", "办公室", "工作室", "图书馆", "博物馆", "美术馆", "体育馆", "电影院", "火车站", "地铁站",
        "公园", "医院", "学校", "公司", "书店", "餐厅", "机场", "车站", "商场", "市场", "酒店", "民宿", "教室", "宿舍", "家里", "家中"
    )
    private val PLACE_SUFFIXES = listOf(
        "省", "市", "区", "县", "镇", "乡", "村", "路", "街", "巷", "大道", "广场", "景区", "园区", "校区",
        "大厦", "码头", "港", "寺", "庙", "教堂", "山", "湖", "江", "河", "岛", "湾", "海滩"
    )
    private val ACTION_WORDS = setOf("聊天", "散步", "吃饭", "工作", "开会", "看书", "喝茶", "喝酒", "见面", "旅行")
    private val LEADING_NOISE = listOf("今天", "昨天", "明天", "后来", "然后", "准备", "想要", "想", "又", "还", "就")
    private val PERSON_REJECTS = setOf("大家", "别人", "有人", "一个人", "对方", "自己", "用户", "我们", "你们", "他们", "她们")
    private val PLACE_REJECTS = setOf(
        "这里", "那里", "哪里", "这边", "那边", "一个地方", "外面", "附近", "原地", "当地", "某地"
    )
    private val ABSTRACT_PLACE_WORDS = setOf(
        "答案", "心里", "心中", "感觉", "觉得", "事情", "问题", "状态", "时候", "活动", "任务", "计划", "目标", "结果", "阶段"
    )
    private val ENGLISH_PERSON = Regex("[A-Z][A-Za-z.'-]{1,23}")
    private val CHINESE_NICKNAME = Regex("[小老阿][\\p{IsHan}]{1,3}")
    private val COMMON_SURNAMES = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄穆萧尹姚邵汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯管卢莫房裘缪解应宗丁宣邓郁单杭洪包诸左石崔吉龚程嵇邢裴陆荣翁荀羊甄曲封芮储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓蓬全班仰秋仲伊宫宁仇栾暴甘厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲台从鄂索咸籍赖卓蔺屠蒙池乔阴胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍却璩桑桂濮牛寿通边扈燕冀郏浦尚农温别庄晏柴瞿阎连茹习艾鱼容向古易慎戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙东欧殳沃利蔚越夔隆师巩厍聂晁勾敖融冷辛阚那简饶空曾毋沙乜养鞠须丰巢关蒯相查后荆红游竺权逯盖益桓公".toSet()
}
