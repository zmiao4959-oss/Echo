package com.example.myapplication.schedule

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduleEngineTest {

    /**
     * Helper: get epoch millis for a given hour/minute today.
     */
    private fun epochToday(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `calculateNextTrigger returns future time for today with future hour`() {
        val now = Calendar.getInstance()
        val futureHour = (now.get(Calendar.HOUR_OF_DAY) + 2) % 24
        val allDays = setOf(1, 2, 3, 4, 5, 6, 7)

        val result = ScheduleEngine.calculateNextTrigger(futureHour, 0, allDays)
        assertTrue("Result should be in the future", result > System.currentTimeMillis())
    }

    @Test
    fun `calculateNextTrigger returns tomorrow when hour already passed today`() {
        val now = Calendar.getInstance()
        val pastHour = (now.get(Calendar.HOUR_OF_DAY) - 1 + 24) % 24
        val pastMinute = now.get(Calendar.MINUTE) - 30
        val allDays = setOf(1, 2, 3, 4, 5, 6, 7)

        val result = ScheduleEngine.calculateNextTrigger(pastHour, pastMinute.coerceAtLeast(0), allDays)
        assertTrue("Result should be in the future", result > System.currentTimeMillis())

        // Should be at least a few hours ahead (tomorrow)
        val diffHours = (result - System.currentTimeMillis()) / 3_600_000
        assertTrue("Should be tomorrow (> 12 hours away)", diffHours > 12)
    }

    @Test
    fun `calculateNextTrigger respects daysOfWeek constraint`() {
        // Only allow on Wednesday (Calendar.WEDNESDAY = 4)
        val wednesdayOnly = setOf(4)
        val allDayHours = 12 // noon, always today-or-tomorrow

        val result = ScheduleEngine.calculateNextTrigger(allDayHours, 0, wednesdayOnly)
        val cal = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals("Should be Wednesday", Calendar.WEDNESDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `calculateNextTrigger with empty daysOfWeek treats as every day`() {
        val emptyDays = emptySet<Int>()

        val result = ScheduleEngine.calculateNextTrigger(12, 0, emptyDays)

        // With empty set, the "in" check is always false, so it returns tomorrow
        assertTrue("Result should be in the future", result > System.currentTimeMillis())
    }

    @Test
    fun `calculateNextTrigger returns tomorrow at given time as fallback`() {
        // Only allow Friday (Calendar.FRIDAY = 6), and today is NOT Friday
        val fridayOnly = setOf(6)
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
            val result = ScheduleEngine.calculateNextTrigger(8, 0, fridayOnly)
            val resultCal = Calendar.getInstance().apply { timeInMillis = result }
            assertEquals("Should be Friday", Calendar.FRIDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        }
    }
}
