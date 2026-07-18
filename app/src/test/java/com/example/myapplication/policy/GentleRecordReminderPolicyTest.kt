package com.example.myapplication.policy

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GentleRecordReminderPolicyTest {

    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test
    fun `disabled reminder stays silent`() {
        assertFalse(GentleRecordReminderPolicy.shouldNotify(enabled = false, recordCountToday = 0))
    }

    @Test
    fun `enabled reminder notifies when today is empty`() {
        assertTrue(GentleRecordReminderPolicy.shouldNotify(enabled = true, recordCountToday = 0))
    }

    @Test
    fun `any record today suppresses the reminder`() {
        assertFalse(GentleRecordReminderPolicy.shouldNotify(enabled = true, recordCountToday = 1))
        assertFalse(GentleRecordReminderPolicy.shouldNotify(enabled = true, recordCountToday = 5))
    }

    @Test
    fun `future time schedules later today`() {
        val now = millis(2026, 7, 18, 20, 0)

        val next = GentleRecordReminderPolicy.nextTriggerAtMillis(now, 21, 30, zone)

        assertEquals(millis(2026, 7, 18, 21, 30), next)
    }

    @Test
    fun `past time schedules tomorrow`() {
        val now = millis(2026, 7, 18, 22, 0)

        val next = GentleRecordReminderPolicy.nextTriggerAtMillis(now, 21, 30, zone)

        assertEquals(millis(2026, 7, 19, 21, 30), next)
    }

    @Test
    fun `exact configured minute schedules tomorrow`() {
        val now = millis(2026, 7, 18, 21, 30)

        val next = GentleRecordReminderPolicy.nextTriggerAtMillis(now, 21, 30, zone)

        assertEquals(millis(2026, 7, 19, 21, 30), next)
    }

    @Test
    fun `invalid saved time is clamped safely`() {
        val now = millis(2026, 7, 18, 10, 0)

        val next = GentleRecordReminderPolicy.nextTriggerAtMillis(now, 40, -3, zone)
        val result = Calendar.getInstance(zone).apply { timeInMillis = next }

        assertEquals(23, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
    }

    @Test
    fun `message is stable for a day and rotates without pressure language`() {
        val message = GentleRecordReminderPolicy.messageFor("2026-07-18")

        assertEquals(message, GentleRecordReminderPolicy.messageFor("2026-07-18"))
        assertNotEquals(message, GentleRecordReminderPolicy.messageFor("2026-07-19"))
        assertFalse(message.contains("断签"))
        assertFalse(message.contains("未完成"))
        assertFalse(message.contains("必须"))
    }

    @Test
    fun `fewer than three record days keep the configured fallback time`() {
        val preferred = GentleRecordReminderPolicy.preferredReminderTime(
            recordedAtMillis = listOf(
                millis(2026, 7, 17, 20, 10),
                millis(2026, 7, 18, 22, 40)
            ),
            fallbackHour = 21,
            fallbackMinute = 30,
            timeZone = zone
        )

        assertEquals(GentleRecordReminderPolicy.TimeOfDay(21, 30), preferred)
    }

    @Test
    fun `adaptive time uses the median first record across distinct days`() {
        val preferred = GentleRecordReminderPolicy.preferredReminderTime(
            recordedAtMillis = listOf(
                millis(2026, 7, 16, 21, 10),
                millis(2026, 7, 16, 22, 45),
                millis(2026, 7, 17, 21, 30),
                millis(2026, 7, 18, 21, 20)
            ),
            fallbackHour = 19,
            fallbackMinute = 0,
            timeZone = zone
        )

        assertEquals(GentleRecordReminderPolicy.TimeOfDay(21, 20), preferred)
    }

    @Test
    fun `learned late-night time is kept within a gentle boundary`() {
        val preferred = GentleRecordReminderPolicy.preferredReminderTime(
            recordedAtMillis = listOf(
                millis(2026, 7, 16, 23, 20),
                millis(2026, 7, 17, 23, 40),
                millis(2026, 7, 18, 23, 30)
            ),
            fallbackHour = 21,
            fallbackMinute = 30,
            timeZone = zone
        )

        assertEquals(GentleRecordReminderPolicy.TimeOfDay(22, 30), preferred)
    }
}
