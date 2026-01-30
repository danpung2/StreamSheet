package com.streamsheet.spring.async

/**
 * 작업 상태 관리자 인터페이스
 * Job status manager interface
 */
interface JobManager {
    fun createJob(): String
    fun getJob(jobId: String): ExportJob?
    fun updateStatus(jobId: String, status: JobStatus, resultUri: java.net.URI? = null, errorMessage: String? = null)

    fun updateProgress(jobId: String, rowsWritten: Long, batchesFlushed: Long) {
        // NOTE: 기본 구현은 no-op 입니다.
        // Default implementation is a no-op.
    }

    fun requestCancel(jobId: String) {
        // NOTE: 기본 구현은 no-op 입니다.
        // Default implementation is a no-op.
    }

    fun isCancelRequested(jobId: String): Boolean {
        return false
    }
}
