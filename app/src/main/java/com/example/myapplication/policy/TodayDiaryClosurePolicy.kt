package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord

/** Decides the honest next action between today's fragments and today's diary. */
object TodayDiaryClosurePolicy {

    sealed class Closure {
        object Hidden : Closure()
        data class Build(val recordCount: Int) : Closure()
        data class Ready(val diaryId: String, val title: String) : Closure()
        data class ReviewChanges(
            val diaryId: String,
            val title: String,
            val changedRecordCount: Int
        ) : Closure()
    }

    fun build(records: List<LifeRecord>, diary: DailyDiary?): Closure {
        val usableRecords = records.filter { it.content.isNotBlank() }
        if (diary == null) {
            return if (usableRecords.isEmpty()) Closure.Hidden else Closure.Build(usableRecords.size)
        }
        if (usableRecords.isEmpty()) {
            // The diary remains a readable artifact even if its source fragments
            // were later removed; offering regeneration would only fail.
            return Closure.Ready(diary.id, diary.title)
        }

        val currentIds = usableRecords.map { it.id }.toSet()
        val sourceIds = diary.sourceRecordIds.toSet()
        val isCurrent = if (sourceIds.isNotEmpty()) {
            currentIds == sourceIds
        } else {
            // Legacy/imported diaries may not have source IDs. Timestamp is the
            // safest available signal without claiming more than we know.
            usableRecords.maxOfOrNull { it.createdAt }?.let { diary.updatedAt >= it } ?: true
        }

        return if (isCurrent) {
            Closure.Ready(diary.id, diary.title)
        } else {
            Closure.ReviewChanges(
                diaryId = diary.id,
                title = diary.title,
                changedRecordCount = (currentIds - sourceIds).size + (sourceIds - currentIds).size
            )
        }
    }
}
