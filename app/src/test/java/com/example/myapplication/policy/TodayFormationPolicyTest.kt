package com.example.myapplication.policy

import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayFormationPolicyTest {

    private fun record(
        content: String,
        mood: String? = null,
        tags: List<String> = emptyList()
    ) = LifeRecord(
        id = content,
        createdAt = 1L,
        date = "2026-07-18",
        content = content,
        source = "text",
        mood = mood,
        tags = tags
    )

    @Test
    fun `empty day invites first record without invented themes`() {
        val result = TodayFormationPolicy.build(emptyList())

        assertEquals(0, result.recordCount)
        assertTrue(result.themes.isEmpty())
        assertNull(result.mood)
        assertTrue(result.observation.contains("第一条"))
    }

    @Test
    fun `explicit tags are preferred and internal tags are ignored`() {
        val records = listOf(
            record("今天继续做事", tags = listOf("项目 Alpha", "echo")),
            record("晚上复盘", tags = listOf("项目 Alpha", "life_records"))
        )

        val themes = TodayFormationPolicy.extractThemes(records)

        assertEquals("项目 Alpha", themes.first())
        assertFalse(themes.contains("echo"))
        assertFalse(themes.contains("life_records"))
    }

    @Test
    fun `common Chinese life topics produce stable theme labels`() {
        val records = listOf(
            record("上午开会讨论项目，下午继续写代码"),
            record("下班后跑步半小时")
        )

        val themes = TodayFormationPolicy.extractThemes(records)

        assertTrue(themes.contains("工作"))
        assertTrue(themes.contains("运动"))
    }

    @Test
    fun `repeated Chinese phrase can become a theme without broken bigrams`() {
        val records = listOf(
            record("上午在咖啡馆整理照片"),
            record("下午又去了咖啡馆见朋友")
        )

        val themes = TodayFormationPolicy.extractThemes(records)

        assertTrue(themes.contains("咖啡馆"))
        assertFalse(themes.contains("啡馆"))
    }

    @Test
    fun `explicit mood wins over inferred mood`() {
        val records = listOf(
            record("忙了一整天有点累", mood = "踏实"),
            record("终于做完了", mood = "踏实")
        )

        assertEquals("踏实", TodayFormationPolicy.extractMood(records))
    }

    @Test
    fun `mood is inferred from clear wording`() {
        val records = listOf(record("项目终于搞定了，特别开心"))

        assertEquals("愉快", TodayFormationPolicy.extractMood(records))
    }

    @Test
    fun `negated positive wording is not treated as positive`() {
        val records = listOf(record("今天不开心，有些失落"))

        assertEquals("低落", TodayFormationPolicy.extractMood(records))
    }

    @Test
    fun `unclear wording does not fabricate mood`() {
        val records = listOf(record("下午三点取了快递"))

        assertNull(TodayFormationPolicy.extractMood(records))
    }

    @Test
    fun `observation grows with the number of records`() {
        val few = TodayFormationPolicy.build(listOf(record("第一条")))
        val many = TodayFormationPolicy.build((1..8).map { record("片段$it") })

        assertTrue(few.observation.contains("开头"))
        assertTrue(many.observation.contains("具体"))
    }
}
