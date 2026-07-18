package com.example.myapplication.domain

import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.policy.ForeshadowPolicy
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Constrains LLM interpretation to grounded, machine-readable decisions.
 * A malformed or ungrounded response returns null so callers can use local rules.
 */
class ForeshadowInterpreter(
    private val complete: suspend (systemPrompt: String, userPrompt: String) -> String?
) {
    data class CandidateDecision(
        val qualifies: Boolean,
        val candidate: ForeshadowPolicy.Candidate? = null
    )

    data class RelationDecision(
        val matches: Boolean,
        val relation: Relation = Relation.NO_MATCH,
        val confidence: Float = 0f,
        val evidenceQuote: String = ""
    )

    enum class Relation {
        NO_MATCH,
        PROGRESS,
        RESOLVED,
        CHANGED,
        ABANDONED,
        CONTINUING
    }

    suspend fun interpretCandidate(
        sourceText: String,
        sourceCreatedAt: Long,
        fallback: ForeshadowPolicy.Candidate
    ): CandidateDecision? {
        val response = complete(EXTRACTION_SYSTEM_PROMPT, extractionUserPrompt(sourceText))
            ?.takeIf(String::isNotBlank)
            ?: return null
        return parseCandidate(response, sourceText, sourceCreatedAt, fallback)
    }

    suspend fun interpretRelation(
        thread: EchoForeshadow,
        sourceText: String
    ): RelationDecision? {
        val response = complete(RELATION_SYSTEM_PROMPT, relationUserPrompt(thread, sourceText))
            ?.takeIf(String::isNotBlank)
            ?: return null
        return parseRelation(response, sourceText)
    }

    internal fun parseCandidate(
        response: String,
        sourceText: String,
        sourceCreatedAt: Long,
        fallback: ForeshadowPolicy.Candidate
    ): CandidateDecision? {
        val json = parseObject(response) ?: return null
        val qualifies = json.boolean("qualifies") ?: return null
        if (!qualifies) return CandidateDecision(qualifies = false)

        val subject = json.string("subject")?.trim()?.takeIf { it.length in 2..48 } ?: return null
        val question = json.string("followUpQuestion")?.trim()?.takeIf { it.length in 4..90 } ?: return null
        val evidence = json.string("evidenceQuote")?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (!sourceText.contains(evidence)) return null
        if (!subjectIsGrounded(subject, evidence)) return null

        val confidence = json.float("confidence")?.coerceIn(0f, 1f) ?: return null
        if (confidence < 0.60f) return CandidateDecision(qualifies = false)
        val horizonDays = (json.int("horizonDays") ?: horizonFromLabel(json.string("horizon")))
            ?.coerceIn(2, 90)
            ?: ((fallback.nextCheckAt - sourceCreatedAt) / ForeshadowPolicy.DAY_MILLIS).toInt()
                .coerceIn(2, 90)

        val title = subject
            .trim('，', '。', '！', '？', ',', '.', '!', '?')
            .take(24)
        return CandidateDecision(
            qualifies = true,
            candidate = ForeshadowPolicy.Candidate(
                subject = subject,
                title = title,
                followUpQuestion = question,
                confidence = confidence,
                nextCheckAt = sourceCreatedAt + horizonDays * ForeshadowPolicy.DAY_MILLIS
            )
        )
    }

    internal fun parseRelation(response: String, sourceText: String): RelationDecision? {
        val json = parseObject(response) ?: return null
        val matches = json.boolean("matches") ?: return null
        if (!matches) return RelationDecision(matches = false)

        val relation = json.string("relation")
            ?.let { runCatching { Relation.valueOf(it.uppercase()) }.getOrNull() }
            ?: return null
        if (relation == Relation.NO_MATCH) return RelationDecision(matches = false)
        val evidence = json.string("evidenceQuote")?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (!sourceText.contains(evidence)) return null
        val confidence = json.float("confidence")?.coerceIn(0f, 1f) ?: return null
        if (confidence < 0.60f) return RelationDecision(matches = false)
        return RelationDecision(true, relation, confidence, evidence)
    }

    private fun subjectIsGrounded(subject: String, evidence: String): Boolean {
        val normalizedSubject = ForeshadowPolicy.normalizeSubject(subject)
        val normalizedEvidence = ForeshadowPolicy.normalizeSubject(evidence)
        if (normalizedSubject.length < 2 || normalizedEvidence.length < 2) return false
        if (normalizedEvidence.contains(normalizedSubject)) return true
        val meaningful = normalizedSubject.toSet()
        return meaningful.count { it in normalizedEvidence }.toDouble() / meaningful.size >= 0.55
    }

    private fun parseObject(value: String): JsonObject? = runCatching {
        val start = value.indexOf('{')
        val end = value.lastIndexOf('}')
        if (start < 0 || end <= start) return@runCatching null
        JsonParser.parseString(value.substring(start, end + 1)).asJsonObject
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.boolean(key: String): Boolean? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()

    private fun JsonObject.float(key: String): Float? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asFloat }.getOrNull()

    private fun JsonObject.int(key: String): Int? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()

    private fun horizonFromLabel(value: String?): Int? = when (value?.lowercase()) {
        "days" -> 4
        "weeks" -> 14
        "months" -> 45
        else -> null
    }

    private fun extractionUserPrompt(sourceText: String) = """
        <source>
        $sourceText
        </source>

        判断这段原文是否包含一个值得未来回访、结局尚不确定的人生线索。
        明确时间和动作的待办、已经完成的事情、稳定事实、随口愿望都不属于伏笔。
    """.trimIndent()

    private fun relationUserPrompt(thread: EchoForeshadow, sourceText: String) = """
        <existing_story>
        主题：${thread.subject}
        最初证据：${thread.sourceRefs.firstOrNull()?.excerpt.orEmpty()}
        </existing_story>

        <new_source>
        $sourceText
        </new_source>

        判断新原文是否确实推进了同一个故事。不要只因出现相似的常用词就判定相关。
    """.trimIndent()

    companion object {
        private val EXTRACTION_SYSTEM_PROMPT = """
            你是 Echo 的伏笔提炼器。只依据 source 原文，不补充未出现的人物、事件或动机。
            只输出一个 JSON 对象，不要 Markdown：
            {"qualifies":true|false,"subject":"简洁主题","followUpQuestion":"未来回访问题","horizonDays":2-90,"confidence":0-1,"evidenceQuote":"source 中逐字存在的短句"}
            若不适合成为伏笔，只需输出：{"qualifies":false}
            evidenceQuote 必须逐字复制原文；问题应温和、开放，不预设结果。
        """.trimIndent()

        private val RELATION_SYSTEM_PROMPT = """
            你是 Echo 的故事关系判断器。只依据 existing_story 与 new_source，不推测。
            只输出一个 JSON 对象，不要 Markdown：
            {"matches":true|false,"relation":"NO_MATCH|PROGRESS|RESOLVED|CHANGED|ABANDONED|CONTINUING","confidence":0-1,"evidenceQuote":"new_source 中逐字存在的短句"}
            若不是同一个故事，输出：{"matches":false,"relation":"NO_MATCH","confidence":0}
            evidenceQuote 必须逐字复制 new_source。宁可不匹配，也不要牵强连接。
        """.trimIndent()
    }
}
