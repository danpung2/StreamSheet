package com.streamsheet.core.progress

data class ExportProgress(
    val phase: ExportPhase,
    val rowsWritten: Long,
    val batchesFlushed: Long,
)
