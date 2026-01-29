package com.streamsheet.spring.async

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@DisplayName("InMemoryJobManager 테스트")
class InMemoryJobManagerTest {

    private val jobManager = InMemoryJobManager()

    @Test
    @DisplayName("작업 생성 시 고유 ID를 반환하고 READY 상태여야 한다")
    fun `createJob() should return valid jobId and initialize with READY status`() {
        // When
        val jobId = jobManager.createJob()

        // Then
        assertNotNull(jobId)
        val job = jobManager.getJob(jobId)
        assertNotNull(job)
        assertEquals(jobId, job?.jobId)
        assertEquals(JobStatus.READY, job?.status)
    }

    @Test
    @DisplayName("존재하지 않는 작업 ID 조회 시 null을 반환해야 한다")
    fun `getJob() should return null for non-existent jobId`() {
        // When
        val job = jobManager.getJob("unknown-id")

        // Then
        assertNull(job)
    }

    @Test
    @DisplayName("작업 상태 업데이트가 정상적으로 반영되어야 한다")
    fun `updateStatus() should update job status and metadata`() {
        // Given
        val jobId = jobManager.createJob()
        val resultUri = URI.create("file:///tmp/test.xlsx")

        // When
        jobManager.updateStatus(jobId, JobStatus.COMPLETED, resultUri = resultUri)

        // Then
        val job = jobManager.getJob(jobId)
        assertNotNull(job)
        assertEquals(JobStatus.COMPLETED, job?.status)
        assertEquals(resultUri, job?.resultUri)
        assertNotNull(job?.completedAt)
    }

    @Test
    @DisplayName("실패 상태 업데이트 시 에러 메시지가 저장되어야 한다")
    fun `updateStatus() should store error message on failure`() {
        // Given
        val jobId = jobManager.createJob()
        val errorMessage = "Something went wrong"

        // When
        jobManager.updateStatus(jobId, JobStatus.FAILED, errorMessage = errorMessage)

        // Then
        val job = jobManager.getJob(jobId)
        assertNotNull(job)
        assertEquals(JobStatus.FAILED, job?.status)
        assertEquals(errorMessage, job?.errorMessage)
        assertNotNull(job?.completedAt)
    }
}
