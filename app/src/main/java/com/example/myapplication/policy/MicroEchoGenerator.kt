package com.example.myapplication.policy

import kotlinx.coroutines.CancellationException

/**
 * 生活片段保存后的“微回声”。
 *
 * 优先请求远程 LLM 生成一句克制、具体的回应；远程不可用时始终返回本地兜底文案，
 * 保证记录行为不会依赖网络，也不会因为生成失败而没有反馈。
 */
class MicroEchoGenerator(
    private val remoteGenerate: suspend (systemPrompt: String, userPrompt: String) -> String? = { _, _ -> null }
) {

    suspend fun generate(
        content: String,
        recentContents: List<String> = emptyList(),
        likedEchoes: List<String> = emptyList(),
        rejectedEchoes: List<String> = emptyList()
    ): String {
        val cleanContent = content.removePrefix("[语音]").trim()
        if (cleanContent.isBlank()) return localFallbackAvoiding(content, rejectedEchoes)

        val remote = try {
            remoteGenerate(
                SYSTEM_PROMPT,
                buildUserPrompt(cleanContent, recentContents, likedEchoes, rejectedEchoes)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        val normalized = normalizeRemote(remote)
        val rejectedSet = rejectedEchoes.map(::normalizeForComparison).toSet()
        return normalized
            ?.takeUnless { normalizeForComparison(it) in rejectedSet }
            ?: localFallbackAvoiding(cleanContent, rejectedEchoes)
    }

    private fun localFallbackAvoiding(content: String, rejectedEchoes: List<String>): String {
        val candidates = localFallbackCandidates(content)
        val rejectedSet = rejectedEchoes.map(::normalizeForComparison).toSet()
        val startIndex = Math.floorMod(rejectedEchoes.size, candidates.size)

        return candidates.indices
            .asSequence()
            .map { offset -> candidates[(startIndex + offset) % candidates.size] }
            .firstOrNull { normalizeForComparison(it) !in rejectedSet }
            ?: candidates[startIndex]
    }

    private fun buildUserPrompt(
        content: String,
        recentContents: List<String>,
        likedEchoes: List<String>,
        rejectedEchoes: List<String>
    ): String {
        val recent = recentContents
            .asSequence()
            .map { it.removePrefix("[语音]").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .take(4)
            .map { "- ${it.take(120)}" }
            .toList()

        return buildString {
            appendLine("用户刚刚留下的生活片段：")
            appendLine(content.take(300))
            if (recent.isNotEmpty()) {
                appendLine()
                appendLine("用户今天此前留下的片段（仅用于理解连续性）：")
                recent.forEach { appendLine(it) }
            }
            val liked = likedEchoes.cleanExamples(4)
            if (liked.isNotEmpty()) {
                appendLine()
                appendLine("用户曾表示有共鸣的表达（学习语气，不要照抄）：")
                liked.forEach { appendLine("- $it") }
            }
            val rejected = rejectedEchoes.cleanExamples(6)
            if (rejected.isNotEmpty()) {
                appendLine()
                appendLine("用户认为不太像自己的表达（避免重复这些措辞和语气）：")
                rejected.forEach { appendLine("- $it") }
            }
            append("只返回一句微回声，不要标题，不要解释。")
        }
    }

    private fun List<String>.cleanExamples(limit: Int): List<String> =
        asSequence()
            .map { it.replace(Regex("\\s+"), " ").trim().take(160) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit)
            .toList()

    companion object {
        private val SYSTEM_PROMPT = """
你是 Echo，一个温柔、克制、真诚的私人生活伙伴。
请对用户刚保存的生活片段写一句“微回声”，让用户感到这句话被听见、被理解。

要求：
- 只写一句自然中文，通常 15～45 个字，最多 72 个字
- 回应片段中真正重要的感受、变化或行动，不机械复述
- 可以在确有依据时指出与今天此前片段的连续性
- 不说教，不提供建议，不夸张赞美，不使用客服腔
- 不编造用户没有表达的事实，不做心理诊断
- 不使用标题、引号、列表、Markdown 或 emoji
        """.trimIndent()

        fun localFallback(content: String, variant: Int = 0): String {
            val candidates = localFallbackCandidates(content)
            return candidates[Math.floorMod(variant, candidates.size)]
        }

        private fun localFallbackCandidates(content: String): List<String> {
            val text = content.removePrefix("[语音]").trim()
            val shared = listOf(
                "你把这一刻写下来，它就没有从今天悄悄溜走。",
                "这段生活已经有了自己的位置，以后回看时还能认出今天。",
                "不需要把一切说完整，这个片段本身就值得留下。",
                "今天因为这句话，多了一处可以回来的记号。",
                "你刚刚为今天停留了一下，这个瞬间已经被收好了。",
                "这不是匆匆过去的一刻，它现在已经属于你的记录。",
                "这句话让今天有了一个清楚的小小落点。",
                "无论这一刻大小，它都已经被认真地留在这里。"
            )

            if (text.isBlank()) return listOf(
                "这一刻我先替你收好，等你想补充时再慢慢说。",
                "声音里的这一刻已经留下，没说完的部分也不用着急。"
            ) + shared

            val lower = text.lowercase()
            return when {
                listOf("累", "疲惫", "烦", "难受", "焦虑", "压力", "崩", "失眠", "生气", "糟糕")
                    .any { it in lower } -> listOf(
                    "听得出来今天有些不容易，这一刻我替你好好收下了。",
                    "今天的这份不容易已经被看见，你不用急着把它说得很完整。"
                ) + shared

                listOf("开心", "高兴", "顺利", "喜欢", "期待", "满足", "幸福", "惊喜")
                    .any { it in lower } -> listOf(
                    "这份开心值得留下，以后再看到时也会想起今天。",
                    "这个让你开心的瞬间已经留下来了，今天也因此多了一点光亮。"
                ) + shared

                listOf("完成", "搞定", "解决", "推进", "开始", "进展", "坚持", "终于")
                    .any { it in lower } -> listOf(
                    "事情正在一点点向前走，这一步值得被记住。",
                    "你刚刚留下的不只是结果，还有自己真正往前走了一步。"
                ) + shared

                else -> listOf(
                    "这一刻已经被好好留下，今天也因此更完整了一点。",
                    "你愿意为这个瞬间停一下，它就不再是匆匆溜走的一天。"
                ) + shared
            }
        }

        private fun normalizeForComparison(text: String): String =
            text.replace(Regex("\\s+"), " ").trim()

        internal fun normalizeRemote(raw: String?): String? {
            var text = raw
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.joinToString("\n")
                ?.takeIf { it.isNotBlank() }
                ?: return null

            val prefixes = listOf("Echo：", "Echo:", "微回声：", "微回声:", "回声：", "回声:")
            for (prefix in prefixes) {
                if (text.startsWith(prefix, ignoreCase = true)) {
                    text = text.substring(prefix.length).trim()
                    break
                }
            }

            text = text
                .lineSequence()
                .map { line ->
                    line.removePrefix("- ")
                        .trim('“', '”', '"', '\'', '*', ' ')
                        .replace(Regex("[\\t ]+"), " ")
                }
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trim()

            if (text.length < 4) return null
            return text
        }
    }
}
