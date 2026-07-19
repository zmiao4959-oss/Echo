package com.example.myapplication.data.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeatherSnapshot(
    val date: String,
    val capturedAt: Long,
    val description: String,
    val temperatureC: Float,
    val city: String = "",
    /** Coordinates are nullable for archives created before per-day location tracking. */
    val latitude: Double? = null,
    val longitude: Double? = null
)

/** Keeps one local weather observation per day so future journals can use real history. */
object WeatherHistoryStore {
    private const val PREFS = "clawspeaker_config"
    private const val KEY = "weather_daily_history_v1"
    private const val MAX_DAYS = 5000
    private val gson = Gson()
    private val listType = object : TypeToken<List<WeatherSnapshot>>() {}.type

    @Synchronized
    fun record(
        context: Context,
        description: String,
        temperatureC: Float,
        city: String = "",
        capturedAt: Long = System.currentTimeMillis(),
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (description.isBlank() || temperatureC !in -60f..60f) return
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(capturedAt))
        merge(
            context,
            listOf(WeatherSnapshot(date, capturedAt, description.trim(), temperatureC, city.trim(), latitude, longitude)),
            overwriteExisting = true
        )
    }

    fun read(context: Context, startDate: String, endDate: String): List<WeatherSnapshot> {
        return all(context).filter { it.date in startDate..endDate }
    }

    fun all(context: Context): List<WeatherSnapshot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return readRaw(raw).sortedBy { it.date }
    }

    fun knownDates(context: Context): Set<String> = all(context).mapTo(linkedSetOf()) { it.date }

    /** Returns the number of newly inserted dates. */
    @Synchronized
    fun merge(
        context: Context,
        snapshots: List<WeatherSnapshot>,
        overwriteExisting: Boolean = false
    ): Int {
        val valid = snapshots.filter {
            DATE_PATTERN.matches(it.date) && it.description.isNotBlank() && it.temperatureC in -60f..60f
        }
        if (valid.isEmpty()) return 0
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = readRaw(prefs.getString(KEY, null)).associateByTo(linkedMapOf()) { it.date }
        var inserted = 0
        valid.forEach { snapshot ->
            if (snapshot.date !in existing) {
                existing[snapshot.date] = snapshot.copy(description = snapshot.description.trim(), city = snapshot.city.trim())
                inserted++
            } else if (overwriteExisting) {
                existing[snapshot.date] = snapshot.copy(description = snapshot.description.trim(), city = snapshot.city.trim())
            }
        }
        val compact = existing.values.sortedByDescending { it.date }.take(MAX_DAYS).sortedBy { it.date }
        prefs.edit().putString(KEY, gson.toJson(compact)).apply()
        return inserted
    }

    private fun readRaw(raw: String?): List<WeatherSnapshot> = runCatching {
        if (raw.isNullOrBlank()) emptyList() else gson.fromJson<List<WeatherSnapshot>>(raw, listType).orEmpty()
    }.getOrDefault(emptyList())

    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
}
