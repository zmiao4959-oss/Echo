package com.example.myapplication.data.repository

import com.example.myapplication.data.model.LifeJournalIssue
import com.example.myapplication.data.store.EchoFileStore
import com.example.myapplication.data.store.JsonAtomicWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LifeJournalRepository {
    private val file = EchoFileStore.lifeJournalsFile

    suspend fun getAll(): List<LifeJournalIssue> = withContext(Dispatchers.IO) {
        JsonAtomicWriter.readItems<LifeJournalIssue>(file)
            .sortedByDescending { it.updatedAt }
    }

    suspend fun getLatest(): LifeJournalIssue? = getAll().firstOrNull()

    suspend fun getForPeriod(startDate: String, endDate: String): LifeJournalIssue? =
        getAll().firstOrNull { it.startDate == startDate && it.endDate == endDate }

    suspend fun save(issue: LifeJournalIssue) = withContext(Dispatchers.IO) {
        val items = JsonAtomicWriter.readItems<LifeJournalIssue>(file).toMutableList()
        val index = items.indexOfFirst { it.id == issue.id }
        if (index >= 0) items[index] = issue else items.add(issue)
        JsonAtomicWriter.writeItems(file, items, schemaVersion = 1)
    }
}
