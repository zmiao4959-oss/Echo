package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicroEchoFeedbackPolicyTest {

    private fun record(
        id: String,
        createdAt: Long,
        content: String = "片段 $id",
        echo: String? = null,
        liked: Boolean = false,
        rejected: List<String>? = null,
        mood: String? = null,
        tags: List<String> = emptyList()
    ) = LifeRecord(
        id = id,
        createdAt = createdAt,
        date = "2026-07-18",
        content = content,
        source = "text",
        mood = mood,
        tags = tags,
        microEcho = echo,
        microEchoLiked = liked,
        rejectedMicroEchoes = rejected
    )

    @Test
    fun `liked and rejected examples are collected newest first`() {
        val preferences = MicroEchoFeedbackPolicy.collect(
            listOf(
                record("old", 1L, echo = "旧的喜欢", liked = true, rejected = listOf("旧的拒绝")),
                record("new", 2L, echo = "新的喜欢", liked = true, rejected = listOf("新的拒绝"))
            )
        )

        assertEquals(listOf("新的喜欢", "旧的喜欢"), preferences.liked)
        assertEquals(listOf("新的拒绝", "旧的拒绝"), preferences.rejected)
    }

    @Test
    fun `adding a rejection removes duplicates and keeps a bounded history`() {
        val updated = MicroEchoFeedbackPolicy.addRejected(
            existing = listOf("一", "二", "三", "四", "五", "六"),
            echo = "二"
        )

        assertEquals(6, updated.size)
        assertEquals(1, updated.count { it == "二" })
        assertTrue("六" in updated)
        assertFalse("一" in MicroEchoFeedbackPolicy.addRejected(updated, "七"))
    }

    @Test
    fun `content relevance outranks recency when selecting liked examples`() {
        val target = record(
            id = "target",
            createdAt = 30L,
            content = "今天终于完成了安卓项目的提醒功能",
            tags = listOf("项目")
        )
        val preferences = MicroEchoFeedbackPolicy.collect(
            records = listOf(
                record(
                    id = "related-old",
                    createdAt = 10L,
                    content = "继续推进安卓项目，把提醒功能完成了",
                    echo = "相关的回应",
                    liked = true,
                    tags = listOf("项目")
                ),
                record(
                    id = "unrelated-new",
                    createdAt = 20L,
                    content = "晚饭的面很好吃",
                    echo = "较新但无关的回应",
                    liked = true
                ),
                target
            ),
            target = target
        )

        assertEquals("相关的回应", preferences.liked.first())
    }

    @Test
    fun `current record rejection is selected before unrelated history`() {
        val target = record(
            id = "target",
            createdAt = 30L,
            content = "今天很累",
            rejected = listOf("刚刚被拒绝的话")
        )
        val preferences = MicroEchoFeedbackPolicy.collect(
            records = listOf(
                record(
                    id = "newer",
                    createdAt = 40L,
                    content = "今天吃到了甜品",
                    rejected = listOf("其他拒绝的话")
                ),
                target
            ),
            target = target
        )

        assertEquals("刚刚被拒绝的话", preferences.rejected.first())
    }

    @Test
    fun `summary counts effective distinct preferences`() {
        val summary = MicroEchoFeedbackPolicy.summarize(
            listOf(
                record("a", 1L, echo = "喜欢", liked = true, rejected = listOf("不喜欢")),
                record("b", 2L, echo = "喜欢", liked = true, rejected = listOf("不喜欢", "另一条"))
            )
        )

        assertEquals(1, summary.likedCount)
        assertEquals(2, summary.rejectedCount)
        assertEquals(3, summary.totalCount)
    }

    @Test
    fun `nearly identical examples do not occupy multiple prompt slots`() {
        val preferences = MicroEchoFeedbackPolicy.collect(
            listOf(
                record("a", 3L, echo = "这一步值得被记住。", liked = true),
                record("b", 2L, echo = "这一步值得被记住！", liked = true),
                record("c", 1L, echo = "今天的开心已经被好好留下。", liked = true)
            )
        )

        assertEquals(2, preferences.liked.size)
        assertTrue("今天的开心已经被好好留下。" in preferences.liked)
    }
}
