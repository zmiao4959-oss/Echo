package com.example.myapplication.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeJournalEntityPolicyTest {
    @Test fun `person boundary stops before location grammar`() {
        val result = LifeJournalEntityPolicy.extract(
            listOf(LifeJournalEntityPolicy.Source("1", "2026-07-01", "和小林在人民公园散步"))
        )
        assertEquals("小林", result.people.single().name)
        assertFalse(result.people.any { it.name.contains("在公") })
        assertTrue(result.places.any { it.name == "人民公园" })
    }

    @Test fun `explicit city context and structured tags are retained`() {
        val result = LifeJournalEntityPolicy.extract(
            listOf(
                LifeJournalEntityPolicy.Source("1", "2026-07-01", "去上海出差", listOf("人物：阿禾")),
                LifeJournalEntityPolicy.Source("2", "2026-07-03", "和阿禾一起吃饭", listOf("地点:静安寺"))
            )
        )
        assertEquals("阿禾", result.people.first().name)
        assertTrue(result.people.first().tagged)
        assertTrue(result.places.any { it.name == "上海" })
        assertTrue(result.places.any { it.name == "静安寺" && it.tagged })
    }

    @Test fun `ambiguous pronouns are ignored and home aliases merge`() {
        val result = LifeJournalEntityPolicy.extract(
            listOf(
                LifeJournalEntityPolicy.Source("1", "2026-07-01", "和大家一起聊，在家里看书"),
                LifeJournalEntityPolicy.Source("2", "2026-07-02", "回到家中休息")
            )
        )
        assertTrue(result.people.isEmpty())
        assertEquals(listOf("家"), result.places.map { it.name })
        assertEquals(2, result.places.single().distinctDates)
    }

    @Test fun `sentence debris and abstract words never become entities`() {
        val result = LifeJournalEntityPolicy.extract(
            listOf(
                LifeJournalEntityPolicy.Source(
                    "record:1",
                    "2026-07-18",
                    "奶奶说起近况。日记和活动并推送给自己，等等；文件与件，跟今天有关，联系纠忙。感到心里很忙，在锋线上找到答案，最后回到原地。后来去了学校，也回到宿舍。"
                )
            )
        )

        assertEquals(listOf("奶奶"), result.people.map { it.name })
        assertEquals(setOf("学校", "宿舍"), result.places.map { it.name }.toSet())
        assertFalse(result.people.any { it.name in setOf("等等", "今天有", "活动并推送给自己", "件", "纠忙") })
        assertFalse(result.places.any { it.name in setOf("锋线", "心", "原地", "答案") })
    }

    @Test fun `single evidence retains date and source kind for editorial label`() {
        val result = LifeJournalEntityPolicy.extract(
            listOf(LifeJournalEntityPolicy.Source("diary:d1", "2026-07-08", "和小林在人民公园散步"))
        )

        assertEquals(listOf("2026-07-08"), result.people.single().dates)
        assertEquals(setOf("diary"), result.people.single().sourceKinds)
    }
}
