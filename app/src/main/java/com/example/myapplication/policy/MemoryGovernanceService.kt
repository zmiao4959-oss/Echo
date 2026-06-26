package com.example.myapplication.policy

/**
 * Pure Kotlin: Unified memory governance — all enable/disable/confirm/discard/pin/edit
 * actions go through here. Writes audit log for accountability.
 *
 * This is the single source of truth for memory state transitions.
 * UI and other layers should NOT directly manipulate repository fields.
 */
object MemoryGovernanceService {

    // ── Audit ──

    data class AuditEntry(
        val action: String,      // "confirm" | "discard" | "disable" | "enable" | "pin" | "unpin" | "edit"
        val memoryType: String,  // "profile" | "memory_card" | "memory_md"
        val memoryId: String,
        val timestamp: Long,
        val summary: String      // short non-private description
    )

    private val auditLog = mutableListOf<AuditEntry>()
    private const val MAX_AUDIT_ENTRIES = 200

    /** Callback for persisting audit entries. Android layer registers this. */
    var onAuditPersist: ((AuditEntry) -> Unit)? = null

    /** Record an audit entry. Thread-safe for in-memory log. */
    @Synchronized
    fun recordAudit(entry: AuditEntry) {
        auditLog.add(entry)
        if (auditLog.size > MAX_AUDIT_ENTRIES) {
            auditLog.removeAt(0)
        }
        // Notify persistence layer
        onAuditPersist?.invoke(entry)
    }

    /** Load entries from external source (e.g., disk) into in-memory log. */
    @Synchronized
    fun loadFromExternal(entries: List<AuditEntry>) {
        auditLog.clear()
        auditLog.addAll(entries.takeLast(MAX_AUDIT_ENTRIES))
    }

    /** Get all audit entries (most recent first). */
    fun getAuditLog(): List<AuditEntry> = auditLog.toList().reversed()

    // ── Profile actions (pure, no I/O — caller persists) ──

    data class ProfileActionResult(
        val updated: com.example.myapplication.data.model.UserProfileMemory?,
        val auditEntry: AuditEntry
    )

    fun confirmProfile(profile: com.example.myapplication.data.model.UserProfileMemory): ProfileActionResult {
        val updated = profile.copy(status = "confirmed")
        val entry = AuditEntry("confirm", "profile", profile.id, System.currentTimeMillis(),
            "确认画像: ${profile.value.take(30)}")
        recordAudit(entry)
        return ProfileActionResult(updated, entry)
    }

    fun discardProfile(profile: com.example.myapplication.data.model.UserProfileMemory): ProfileActionResult {
        val entry = AuditEntry("discard", "profile", profile.id, System.currentTimeMillis(),
            "丢弃画像: ${profile.value.take(30)}")
        recordAudit(entry)
        return ProfileActionResult(null, entry)
    }

    fun disableProfile(profile: com.example.myapplication.data.model.UserProfileMemory): ProfileActionResult {
        val updated = profile.copy(enabled = false)
        val entry = AuditEntry("disable", "profile", profile.id, System.currentTimeMillis(),
            "禁用画像: ${profile.value.take(30)}")
        recordAudit(entry)
        return ProfileActionResult(updated, entry)
    }

    fun enableProfile(profile: com.example.myapplication.data.model.UserProfileMemory): ProfileActionResult {
        val updated = profile.copy(enabled = true, status = "confirmed")
        val entry = AuditEntry("enable", "profile", profile.id, System.currentTimeMillis(),
            "启用画像: ${profile.value.take(30)}")
        recordAudit(entry)
        return ProfileActionResult(updated, entry)
    }

    fun editProfile(profile: com.example.myapplication.data.model.UserProfileMemory,
                    newValue: String): ProfileActionResult {
        val updated = profile.copy(value = newValue)
        val entry = AuditEntry("edit", "profile", profile.id, System.currentTimeMillis(),
            "编辑画像: ${profile.value.take(20)} → ${newValue.take(20)}")
        recordAudit(entry)
        return ProfileActionResult(updated, entry)
    }

    // ── Card actions ──

    data class CardActionResult(
        val updated: com.example.myapplication.data.model.MemoryCard?,
        val auditEntry: AuditEntry
    )

    fun togglePin(card: com.example.myapplication.data.model.MemoryCard): CardActionResult {
        val updated = card.copy(pinned = !card.pinned)
        val action = if (updated.pinned) "pin" else "unpin"
        val entry = AuditEntry(action, "memory_card", card.id, System.currentTimeMillis(),
            if (updated.pinned) "置顶卡片: ${card.quote.take(30)}" else "取消置顶: ${card.quote.take(30)}")
        recordAudit(entry)
        return CardActionResult(updated, entry)
    }

    fun disableCard(card: com.example.myapplication.data.model.MemoryCard): CardActionResult {
        val updated = card.copy(status = "disabled")
        val entry = AuditEntry("disable", "memory_card", card.id, System.currentTimeMillis(),
            "禁用卡片: ${card.quote.take(30)}")
        recordAudit(entry)
        return CardActionResult(updated, entry)
    }

    fun enableCard(card: com.example.myapplication.data.model.MemoryCard): CardActionResult {
        val updated = card.copy(status = "confirmed")
        val entry = AuditEntry("enable", "memory_card", card.id, System.currentTimeMillis(),
            "启用卡片: ${card.quote.take(30)}")
        recordAudit(entry)
        return CardActionResult(updated, entry)
    }

    // ── Query ──

    /** Get recent audit entries filtered by action type. */
    fun getAuditByAction(action: String): List<AuditEntry> =
        getAuditLog().filter { it.action == action }

    /** Get recent audit entries filtered by memory type. */
    fun getAuditByType(memoryType: String): List<AuditEntry> =
        getAuditLog().filter { it.memoryType == memoryType }

    /** Clear in-memory audit log (for testing). */
    @Synchronized
    fun clearAuditLog() {
        auditLog.clear()
    }
}
