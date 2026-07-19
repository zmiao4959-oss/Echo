package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeJournalPolicyTest {
    private val start = 1_720_000_000_000L
    private val end = start + 31L * 86_400_000L

    @Test fun `empty input keeps honest blanks`() {
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(emptyList(), emptyList(), emptyList(), emptyList(), start, end))
        assertTrue(out.overview.contains("没有足够"))
        assertTrue(out.weatherNotes.isEmpty())
        assertEquals(listOf("fragments"), out.chapters.map { it.id })
    }

    @Test fun `people places and weather only come from sources`() {
        val records = listOf(
            LifeRecord("r1", start, "2024-07-04", "和小林在公园散步，后来下雨了，气温 24℃", "text", "平静", listOf("人物:小林", "地点:公园"), 4),
            LifeRecord("r2", start + 1000, "2024-07-05", "整理旧照片", "text", null, emptyList(), 2)
        )
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(records, emptyList(), emptyList(), emptyList(), start, end))
        val coordinates = out.chapters.first { it.id == "coordinates" }
        assertTrue(coordinates.fragments.any { it == "人物｜小林 · 07.04 · 明确标签" })
        assertFalse(coordinates.fragments.any { it.contains("小林在公") })
        assertFalse(coordinates.fragments.any { it.contains("朋友") })
        assertTrue(coordinates.fragments.any { it == "地点｜公园 · 07.04 · 明确标签" })
        assertTrue(coordinates.body.contains("人物是小林"))
        assertTrue(coordinates.body.contains("地点是公园"))
        val weatherPoint = out.moodPoints.first { it.date == "2024-07-04" }
        assertEquals(24f, weatherPoint.temperatureC ?: Float.NaN, .01f)
        assertEquals("下雨", weatherPoint.weatherLabel)
    }

    @Test fun `diary mood color is kept and participates in standardized mood`() {
        val diary = DailyDiary(
            "d", "2024-07-06", "搬书", "整理了书架", "把旧书重新分类", "平静",
            listOf("整理"), emptyList(), start, start, color = "#E68A56"
        )
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(emptyList(), listOf(diary), emptyList(), emptyList(), start, end))
        val point = out.moodPoints.single()
        assertEquals("平静", point.label)
        assertEquals("#E68A56", point.moodColor)
        assertTrue(point.score in -1f..1f)
        assertNotNull(LifeJournalClimateScale.colorToMoodScore(point.moodColor))
    }

    @Test fun `overview uses recorded facts and plan state`() {
        val diary = DailyDiary("d", "2024-07-06", "搬书", "整理了书架", "把旧书重新分类", "平静", listOf("整理"), emptyList(), start, start)
        val done = EchoPlan("p", "task_reminder", "交稿", "交出初稿", start, enabled = true, createdAt = start, updatedAt = start, lastTriggeredAt = start + 1)
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(emptyList(), listOf(diary), listOf(done), emptyList(), start, end))
        assertTrue(out.overview.contains("整理了书架"))
        assertTrue(out.overview.contains("1 项本期有提醒记录"))
        assertFalse(out.overview.contains("已完成"))
        assertTrue(out.chapters.first { it.id == "plans" }.body.contains("不等于任务已经完成"))
        assertEquals(1, out.sourceCounts["日记"])
    }

    @Test fun `repeated thoughts require two distinct dates`() {
        val sameDay = listOf(
            LifeRecord("1", start, "2024-07-04", "今天工作压力很大", "text", null, emptyList(), 3),
            LifeRecord("2", start + 1, "2024-07-04", "继续处理工作", "text", null, emptyList(), 3)
        )
        val oneDayOut = LifeJournalPolicy.build(LifeJournalPolicy.Input(sameDay, emptyList(), emptyList(), emptyList(), start, end))
        assertFalse(oneDayOut.chapters.any { it.id == "thoughts" })

        val twoDayOut = LifeJournalPolicy.build(
            LifeJournalPolicy.Input(
                sameDay + LifeRecord("3", start + 86_400_000, "2024-07-05", "工作告一段落", "text", null, emptyList(), 3),
                emptyList(), emptyList(), emptyList(), start, end
            )
        )
        assertTrue(twoDayOut.chapters.first { it.id == "thoughts" }.fragments.contains("工作"))
    }

    @Test fun `conversation and plan text cannot create factual people or places`() {
        val plan = EchoPlan("p", "task_reminder", "和阿哲见面", "去杭州", start, enabled = true, createdAt = start, updatedAt = start)
        val conversation = LifeJournalPolicy.ConversationExcerpt("随便聊聊", "我也许会和小周去北京")
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(emptyList(), emptyList(), listOf(plan), listOf(conversation), start, end))
        assertFalse(out.chapters.any { it.id == "coordinates" })
    }
}
