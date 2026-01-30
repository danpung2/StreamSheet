package com.streamsheet.core.progress

enum class ExportPhase {
    STARTING,
    FLUSHED_BATCH,
    WRITING_WORKBOOK,
    COMPLETED,
    CANCELLED,
    FAILED,
}
