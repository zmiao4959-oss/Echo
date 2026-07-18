package com.example.myapplication.data.model

/**
 * A story the user started but has not finished yet.
 *
 * Foreshadows are deliberately separate from plans: a plan is an actionable task,
 * while a foreshadow is an uncertain, unfolding part of the user's life.
 */
data class EchoForeshadow(
    val id: String,
    val subject: String,
    val title: String,
    val followUpQuestion: String,
    val state: ForeshadowState = ForeshadowState.WATCHING,
    val outcome: ForeshadowOutcome? = null,
    val confidence: Float,
    val importance: Int = 1,
    val sourceRefs: List<ForeshadowSourceRef>,
    val createdAt: Long,
    val lastEvidenceAt: Long,
    val nextCheckAt: Long?,
    val lastPromptedAt: Long? = null,
    val promptCount: Int = 0,
    val suggestedOutcome: ForeshadowOutcome? = null,
    val suggestionConfidence: Float? = null
)

data class ForeshadowSourceRef(
    val type: String,
    val id: String,
    val excerpt: String,
    val createdAt: Long
)

enum class ForeshadowState {
    WATCHING,
    CLOSED,
    DISMISSED
}

enum class ForeshadowOutcome {
    HAPPENED,
    CHANGED,
    ABANDONED,
    CONTINUING
}
