package com.streamsheet.spring.async

import java.net.URI
import java.time.LocalDateTime

/**
 * 비동기 엑셀 내보내기 작업 상태
 * Async Excel export job status
 */
enum class JobStatus {
    READY,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    FAILED
}

/**
 * 작업 메타데이터
 * Job metadata
 */
data class ExportJob(
    val jobId: String,
    val status: JobStatus,
    val resultUri: URI? = null,
    val errorMessage: String? = null,
    val rowsWritten: Long = 0,
    val batchesFlushed: Long = 0,
    val cancelRequested: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null
)
