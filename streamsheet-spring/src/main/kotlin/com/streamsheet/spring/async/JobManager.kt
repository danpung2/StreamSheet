package com.streamsheet.spring.async

/**
 * 작업 상태 관리자 인터페이스
 * Job status manager interface
 */
interface JobManager {
    fun createJob(): String
    fun getJob(jobId: String): ExportJob?
    fun updateStatus(jobId: String, status: JobStatus, resultUri: java.net.URI? = null, errorMessage: String? = null)
}
