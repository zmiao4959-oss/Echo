package com.example.myapplication.memory

import android.content.Context
import com.example.myapplication.data.model.LifeJournalMoodPoint
import com.example.myapplication.data.repository.DiaryRepository
import com.example.myapplication.data.repository.LifeJournalRepository
import com.example.myapplication.data.repository.LifeRecordRepository
import com.example.myapplication.data.store.WeatherHistoryStore
import com.example.myapplication.data.store.WeatherSnapshot
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object HistoricalWeatherBackfill {
    data class Result(
        val requestedDays: Int,
        val writtenDays: Int,
        val unresolvedDays: Int,
        val syncedIssues: Int,
        val locationLabel: String,
        val totalStoredDays: Int,
        val corrected: Boolean = false
    )

    internal data class Location(val latitude: Double, val longitude: Double, val label: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun backfill(context: Context, city: String): Result =
        run(context, city, startDate = null, endDate = null, overwriteExisting = false)

    suspend fun correct(context: Context, city: String, startDate: String, endDate: String): Result {
        require(city.isNotBlank()) { "纠正历史天气前，请填写当时所在城市" }
        require(DATE_PATTERN.matches(startDate) && DATE_PATTERN.matches(endDate) && startDate <= endDate) { "日期范围不正确" }
        return run(context, city, startDate, endDate, overwriteExisting = true)
    }

    suspend fun recordedDateRange(): Pair<String, String>? = withContext(Dispatchers.IO) {
        recordedDates().let { dates -> dates.firstOrNull()?.let { it to dates.last() } }
    }

    private suspend fun run(
        context: Context,
        city: String,
        startDate: String?,
        endDate: String?,
        overwriteExisting: Boolean
    ): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val recordedDates = recordedDates().filter { date ->
            (startDate == null || date >= startDate) && (endDate == null || date <= endDate)
        }
        val knownDates = WeatherHistoryStore.knownDates(appContext)
        val targetDates = if (overwriteExisting) recordedDates else recordedDates.filterNot(knownDates::contains)
        if (targetDates.isEmpty()) {
            val synced = syncSavedIssues(appContext, WeatherHistoryStore.all(appContext))
            return@withContext Result(0, 0, 0, synced, city.ifBlank { "自动定位" }, knownDates.size, overwriteExisting)
        }

        val location = resolveLocation(appContext, city)
        saveResolvedLocation(appContext, location)
        val fetched = fetchArchive(location, targetDates)
        WeatherHistoryStore.merge(appContext, fetched, overwriteExisting = overwriteExisting)
        val allWeather = WeatherHistoryStore.all(appContext)
        val synced = syncSavedIssues(appContext, allWeather)
        Result(
            requestedDays = targetDates.size,
            writtenDays = fetched.map { it.date }.distinct().size,
            unresolvedDays = targetDates.size - fetched.map { it.date }.distinct().size,
            syncedIssues = synced,
            locationLabel = location.label,
            totalStoredDays = allWeather.size,
            corrected = overwriteExisting
        )
    }

    private suspend fun recordedDates(): List<String> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
        return (
            DiaryRepository().getAll().map { it.date } +
                LifeRecordRepository().getAll().map { it.date }
            ).filter { DATE_PATTERN.matches(it) && it < today }.distinct().sorted()
    }

    private fun resolveLocation(context: Context, city: String): Location {
        return if (city.isNotBlank()) resolveConfiguredCity(city) else resolveAutomaticLocation(context)
    }

    private fun resolveConfiguredCity(city: String): Location {
        val query = normalizeCityQuery(city)
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=zh&format=json")
        return selectConfiguredLocation(json, city)
    }

    internal fun normalizeCityQuery(city: String): String {
        val trimmed = city.trim()
        return if (trimmed.any { it.code in 0x4E00..0x9FFF }) {
            trimmed.removeSuffix("特别行政区").removeSuffix("自治区").removeSuffix("自治州")
                .removeSuffix("地区").removeSuffix("省").removeSuffix("市").ifBlank { trimmed }
        } else trimmed
    }

    internal fun selectConfiguredLocation(root: JsonObject, rawQuery: String): Location {
        val wanted = normalizeCityQuery(rawQuery).lowercase(Locale.ROOT)
        val administrativeQuery = rawQuery.trim().endsWith("市") || rawQuery.trim().endsWith("省")
        val candidates = root.getAsJsonArray("results")?.mapNotNull { it.takeUnless { value -> value.isJsonNull }?.asJsonObject }.orEmpty()
        val selected = candidates.mapIndexed { index, candidate -> candidate to run {
            val name = normalizeCityQuery(candidate.string("name")).lowercase(Locale.ROOT)
            val admin = normalizeCityQuery(candidate.string("admin1")).lowercase(Locale.ROOT)
            var score = 0L
            if (name == wanted) score += 1_000
            else if (name.contains(wanted) || wanted.contains(name)) score += 250
            if (admin == wanted) score += 350
            val population = candidate.long("population") ?: 0L
            score += when {
                population >= 5_000_000 -> 1_600
                population >= 1_000_000 -> 1_200
                population >= 100_000 -> 700
                population >= 10_000 -> 250
                else -> 0
            }
            score += when (candidate.string("feature_code")) {
                "PPLC" -> 250
                "PPLA", "PPLA2" -> 150
                else -> 0
            }
            score += (200 - index * 20).coerceAtLeast(0)
            if (administrativeQuery && candidate.string("country_code").equals("CN", ignoreCase = true)) score += 180
            if (candidate.string("timezone") == "Asia/Shanghai") score += 30
            score
        } }.maxByOrNull { it.second }?.first
            ?: error("没有找到城市“${rawQuery.trim()}”，请检查天气城市设置")
        val name = selected.string("name").ifBlank { rawQuery.trim() }
        val area = selected.string("admin1")
        val country = selected.string("country")
        val label = listOf(name, area, country).filter(String::isNotBlank).distinct().joinToString(" · ")
        return Location(selected.double("latitude"), selected.double("longitude"), label)
    }

    private fun resolveAutomaticLocation(context: Context): Location {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedLatitude = prefs.getString(KEY_LOCATION_LAT, null)?.toDoubleOrNull()
        val cachedLongitude = prefs.getString(KEY_LOCATION_LON, null)?.toDoubleOrNull()
        if (cachedLatitude != null && cachedLongitude != null) {
            return Location(
                cachedLatitude,
                cachedLongitude,
                prefs.getString(KEY_LOCATION_LABEL, null).orEmpty().ifBlank { "最近天气位置" }
            )
        }

        val json = try {
            getJson("https://wttr.in/?format=j1")
        } catch (_: Exception) {
            error("自动定位暂时不可用，请先在天气设置中填写城市后重试")
        }
        val nearest = json.getAsJsonArray("nearest_area")?.firstOrNull()?.asJsonObject
            ?: error("自动定位失败，请先在天气设置中填写城市")
        val latitude = nearest.arrayValue("latitude").toDoubleOrNull()
            ?: error("自动定位没有返回纬度，请手动填写城市")
        val longitude = nearest.arrayValue("longitude").toDoubleOrNull()
            ?: error("自动定位没有返回经度，请手动填写城市")
        val area = nearest.arrayValue("areaName")
        val region = nearest.arrayValue("region")
        val country = nearest.arrayValue("country")
        return Location(latitude, longitude, listOf(area, region, country).filter(String::isNotBlank).distinct().joinToString(" · ").ifBlank { "自动定位" })
    }

    private fun saveResolvedLocation(context: Context, location: Location) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LOCATION_LAT, location.latitude.toString())
            .putString(KEY_LOCATION_LON, location.longitude.toString())
            .putString(KEY_LOCATION_LABEL, location.label)
            .apply()
    }

    private fun fetchArchive(location: Location, targetDates: List<String>): List<WeatherSnapshot> {
        val startDate = targetDates.first()
        val endDate = targetDates.last()
        val url = "https://archive-api.open-meteo.com/v1/archive" +
            "?latitude=${location.latitude}&longitude=${location.longitude}" +
            "&start_date=$startDate&end_date=$endDate" +
            "&daily=weather_code,temperature_2m_mean,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto"
        return parseArchive(getJson(url), targetDates.toSet(), location.label, location.latitude, location.longitude)
    }

    internal fun parseArchive(
        root: JsonObject,
        targetDates: Set<String>,
        city: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<WeatherSnapshot> {
        val daily = root.getAsJsonObject("daily") ?: return emptyList()
        val dates = daily.getAsJsonArray("time") ?: return emptyList()
        val means = daily.getAsJsonArray("temperature_2m_mean")
        val maximums = daily.getAsJsonArray("temperature_2m_max")
        val minimums = daily.getAsJsonArray("temperature_2m_min")
        val codes = daily.getAsJsonArray("weather_code") ?: daily.getAsJsonArray("weathercode")
        return dates.mapIndexedNotNull { index, dateElement ->
            val date = dateElement.asString
            if (date !in targetDates) return@mapIndexedNotNull null
            val mean = means.floatAt(index) ?: run {
                val high = maximums.floatAt(index)
                val low = minimums.floatAt(index)
                if (high == null || low == null) null else (high + low) / 2f
            } ?: return@mapIndexedNotNull null
            val code = codes.intAt(index) ?: -1
            WeatherSnapshot(date, System.currentTimeMillis(), describeWmo(code), mean, city, latitude, longitude)
        }
    }

    internal fun describeWmo(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "大部晴朗"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        in 51..55 -> "毛毛雨"
        56, 57, 66, 67 -> "冻雨"
        in 61..65 -> "雨"
        in 71..75 -> "雪"
        77 -> "米雪"
        in 80..82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷雨"
        96, 99 -> "雷雨伴冰雹"
        else -> "天气未分类"
    }

    private suspend fun syncSavedIssues(context: Context, snapshots: List<WeatherSnapshot>): Int {
        if (snapshots.isEmpty()) return 0
        val repository = LifeJournalRepository()
        var changedCount = 0
        repository.getAll().forEach { issue ->
            val relevant = snapshots.filter { it.date in issue.startDate..issue.endDate }
            if (relevant.isEmpty()) return@forEach
            val weatherByDate = relevant.associateBy { it.date }
            val pointByDate = issue.moodPoints.associateByTo(linkedMapOf()) { it.date }
            relevant.forEach { weather ->
                val existing = pointByDate[weather.date]
                pointByDate[weather.date] = if (existing == null) {
                    LifeJournalMoodPoint(weather.date, "", 0f, temperatureC = weather.temperatureC, weatherLabel = weather.description)
                } else {
                    existing.copy(temperatureC = weather.temperatureC, weatherLabel = weather.description)
                }
            }
            val points = pointByDate.values.sortedBy { it.date }
            val alreadySynced = issue.moodPoints == points && issue.sourceCounts["天气"] == weatherByDate.size
            if (!alreadySynced) {
                repository.save(
                    issue.copy(
                        moodPoints = points,
                        weatherNotes = relevant.takeLast(8).map { "${it.date.takeLast(5).replace('-', '.')} ${it.description} ${it.temperatureC.roundToInt()}℃" },
                        sourceCounts = issue.sourceCounts + ("天气" to weatherByDate.size),
                        updatedAt = System.currentTimeMillis(),
                        revision = issue.revision + 1
                    )
                )
                changedCount++
            }
        }
        return changedCount
    }

    private fun getJson(url: String): JsonObject {
        val response = client.newCall(Request.Builder().url(url).header("User-Agent", "Echo-Life-Journal/1.0").build()).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) error("天气服务请求失败（${it.code}）")
            val root = JsonParser.parseString(body).asJsonObject
            if (root.get("error")?.asBoolean == true) error(root.string("reason").ifBlank { "天气服务返回错误" })
            return root
        }
    }

    private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.double(name: String): Double = get(name)?.takeUnless { it.isJsonNull }?.asDouble ?: error("定位结果缺少 $name")
    private fun JsonObject.long(name: String): Long? = get(name)?.takeUnless { it.isJsonNull }?.asLong
    private fun JsonObject.arrayValue(name: String): String =
        getAsJsonArray(name)?.firstOrNull()?.asJsonObject?.get("value")?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun com.google.gson.JsonArray?.floatAt(index: Int): Float? {
        if (this == null || index !in 0 until size()) return null
        return get(index).takeUnless { it.isJsonNull }?.asFloat
    }

    private fun com.google.gson.JsonArray?.intAt(index: Int): Int? {
        if (this == null || index !in 0 until size()) return null
        return get(index).takeUnless { it.isJsonNull }?.asInt
    }

    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private const val PREFS_NAME = "clawspeaker_config"
    private const val KEY_LOCATION_LAT = "weather_location_lat"
    private const val KEY_LOCATION_LON = "weather_location_lon"
    private const val KEY_LOCATION_LABEL = "weather_location_label"
}
