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
    val score: Float
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
    val revision: Int = 1
)
