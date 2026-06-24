package com.example.myapplication.tts

/**
 * 豆包 TTS <mood> 标签解析器 — 移植自 clawspeaker tts/parser.py。
 *
 * 输入示例:
 *   <mood>用害羞的语气说</mood>哼！谁让你是我的主人呢。
 *   <mood>声音渐渐变小</mood>不过……我这么宠你。
 *   所以！五分钟到了就起来，好不好？
 */
object TTSParser {

    private val MOOD_TAG_RE = Regex("<mood>(.*?)</mood>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * 一段待合成的语音。
     */
    data class TTSSegment(
        val text: String,
        val mood: String? = null
    )

    /**
     * 解析剧本文本，返回若干 TTSSegment。
     */
    fun parseMoodScript(script: String): List<TTSSegment> {
        if (script.isBlank()) return emptyList()

        val segments = mutableListOf<TTSSegment>()
        var lastEnd = 0

        for (match in MOOD_TAG_RE.findAll(script)) {
            val prefix = script.substring(lastEnd, match.range.first).trim()
            if (prefix.isNotEmpty()) {
                segments.add(TTSSegment(text = prefix, mood = null))
            }

            val mood = match.groupValues[1].trim()
            val contentStart = match.range.last + 1
            val nextMatch = MOOD_TAG_RE.find(script, contentStart)
            val hardEnd = nextMatch?.range?.first ?: script.length
            val region = script.substring(contentStart, hardEnd)

            // mood 只绑定紧跟的一段正文
            val paraBreak = region.indexOf("\n\n")
            val (speech, newLastEnd) = if (paraBreak != -1) {
                region.substring(0, paraBreak).trim() to (contentStart + paraBreak + 2)
            } else {
                region.trim() to hardEnd
            }

            if (speech.isNotEmpty()) {
                segments.add(TTSSegment(text = speech, mood = mood.ifEmpty { null }))
            }
            lastEnd = newLastEnd
        }

        val suffix = script.substring(lastEnd).trim()
        if (suffix.isNotEmpty()) {
            segments.add(TTSSegment(text = suffix, mood = null))
        }

        return segments
    }

    /**
     * 合并相邻且 mood 相同的段。
     */
    fun mergeAdjacentSegments(segments: List<TTSSegment>): List<TTSSegment> {
        if (segments.isEmpty()) return emptyList()

        val merged = mutableListOf<TTSSegment>()
        for (seg in segments) {
            if (merged.isEmpty()) {
                merged.add(seg)
                continue
            }
            val prev = merged.last()
            if (prev.mood == seg.mood) {
                merged[merged.lastIndex] = TTSSegment(
                    text = joinSegmentText(prev.text, seg.text),
                    mood = prev.mood
                )
            } else {
                merged.add(seg)
            }
        }
        return merged
    }

    private fun joinSegmentText(left: String, right: String): String {
        val l = left.trim()
        val r = right.trim()
        if (l.isEmpty()) return r
        if (r.isEmpty()) return l
        if (l.last() in "。！？.!?…~～") return l + r
        if (r.first() in "，。！？、") return l + r
        return "$l$r"
    }

    /**
     * 构建豆包 TTS API 请求体。
     */
    fun buildPayload(
        segment: TTSSegment,
        speaker: String,
        uid: String = "clawspeaker_android",
        sectionId: String? = null,
        disableMarkdownFilter: Boolean = true
    ): Map<String, Any> {
        val text = segment.text.trim()
        if (text.isEmpty()) throw IllegalArgumentException("segment text is empty")

        val reqParams = mutableMapOf<String, Any>(
            "text" to text,
            "speaker" to speaker,
            "audio_params" to mapOf(
                "format" to "mp3",
                "sample_rate" to 24000
            )
        )

        val additions = mutableMapOf<String, Any>()
        if (sectionId != null && !speaker.startsWith("S_")) {
            additions["section_id"] = sectionId
        }
        if (segment.mood != null) {
            additions["context_texts"] = listOf(segment.mood!!)
        }
        if (speaker.startsWith("S_")) {
            additions["model_type"] = 4
        }
        if (disableMarkdownFilter) {
            additions["disable_markdown_filter"] = true
        }
        if (additions.isNotEmpty()) {
            reqParams["additions"] = com.google.gson.Gson().toJson(additions)
        }

        return mapOf(
            "user" to mapOf("uid" to uid),
            "req_params" to reqParams
        )
    }
}
