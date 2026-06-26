package com.example.myapplication.data.store

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

data class TestItem(val id: String, val name: String)

class JsonAtomicWriterTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun `writeItems then readItems roundtrips`() {
        val file = tmpDir.newFile("test.json")
        val items = listOf(TestItem("1", "hello"), TestItem("2", "world"))

        JsonAtomicWriter.writeItems(file, items)
        val read = JsonAtomicWriter.readItems<TestItem>(file)

        assertEquals(2, read.size)
        assertEquals("1", read[0].id)
        assertEquals("hello", read[0].name)
        assertEquals("2", read[1].id)
        assertEquals("world", read[1].name)
    }

    @Test
    fun `readItems returns empty for missing file`() {
        val file = File(tmpDir.root, "nonexistent.json")
        val read = JsonAtomicWriter.readItems<TestItem>(file)
        assertTrue("Missing file should return empty", read.isEmpty())
    }

    @Test
    fun `writeItems overwrites existing file`() {
        val file = tmpDir.newFile("overwrite.json")
        val initial = listOf(TestItem("a", "old"))
        JsonAtomicWriter.writeItems(file, initial)
        assertEquals(1, JsonAtomicWriter.readItems<TestItem>(file).size)

        val updated = listOf(TestItem("b", "new"), TestItem("c", "newer"))
        JsonAtomicWriter.writeItems(file, updated)
        val read = JsonAtomicWriter.readItems<TestItem>(file)
        assertEquals(2, read.size)
        assertEquals("b", read[0].id)
    }

    @Test
    fun `writeItems cleans up tmp file`() {
        val file = tmpDir.newFile("cleanup.json")
        JsonAtomicWriter.writeItems(file, listOf(TestItem("x", "y")))

        // No tmp file should remain
        val allFiles = tmpDir.root.listFiles() ?: emptyArray()
        val tmpFiles = allFiles.filter { it.name.endsWith(".tmp") }
        assertTrue("No tmp file should remain", tmpFiles.isEmpty())

        // Final file should exist with correct content
        assertTrue(file.exists())
        val read = JsonAtomicWriter.readItems<TestItem>(file)
        assertEquals(1, read.size)
    }

    @Test
    fun `writeItems with empty list`() {
        val file = tmpDir.newFile("empty_list.json")
        JsonAtomicWriter.writeItems(file, emptyList<TestItem>())
        val read = JsonAtomicWriter.readItems<TestItem>(file)
        assertTrue(read.isEmpty())
    }
}
