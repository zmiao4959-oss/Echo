package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PastEchoPolicyTest {

    private fun record(
        id: String,
        date: String,
        content: String = "生活片段",
        mood: String? = null,
        microEcho: String? = null,
        importance: Int = 1
    ) = LifeRecord(
        id = id,
        createdAt = 1L,
        date = date,
        content = content,
        source = "text",
        mood = mood,
        importance = importance,
        microEcho = microEcho
    )

    private fun diary(id: String, date: String, title: String = "那天的标题") = DailyDiary(
        id = id,
        date = date,
        title = title,
        summary = "那天的摘要",
        diaryText = "那天的正文",
        mood = "平静",
        tags = emptyList(),
        sourceRecordIds = emptyList(),
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun `empty history returns no echo`() {
        assertNull(PastEchoPolicy.select(emptyList(), emptyList(), "2026-07-18"))
    }

    @Test
    fun `records newer than three days are not resurfaced`() {
        val result = PastEchoPolicy.select(
            emptyList(),
            listOf(record("r1", "2026-07-17"), record("r2", "2026-07-16")),
            "2026-07-18"
        )

        assertNull(result)
    }

    @Test
    fun `anniversary is preferred over a recent milestone`() {
        val result = PastEchoPolicy.select(
            diaries = listOf(diary("d1", "2025-07-18")),
            records = listOf(record("r1", "2026-07-11")),
            today = "2026-07-18"
        )

        assertEquals("d1", result?.sourceId)
        assertEquals("一年前的今天", result?.contextLabel)
    }

    @Test
    fun `older milestone is preferred when several milestones exist`() {
        val result = PastEchoPolicy.select(
            emptyList(),
            listOf(
                record("week", "2026-07-11"),
                record("month", "2026-06-18")
            ),
            "2026-07-18"
        )

        assertEquals("month", result?.sourceId)
        assertEquals("30 天前", result?.contextLabel)
    }

    @Test
    fun `diary wins over life record from the same date`() {
        val result = PastEchoPolicy.select(
            listOf(diary("diary", "2026-07-11")),
            listOf(record("record", "2026-07-11", importance = 5)),
            "2026-07-18"
        )

        assertEquals("diary", result?.sourceType)
        assertEquals("diary", result?.sourceId)
    }

    @Test
    fun `fallback selection is stable during the same day`() {
        val records = listOf(
            record("r1", "2026-07-10"),
            record("r2", "2026-07-09"),
            record("r3", "2026-07-08")
        )

        val first = PastEchoPolicy.select(emptyList(), records, "2026-07-18")
        val second = PastEchoPolicy.select(emptyList(), records, "2026-07-18")

        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun `life record keeps its mood and saved micro echo`() {
        val result = PastEchoPolicy.select(
            emptyList(),
            listOf(record("r1", "2026-07-11", mood = "踏实", microEcho = "这一步值得记住。")),
            "2026-07-18"
        )

        assertEquals("踏实", result?.mood)
        assertEquals("这一步值得记住。", result?.microEcho)
    }

    @Test
    fun `invalid and future dates are ignored`() {
        val result = PastEchoPolicy.select(
            emptyList(),
            listOf(record("bad", "not-a-date"), record("future", "2026-07-20")),
            "2026-07-18"
        )

        assertNull(result)
    }

    @Test
    fun `clearly distressing memories are not automatically resurfaced`() {
        val result = PastEchoPolicy.select(
            emptyList(),
            listOf(record("r1", "2026-07-11", content = "那天经历了亲人去世，非常痛苦")),
            "2026-07-18"
        )

        assertNull(result)
    }
}
