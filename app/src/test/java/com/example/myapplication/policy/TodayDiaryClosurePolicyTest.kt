package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayDiaryClosurePolicyTest {

    private fun record(id: String, createdAt: Long = 100L, content: String = "片段") = LifeRecord(
        id = id,
        createdAt = createdAt,
        date = "2026-07-18",
        content = content,
        source = "text"
    )

    private fun diary(
        sourceIds: List<String>,
        updatedAt: Long = 200L
    ) = DailyDiary(
        id = "diary-1",
        date = "2026-07-18",
        title = "今天的日记",
        summary = "摘要",
        diaryText = "正文",
        mood = "平静",
        tags = emptyList(),
        sourceRecordIds = sourceIds,
        createdAt = 150L,
        updatedAt = updatedAt
    )

    @Test
    fun `empty day without diary hides the closure action`() {
        assertTrue(TodayDiaryClosurePolicy.build(emptyList(), null) is TodayDiaryClosurePolicy.Closure.Hidden)
    }

    @Test
    fun `records without diary offer one build action`() {
        val result = TodayDiaryClosurePolicy.build(listOf(record("a"), record("b")), null)

        assertEquals(TodayDiaryClosurePolicy.Closure.Build(2), result)
    }

    @Test
    fun `diary covering current records is ready to read`() {
        val result = TodayDiaryClosurePolicy.build(
            listOf(record("a"), record("b")),
            diary(listOf("a", "b"))
        )

        assertEquals(TodayDiaryClosurePolicy.Closure.Ready("diary-1", "今天的日记"), result)
    }

    @Test
    fun `new record after generation asks for review`() {
        val result = TodayDiaryClosurePolicy.build(
            listOf(record("a"), record("b")),
            diary(listOf("a"))
        ) as TodayDiaryClosurePolicy.Closure.ReviewChanges

        assertEquals(1, result.changedRecordCount)
    }

    @Test
    fun `deleted source record also asks for review`() {
        val result = TodayDiaryClosurePolicy.build(
            listOf(record("a")),
            diary(listOf("a", "b"))
        ) as TodayDiaryClosurePolicy.Closure.ReviewChanges

        assertEquals(1, result.changedRecordCount)
    }

    @Test
    fun `diary remains readable when all source records were removed`() {
        val result = TodayDiaryClosurePolicy.build(emptyList(), diary(listOf("a", "b")))

        assertTrue(result is TodayDiaryClosurePolicy.Closure.Ready)
    }

    @Test
    fun `legacy diary uses update time when source ids are absent`() {
        val ready = TodayDiaryClosurePolicy.build(
            listOf(record("a", createdAt = 100L)),
            diary(emptyList(), updatedAt = 200L)
        )
        val changed = TodayDiaryClosurePolicy.build(
            listOf(record("a", createdAt = 300L)),
            diary(emptyList(), updatedAt = 200L)
        )

        assertTrue(ready is TodayDiaryClosurePolicy.Closure.Ready)
        assertTrue(changed is TodayDiaryClosurePolicy.Closure.ReviewChanges)
    }
}
