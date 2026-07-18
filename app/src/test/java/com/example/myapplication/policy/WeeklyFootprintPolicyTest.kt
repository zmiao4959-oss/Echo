package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyFootprintPolicyTest {

    private fun record(id: String, date: String, content: String = "片段") = LifeRecord(
        id = id,
        createdAt = 1L,
        date = date,
        content = content,
        source = "text"
    )

    @Test
    fun `empty week offers a gentle start without streak language`() {
        val result = WeeklyFootprintPolicy.build(emptyList(), "2026-07-18")

        assertEquals(7, result.days.size)
        assertEquals(0, result.activeDays)
        assertTrue(result.message.contains("写一句也算开始"))
        assertFalse(result.message.contains("中断"))
        assertFalse(result.message.contains("归零"))
    }

    @Test
    fun `current window counts distinct days and all records`() {
        val result = WeeklyFootprintPolicy.build(
            listOf(
                record("a", "2026-07-12"),
                record("b", "2026-07-12"),
                record("c", "2026-07-18")
            ),
            "2026-07-18"
        )

        assertEquals(2, result.activeDays)
        assertEquals(3, result.totalRecords)
        assertEquals(2, result.days.first().recordCount)
        assertEquals(1, result.days.last().recordCount)
    }

    @Test
    fun `positive comparison is celebrated`() {
        val result = WeeklyFootprintPolicy.build(
            listOf(
                record("previous", "2026-07-10"),
                record("a", "2026-07-12"),
                record("b", "2026-07-14"),
                record("c", "2026-07-18")
            ),
            "2026-07-18"
        )

        assertEquals(2, result.positiveChangeDays)
        assertTrue(result.message.contains("多留下 2 天"))
    }

    @Test
    fun `quieter week is never described as a decline`() {
        val result = WeeklyFootprintPolicy.build(
            listOf(
                record("p1", "2026-07-05"),
                record("p2", "2026-07-06"),
                record("p3", "2026-07-07"),
                record("now", "2026-07-18")
            ),
            "2026-07-18"
        )

        assertEquals(0, result.positiveChangeDays)
        assertFalse(result.message.contains("少"))
        assertFalse(result.message.contains("下降"))
    }

    @Test
    fun `four active days acknowledges an established rhythm`() {
        val result = WeeklyFootprintPolicy.build(
            listOf(
                record("a", "2026-07-12"),
                record("b", "2026-07-13"),
                record("c", "2026-07-15"),
                record("d", "2026-07-18")
            ),
            "2026-07-18"
        )

        assertTrue(result.message.contains("自己的节奏"))
    }

    @Test
    fun `all seven days form a complete trajectory`() {
        val records = (12..18).map { day -> record("r$day", "2026-07-$day") }

        val result = WeeklyFootprintPolicy.build(records, "2026-07-18")

        assertEquals(7, result.activeDays)
        assertTrue(result.message.contains("完整的生活轨迹"))
    }

    @Test
    fun `today uses the short now label`() {
        val result = WeeklyFootprintPolicy.build(emptyList(), "2026-07-18")

        assertEquals("今", result.days.last().weekdayLabel)
        assertTrue(result.days.dropLast(1).all { it.weekdayLabel.isNotBlank() })
    }

    @Test
    fun `only the required fourteen day range is counted`() {
        val result = WeeklyFootprintPolicy.build(
            listOf(
                record("boundary", "2026-07-05"),
                record("too-old", "2026-07-04"),
                record("future", "2026-07-19"),
                record("blank", "2026-07-18", content = "")
            ),
            "2026-07-18"
        )

        assertEquals(1, result.previousActiveDays)
        assertEquals(0, result.activeDays)
        assertEquals("2026-07-05", WeeklyFootprintPolicy.earliestRequiredDate("2026-07-18"))
    }
}
