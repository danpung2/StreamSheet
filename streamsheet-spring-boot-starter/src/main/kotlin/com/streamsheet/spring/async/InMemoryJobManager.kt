package com.streamsheet.spring.async

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 인메모리 작업 관리자 (Caffeine 기반)
 * In-memory job manager (Caffeine based)
 *
 * NOTE: 분산 환경에서는 Redis 등으로 대체해야 합니다.
 * Should be replaced with Redis etc. in distributed environments.
 */
class InMemoryJobManager(
    private val retentionHours: Long = 24,
    private val onRemoval: ((ExportJob) -> Unit)? = null
) : JobManager {

    private val logger = LoggerFactory.getLogger(InMemoryJobManager::class.java)

    private val jobCache: Cache<String, ExportJob> = Caffeine.newBuilder()
        .expireAfterWrite(retentionHours, TimeUnit.HOURS) // 설정된 시간 후 만료
        .maximumSize(10000)
        .removalListener<String, ExportJob> { _, job, cause ->
            if (job != null && cause.wasEvicted()) {
                logger.debug("Job {} evicted (cause: {}). Triggering removal callback.", job.jobId, cause)
                onRemoval?.invoke(job)
            }
        }
        .build()

    override fun createJob(): String {
        val jobId = UUID.randomUUID().toString()
        val job = ExportJob(jobId, JobStatus.READY)
        jobCache.put(jobId, job)
        return jobId
    }

    override fun getJob(jobId: String): ExportJob? {
        return jobCache.getIfPresent(jobId)
    }

    override fun updateStatus(jobId: String, status: JobStatus, resultUri: URI?, errorMessage: String?) {
        val currentJob = jobCache.getIfPresent(jobId) ?: return
        
        val updatedJob = currentJob.copy(
            status = status,
            resultUri = resultUri ?: currentJob.resultUri,
            errorMessage = errorMessage ?: currentJob.errorMessage,
            completedAt = if (status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED) LocalDateTime.now() else null
        )
        
        jobCache.put(jobId, updatedJob)
    }

    override fun updateProgress(jobId: String, rowsWritten: Long, batchesFlushed: Long) {
        val currentJob = jobCache.getIfPresent(jobId) ?: return
        val updatedJob = currentJob.copy(
            rowsWritten = rowsWritten,
            batchesFlushed = batchesFlushed,
        )
        jobCache.put(jobId, updatedJob)
    }

    override fun requestCancel(jobId: String) {
        val currentJob = jobCache.getIfPresent(jobId) ?: return
        val updatedJob = currentJob.copy(cancelRequested = true)
        jobCache.put(jobId, updatedJob)
    }

    override fun isCancelRequested(jobId: String): Boolean {
        return jobCache.getIfPresent(jobId)?.cancelRequested == true
    }
}
