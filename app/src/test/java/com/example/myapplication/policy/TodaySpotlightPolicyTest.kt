package com.example.myapplication.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySpotlightPolicyTest {

    private val welcome = ReturnWelcomePolicy.Welcome.WelcomeBack(
        lastRecordDate = "2026-07-14",
        daysSinceLastRecord = 4,
        message = "欢迎回来"
    )

    @Test
    fun `generating echo has the highest priority`() {
        val result = TodaySpotlightPolicy.select(
            returnWelcome = welcome,
            echo = TodaySpotlightPolicy.EchoCandidate(generating = true),
            nowMillis = 10_000L
        )

        assertTrue(result is TodaySpotlightPolicy.Spotlight.MicroEcho)
        assertTrue((result as TodaySpotlightPolicy.Spotlight.MicroEcho).generating)
    }

    @Test
    fun `fresh echo occupies the temporary feedback slot`() {
        val createdAt = 1_000_000L
        val result = TodaySpotlightPolicy.select(
            returnWelcome = ReturnWelcomePolicy.Welcome.Hidden,
            echo = TodaySpotlightPolicy.EchoCandidate(
                text = "这一刻值得被记住。",
                recordCreatedAtMillis = createdAt
            ),
            nowMillis = createdAt + 5 * 60 * 1000L
        ) as TodaySpotlightPolicy.Spotlight.MicroEcho

        assertFalse(result.generating)
        assertEquals("这一刻值得被记住。", result.text)
        assertEquals(
            createdAt + TodaySpotlightPolicy.ECHO_FRESHNESS_MILLIS,
            result.freshUntilMillis
        )
    }

    @Test
    fun `expired echo gives way to return welcome`() {
        val result = TodaySpotlightPolicy.select(
            returnWelcome = welcome,
            echo = TodaySpotlightPolicy.EchoCandidate(
                text = "旧回声",
                recordCreatedAtMillis = 1_000L
            ),
            nowMillis = 1_000L + TodaySpotlightPolicy.ECHO_FRESHNESS_MILLIS
        )

        assertEquals(
            TodaySpotlightPolicy.Spotlight.ReturnWelcome("欢迎回来"),
            result
        )
    }

    @Test
    fun `future or timestamp-free echoes do not take over the page`() {
        val withoutTimestamp = TodaySpotlightPolicy.select(
            returnWelcome = ReturnWelcomePolicy.Welcome.Hidden,
            echo = TodaySpotlightPolicy.EchoCandidate(text = "没有时间"),
            nowMillis = 50_000L
        )
        val fromFuture = TodaySpotlightPolicy.select(
            returnWelcome = ReturnWelcomePolicy.Welcome.Hidden,
            echo = TodaySpotlightPolicy.EchoCandidate(
                text = "来自未来",
                recordCreatedAtMillis = 60_000L
            ),
            nowMillis = 50_000L
        )

        assertTrue(withoutTimestamp is TodaySpotlightPolicy.Spotlight.Hidden)
        assertTrue(fromFuture is TodaySpotlightPolicy.Spotlight.Hidden)
    }

    @Test
    fun `temporary feedback stays hidden when nothing needs emphasis`() {
        val result = TodaySpotlightPolicy.select(
            returnWelcome = ReturnWelcomePolicy.Welcome.Hidden,
            echo = null,
            nowMillis = 10_000L
        )

        assertEquals(TodaySpotlightPolicy.Spotlight.Hidden, result)
    }
}
