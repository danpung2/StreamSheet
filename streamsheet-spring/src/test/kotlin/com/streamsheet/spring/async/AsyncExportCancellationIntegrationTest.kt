package com.streamsheet.spring.async

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.core.schema.excelSchema
import com.streamsheet.spring.storage.FileStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.context.ApplicationEventPublisher
import com.streamsheet.spring.tracing.NoopStreamSheetTracer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("AsyncExport 취소 통합 테스트")
class AsyncExportCancellationIntegrationTest {

    data class Row(val v: Int)

    @Test
    @DisplayName("JobManager.requestCancel 호출 시 다음 배치 경계에서 작업이 CANCELLED로 종료되어야 한다")
    fun `should cancel job when cancel is requested`() {
        val jobManager = InMemoryJobManager()
        val fileStorage = mock<FileStorage>()
        val eventPublisher = mock<ApplicationEventPublisher>()
        val config = ExcelExportConfig(
            rowAccessWindowSize = 10,
            flushBatchSize = 10,
            maxRows = 100,
        )

        val worker = AsyncExportWorker(
            jobManager = jobManager,
            fileStorage = fileStorage,
            excelExporter = SxssfExcelExporter(),
            config = config,
            eventPublisher = eventPublisher,
            tracer = NoopStreamSheetTracer,
        )

        val schema = excelSchema<Row> {
            sheetName = "Rows"
            column("v") { it.v }
        }

        val dataSource = object : StreamingDataSource<Row> {
            override fun stream(): Sequence<Row> = generateSequence(0) { it + 1 }
                .map {
                    Thread.sleep(5)
                    Row(it)
                }

            override fun close() {}
        }

        val jobId = jobManager.createJob()

        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit {
                worker.processExport(jobId, schema, dataSource)
            }

            // Wait until job enters PROCESSING, then request cancellation.
            val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadlineNanos) {
                val job = jobManager.getJob(jobId)
                if (job != null && job.status == JobStatus.PROCESSING) break
                Thread.sleep(10)
            }

            jobManager.requestCancel(jobId)

            future.get(20, TimeUnit.SECONDS)

            val job = jobManager.getJob(jobId)
            assertNotNull(job)
            assertEquals(JobStatus.CANCELLED, job!!.status)
            assertTrue(job.rowsWritten >= 0)

        } finally {
            executor.shutdownNow()
        }
    }
}
