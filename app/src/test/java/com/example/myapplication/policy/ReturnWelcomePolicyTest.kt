package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnWelcomePolicyTest {

    private fun record(id: String, date: String, content: String = "片段") = LifeRecord(
        id = id,
        createdAt = 1L,
        date = date,
        content = content,
        source = "text"
    )

    @Test
    fun `first-time user is not shown a return message`() {
        assertTrue(ReturnWelcomePolicy.build(emptyList(), "2026-07-18") is ReturnWelcomePolicy.Welcome.Hidden)
    }

    @Test
    fun `record from today hides the welcome`() {
        val result = ReturnWelcomePolicy.build(listOf(record("a", "2026-07-18")), "2026-07-18")

        assertTrue(result is ReturnWelcomePolicy.Welcome.Hidden)
    }

    @Test
    fun `record from yesterday does not treat normal rhythm as a gap`() {
        val result = ReturnWelcomePolicy.build(listOf(record("a", "2026-07-17")), "2026-07-18")

        assertTrue(result is ReturnWelcomePolicy.Welcome.Hidden)
    }

    @Test
    fun `one missed day gets a gentle reconnection`() {
        val result = ReturnWelcomePolicy.build(
            listOf(record("a", "2026-07-16")),
            "2026-07-18"
        ) as ReturnWelcomePolicy.Welcome.WelcomeBack

        assertEquals(2, result.daysSinceLastRecord)
        assertTrue(result.message.contains("回来就已经接上了"))
        assertFalse(result.message.contains("断"))
    }

    @Test
    fun `several missed days explicitly remove catch-up pressure`() {
        val result = ReturnWelcomePolicy.build(
            listOf(record("a", "2026-07-14")),
            "2026-07-18"
        ) as ReturnWelcomePolicy.Welcome.WelcomeBack

        assertTrue(result.message.contains("不必补齐"))
    }

    @Test
    fun `longer gap reminds user that old records remain`() {
        val result = ReturnWelcomePolicy.build(
            listOf(record("a", "2026-07-08")),
            "2026-07-18"
        ) as ReturnWelcomePolicy.Welcome.WelcomeBack

        assertTrue(result.message.contains("过去的记录都还在"))
    }

    @Test
    fun `very long gap says there is no debt`() {
        val result = ReturnWelcomePolicy.build(
            listOf(record("a", "2026-06-01")),
            "2026-07-18"
        ) as ReturnWelcomePolicy.Welcome.WelcomeBack

        assertTrue(result.message.contains("没有欠下"))
        assertFalse(result.message.contains("必须"))
    }

    @Test
    fun `latest valid past record wins regardless of input order`() {
        val result = ReturnWelcomePolicy.build(
            listOf(
                record("future", "2026-07-20"),
                record("older", "2026-07-01"),
                record("invalid", "not-a-date"),
                record("latest", "2026-07-15")
            ),
            "2026-07-18"
        ) as ReturnWelcomePolicy.Welcome.WelcomeBack

        assertEquals("2026-07-15", result.lastRecordDate)
        assertEquals(3, result.daysSinceLastRecord)
    }
}
