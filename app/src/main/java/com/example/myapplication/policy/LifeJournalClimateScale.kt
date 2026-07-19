package com.example.myapplication.policy

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Shared scale for the editorial climate chart. */
object LifeJournalClimateScale {
    const val MIN_TEMPERATURE_C = -10f
    const val MAX_TEMPERATURE_C = 40f
    private const val RANGE = MAX_TEMPERATURE_C - MIN_TEMPERATURE_C

    fun moodToTemperature(score: Float): Float =
        MIN_TEMPERATURE_C + ((score.coerceIn(-1f, 1f) + 1f) / 2f) * RANGE

    fun temperatureToUnit(value: Float): Float =
        ((value.coerceIn(MIN_TEMPERATURE_C, MAX_TEMPERATURE_C) - MIN_TEMPERATURE_C) / RANGE)

    fun colorToMoodScore(hex: String?): Float? {
        val raw = hex?.removePrefix("#")?.takeIf { it.length == 6 && it.all { character -> character.isHexDigitValue() } } ?: return null
        val rgb = raw.toInt(16)
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val lightness = (high + low) / 2f
        val saturation = if (high == low) 0f else {
            val delta = high - low
            delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        }
        val hue = when (high) {
            low -> 0f
            r -> 60f * (((g - b) / (high - low)) % 6f)
            g -> 60f * (((b - r) / (high - low)) + 2f)
            else -> 60f * (((r - g) / (high - low)) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val warmth = ((cos((hue - 45f) * PI / 180.0) + 1.0) / 2.0).toFloat()
        val emotionalLight = .68f * lightness + .20f * warmth + .12f * saturation
        return (emotionalLight * 2f - 1f).coerceIn(-1f, 1f)
    }

    fun extractTemperature(text: String): Float? =
        Regex("(-?\\d{1,2}(?:\\.\\d+)?)\\s*(?:℃|°\\s*C|摄氏度|度)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?.takeIf { it in -60f..60f }

    private fun Char.isHexDigitValue(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
