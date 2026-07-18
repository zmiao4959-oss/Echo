package com.example.myapplication.domain

import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.data.model.ForeshadowSourceRef
import com.example.myapplication.policy.ForeshadowPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeshadowInterpreterTest {
    private val now = 5_000_000L
    private val source = "最近总想着重新开始学摄影，但不知道自己能不能坚持。"
    private val fallback = requireNotNull(ForeshadowPolicy.detect(source, now))

    @Test
    fun `grounded fenced json refines a local candidate`() = runBlocking {
        val interpreter = ForeshadowInterpreter { _, _ ->
            """```json
                {"qualifies":true,"subject":"重新开始学摄影","followUpQuestion":"你后来重新拿起相机了吗？","horizonDays":21,"confidence":0.88,"evidenceQuote":"重新开始学摄影"}
                ```""".trimIndent()
        }

        val decision = interpreter.interpretCandidate(source, now, fallback)

        assertTrue(decision!!.qualifies)
        assertEquals("重新开始学摄影", decision.candidate?.subject)
        assertEquals(now + 21L * ForeshadowPolicy.DAY_MILLIS, decision.candidate?.nextCheckAt)
    }

    @Test
    fun `invented evidence is rejected so caller can fall back locally`() = runBlocking {
        val interpreter = ForeshadowInterpreter { _, _ ->
            """{"qualifies":true,"subject":"开一家摄影工作室","followUpQuestion":"工作室开起来了吗？","horizonDays":30,"confidence":0.9,"evidenceQuote":"开一家摄影工作室"}"""
        }

        assertNull(interpreter.interpretCandidate(source, now, fallback))
    }

    @Test
    fun `explicit rejection remains distinguishable from parse failure`() = runBlocking {
        val interpreter = ForeshadowInterpreter { _, _ -> """{"qualifies":false}""" }
        val decision = interpreter.interpretCandidate(source, now, fallback)
        assertNotNull(decision)
        assertFalse(decision!!.qualifies)
    }

    @Test
    fun `relation requires an exact quote from the new source`() = runBlocking {
        val newSource = "今天终于拿相机出门拍了一下午。"
        val interpreter = ForeshadowInterpreter { _, _ ->
            """{"matches":true,"relation":"PROGRESS","confidence":0.91,"evidenceQuote":"拿相机出门拍了一下午"}"""
        }

        val decision = interpreter.interpretRelation(thread(), newSource)

        assertTrue(decision!!.matches)
        assertEquals(ForeshadowInterpreter.Relation.PROGRESS, decision.relation)
    }

    private fun thread() = EchoForeshadow(
        id = "photography",
        subject = "重新开始学摄影",
        title = "重新开始学摄影",
        followUpQuestion = "后来怎么样了？",
        confidence = 0.8f,
        sourceRefs = listOf(ForeshadowSourceRef("life_record", "r1", source, now)),
        createdAt = now,
        lastEvidenceAt = now,
        nextCheckAt = now + ForeshadowPolicy.DAY_MILLIS
    )
}
