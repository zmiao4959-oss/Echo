package com.example.myapplication.domain

import com.example.myapplication.data.model.LifeRecord
import com.example.myapplication.data.repository.ForeshadowRepository
import com.example.myapplication.data.model.ForeshadowOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ForeshadowCoordinatorTest {
    private lateinit var directory: File
    private lateinit var repository: ForeshadowRepository
    private lateinit var coordinator: ForeshadowCoordinator

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("echo-foreshadow-coordinator").toFile()
        repository = ForeshadowRepository(directory.resolve("foreshadows.json"))
        coordinator = ForeshadowCoordinator(repository)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `backfill creates one story and attaches later evidence`() = runBlocking {
        val records = listOf(
            record("start", 1_000L, "最近一直想重新开始跑步"),
            record("progress", 2_000L, "今天出门跑了三公里")
        )

        assertEquals(1, coordinator.backfill(records))
        val stored = repository.getAll().single()
        assertEquals(setOf("start", "progress"), stored.sourceRefs.map { it.id }.toSet())
        assertTrue(stored.followUpQuestion.contains("跑步"))

        assertEquals(0, coordinator.backfill(records))
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun `an explicit ai rejection prevents a local candidate`() = runBlocking {
        val interpreter = ForeshadowInterpreter { _, _ -> """{"qualifies":false}""" }
        val aiCoordinator = ForeshadowCoordinator(repository, interpreter)

        aiCoordinator.onRecordSaved(record("idea", 3_000L, "最近一直想重新开始跑步"))

        assertTrue(repository.getAll().isEmpty())
    }

    @Test
    fun `ai relation adds evidence but only suggests the outcome`() = runBlocking {
        coordinator.onRecordSaved(record("start", 1_000L, "最近一直想重新开始跑步"))
        val interpreter = ForeshadowInterpreter { _, _ ->
            """{"matches":true,"relation":"PROGRESS","confidence":0.9,"evidenceQuote":"跑了三公里"}"""
        }
        val aiCoordinator = ForeshadowCoordinator(repository, interpreter)

        aiCoordinator.onRecordSaved(record("progress", 2_000L, "今天出门跑了三公里"))

        val stored = repository.getAll().single()
        assertEquals(setOf("start", "progress"), stored.sourceRefs.map { it.id }.toSet())
        assertEquals(ForeshadowOutcome.CONTINUING, stored.suggestedOutcome)
        assertEquals(null, stored.outcome)
    }

    private fun record(id: String, createdAt: Long, content: String) = LifeRecord(
        id = id,
        createdAt = createdAt,
        date = "2026-07-01",
        content = content,
        source = "text"
    )
}
