package com.streamsheet.spring.async

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class RedisJobManagerTest {

    @Mock
    private lateinit var redisTemplate: StringRedisTemplate

    @Mock
    private lateinit var hashOps: HashOperations<String, String, String>

    private lateinit var redisJobManager: RedisJobManager

    private val keyPrefix = "streamsheet:jobs:"
    private val retentionHours = 24L

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
        redisJobManager = RedisJobManager(redisTemplate, keyPrefix, retentionHours)
    }

    @Test
    @DisplayName("createJob: Job을 생성하고 Redis에 초기 상태를 저장한다")
    fun createJob() {
        // Given
        val keyCaptor = argumentCaptor<String>()
        val mapCaptor = argumentCaptor<Map<String, String>>()

        // When
        val jobId = redisJobManager.createJob()

        // Then
        verify(hashOps).putAll(keyCaptor.capture(), mapCaptor.capture())
        verify(redisTemplate).expire(eq(keyPrefix + jobId), eq(Duration.ofHours(retentionHours)))

        assertThat(keyCaptor.firstValue).isEqualTo(keyPrefix + jobId)
        
        val map = mapCaptor.firstValue
        assertThat(map["jobId"]).isEqualTo(jobId)
        assertThat(map["status"]).isEqualTo(JobStatus.READY.name)
        assertThat(map["rowsWritten"]).isEqualTo("0")
        assertThat(map["batchesFlushed"]).isEqualTo("0")
        assertThat(map["cancelRequested"]).isEqualTo("false")
        assertThat(map["createdAt"]).isNotNull()
    }

    @Test
    @DisplayName("getJob: 존재하지 않는 Job ID인 경우 null을 반환한다")
    fun getJob_notFound() {
        // Given
        val jobId = "non-existent"
        whenever(hashOps.entries(keyPrefix + jobId)).thenReturn(emptyMap())

        // When
        val result = redisJobManager.getJob(jobId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("getJob: Redis에서 Job 정보를 조회하여 반환한다")
    fun getJob_success() {
        // Given
        val jobId = "job-123"
        val now = LocalDateTime.now().toString()
        val data = mapOf(
            "jobId" to jobId,
            "status" to JobStatus.PROCESSING.name,
            "rowsWritten" to "100",
            "batchesFlushed" to "2",
            "cancelRequested" to "false",
            "createdAt" to now
        )
        whenever(hashOps.entries(keyPrefix + jobId)).thenReturn(data)

        // When
        val result = redisJobManager.getJob(jobId)

        // Then
        assertThat(result).isNotNull
        assertThat(result?.jobId).isEqualTo(jobId)
        assertThat(result?.status).isEqualTo(JobStatus.PROCESSING)
        assertThat(result?.rowsWritten).isEqualTo(100L)
        assertThat(result?.batchesFlushed).isEqualTo(2L)
        assertThat(result?.createdAt.toString()).isEqualTo(now)
    }

    @Test
    @DisplayName("updateStatus: Job 상태를 업데이트하고 만료 시간을 갱신한다")
    fun updateStatus() {
        // Given
        val jobId = "job-123"
        val key = keyPrefix + jobId
        whenever(redisTemplate.hasKey(key)).thenReturn(true)

        // When
        redisJobManager.updateStatus(jobId, JobStatus.COMPLETED, URI.create("s3://bucket/file.xlsx"), null)

        // Then
        verify(hashOps).put(key, "status", JobStatus.COMPLETED.name)
        verify(hashOps).put(key, "resultUri", "s3://bucket/file.xlsx")
        verify(hashOps).put(eq(key), eq("completedAt"), any())
        verify(redisTemplate).expire(key, Duration.ofHours(retentionHours))
    }

    @Test
    @DisplayName("updateStatus: 존재하지 않는 Job의 경우 업데이트하지 않는다")
    fun updateStatus_notFound() {
        // Given
        val jobId = "job-123"
        val key = keyPrefix + jobId
        whenever(redisTemplate.hasKey(key)).thenReturn(false)

        // When
        redisJobManager.updateStatus(jobId, JobStatus.COMPLETED, null, null)

        // Then
        verify(hashOps, never()).put(any(), any(), any())
    }

    @Test
    @DisplayName("updateProgress: 진행 상황을 업데이트하고 만료 시간을 갱신한다")
    fun updateProgress() {
        // Given
        val jobId = "job-123"
        val key = keyPrefix + jobId
        whenever(redisTemplate.hasKey(key)).thenReturn(true)

        // When
        redisJobManager.updateProgress(jobId, 500, 5)

        // Then
        verify(hashOps).put(key, "rowsWritten", "500")
        verify(hashOps).put(key, "batchesFlushed", "5")
        verify(redisTemplate).expire(key, Duration.ofHours(retentionHours))
    }

    @Test
    @DisplayName("requestCancel: 취소 요청을 기록한다")
    fun requestCancel() {
        // Given
        val jobId = "job-123"
        val key = keyPrefix + jobId
        whenever(redisTemplate.hasKey(key)).thenReturn(true)

        // When
        redisJobManager.requestCancel(jobId)

        // Then
        verify(hashOps).put(key, "cancelRequested", "true")
        verify(redisTemplate).expire(key, Duration.ofHours(retentionHours))
    }

    @Test
    @DisplayName("isCancelRequested: 취소 요청 여부를 반환한다")
    fun isCancelRequested() {
        // Given
        val jobId = "job-123"
        val key = keyPrefix + jobId
        whenever(redisTemplate.hasKey(key)).thenReturn(true)
        whenever(hashOps.get(key, "cancelRequested")).thenReturn("true")

        // When
        val result = redisJobManager.isCancelRequested(jobId)

        // Then
        assertThat(result).isTrue
    }
}
