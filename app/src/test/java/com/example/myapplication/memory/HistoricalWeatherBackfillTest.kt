package com.example.myapplication.memory

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricalWeatherBackfillTest {
    @Test fun `archive parser keeps requested dates and mean temperature`() {
        val json = JsonParser.parseString(
            """
            {
              "daily": {
                "time": ["2026-07-01", "2026-07-02", "2026-07-03"],
                "weather_code": [0, 61, 3],
                "temperature_2m_mean": [26.4, 23.8, 25.0],
                "temperature_2m_max": [31, 27, 29],
                "temperature_2m_min": [22, 20, 21]
              }
            }
            """.trimIndent()
        ).asJsonObject

        val result = HistoricalWeatherBackfill.parseArchive(
            json,
            setOf("2026-07-01", "2026-07-03"),
            "上海"
        )

        assertEquals(listOf("2026-07-01", "2026-07-03"), result.map { it.date })
        assertEquals(26.4f, result.first().temperatureC, .001f)
        assertEquals("晴", result.first().description)
        assertEquals("阴", result.last().description)
        assertEquals("上海", result.first().city)
    }

    @Test fun `archive parser falls back to max min midpoint`() {
        val json = JsonParser.parseString(
            """
            {
              "daily": {
                "time": ["2026-01-08"],
                "weather_code": [71],
                "temperature_2m_mean": [null],
                "temperature_2m_max": [4],
                "temperature_2m_min": [-2]
              }
            }
            """.trimIndent()
        ).asJsonObject

        val result = HistoricalWeatherBackfill.parseArchive(json, setOf("2026-01-08"), "北京")
        assertEquals(1f, result.single().temperatureC, .001f)
        assertEquals("雪", result.single().description)
    }

    @Test fun `wmo groups use readable Chinese labels`() {
        assertEquals("毛毛雨", HistoricalWeatherBackfill.describeWmo(53))
        assertEquals("阵雨", HistoricalWeatherBackfill.describeWmo(81))
        assertEquals("雷雨伴冰雹", HistoricalWeatherBackfill.describeWmo(99))
    }

    @Test fun `administrative suffix is removed before geocoding`() {
        assertEquals("上海", HistoricalWeatherBackfill.normalizeCityQuery("上海市"))
        assertEquals("内蒙古", HistoricalWeatherBackfill.normalizeCityQuery("内蒙古自治区"))
        assertEquals("Tokyo", HistoricalWeatherBackfill.normalizeCityQuery("Tokyo"))
    }

    @Test fun `large exact city wins over same name American village`() {
        val json = JsonParser.parseString(
            """
            {
              "results": [
                {"name":"上海市","admin1":"伊利诺伊州","country":"美国","country_code":"US","latitude":41.05087,"longitude":-90.4968,"timezone":"America/Chicago"},
                {"name":"上海","admin1":"上海市","country":"中国","country_code":"CN","latitude":31.22222,"longitude":121.45806,"population":24874500,"timezone":"Asia/Shanghai"}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val selected = HistoricalWeatherBackfill.selectConfiguredLocation(json, "上海市")
        assertEquals(31.22222, selected.latitude, .00001)
        assertEquals("上海 · 上海市 · 中国", selected.label)
    }

    @Test fun `localized metropolis wins when English query matches a tiny village literally`() {
        val json = JsonParser.parseString(
            """
            {
              "results": [
                {"name":"上海","admin1":"上海市","country":"中国","country_code":"CN","latitude":31.22222,"longitude":121.45806,"population":24874500,"feature_code":"PPLA","timezone":"Asia/Shanghai"},
                {"name":"Shanghai","admin1":"亚拉巴马州","country":"美国","country_code":"US","latitude":34.85009,"longitude":-87.08501,"feature_code":"PPL","timezone":"America/Chicago"}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val selected = HistoricalWeatherBackfill.selectConfiguredLocation(json, "Shanghai")
        assertEquals(31.22222, selected.latitude, .00001)
        assertEquals("上海 · 上海市 · 中国", selected.label)
    }

    @Test fun `archive parser stores the location used for that day`() {
        val json = JsonParser.parseString(
            """{"daily":{"time":["2026-07-01"],"weather_code":[0],"temperature_2m_mean":[26]}}"""
        ).asJsonObject
        val result = HistoricalWeatherBackfill.parseArchive(json, setOf("2026-07-01"), "杭州 · 浙江 · 中国", 30.27, 120.15)
        assertEquals(30.27, result.single().latitude ?: Double.NaN, .00001)
        assertEquals(120.15, result.single().longitude ?: Double.NaN, .00001)
    }
}
