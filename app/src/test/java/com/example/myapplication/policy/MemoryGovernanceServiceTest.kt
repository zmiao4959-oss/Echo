package com.example.myapplication.policy

import com.example.myapplication.data.model.MemoryCard
import com.example.myapplication.data.model.UserProfileMemory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemoryGovernanceServiceTest {

    private val svc = MemoryGovernanceService

    @Before
    fun setUp() {
        svc.clearAuditLog()
    }

    // ── Profile: confirm ──

    @Test
    fun `confirmProfile sets status to confirmed`() {
        val profile = UserProfileMemory(
            id = "p1", key = "test", value = "用户叫小明", category = "identity",
            confidence = 0.6f, sourceIds = emptyList(),
            createdAt = 1000L, updatedAt = 1000L,
            status = "pending"
        )
        val result = svc.confirmProfile(profile)
        assertEquals("confirmed", result.updated!!.status)
        assertNotNull(svc.getAuditByAction("confirm").find { it.memoryId == "p1" })
    }

    @Test
    fun `confirmProfile writes audit entry`() {
        val profile = UserProfileMemory(
            id = "p2", key = "k2", value = "喜欢咖啡", category = "preference",
            confidence = 0.8f, sourceIds = emptyList(),
            createdAt = 1000L, updatedAt = 1000L,
            status = "pending"
        )
        svc.confirmProfile(profile)
        val audit = svc.getAuditByAction("confirm")
        assertEquals(1, audit.size)
        assertEquals("p2", audit[0].memoryId)
        assertEquals("profile", audit[0].memoryType)
    }

    // ── Profile: discard ──

    @Test
    fun `discardProfile returns null updated`() {
        val profile = UserProfileMemory(
            id = "p3", key = "k3", value = "待丢弃", category = "habit",
            confidence = 0.5f, sourceIds = emptyList(),
            createdAt = 1000L, updatedAt = 1000L
        )
        val result = svc.discardProfile(profile)
        assertNull(result.updated)
        assertNotNull(svc.getAuditByAction("discard").find { it.memoryId == "p3" })
    }

    // ── Profile: disable ──

    @Test
    fun `disableProfile sets enabled to false`() {
        val profile = UserProfileMemory(
            id = "p4", key = "k4", value = "待禁用", category = "goal",
            confidence = 0.9f, sourceIds = emptyList(),
            createdAt = 1000L, updatedAt = 1000L,
            enabled = true
        )
        val result = svc.disableProfile(profile)
        assertFalse(result.updated!!.enabled)
    }

    // ── Profile: enable ──

    @Test
    fun `enableProfile sets enabled to true and status to confirmed`() {
        val profile = UserProfileMemory(
            id = "p5", key = "k5", value = "待启用", category = "identity",
            confidence = 0.7f, sourceIds = emptyList(),
            createdAt = 1000L, updatedAt = 1000L,
            enabled = false, status = "disabled"
        )
        val result = svc.enableProfile(profile)
        assertTrue(result.updated!!.enabled)
        assertEquals("confirmed", result.updated!!.status)
    }

    // ── Profile: edit ──

    @Test
    fun `editProfile updates value`() {
        val profile = UserProfileMemory(
            id = "p6", key = "k6", value = "旧值", category = "preference",
            confidence = 0.8f, sourceIds = emptyList(),
            createdAt = 1000L, updatedAt = 1000L
        )
        val result = svc.editProfile(profile, "新值")
        assertEquals("新值", result.updated!!.value)
        assertNotNull(svc.getAuditByAction("edit").find { it.memoryId == "p6" })
    }

    // ── Card: togglePin ──

    @Test
    fun `togglePin toggles pinned state`() {
        val card = MemoryCard(
            id = "c1", createdAt = 1000L, memoryDate = "2026-06-26",
            quote = "值得回忆", note = "重要",
            tags = emptyList(), sourceType = "chat", sourceId = "src1",
            mood = null, pinned = false
        )
        val result = svc.togglePin(card)
        assertTrue(result.updated!!.pinned)
        assertNotNull(svc.getAuditByAction("pin").find { it.memoryId == "c1" })

        // Toggle again
        val result2 = svc.togglePin(result.updated!!)
        assertFalse(result2.updated!!.pinned)
        assertNotNull(svc.getAuditByAction("unpin").find { it.memoryId == "c1" })
    }

    // ── Card: disable ──

    @Test
    fun `disableCard sets status to disabled`() {
        val card = MemoryCard(
            id = "c2", createdAt = 1000L, memoryDate = "2026-06-26",
            quote = "旧卡片", note = "",
            tags = emptyList(), sourceType = "chat", sourceId = "src2"
        )
        val result = svc.disableCard(card)
        assertEquals("disabled", result.updated!!.status)
    }

    // ── Card: enable ──

    @Test
    fun `enableCard sets status to confirmed`() {
        val card = MemoryCard(
            id = "c3", createdAt = 1000L, memoryDate = "2026-06-26",
            quote = "恢复卡片", note = "",
            tags = emptyList(), sourceType = "diary", sourceId = "src3",
            status = "disabled"
        )
        val result = svc.enableCard(card)
        assertEquals("confirmed", result.updated!!.status)
    }

    // ── Audit ──

    @Test
    fun `audit log respects max entries`() {
        for (i in 0..210) {
            val profile = UserProfileMemory(
                id = "audit$i", key = "k$i", value = "v$i", category = "habit",
                confidence = 0.5f, sourceIds = emptyList(),
                createdAt = 1000L, updatedAt = 1000L
            )
            svc.discardProfile(profile)
        }
        val log = svc.getAuditLog()
        assertTrue("Audit log should be capped at 200", log.size <= 200)
    }

    @Test
    fun `getAuditByAction filters correctly`() {
        val p1 = UserProfileMemory("p_a", "k", "v", "habit", 0.5f, emptyList(), 1L, 1L)
        val p2 = UserProfileMemory("p_b", "k2", "v2", "preference", 0.8f, emptyList(), 1L, 1L)
        svc.confirmProfile(p1)
        svc.disableProfile(p2)
        assertEquals(1, svc.getAuditByAction("confirm").size)
        assertEquals(1, svc.getAuditByAction("disable").size)
    }

    @Test
    fun `getAuditByType filters correctly`() {
        val p = UserProfileMemory("p_type", "k", "v", "identity", 0.5f, emptyList(), 1L, 1L)
        val c = MemoryCard(id = "c_type", createdAt = 1L, memoryDate = "2026-06-26",
            quote = "q", note = "", tags = emptyList(), sourceType = "chat", sourceId = "s")
        svc.discardProfile(p)
        svc.togglePin(c)
        assertEquals(1, svc.getAuditByType("profile").size)
        assertEquals(1, svc.getAuditByType("memory_card").size)
    }
}
