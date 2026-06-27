package com.example.myapplication.ui

/**
 * Sealed class representing a single entry in the growth timeline.
 */
sealed class TimelineItem {
    abstract val id: String
    abstract val timestamp: Long
    abstract val typeLabel: String
    abstract val iconEmoji: String

    /** One-line summary for list display. */
    abstract fun summaryText(): String

    /** Optional mood/emotion indicator. */
    open fun moodText(): String? = null

    /** Whether this item is pinned (for visual marker). */
    open val isPinned: Boolean = false

    data class LifeRecordItem(
        override val id: String,
        override val timestamp: Long,
        val content: String,
        val mood: String?,
        val tags: List<String>,
        val source: String
    ) : TimelineItem() {
        override val typeLabel = "生活记录"
        override val iconEmoji = "✏️"
        override fun summaryText(): String = content.take(120)
        override fun moodText(): String? = mood
    }

    data class DiaryItem(
        override val id: String,
        override val timestamp: Long,
        val title: String,
        val summary: String,
        val mood: String,
        val date: String
    ) : TimelineItem() {
        override val typeLabel = "日记"
        override val iconEmoji = "📖"
        override fun summaryText(): String = title.ifBlank { summary.take(100) }
        override fun moodText(): String? = if (mood.isNotBlank()) mood else null
    }

    data class MemoryCardItem(
        override val id: String,
        override val timestamp: Long,
        val quote: String,
        val note: String,
        val mood: String?,
        val tags: List<String>,
        val memoryDate: String,
        override val isPinned: Boolean = false
    ) : TimelineItem() {
        override val typeLabel = "记忆卡片"
        override val iconEmoji = "💬"
        override fun summaryText(): String = quote.ifBlank { note }.take(120)
        override fun moodText(): String? = mood
    }

    data class WeeklyReviewItem(
        override val id: String,
        override val timestamp: Long,
        val dateRange: String,
        val summary: String,
        val lifeRecordCount: Int,
        val diaryCount: Int,
        val memoryCardCount: Int
    ) : TimelineItem() {
        override val typeLabel = "周回顾"
        override val iconEmoji = "📊"
        override fun summaryText(): String = if (lifeRecordCount + diaryCount + memoryCardCount == 0) {
            "$dateRange · 暂无记录"
        } else {
            "$dateRange · ${lifeRecordCount}条记录 ${diaryCount}篇日记 ${memoryCardCount}张卡片"
        }
    }

    data class PlanItem(
        override val id: String,
        override val timestamp: Long,
        val planType: String,
        val title: String,
        val message: String,
        val completed: Boolean
    ) : TimelineItem() {
        override val typeLabel = "计划"
        override val iconEmoji = if (completed) "✅" else "⏰"
        override fun summaryText(): String {
            val prefix = if (completed) "[已完成] " else ""
            return "$prefix${title.ifBlank { message.take(80) }}"
        }
    }

    data class ProfileChangeItem(
        override val id: String,
        override val timestamp: Long,
        val key: String,
        val value: String,
        val category: String,
        val changeType: String   // "created" | "confirmed" | "edited" | "disabled" | "enabled"
    ) : TimelineItem() {
        override val typeLabel = "画像变更"
        override val iconEmoji = "💡"
        override fun summaryText(): String {
            val action = when (changeType) {
                "created" -> "新增"
                "confirmed" -> "确认"
                "edited" -> "编辑"
                "disabled" -> "禁用"
                "enabled" -> "启用"
                else -> "更新"
            }
            return "${action}画像: $value"
        }
    }
}
