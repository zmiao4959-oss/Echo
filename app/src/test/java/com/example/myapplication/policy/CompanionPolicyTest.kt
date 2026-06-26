package com.example.myapplication.policy

import org.junit.Assert.*
import org.junit.Test

class CompanionPolicyTest {

    private val policy = CompanionPolicy

    // ── Trigger decisions ──

    @Test
    fun `task_reminder always triggers regardless of companion settings`() {
        val ctx = CompanionPolicy.TriggerContext(
            planType = "task_reminder",
            companionEnabled = false,
            hourOfDay = 10,
            quietStart = 23, quietEnd = 7,
            companionAllowVoice = false
        )
        val decision = policy.shouldTrigger(ctx)
        assertTrue("task_reminder should always trigger", decision.shouldTrigger)
        assertFalse(decision.suppressVoice)
    }

    @Test
    fun `companion_checkin does not trigger when companion disabled`() {
        val ctx = CompanionPolicy.TriggerContext(
            planType = "companion_checkin",
            companionEnabled = false,
            hourOfDay = 10,
            quietStart = 23, quietEnd = 7,
            companionAllowVoice = true
        )
        val decision = policy.shouldTrigger(ctx)
        assertFalse("companion_checkin should not trigger when disabled", decision.shouldTrigger)
        assertEquals("Companion disabled", decision.reason)
    }

    @Test
    fun `memory_trigger does not trigger when companion disabled`() {
        val ctx = CompanionPolicy.TriggerContext(
            planType = "memory_trigger",
            companionEnabled = false,
            hourOfDay = 10,
            quietStart = 23, quietEnd = 7,
            companionAllowVoice = true
        )
        val decision = policy.shouldTrigger(ctx)
        assertFalse("memory_trigger should not trigger when disabled", decision.shouldTrigger)
    }

    @Test
    fun `companion_checkin triggers when enabled and not in quiet hours`() {
        val ctx = CompanionPolicy.TriggerContext(
            planType = "companion_checkin",
            companionEnabled = true,
            hourOfDay = 10,
            quietStart = 23, quietEnd = 7,
            companionAllowVoice = true
        )
        val decision = policy.shouldTrigger(ctx)
        assertTrue("Should trigger outside quiet hours", decision.shouldTrigger)
        assertFalse(decision.suppressVoice)
    }

    @Test
    fun `voice suppressed when companionAllowVoice is false`() {
        val ctx = CompanionPolicy.TriggerContext(
            planType = "companion_checkin",
            companionEnabled = true,
            hourOfDay = 10,
            quietStart = 23, quietEnd = 7,
            companionAllowVoice = false
        )
        val decision = policy.shouldTrigger(ctx)
        assertTrue("Should still trigger", decision.shouldTrigger)
        assertTrue("Voice should be suppressed", decision.suppressVoice)
    }

    // ── Quiet hours ──

    @Test
    fun `isInQuietHours within range`() {
        assertTrue(policy.isInQuietHours(23, 22, 6))
        assertTrue(policy.isInQuietHours(0, 22, 6))
        assertTrue(policy.isInQuietHours(5, 22, 6))
    }

    @Test
    fun `isInQuietHours outside range`() {
        assertFalse(policy.isInQuietHours(10, 22, 6))
        assertFalse(policy.isInQuietHours(7, 22, 6))
    }

    @Test
    fun `isInQuietHours normal range`() {
        assertTrue(policy.isInQuietHours(0, 0, 7))
        assertFalse(policy.isInQuietHours(8, 0, 7))
    }

    @Test
    fun `companion suppressed in quiet hours`() {
        val ctx = CompanionPolicy.TriggerContext(
            planType = "companion_checkin",
            companionEnabled = true,
            hourOfDay = 23,
            quietStart = 22, quietEnd = 6,
            companionAllowVoice = true
        )
        val decision = policy.shouldTrigger(ctx)
        assertFalse("Should not trigger in quiet hours", decision.shouldTrigger)
    }

    // ── Greeting variety ──

    @Test
    fun `buildGreeting varies across days`() {
        val ctx = CompanionPolicy.GreetingContext(
            hourOfDay = 8, recordCount = 3, hasDiary = false,
            hasPendingPlan = false, planId = "plan1", todayDate = "2026-06-26"
        )
        val greetings = policy.generateGreetingsForNDays(ctx, 3)
        assertTrue("Should have some variety across 3 days", greetings.size >= 1)
    }

    @Test
    fun `buildGreeting morning with records returns morning scenario`() {
        val ctx = CompanionPolicy.GreetingContext(
            hourOfDay = 8, recordCount = 5, hasDiary = false,
            hasPendingPlan = false, planId = "p1", todayDate = "2026-06-26"
        )
        val result = policy.buildGreeting(ctx)
        assertTrue(result.scenario.startsWith("morning"))
        assertTrue(result.greeting.contains("5"))
    }

    @Test
    fun `buildGreeting evening with diary returns evening scenario`() {
        val ctx = CompanionPolicy.GreetingContext(
            hourOfDay = 20, recordCount = 4, hasDiary = true,
            hasPendingPlan = false, planId = "p1", todayDate = "2026-06-26"
        )
        val result = policy.buildGreeting(ctx)
        assertEquals("evening_records_diary", result.scenario)
        assertTrue(result.greeting.contains("4"))
    }

    @Test
    fun `buildGreeting no records morning different from records morning`() {
        val withRecords = CompanionPolicy.GreetingContext(8, 3, false, false, "p1", "2026-06-26")
        val noRecords = CompanionPolicy.GreetingContext(8, 0, false, false, "p1", "2026-06-26")
        val g1 = policy.buildGreeting(withRecords).greeting
        val g2 = policy.buildGreeting(noRecords).greeting
        assertNotEquals(g1, g2)
    }

    @Test
    fun `buildGreeting does not fabricate claims`() {
        // When recordCount = 0, greeting should not mention records
        val ctx = CompanionPolicy.GreetingContext(8, 0, false, false, "p1", "2026-06-26")
        val result = policy.buildGreeting(ctx)
        // Morning no-records variants shouldn't contain numbers about records
        assertFalse("Should not mention record count when there are none",
            result.greeting.contains("0 条") && result.greeting.length < 30)
    }

    @Test
    fun `buildGreeting other time returns fallback scenario`() {
        val ctx = CompanionPolicy.GreetingContext(14, 0, false, false, "p1", "2026-06-26")
        val result = policy.buildGreeting(ctx)
        assertEquals("other_time", result.scenario)
    }

    // ── Variant pools are non-empty ──

    @Test
    fun `all variant pools have entries`() {
        assertTrue(policy.MORNING_WITH_RECORDS.isNotEmpty())
        assertTrue(policy.MORNING_WITH_RECORDS_AND_PLANS.isNotEmpty())
        assertTrue(policy.MORNING_NO_RECORDS.isNotEmpty())
        assertTrue(policy.EVENING_WITH_RECORDS.isNotEmpty())
        assertTrue(policy.EVENING_WITH_RECORDS_AND_DIARY.isNotEmpty())
        assertTrue(policy.EVENING_NO_RECORDS.isNotEmpty())
        assertTrue(policy.OTHER_TIME.isNotEmpty())
    }

    @Test
    fun `greetings do not contain cliche phrases`() {
        val ctx = CompanionPolicy.GreetingContext(8, 3, false, true, "p1", "2026-06-26")
        val result = policy.buildGreeting(ctx)
        val forbidden = listOf("你最棒", "加油", "一定行", "必须")
        for (word in forbidden) {
            assertFalse("Greeting should not contain cliche: $word", result.greeting.contains(word))
        }
    }
}
