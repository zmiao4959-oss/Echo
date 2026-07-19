package com.example.myapplication.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeJournalClimateScaleTest {
    @Test fun `mood scale maps linearly onto temperature axis`() {
        assertEquals(-10f, LifeJournalClimateScale.moodToTemperature(-1f), .001f)
        assertEquals(15f, LifeJournalClimateScale.moodToTemperature(0f), .001f)
        assertEquals(40f, LifeJournalClimateScale.moodToTemperature(1f), .001f)
    }

    @Test fun `temperature normalization clamps chart boundaries`() {
        assertEquals(0f, LifeJournalClimateScale.temperatureToUnit(-30f), .001f)
        assertEquals(.5f, LifeJournalClimateScale.temperatureToUnit(15f), .001f)
        assertEquals(1f, LifeJournalClimateScale.temperatureToUnit(55f), .001f)
    }

    @Test fun `color and temperature parsers are conservative`() {
        assertTrue((LifeJournalClimateScale.colorToMoodScore("#E68A56") ?: Float.NaN) in -1f..1f)
        assertNull(LifeJournalClimateScale.colorToMoodScore("not-a-color"))
        assertEquals(23.5f, LifeJournalClimateScale.extractTemperature("多云 23.5℃") ?: Float.NaN, .001f)
        assertNull(LifeJournalClimateScale.extractTemperature("只是有点冷"))
    }
}
