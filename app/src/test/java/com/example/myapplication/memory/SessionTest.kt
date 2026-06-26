package com.example.myapplication.memory

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private fun sampleToolCallJson(): String = """
{
  "sessionId": "android:test123:1000",
  "chatId": "test123",
  "messages": [
    {
      "role": "user",
      "content": "帮我查天气",
      "tool_call_id": "",
      "tool_calls": [],
      "name": ""
    },
    {
      "role": "assistant",
      "content": "",
      "tool_call_id": "",
      "tool_calls": [
        {
          "id": "call_001",
          "type": "function",
          "function": {
            "name": "get_weather",
            "arguments": "{\"city\":\"Beijing\"}"
          }
        }
      ],
      "name": ""
    },
    {
      "role": "tool",
      "content": "{\"temp\":25,\"desc\":\"晴天\"}",
      "tool_call_id": "call_001",
      "tool_calls": [],
      "name": "get_weather"
    }
  ],
  "createdAt": 1000,
  "lastActive": 2000,
  "metadata": { "title": "测试对话" }
}
"""

    @Test
    fun `fromJsonFile recovers session with tool calls`() {
        val file = tmpDir.newFile("test_session.json")
        file.writeText(sampleToolCallJson())

        val session = Session.fromJsonFile(file)
        assertNotNull("Session should not be null", session)
        assertEquals("android:test123:1000", session!!.sessionId)
        assertEquals("test123", session.chatId)
        assertEquals(3, session.messages.size)

        // Verify tool_calls deserialization
        val assistantMsg = session.messages[1]
        assertEquals("assistant", assistantMsg.role)
        assertNotNull("toolCalls should not be null", assistantMsg.toolCalls)
        assertEquals(1, assistantMsg.toolCalls!!.size)
        assertEquals("call_001", assistantMsg.toolCalls!![0].id)
        assertEquals("get_weather", assistantMsg.toolCalls!![0].function.name)
        assertEquals("{\"city\":\"Beijing\"}", assistantMsg.toolCalls!![0].function.arguments)

        // Verify tool message
        val toolMsg = session.messages[2]
        assertEquals("tool", toolMsg.role)
        assertEquals("call_001", toolMsg.toolCallId)
        assertEquals("get_weather", toolMsg.name)

        // Verify metadata
        assertEquals("测试对话", session.title)
    }

    @Test
    fun `fromJsonFile recovers session without tool calls`() {
        val json = """
{
  "sessionId": "android:test456:2000",
  "chatId": "test456",
  "messages": [
    { "role": "user", "content": "你好", "tool_call_id": "", "tool_calls": [], "name": "" },
    { "role": "assistant", "content": "你好！有什么可以帮你的？", "tool_call_id": "", "tool_calls": [], "name": "" }
  ],
  "createdAt": 2000,
  "lastActive": 3000,
  "metadata": {}
}
"""
        val file = tmpDir.newFile("test_no_tools.json")
        file.writeText(json)

        val session = Session.fromJsonFile(file)
        assertNotNull(session)
        assertEquals(2, session!!.messages.size)
        assertNull(session.messages[0].toolCalls)
        assertNull(session.messages[1].toolCalls)
    }

    @Test
    fun `fromJsonFile returns null on corrupted file`() {
        val file = tmpDir.newFile("corrupted.json")
        file.writeText("this is not valid json {{{")

        val session = Session.fromJsonFile(file)
        assertNull("Corrupted file should return null", session)
    }

    @Test
    fun `fromJsonFile returns null on empty file`() {
        val file = tmpDir.newFile("empty.json")
        file.writeText("")

        val session = Session.fromJsonFile(file)
        assertNull("Empty file should return null", session)
    }

    @Test
    fun `fromJsonFile returns null for missing file`() {
        val file = File(tmpDir.root, "nonexistent.json")
        val session = Session.fromJsonFile(file)
        assertNull("Missing file should return null", session)
    }

    @Test
    fun `atomic write pattern protects against partial writes`() {
        val file = tmpDir.newFile("atomic_test.json")
        val tmpFile = File(file.absolutePath + ".tmp")
        val session = Session(
            sessionId = "android:atomic:4000",
            chatId = "atomic",
            messages = mutableListOf(
                com.example.myapplication.llm.LLMMessage(role = "user", content = "test atomic write")
            ),
            createdAt = 4000,
            lastActive = 5000
        )

        // Simulate the atomic write pattern used by SessionManager.save()
        tmpFile.writeText(session.toJson(), Charsets.UTF_8)
        val renamed = tmpFile.renameTo(file)
        assertTrue("renameTo should succeed on same filesystem", renamed)
        assertTrue("Final file should exist", file.exists())
        assertFalse("Tmp file should not exist after rename", tmpFile.exists())

        // Verify data is intact after atomic write
        val restored = Session.fromJsonFile(file)
        assertNotNull("Should read back atomically written session", restored)
        assertEquals(session.sessionId, restored!!.sessionId)
        assertEquals("test atomic write", restored.messages[0].content)
    }

    @Test
    fun `copyTo fallback when rename fails`() {
        val file = tmpDir.newFile("copy_fallback.json")
        val tmpFile = File(file.absolutePath + ".tmp")
        val content = """{"sessionId":"test","chatId":"test","messages":[],"createdAt":1,"lastActive":2,"metadata":{}}"""

        // Simulate: write tmp, use copyTo as fallback (without rename)
        tmpFile.writeText(content, Charsets.UTF_8)
        tmpFile.copyTo(file, overwrite = true)
        tmpFile.delete()

        assertTrue("Final file should exist after copyTo", file.exists())
        assertFalse("Tmp should be cleaned up", tmpFile.exists())
        val session = Session.fromJsonFile(file)
        assertNotNull("Should read back after copyTo", session)
    }

    @Test
    fun `fromJsonFile then toJson roundtrips correctly`() {
        val session = Session(
            sessionId = "android:roundtrip:3000",
            chatId = "roundtrip",
            messages = mutableListOf(
                com.example.myapplication.llm.LLMMessage(role = "user", content = "hello")
            ),
            createdAt = 3000,
            lastActive = 4000,
            metadata = mutableMapOf("key" to "value")
        )

        val file = tmpDir.newFile("roundtrip.json")
        file.writeText(session.toJson())

        val restored = Session.fromJsonFile(file)
        assertNotNull(restored)
        assertEquals(session.sessionId, restored!!.sessionId)
        assertEquals(session.chatId, restored.chatId)
        assertEquals(1, restored.messages.size)
        assertEquals("user", restored.messages[0].role)
        assertEquals("hello", restored.messages[0].content)
    }

    @Test
    fun `autoTitle strips Memory Search Results prefix`() {
        val session = Session(
            sessionId = "test", chatId = "test",
            messages = mutableListOf(
                com.example.myapplication.llm.LLMMessage(role = "user",
                    content = "[Memory Search Results]\nSource: test (score: 1.00)\nhello\n\n你好，帮我查天气")
            )
        )
        val title = session.autoTitle()
        assertFalse("Title should not contain Memory Search Results", title.contains("Memory Search Results"))
        assertTrue("Title should contain actual message", title.contains("你好"))
    }

    @Test
    fun `autoTitle handles empty messages gracefully`() {
        val session = Session(sessionId = "test", chatId = "test")
        assertEquals("新对话", session.autoTitle())
    }
}
