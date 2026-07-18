package com.example.myapplication.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TTSParserDisplayTest {

    @Test
    fun `display text removes complete mood directive`() {
        val script = "<mood>用温柔、理解的语气说</mood>你已经做得很好了。"

        assertEquals("你已经做得很好了。", TTSParser.toDisplayText(script))
    }

    @Test
    fun `display text accepts broken closing tag`() {
        val script = "<mood>声音放轻</ mood>先休息一下吧。"

        assertEquals("先休息一下吧。", TTSParser.toDisplayText(script))
    }

    @Test
    fun `display text keeps normal paragraphs`() {
        val script = "第一段。\n\n第二段。"

        assertEquals(script, TTSParser.toDisplayText(script))
    }

    @Test
    fun `unfinished streaming mood directive stays hidden`() {
        val script = "上一段已经完成。<mood>正在生成的语气"

        assertEquals("上一段已经完成。", TTSParser.toDisplayText(script))
    }
}
