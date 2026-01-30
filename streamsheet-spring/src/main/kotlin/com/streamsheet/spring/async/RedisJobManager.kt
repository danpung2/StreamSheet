package com.streamsheet.spring.async

import org.springframework.data.redis.core.StringRedisTemplate
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * Redis 기반 작업 관리자
 * Redis-backed job manager
 */
class RedisJobManager(
    private val redisTemplate: StringRedisTemplate,
    private val keyPrefix: String,
    private val retentionHours: Long,
) : JobManager {

    private val hashOps = redisTemplate.opsForHash<String, String>()

    override fun createJob(): String {
        val jobId = UUID.randomUUID().toString()
        val key = key(jobId)
        val now = LocalDateTime.now()

        hashOps.putAll(
            key,
            mapOf(
                FIELD_JOB_ID to jobId,
                FIELD_STATUS to JobStatus.READY.name,
                FIELD_ROWS_WRITTEN to "0",
                FIELD_BATCHES_FLUSHED to "0",
                FIELD_CANCEL_REQUESTED to "false",
                FIELD_CREATED_AT to now.toString(),
            )
        )
        redisTemplate.expire(key, Duration.ofHours(retentionHours))
        return jobId
    }

    override fun getJob(jobId: String): ExportJob? {
        val key = key(jobId)
        val entries = hashOps.entries(key)
        if (entries.isEmpty()) return null

        val status = entries[FIELD_STATUS]?.let { JobStatus.valueOf(it) } ?: return null
        val createdAt = entries[FIELD_CREATED_AT]?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
        val completedAt = entries[FIELD_COMPLETED_AT]?.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) }
        val resultUri = entries[FIELD_RESULT_URI]?.takeIf { it.isNotBlank() }?.let { URI.create(it) }
        val errorMessage = entries[FIELD_ERROR_MESSAGE]?.takeIf { it.isNotBlank() }
        val rowsWritten = entries[FIELD_ROWS_WRITTEN]?.toLongOrNull() ?: 0L
        val batchesFlushed = entries[FIELD_BATCHES_FLUSHED]?.toLongOrNull() ?: 0L
        val cancelRequested = entries[FIELD_CANCEL_REQUESTED]?.toBooleanStrictOrNull() ?: false

        return ExportJob(
            jobId = jobId,
            status = status,
            resultUri = resultUri,
            errorMessage = errorMessage,
            rowsWritten = rowsWritten,
            batchesFlushed = batchesFlushed,
            cancelRequested = cancelRequested,
            createdAt = createdAt,
            completedAt = completedAt,
        )
    }

    override fun updateStatus(jobId: String, status: JobStatus, resultUri: URI?, errorMessage: String?) {
        val key = key(jobId)
        if (redisTemplate.hasKey(key) != true) return

        hashOps.put(key, FIELD_STATUS, status.name)

        if (resultUri != null) {
            hashOps.put(key, FIELD_RESULT_URI, resultUri.toString())
        }
        if (errorMessage != null) {
            hashOps.put(key, FIELD_ERROR_MESSAGE, errorMessage)
        }

        val completedAt = if (status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED) {
            LocalDateTime.now().toString()
        } else {
            ""
        }
        hashOps.put(key, FIELD_COMPLETED_AT, completedAt)

        // Refresh TTL on update so active jobs don't expire mid-flight
        redisTemplate.expire(key, Duration.ofHours(retentionHours))
    }

    override fun updateProgress(jobId: String, rowsWritten: Long, batchesFlushed: Long) {
        val key = key(jobId)
        if (redisTemplate.hasKey(key) != true) return
        hashOps.put(key, FIELD_ROWS_WRITTEN, rowsWritten.toString())
        hashOps.put(key, FIELD_BATCHES_FLUSHED, batchesFlushed.toString())
        redisTemplate.expire(key, Duration.ofHours(retentionHours))
    }

    override fun requestCancel(jobId: String) {
        val key = key(jobId)
        if (redisTemplate.hasKey(key) != true) return
        hashOps.put(key, FIELD_CANCEL_REQUESTED, "true")
        redisTemplate.expire(key, Duration.ofHours(retentionHours))
    }

    override fun isCancelRequested(jobId: String): Boolean {
        val key = key(jobId)
        if (redisTemplate.hasKey(key) != true) return false
        return hashOps.get(key, FIELD_CANCEL_REQUESTED)?.toBooleanStrictOrNull() ?: false
    }

    private fun key(jobId: String): String = "${keyPrefix}${jobId}"

    private companion object {
        private const val FIELD_JOB_ID = "jobId"
        private const val FIELD_STATUS = "status"
        private const val FIELD_RESULT_URI = "resultUri"
        private const val FIELD_ERROR_MESSAGE = "errorMessage"
        private const val FIELD_ROWS_WRITTEN = "rowsWritten"
        private const val FIELD_BATCHES_FLUSHED = "batchesFlushed"
        private const val FIELD_CANCEL_REQUESTED = "cancelRequested"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_COMPLETED_AT = "completedAt"
    }
}
