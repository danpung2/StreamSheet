package com.streamsheet.spring.async

/**
 * 작업 상태 관리자 인터페이스
 * Job status manager interface
 */
/**
 * 작업 상태 관리자 인터페이스
 * Job status manager interface
 *
 * NOTE: 비동기 내보내기 작업의 전체 수명 주기(Lifecycle)를 관리합니다.
 * 기본적인 흐름: [createJob] (PENDING) -> [updateStatus] (PROCESSING) -> [updateStatus] (COMPLETED/FAILED/CANCELLED)
 * Manages the full lifecycle of asynchronous export jobs.
 * Basic flow: [createJob] (PENDING) -> [updateStatus] (PROCESSING) -> [updateStatus] (COMPLETED/FAILED/CANCELLED)
 */
interface JobManager {
    /**
     * 새로운 작업을 생성하고 ID를 반환합니다. 초기 상태는 PENDING입니다.
     * Creates a new job and returns its ID. Initial state is PENDING.
     * @return Unique Job ID
     */
    fun createJob(): String

    /**
     * 작업 정보를 조회합니다.
     * Retrieves job information.
     */
    fun getJob(jobId: String): ExportJob?

    /**
     * 작업 상태를 업데이트합니다.
     * Updates the job status.
     */
    fun updateStatus(jobId: String, status: JobStatus, resultUri: java.net.URI? = null, errorMessage: String? = null)

    fun updateProgress(jobId: String, rowsWritten: Long, batchesFlushed: Long) {
        // NOTE: 기본 구현은 no-op 입니다.
        // Default implementation is a no-op.
    }

    fun requestCancel(jobId: String) {
        // NOTE: 기본 구현은 no-op 입니다.
        // Default implementation is a no-op.
    }

    /**
     * 취소 요청 여부를 확인합니다.
     * Checks if cancellation has been requested.
     */
    fun isCancelRequested(jobId: String): Boolean {
        return false
    }
}
