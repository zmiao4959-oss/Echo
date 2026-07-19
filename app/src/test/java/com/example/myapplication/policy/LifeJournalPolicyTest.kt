package com.example.myapplication.policy

import com.example.myapplication.data.model.DailyDiary
import com.example.myapplication.data.model.EchoPlan
import com.example.myapplication.data.model.LifeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeJournalPolicyTest {
    private val start = 1_720_000_000_000L
    private val end = start + 31L * 86_400_000L

    @Test fun `empty input keeps honest blanks`() {
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(emptyList(), emptyList(), emptyList(), emptyList(), start, end))
        assertTrue(out.overview.contains("没有足够"))
        assertTrue(out.weatherNotes.isEmpty())
        assertTrue(out.chapters.first { it.id == "people" }.body.contains("没有足够"))
        assertTrue(out.chapters.first { it.id == "places" }.body.contains("没有可确认"))
    }

    @Test fun `people places and weather only come from sources`() {
        val records = listOf(
            LifeRecord("r1", start, "2024-07-04", "和小林在公园散步，后来下雨了", "text", "平静", listOf("人物:小林", "地点:公园"), 4),
            LifeRecord("r2", start + 1000, "2024-07-05", "整理旧照片", "text", null, emptyList(), 2)
        )
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(records, emptyList(), emptyList(), emptyList(), start, end))
        val people = out.chapters.first { it.id == "people" }
        val places = out.chapters.first { it.id == "places" }
        assertTrue(people.body.contains("小林"))
        assertFalse(people.body.contains("朋友"))
        assertTrue(places.body.contains("公园"))
        assertEquals(listOf("下雨", "雨"), out.weatherNotes)
    }

    @Test fun `overview uses recorded facts and plan state`() {
        val diary = DailyDiary("d", "2024-07-06", "搬书", "整理了书架", "把旧书重新分类", "平静", listOf("整理"), emptyList(), start, start)
        val done = EchoPlan("p", "task_reminder", "交稿", "交出初稿", start, enabled = true, createdAt = start, updatedAt = start, lastTriggeredAt = start + 1)
        val out = LifeJournalPolicy.build(LifeJournalPolicy.Input(emptyList(), listOf(diary), listOf(done), emptyList(), start, end))
        assertTrue(out.overview.contains("整理了书架"))
        assertTrue(out.overview.contains("1 项已完成"))
        assertEquals(1, out.sourceCounts["日记"])
    }
}
