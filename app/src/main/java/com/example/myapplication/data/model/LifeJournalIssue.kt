package com.example.myapplication.data.model

enum class LifeJournalPeriod { WEEK, MONTH }

data class LifeJournalChapter(
    val id: String,
    val eyebrow: String,
    val title: String,
    val body: String,
    val fragments: List<String> = emptyList(),
    val sourceLabels: List<String> = emptyList()
)

data class LifeJournalMoodPoint(
    val date: String,
    val label: String,
    /** Standardized emotional value in [-1, 1]. The renderer maps it linearly to -10..40°C. */
    val score: Float,
    /** The diary's original "today color". It is presentation data, not a replacement for mood text. */
    val moodColor: String? = null,
    /** Daily weather temperature. Nullable because older entries and text-only days may not have it. */
    val temperatureC: Float? = null,
    val weatherLabel: String? = null
)

data class LifeJournalIssue(
    val id: String,
    val period: LifeJournalPeriod,
    val startDate: String,
    val endDate: String,
    val issueLabel: String,
    val title: String,
    val subtitle: String,
    val themeKey: String,
    val primaryColor: String,
    val overview: String,
    val chapters: List<LifeJournalChapter>,
    val moodPoints: List<LifeJournalMoodPoint>,
    val keywords: List<String>,
    val weatherNotes: List<String>,
    val sourceCounts: Map<String, Int>,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Int = 1,
    /** Page ids edited by the user. Nullable keeps older Gson archives migration-safe. */
    val manuallyEditedPages: List<String>? = emptyList()
)
