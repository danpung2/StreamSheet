package com.streamsheet.spring.async

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
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
class InMemoryJobManager : JobManager {

    private val jobCache: Cache<String, ExportJob> = Caffeine.newBuilder()
        .expireAfterWrite(24, TimeUnit.HOURS) // 24시간 후 만료
        .maximumSize(10000)
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
            completedAt = if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) LocalDateTime.now() else null
        )
        
        jobCache.put(jobId, updatedJob)
    }
}
