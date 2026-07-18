package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord

/** Keeps lightweight, local examples of the Echo tone the user accepts or rejects. */
object MicroEchoFeedbackPolicy {

    private const val MAX_LIKED_EXAMPLES = 4
    private const val MAX_REJECTED_EXAMPLES = 6
    private const val MAX_REJECTED_PER_RECORD = 6

    data class Preferences(
        val liked: List<String>,
        val rejected: List<String>
    )

    data class Summary(val likedCount: Int, val rejectedCount: Int) {
        val totalCount: Int get() = likedCount + rejectedCount
    }

    private data class Candidate(
        val text: String,
        val record: LifeRecord,
        val withinRecordOrder: Int = 0
    )

    fun collect(records: List<LifeRecord>, target: LifeRecord? = null): Preferences {
        val likedCandidates = records.mapNotNull { record ->
            if (!record.microEchoLiked) return@mapNotNull null
            record.microEcho?.cleanExample()?.let { Candidate(it, record) }
        }
        val rejectedCandidates = records.flatMap { record ->
            record.rejectedMicroEchoes.orEmpty()
                .asReversed()
                .mapIndexedNotNull { index, echo ->
                    echo.cleanExample()?.let {
                        Candidate(it, record, withinRecordOrder = -index)
                    }
                }
        }
        val liked = selectDiverse(likedCandidates, target, MAX_LIKED_EXAMPLES)
        val rejected = selectDiverse(rejectedCandidates, target, MAX_REJECTED_EXAMPLES)
        return Preferences(liked = liked, rejected = rejected)
    }

    fun summarize(records: List<LifeRecord>): Summary {
        val liked = records
            .asSequence()
            .filter { it.microEchoLiked }
            .mapNotNull { it.microEcho?.cleanExample() }
            .distinct()
            .count()
        val rejected = records
            .asSequence()
            .flatMap { it.rejectedMicroEchoes.orEmpty().asSequence() }
            .mapNotNull { it.cleanExample() }
            .distinct()
            .count()
        return Summary(likedCount = liked, rejectedCount = rejected)
    }

    fun addRejected(existing: List<String>?, echo: String): List<String> {
        val clean = echo.cleanExample() ?: return existing.orEmpty()
        return (existing.orEmpty() + clean)
            .distinct()
            .takeLast(MAX_REJECTED_PER_RECORD)
    }

    private fun selectDiverse(
        candidates: List<Candidate>,
        target: LifeRecord?,
        limit: Int
    ): List<String> {
        val ranked = candidates.sortedWith(
            compareByDescending<Candidate> { relevance(it.record, target) }
                .thenByDescending { it.record.createdAt }
                .thenByDescending { it.withinRecordOrder }
        )
        val selected = mutableListOf<String>()
        for (candidate in ranked) {
            if (selected.any { textSimilarity(it, candidate.text) >= 0.82 }) continue
            selected += candidate.text
            if (selected.size == limit) break
        }
        return selected
    }

    private fun relevance(record: LifeRecord, target: LifeRecord?): Double {
        if (target == null || record.id == target.id) {
            return if (record.id == target?.id) 10.0 else 0.0
        }
        val contentScore = textSimilarity(record.content, target.content) * 3.0
        val moodScore = if (
            !record.mood.isNullOrBlank() &&
            record.mood.equals(target.mood, ignoreCase = true)
        ) 0.8 else 0.0
        val recordTags = record.tags.map { it.lowercase() }.toSet()
        val targetTags = target.tags.map { it.lowercase() }.toSet()
        val tagUnion = recordTags union targetTags
        val tagScore = if (tagUnion.isEmpty()) {
            0.0
        } else {
            (recordTags intersect targetTags).size.toDouble() / tagUnion.size * 1.2
        }
        val sourceScore = if (record.source == target.source) 0.1 else 0.0
        return contentScore + moodScore + tagScore + sourceScore
    }

    private fun textSimilarity(left: String, right: String): Double {
        val leftSignals = textSignals(left)
        val rightSignals = textSignals(right)
        if (leftSignals.isEmpty() || rightSignals.isEmpty()) {
            return if (left.trim().equals(right.trim(), ignoreCase = true)) 1.0 else 0.0
        }
        val union = leftSignals union rightSignals
        return (leftSignals intersect rightSignals).size.toDouble() / union.size
    }

    private fun textSignals(text: String): Set<String> {
        val normalized = text.lowercase()
        val signals = mutableSetOf<String>()
        Regex("[a-z0-9]{2,}").findAll(normalized).forEach { signals += it.value }
        Regex("[\\u4e00-\\u9fff]+").findAll(normalized).forEach { match ->
            val run = match.value
            if (run.length == 1) {
                signals += run
            } else {
                run.windowed(2).forEach { signals += it }
            }
        }
        return signals
    }

    private fun String.cleanExample(): String? =
        replace(Regex("\\s+"), " ")
            .trim()
            .take(160)
            .takeIf { it.isNotBlank() }
}
