package com.example.myapplication.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class NaturalLanguagePlanParserTest {

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 19, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `parses tomorrow afternoon plan`() {
        val draft = NaturalLanguagePlanParser.parse("明天下午三点提醒我取快递", now)!!
        val cal = Calendar.getInstance().apply { timeInMillis = draft.triggerAt }

        assertEquals("取快递", draft.title)
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `parses daily plan with half hour`() {
        val draft = NaturalLanguagePlanParser.parse("每天晚上八点半提醒我散步", now)!!
        val cal = Calendar.getInstance().apply { timeInMillis = draft.triggerAt }

        assertEquals("每天", draft.repeatRule)
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertEquals("散步", draft.title)
    }

    @Test
    fun `past time without date rolls to tomorrow`() {
        val draft = NaturalLanguagePlanParser.parse("上午九点交报告", now)!!
        val cal = Calendar.getInstance().apply { timeInMillis = draft.triggerAt }

        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `blank input is rejected`() {
        assertEquals(null, NaturalLanguagePlanParser.parse("  ", now))
    }
}
