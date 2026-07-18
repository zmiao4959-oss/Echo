package com.example.myapplication.data.repository

import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.data.model.ForeshadowOutcome
import com.example.myapplication.data.model.ForeshadowSourceRef
import com.example.myapplication.data.model.ForeshadowState
import com.example.myapplication.policy.ForeshadowPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ForeshadowRepositoryTest {
    private lateinit var directory: File
    private lateinit var repository: ForeshadowRepository
    private val now = 2_000_000L

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("echo-foreshadow-test").toFile()
        repository = ForeshadowRepository(directory.resolve("foreshadows.json"))
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `due threads are ordered and closed threads disappear`() = runBlocking {
        repository.add(thread("later", nextCheckAt = now + ForeshadowPolicy.DAY_MILLIS))
        repository.add(thread("due", nextCheckAt = now - 1L))

        assertEquals(listOf("due"), repository.getDue(now).map { it.id })

        repository.respond(
            "due",
            ForeshadowOutcome.HAPPENED,
            ForeshadowSourceRef("life_record", "answer", "已经发生了", now),
            now
        )
        val closed = repository.getAll().first { it.id == "due" }
        assertEquals(ForeshadowState.CLOSED, closed.state)
        assertNull(closed.nextCheckAt)
        assertTrue(repository.getDue(now).isEmpty())
    }

    @Test
    fun `continuing keeps the thread alive and snooze backs off`() = runBlocking {
        repository.add(thread("due", nextCheckAt = now - 1L))
        repository.respond(
            "due",
            ForeshadowOutcome.CONTINUING,
            ForeshadowSourceRef("life_record", "answer", "还在继续", now),
            now
        )
        val continuing = repository.getAll().single()
        assertEquals(ForeshadowState.WATCHING, continuing.state)
        assertEquals(ForeshadowOutcome.CONTINUING, continuing.outcome)
        assertTrue(continuing.nextCheckAt!! > now)

        repository.snooze("due", now)
        val snoozed = repository.getAll().single()
        assertEquals(now + 30L * ForeshadowPolicy.DAY_MILLIS, snoozed.nextCheckAt)
    }

    @Test
    fun `deleting the only source removes its thread`() = runBlocking {
        repository.add(thread("only", nextCheckAt = now))
        repository.removeSource("source-only")
        assertTrue(repository.getAll().isEmpty())
    }

    private fun thread(id: String, nextCheckAt: Long) = EchoForeshadow(
        id = id,
        subject = "重新开始跑步",
        title = "重新开始跑步",
        followUpQuestion = "这件事后来怎么样了？",
        confidence = 0.82f,
        sourceRefs = listOf(
            ForeshadowSourceRef("life_record", "source-$id", "我想重新开始跑步", now - 10L)
        ),
        createdAt = now - 10L,
        lastEvidenceAt = now - 10L,
        nextCheckAt = nextCheckAt
    )
}
