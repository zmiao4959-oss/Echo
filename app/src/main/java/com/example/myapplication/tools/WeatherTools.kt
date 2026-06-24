package com.example.myapplication.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 天气工具 — 基于 wttr.in（免费、无需 API Key）。
 */
object WeatherTools {

    private const val BASE_URL = "https://wttr.in"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun registerAll() {
        ToolRegistry.register(weatherTool)
    }

    private val weatherTool = ToolDefinition(
        name = "get_weather",
        description = "获取指定城市的当前天气和未来预报。不需要 API Key。",
        schema = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "get_weather",
                "description" to "获取指定城市的天气信息（当前 + 未来 2 天预报）。城市名支持中文（如 北京、上海、东京）和英文。省略 city 则基于 IP 自动定位。",
                "parameters" to mapOf(
                    "type" to "object",
                    "required" to emptyList<String>(),
                    "properties" to mapOf(
                        "city" to mapOf("type" to "string", "description" to "城市名，如 Beijing、上海、Tokyo。留空则自动定位。")
                    )
                )
            )
        ),
        requireApproval = false,
        riskLevel = "low",
        tags = listOf("weather", "web"),
        timeoutSec = 15
    ) { args ->
        val city = (args["city"] as? String)?.trim()?.ifEmpty { null }
        try {
            val encodedCity = if (city != null) URLEncoder.encode(city, "UTF-8") else ""
            val url = if (encodedCity.isNotEmpty()) {
                "$BASE_URL/$encodedCity?format=j1"
            } else {
                "$BASE_URL/?format=j1"
            }

            val request = Request.Builder().url(url).build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string() ?: return@ToolDefinition "Error: empty response"

            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val currentArr = json.getAsJsonArray("current_condition")
            val current = currentArr?.get(0)?.asJsonObject
            val weather = json.getAsJsonArray("weather")

            val sb = StringBuilder()

            // 当前天气
            if (current != null) {
                val loc = json.getAsJsonArray("nearest_area")?.get(0)?.asJsonObject
                    ?.getAsJsonArray("areaName")?.get(0)?.asJsonObject
                    ?.get("value")?.asString ?: (city ?: "当前位置")
                val tempC = current.get("temp_C")?.asString ?: "?"
                val desc = current.getAsJsonArray("weatherDesc")?.get(0)?.asJsonObject
                    ?.get("value")?.asString ?: "?"
                val humidity = current.get("humidity")?.asString ?: "?"
                val windSpeed = current.get("windspeedKmph")?.asString ?: "?"
                val windDir = current.get("winddir16Point")?.asString ?: ""
                val feel = current.get("FeelsLikeC")?.asString ?: "?"

                sb.appendLine("📍 $loc")
                sb.appendLine("🌡 当前: ${tempC}°C（体感 ${feel}°C） | $desc")
                sb.appendLine("💧 湿度: ${humidity}% | 🌬 风: $windDir ${windSpeed}km/h")
                sb.appendLine()
            }

            // 天气预报
            if (weather != null && weather.size() > 0) {
                sb.appendLine("📅 预报:")
                for (i in 0 until minOf(weather.size(), 3)) {
                    val day = weather[i].asJsonObject
                    val date = day.get("date")?.asString ?: ""
                    val maxC = day.get("maxtempC")?.asString ?: "?"
                    val minC = day.get("mintempC")?.asString ?: "?"
                    val hourly = day.getAsJsonArray("hourly")
                    val dayDesc = if (hourly != null && hourly.size() >= 4) {
                        hourly[2].asJsonObject.getAsJsonArray("weatherDesc")
                            ?.get(0)?.asJsonObject?.get("value")?.asString ?: "?"
                    } else "?"
                    sb.appendLine("  $date: $dayDesc | ${minC}°C ~ ${maxC}°C")
                }
            }

            val result = sb.toString().trim()
            if (result.isBlank()) "天气数据暂时不可用，请稍后重试。" else result
        } catch (e: Exception) {
            "获取天气失败: ${e.message}"
        }
    }
}
