package com.streamsheet.spring.metrics

import com.streamsheet.core.metrics.StreamSheetMetrics
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration

class MicrometerStreamSheetMetrics(
    registry: MeterRegistry,
) : StreamSheetMetrics {

    private val exportDuration = registry.timer("streamsheet.export.duration")
    private val exportedRows = registry.counter("streamsheet.export.rows.total")
    private val exportSuccess = registry.counter("streamsheet.export.success.total")
    private val exportFailure = registry.counter("streamsheet.export.failure.total")

    override fun recordExportDurationMs(durationMs: Long, success: Boolean) {
        exportDuration.record(Duration.ofMillis(durationMs))
        if (success) {
            exportSuccess.increment()
        } else {
            exportFailure.increment()
        }
    }

    override fun incrementExportedRows(rows: Long) {
        exportedRows.increment(rows.toDouble())
    }
}
