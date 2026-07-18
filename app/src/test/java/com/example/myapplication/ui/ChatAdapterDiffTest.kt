package com.example.myapplication.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAdapterDiffTest {

    private val callback = ChatAdapter.DiffCallback()

    @Test
    fun `same stable id represents the same item`() {
        val old = ChatMessage(id = "message-1", role = "assistant", content = "旧内容")
        val new = old.copy(content = "新内容")

        assertTrue(callback.areItemsTheSame(old, new))
    }

    @Test
    fun `changed streaming content requires rebind`() {
        val old = ChatMessage(id = "message-1", role = "assistant", content = "正在")
        val new = old.copy(content = "正在生成", isStreaming = true)

        assertFalse(callback.areContentsTheSame(old, new))
    }

    @Test
    fun `changed tool details require rebind`() {
        val old = ChatMessage(
            id = "message-1",
            role = "assistant",
            content = "",
            toolCalls = listOf("search")
        )
        val new = old.copy(toolResults = listOf("search" to "完成"))

        assertFalse(callback.areContentsTheSame(old, new))
    }

    @Test
    fun `identical message content does not rebind`() {
        val old = ChatMessage(
            id = "message-1",
            role = "assistant",
            content = "完成",
            toolCalls = listOf("search"),
            toolResults = listOf("search" to "结果")
        )

        assertTrue(callback.areContentsTheSame(old, old.copy()))
    }
}
