package com.example.myapplication.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeManagerTest {

    @Test
    fun `collection contains four static and four dynamic themes`() {
        assertEquals(8, ThemeManager.themes.size)
        assertEquals(4, ThemeManager.themes.count { it.kind == ThemeManager.Kind.STATIC })
        assertEquals(4, ThemeManager.themes.count { it.kind == ThemeManager.Kind.DYNAMIC })
    }

    @Test
    fun `static themes own artwork and dynamic themes own distinct motion`() {
        ThemeManager.themes.filter { it.kind == ThemeManager.Kind.STATIC }.forEach {
            assertNotNull(it.artworkRes)
            assertNotNull(it.previewArtworkRes)
            assertNotNull(it.surfaceTextureRes)
        }
        val motions = ThemeManager.themes
            .filter { it.kind == ThemeManager.Kind.DYNAMIC }
            .map { it.motion }
        assertEquals(4, motions.distinct().size)
        assertTrue(motions.none { it == ThemeManager.Motion.NONE })
        assertEquals(8, ThemeManager.themes.map { it.typography }.distinct().size)
    }

    @Test
    fun `legacy keys migrate to authored collection`() {
        assertEquals(ThemeManager.THEME_PAPER_ATELIER, ThemeManager.specFor(ThemeManager.THEME_WARM_TEA).key)
        assertEquals(ThemeManager.THEME_ABYSSAL_ARCHIVE, ThemeManager.specFor(ThemeManager.THEME_OCEAN).key)
        assertNotEquals(ThemeManager.THEME_DARK, ThemeManager.specFor(ThemeManager.THEME_DARK).key)
    }
}
