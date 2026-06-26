package com.example.myapplication.memory

import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import com.example.myapplication.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified memory injection entry point — all Agent memory injection must go through here.
 *
 * Permission rules (centralized, Agent no longer decides):
 * - MEMORY.md and memory dir md files: only confirmed sections searchable (guaranteed by MemoryMdParser)
 * - UserProfileMemory: only enabled=true and status=confirmed injectable
 * - MemoryCard: only status=confirmed injectable
 */
object MemoryContextBuilder {

    data class MemoryContext(
        /** User profile summary for system prompt (~600 chars) */
        val profileSummary: String,
        /** Relevant memory snippets for system prompt */
        val relevantMemorySection: String,
        /** [Memory Search Results] prefix prepended to user message */
        val memoryPrefix: String,
        /** Structured profile injection (enabled + confirmed) */
        val structuredProfileInjection: String
    )

    /**
     * Single call, returns all memory context. Agent calls once, distributes fields.
     */
    suspend fun build(userMessage: String): MemoryContext = withContext(Dispatchers.IO) {
        val repo = MemoryRepository()

        // 1. Keyword search over confirmed-only MEMORY.md / memory *.md
        val searchResults = if (userMessage.isNotBlank()) {
            MemorySearch.keywordSearch(FileStore.workspaceDir, userMessage, maxResults = 3)
        } else emptyList()

        // 2. Confirmed + enabled UserProfileMemory
        val profiles = repo.getEnabledProfiles().filter { it.status == "confirmed" }

        // 3. Confirmed MemoryCards (recent 2)
        val confirmedCards = repo.getAllCards().filter { it.status == "confirmed" }
        val recentCards = confirmedCards.sortedByDescending { it.createdAt }.take(2)

        // 4. Read confirmed section of MEMORY.md for profile summary
        val confirmedMdContent = MemoryMdParser.readConfirmedSection(
            FileStore.readWorkspaceFile("MEMORY.md")
        )

        MemoryContext(
            profileSummary = buildProfileSummary(confirmedMdContent),
            relevantMemorySection = buildRelevantMemorySection(searchResults, recentCards),
            memoryPrefix = buildMemoryPrefix(searchResults),
            structuredProfileInjection = buildStructuredProfileInjection(profiles)
        )
    }

    // -- private builders --

    private fun buildProfileSummary(confirmedContent: String): String {
        if (confirmedContent.isBlank()) return ""
        val lines = confirmedContent.split("\n").filter { it.isNotBlank() }
        val summary = lines.take(10).joinToString("\n")
        return if (summary.length > 600) summary.take(600) + "…" else summary
    }

    private fun buildRelevantMemorySection(
        searchResults: List<MemorySearch.SearchResult>,
        recentCards: List<MemoryCard>
    ): String {
        if (searchResults.isEmpty() && recentCards.isEmpty()) return ""
        return buildString {
            for (r in searchResults) {
                val shortSnippet = r.snippet.take(300)
                append("\n[记忆: ${r.filePath}]\n$shortSnippet\n")
            }
            for (c in recentCards) {
                append("\n[最近: ${c.id}]\n${c.quote.take(300)}\n")
            }
        }
    }

    private fun buildMemoryPrefix(results: List<MemorySearch.SearchResult>): String {
        if (results.isEmpty()) return ""
        val lines = mutableListOf("[Memory Search Results]")
        for (r in results) {
            lines.add("Source: ${r.filePath} (score: ${"%.2f".format(r.score)})")
            lines.add(r.snippet)
            lines.add("")
        }
        return lines.joinToString("\n")
    }

    private fun buildStructuredProfileInjection(profiles: List<UserProfileMemory>): String {
        if (profiles.isEmpty()) return ""
        val cats = linkedMapOf(
            "identity" to "身份", "preference" to "偏好", "habit" to "习惯",
            "goal" to "目标", "project" to "项目", "relationship" to "关系"
        )
        return buildString {
            append("\n\n## 结构化用户画像\n")
            for ((cat, label) in cats) {
                val items = profiles.filter { it.category == cat }
                if (items.isNotEmpty()) {
                    append("- $label: ${items.joinToString("；") { it.value }}\n")
                }
            }
        }
    }
}
