package com.example.myapplication.policy

import com.example.myapplication.data.model.EchoForeshadow
import com.example.myapplication.data.model.ForeshadowSourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeshadowPolicyTest {
    private val now = 1_000_000L

    @Test
    fun `an uncertain intention becomes a foreshadow`() {
        val result = ForeshadowPolicy.detect("最近一直想重新开始跑步，不知道能不能坚持", now)

        assertNotNull(result)
        assertTrue(result!!.subject.contains("跑步"))
        assertTrue(result.followUpQuestion.contains("后来怎么样"))
        assertTrue(result.nextCheckAt > now)
    }

    @Test
    fun `an exact task remains a plan instead of a foreshadow`() {
        assertNull(ForeshadowPolicy.detect("明天下午三点提醒我取快递", now))
    }

    @Test
    fun `completed and negated statements are rejected`() {
        assertNull(ForeshadowPolicy.detect("我已经完成了这次考试", now))
        assertNull(ForeshadowPolicy.detect("我不想再学吉他了", now))
    }

    @Test
    fun `similar subjects are deduplicated`() {
        val existing = thread("重新开始跑步")

        assertTrue(ForeshadowPolicy.isDuplicate("开始跑步", listOf(existing)))
        assertFalse(ForeshadowPolicy.isDuplicate("学习水彩画", listOf(existing)))
    }

    @Test
    fun `later progress can find its existing story`() {
        val running = thread("重新开始跑步")
        val painting = thread("学习水彩画").copy(id = "painting")

        val result = ForeshadowPolicy.findRelated(
            "今天终于出门跑了三公里",
            listOf(painting, running)
        )

        assertEquals(running.id, result?.id)
    }

    private fun thread(subject: String) = EchoForeshadow(
        id = "running",
        subject = subject,
        title = subject,
        followUpQuestion = "后来怎么样了？",
        confidence = 0.8f,
        sourceRefs = listOf(ForeshadowSourceRef("life_record", "r1", subject, now)),
        createdAt = now,
        lastEvidenceAt = now,
        nextCheckAt = now + ForeshadowPolicy.DAY_MILLIS
    )
}
