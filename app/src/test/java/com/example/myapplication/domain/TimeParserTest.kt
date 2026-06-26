package com.example.myapplication.domain

import org.junit.Assert.*
import org.junit.Test

class TimeParserTest {

    @Test
    fun `parseIsoTime with ISO format returns epoch ms`() {
        val result = TimeParser.parseIsoTime("2026-06-27T21:00")
        assertNotNull("Should parse valid ISO time", result)
        val expected = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-06-27 21:00")!!.time
        assertEquals(expected, result)
    }

    @Test
    fun `parseIsoTime with space separator returns epoch ms`() {
        val result = TimeParser.parseIsoTime("2026-06-27 21:00")
        assertNotNull("Should parse space-separated time", result)
    }

    @Test
    fun `parseIsoTime null input returns null`() {
        assertNull(TimeParser.parseIsoTime(null))
    }

    @Test
    fun `parseIsoTime invalid string returns null`() {
        assertNull(TimeParser.parseIsoTime("not-a-date"))
        assertNull(TimeParser.parseIsoTime(""))
        assertNull(TimeParser.parseIsoTime("abc123"))
    }

    @Test
    fun `parseTimestamp with Number returns Long`() {
        assertEquals(1617000000000L, TimeParser.parseTimestamp(1617000000000L))
        assertEquals(0L, TimeParser.parseTimestamp(0))
    }

    @Test
    fun `parseTimestamp with numeric String returns Long`() {
        assertEquals(1617000000000L, TimeParser.parseTimestamp("1617000000000"))
    }

    @Test
    fun `parseTimestamp with invalid String returns null`() {
        assertNull(TimeParser.parseTimestamp("abc"))
        assertNull(TimeParser.parseTimestamp(""))
    }

    @Test
    fun `parseTimestamp returns null for invalid String`() {
        // "abc" matches 'is String' → toLongOrNull returns null → no fallback
        assertNull(TimeParser.parseTimestamp("abc"))
        assertNull(TimeParser.parseTimestamp("abc", fallback = 999L))
    }

    @Test
    fun `parseTimestamp returns fallback for non-String non-Number types`() {
        // null / Boolean / etc. match 'else' → fallback used
        assertEquals(42L, TimeParser.parseTimestamp(null, fallback = 42L))
        assertEquals(999L, TimeParser.parseTimestamp(listOf(1), fallback = 999L))
    }
}
