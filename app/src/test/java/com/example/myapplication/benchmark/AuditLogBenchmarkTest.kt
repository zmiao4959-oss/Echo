package com.example.myapplication.benchmark

import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogBenchmarkTest {

    data class SimulatedAuditEntry(
        val action: String, val memoryType: String, val memoryId: String,
        val timestamp: Long, val summary: String
    )

    @Test
    fun `truncate 1000 audit entries to 500`() {
        val entries = BenchmarkRunner.generate(1000) { i ->
            SimulatedAuditEntry("confirm", "profile", "p$i", i.toLong(), "entry $i")
        }

        BenchmarkRunner.measure("audit_truncate_1000", BenchmarkRunner.THRESHOLD_AUDIT_TRUNCATE_1000) {
            val trimmed = entries.takeLast(500)
            assertTrue(trimmed.size == 500)
            assertTrue(trimmed.first().memoryId == "p500")
        }
    }
}
