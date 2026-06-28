package com.example.myapplication.diagnostics

import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量诊断记录器 — 记录最近 N 次服务失败，供设置面板展示。
 *
 * 只追记错误，成功不记录（减少内存）。
 */
object ServiceHealth {

    private const val MAX_PER_SERVICE = 10

    data class Failure(
        val service: String,
        val timestamp: Long,
        val message: String  // 已脱敏，不含 API key
    )

    private val failures = ConcurrentHashMap<String, MutableList<Failure>>()

    /** 记录一次服务失败（自动脱敏：去除 key= 后的内容） */
    fun record(service: String, rawMessage: String) {
        val sanitized = rawMessage.replace(Regex("(key=|api_key=|Bearer )\\S+", RegexOption.IGNORE_CASE), "$1***")
        val list = failures.getOrPut(service) { mutableListOf() }
        synchronized(list) {
            list.add(Failure(service, System.currentTimeMillis(), sanitized))
            if (list.size > MAX_PER_SERVICE) list.removeAt(0)
        }
    }

    /** 获取某服务的最近失败记录 */
    fun getRecentFailures(service: String): List<Failure> {
        return failures[service]?.toList() ?: emptyList()
    }

    /** 获取最近 N 小时内的失败次数 */
    fun failureCount(service: String, windowHours: Int = 24): Int {
        val cutoff = System.currentTimeMillis() - windowHours * 3_600_000L
        return getRecentFailures(service).count { it.timestamp > cutoff }
    }

    /** 所有有记录的服务名 */
    fun allServices(): Set<String> = failures.keys.toSet()

    /** 简要状态文本（给 UI 展示用） */
    fun summary(service: String): String {
        val count24h = failureCount(service, 24)
        val count1h = failureCount(service, 1)
        return if (count24h == 0) "$service: OK"
        else if (count1h > 0) "$service: 最近1小时 $count1h 次失败 (24h: $count24h)"
        else "$service: 24小时内 $count24h 次失败"
    }

    /** 获取最近一次失败的错误消息（已脱敏） */
    fun lastErrorMessage(service: String): String? {
        val list = failures[service] ?: return null
        return synchronized(list) { list.lastOrNull()?.message }
    }
}
