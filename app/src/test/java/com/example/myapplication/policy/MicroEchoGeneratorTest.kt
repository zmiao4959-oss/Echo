package com.example.myapplication.policy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicroEchoGeneratorTest {

    @Test
    fun `remote echo keeps every generated line while removing wrappers`() = runBlocking {
        val generator = MicroEchoGenerator { _, _ ->
            "Echo： “你没有停在焦虑里，而是在一点点把事情向前推。”\n额外解释"
        }

        val result = generator.generate("今天终于把第一版做完了")

        assertEquals("你没有停在焦虑里，而是在一点点把事情向前推。\n额外解释", result)
    }

    @Test
    fun `remote failure falls back without throwing`() = runBlocking {
        val generator = MicroEchoGenerator { _, _ -> error("network unavailable") }

        val result = generator.generate("今天有点累，什么都不想做")

        assertTrue(result.contains("不容易"))
    }

    @Test
    fun `progress record gets progress fallback`() = runBlocking {
        val generator = MicroEchoGenerator()

        val result = generator.generate("终于完成了拖了很久的功能")

        assertTrue(result.contains("向前走"))
    }

    @Test
    fun `voice note without text still receives an echo`() = runBlocking {
        val generator = MicroEchoGenerator()

        val result = generator.generate("[语音]")

        assertFalse(result.isBlank())
        assertTrue(result.contains("收好"))
    }

    @Test
    fun `overlong remote response is preserved`() = runBlocking {
        val remote = "这".repeat(120)
        val generator = MicroEchoGenerator { _, _ -> remote }

        val result = generator.generate("普通的一天")

        assertEquals(remote, result)
    }

    @Test
    fun `prompt carries at most four recent records`() = runBlocking {
        var capturedPrompt = ""
        val generator = MicroEchoGenerator { _, userPrompt ->
            capturedPrompt = userPrompt
            "这一步对你来说很具体，也确实值得留下。"
        }

        generator.generate(
            content = "刚刚完成了微回声功能",
            recentContents = listOf("片段一", "片段二", "片段三", "片段四", "片段五")
        )

        assertTrue(capturedPrompt.contains("片段一"))
        assertTrue(capturedPrompt.contains("片段四"))
        assertFalse(capturedPrompt.contains("片段五"))
    }

    @Test
    fun `prompt carries local liked and rejected tone examples`() = runBlocking {
        var capturedPrompt = ""
        val generator = MicroEchoGenerator { _, userPrompt ->
            capturedPrompt = userPrompt
            "这一刻已经被好好看见了。"
        }

        generator.generate(
            content = "普通的一天",
            likedEchoes = listOf("这种语气我喜欢"),
            rejectedEchoes = listOf("这种语气不太像我")
        )

        assertTrue(capturedPrompt.contains("这种语气我喜欢"))
        assertTrue(capturedPrompt.contains("这种语气不太像我"))
    }

    @Test
    fun `rejected remote wording is not returned again`() = runBlocking {
        val rejected = "这一刻已经被好好留下，今天也因此更完整了一点。"
        val generator = MicroEchoGenerator { _, _ -> rejected }

        val result = generator.generate(
            content = "普通的一天",
            rejectedEchoes = listOf(rejected)
        )

        assertFalse(result == rejected)
    }

    @Test
    fun `full rejection list still advances to an unused local fallback`() = runBlocking {
        val rejected = (0 until 6).map { variant ->
            MicroEchoGenerator.localFallback("普通的一天", variant)
        }
        val generator = MicroEchoGenerator { _, _ -> null }

        val result = generator.generate(
            content = "普通的一天",
            rejectedEchoes = rejected
        )

        assertFalse(result in rejected)
    }

    @Test
    fun `empty voice note fallback also avoids rejected wording`() = runBlocking {
        val rejected = MicroEchoGenerator.localFallback("[语音]")
        val generator = MicroEchoGenerator()

        val result = generator.generate(
            content = "[语音]",
            rejectedEchoes = listOf(rejected)
        )

        assertFalse(result == rejected)
    }
}
