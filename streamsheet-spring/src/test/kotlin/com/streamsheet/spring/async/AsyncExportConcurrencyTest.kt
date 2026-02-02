package com.streamsheet.spring.async

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.schema.ExcelSchema
import com.streamsheet.spring.storage.FileStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.springframework.context.ApplicationEventPublisher
import com.streamsheet.spring.tracing.NoopStreamSheetTracer
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("AsyncExportService 동시성 테스트")
class AsyncExportConcurrencyTest {

    @Test
    @DisplayName("여러 개의 내보내기 요청이 동시에 들어와도 각 작업을 독립적으로 처리해야 한다")
    fun `should handle multiple concurrent export requests`() {
        // Given: 테스트 데이터 및 모의 객체 설정
        // Given: Setup test data and mocks
        val threadCount = 10
        val jobManager = InMemoryJobManager()
        val fileStorage = mock(FileStorage::class.java)
        val excelExporter = mock(ExcelExporter::class.java)
        val eventPublisher = mock(ApplicationEventPublisher::class.java)
        val config = ExcelExportConfig.DEFAULT

        val worker = AsyncExportWorker(jobManager, fileStorage, excelExporter, config, eventPublisher, NoopStreamSheetTracer)
        val asyncExportService = AsyncExportService(jobManager, worker)

        val schema = mock(ExcelSchema::class.java) as ExcelSchema<Any>
        `when`(schema.sheetName).thenReturn("TestSheet")
        
        val dataSource = mock(StreamingDataSource::class.java) as StreamingDataSource<Any>
        `when`(fileStorage.save(any(), any(), any(), anyLong())).thenReturn(URI.create("s3://test/file.xlsx"))
        
        // 동시 실행 중첩을 보장하기 위해 현실적인 Export 소요 시간을 시뮬레이션합니다.
        // Simulating realistic export duration to ensure concurrent execution overlap
        doAnswer { 
            Thread.sleep(50) 
            null
        }.`when`(excelExporter).export(
            any<ExcelSchema<Any>>(),
            any<StreamingDataSource<Any>>(),
            any<OutputStream>(),
            any<ExcelExportConfig>()
        )

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val jobIds = mutableListOf<String>()

        // When: 여러 스레드에서 동시에 내보내기 요청
        // When: Parallel export requests from multiple threads
        repeat(threadCount) {
            executor.submit {
                try {
                    val jobId = asyncExportService.startExport(schema, dataSource)
                    synchronized(jobIds) { jobIds.add(jobId) }
                } finally {
                    latch.countDown()
                }
            }
        }

        // NOTE: 모든 스레드가 startExport 호출을 완료할 때까지 대기
        // Wait for all threads to finish calling startExport
        latch.await(5, TimeUnit.SECONDS)

        // Then: 결과 검증 (JobId 고유성 및 상태 확인)
        // Then: Verify results (Unique JobIDs and status)
        assertEquals(threadCount, jobIds.size)
        assertEquals(threadCount, jobIds.distinct().size, "Job IDs should be unique")

        // NOTE: 단위 테스트 환경에서는 프록시가 없으므로 @Async가 무시되고 동기적으로 실행됨
        // @Async is ignored in unit tests without Spring Proxy, running synchronously.
        jobIds.forEach { jobId ->
            val job = jobManager.getJob(jobId)
            assertTrue(job != null && (job.status == JobStatus.COMPLETED), "Job $jobId should be completed")
        }
        
        executor.shutdown()
    }
}
