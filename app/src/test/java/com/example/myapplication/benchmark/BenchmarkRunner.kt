package com.example.myapplication.benchmark

/**
 * Simple benchmark runner for JVM-level performance tests.
 * Outputs timing to stdout/stderr; marks as WARNING if threshold exceeded.
 */
object BenchmarkRunner {

    data class BenchmarkResult(
        val name: String,
        val elapsedMillis: Long,
        val thresholdMillis: Long,
        val passed: Boolean
    )

    /** Magnitude expectations — soft thresholds, not hard limits. */
    const val THRESHOLD_SEARCH_1000 = 500L
    const val THRESHOLD_TIMELINE_SORT_1000 = 300L
    const val THRESHOLD_CARD_FILTER_500 = 200L
    const val THRESHOLD_AUDIT_TRUNCATE_1000 = 100L
    const val THRESHOLD_INDEX_REBUILD = 3000L

    /**
     * Measure execution time and compare against threshold.
     * Prints warning to stdout if exceeded.
     */
    fun measure(name: String, thresholdMs: Long, block: () -> Unit): BenchmarkResult {
        val start = System.currentTimeMillis()
        block()
        val elapsed = System.currentTimeMillis() - start
        val passed = elapsed <= thresholdMs
        val result = BenchmarkResult(name, elapsed, thresholdMs, passed)

        println("[BENCH] $name: ${elapsed}ms (threshold: ${thresholdMs}ms) ${if (passed) "OK" else "WARNING: slow!"}")

        if (!passed) {
            System.err.println("[BENCH-WARNING] $name exceeded threshold: ${elapsed}ms > ${thresholdMs}ms")
        }
        return result
    }

    /** Generate N synthetic items */
    fun <T> generate(count: Int, factory: (Int) -> T): List<T> =
        (0 until count).map(factory)
}
