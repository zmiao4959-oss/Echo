package com.example.myapplication.domain

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 时间解析工具 — 从 LLM 工具调用参数中提取时间戳。
 * 提取为 internal 对象方便单元测试。
 */
object TimeParser {

    /** 解析 ISO 时间字符串如 "2026-06-27T21:00" → epoch ms */
    fun parseIsoTime(value: Any?): Long? {
        val str = value as? String ?: return null
        return try {
            val s = str.trim().replace(" ", "T").replace("T", " ")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.parse(s)?.time
        } catch (_: Exception) {
            null
        }
    }

    /** 解析 Unix 毫秒时间戳（Number 或数字字符串） */
    fun parseTimestamp(value: Any?, fallback: Long? = null): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> fallback
        }
    }
}
