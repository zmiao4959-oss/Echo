package com.example.myapplication.data.repository

import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.data.model.ForeshadowOutcome
import com.example.myapplication.data.model.ForeshadowSourceRef
import com.example.myapplication.data.model.ForeshadowState
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.data.store.JsonAtomicWriter
import com.example.myapplication.policy.ForeshadowPolicy
import com.example.myapplication.search.SearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ForeshadowRepository(
    private val file: File = EchoFileStore.foreshadowsFile
) {
    suspend fun getAll(): List<EchoForeshadow> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems(file)
    }

    suspend fun getWatching(): List<EchoForeshadow> = getAll().filter {
        it.state == ForeshadowState.WATCHING
    }

    suspend fun getDue(nowMillis: Long): List<EchoForeshadow> = getWatching()
        .filter { it.nextCheckAt != null && it.nextCheckAt <= nowMillis }
        .sortedWith(compareByDescending<EchoForeshadow> { it.importance }.thenBy { it.nextCheckAt })

    suspend fun add(thread: EchoForeshadow) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val items = JsonAtomicWriter.readItems<EchoForeshadow>(file).toMutableList()
            if (items.none { it.id == thread.id }) {
                items += thread
                JsonAtomicWriter.writeItems(file, items)
                index(thread)
            }
        }
    }

    suspend fun addEvidence(
        threadId: String,
        source: ForeshadowSourceRef,
        suggestedOutcome: ForeshadowOutcome? = null,
        suggestionConfidence: Float? = null
    ) =
        update(threadId) { current ->
            val alreadyLinked = current.sourceRefs.any { it.type == source.type && it.id == source.id }
            current.copy(
                sourceRefs = if (alreadyLinked) current.sourceRefs else current.sourceRefs + source,
                lastEvidenceAt = maxOf(current.lastEvidenceAt, source.createdAt),
                suggestedOutcome = suggestedOutcome ?: current.suggestedOutcome,
                suggestionConfidence = suggestionConfidence ?: current.suggestionConfidence
            )
        }

    suspend fun respond(
        threadId: String,
        outcome: ForeshadowOutcome,
        responseSource: ForeshadowSourceRef,
        nowMillis: Long
    ) = update(threadId) { current ->
        val sources = if (current.sourceRefs.any { it.id == responseSource.id }) {
            current.sourceRefs
        } else {
            current.sourceRefs + responseSource
        }
        if (outcome == ForeshadowOutcome.CONTINUING) {
            current.copy(
                state = ForeshadowState.WATCHING,
                outcome = outcome,
                sourceRefs = sources,
                lastEvidenceAt = nowMillis,
                nextCheckAt = nowMillis + 14L * ForeshadowPolicy.DAY_MILLIS,
                lastPromptedAt = nowMillis,
                promptCount = current.promptCount + 1,
                suggestedOutcome = null,
                suggestionConfidence = null
            )
        } else {
            current.copy(
                state = ForeshadowState.CLOSED,
                outcome = outcome,
                sourceRefs = sources,
                lastEvidenceAt = nowMillis,
                nextCheckAt = null,
                lastPromptedAt = nowMillis,
                promptCount = current.promptCount + 1,
                suggestedOutcome = null,
                suggestionConfidence = null
            )
        }
    }

    suspend fun snooze(threadId: String, nowMillis: Long) = update(threadId) { current ->
        val nextPromptCount = current.promptCount + 1
        val sleepDays = if (nextPromptCount >= 2) 30L else 7L
        current.copy(
            nextCheckAt = nowMillis + sleepDays * ForeshadowPolicy.DAY_MILLIS,
            lastPromptedAt = nowMillis,
            promptCount = nextPromptCount
        )
    }

    suspend fun dismiss(threadId: String, nowMillis: Long) = update(threadId) { current ->
        current.copy(
            state = ForeshadowState.DISMISSED,
            nextCheckAt = null,
            lastPromptedAt = nowMillis
        )
    }

    suspend fun removeSource(sourceId: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val original = JsonAtomicWriter.readItems<EchoForeshadow>(file)
            val removedThreadIds = mutableListOf<String>()
            val changedThreadIds = mutableListOf<String>()
            val updated = original.mapNotNull { thread ->
                val remaining = thread.sourceRefs.filterNot { it.id == sourceId }
                when {
                    remaining.size == thread.sourceRefs.size -> thread
                    remaining.isEmpty() -> {
                        removedThreadIds += thread.id
                        null
                    }
                    else -> {
                        changedThreadIds += thread.id
                        thread.copy(
                            sourceRefs = remaining,
                            lastEvidenceAt = remaining.maxOf { it.createdAt }
                        )
                    }
                }
            }
            if (updated != original) {
                JsonAtomicWriter.writeItems(file, updated)
                removedThreadIds.forEach { SearchIndex.remove("foreshadow", it) }
                updated.filter { it.id in changedThreadIds }.forEach(::index)
            }
        }
    }

    private suspend fun update(
        threadId: String,
        transform: (EchoForeshadow) -> EchoForeshadow
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val items = JsonAtomicWriter.readItems<EchoForeshadow>(file).toMutableList()
            val itemIndex = items.indexOfFirst { it.id == threadId }
            if (itemIndex < 0) return@withLock
            val updated = transform(items[itemIndex])
            items[itemIndex] = updated
            JsonAtomicWriter.writeItems(file, items)
            if (updated.state == ForeshadowState.DISMISSED) {
                SearchIndex.remove("foreshadow", updated.id)
            } else {
                index(updated)
            }
        }
    }

    private fun index(thread: EchoForeshadow) {
        val text = buildString {
            append(thread.title).append(' ').append(thread.subject).append(' ')
            append(thread.followUpQuestion).append(' ')
            thread.sourceRefs.forEach { append(it.excerpt).append(' ') }
        }
        SearchIndex.upsert("foreshadow", thread.id, text, thread.lastEvidenceAt)
    }

    private companion object {
        val writeMutex = Mutex()
    }
}
