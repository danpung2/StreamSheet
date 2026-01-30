package com.streamsheet.core.metrics

/**
 * StreamSheet 메트릭 수집 인터페이스
 * StreamSheet metrics recorder interface
 */
interface StreamSheetMetrics {
    fun recordExportDurationMs(durationMs: Long, success: Boolean)
    fun incrementExportedRows(rows: Long)

    companion object {
        val NOOP: StreamSheetMetrics = object : StreamSheetMetrics {
            override fun recordExportDurationMs(durationMs: Long, success: Boolean) {}
            override fun incrementExportedRows(rows: Long) {}
        }
    }
}
