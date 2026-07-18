package com.example.myapplication.data.repository

import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LifeRecordRepositoryTest {

    @Test
    fun `clearing echo preferences preserves records and generated echoes`() {
        val original = LifeRecord(
            id = "record-1",
            createdAt = 10L,
            date = "2026-07-18",
            content = "今天完成了一件事",
            source = "text",
            microEcho = "这一步值得被记住。",
            microEchoLiked = true,
            rejectedMicroEchoes = listOf("不太像我的话")
        )

        val cleared = LifeRecordRepository.withoutMicroEchoPreferences(listOf(original)).single()

        assertEquals(original.id, cleared.id)
        assertEquals(original.content, cleared.content)
        assertEquals(original.microEcho, cleared.microEcho)
        assertFalse(cleared.microEchoLiked)
        assertNull(cleared.rejectedMicroEchoes)
    }
}
