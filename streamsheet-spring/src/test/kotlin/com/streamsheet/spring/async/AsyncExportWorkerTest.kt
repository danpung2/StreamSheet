package com.streamsheet.spring.async

import com.streamsheet.core.cancel.ExportCancelledException
import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.schema.ExcelSchema
import com.streamsheet.spring.storage.FileStorage
import com.streamsheet.spring.tracing.NoopStreamSheetTracer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.net.URI

@ExtendWith(MockitoExtension::class)
@DisplayName("AsyncExportWorker 테스트")
class AsyncExportWorkerTest {

    @Mock
    private lateinit var jobManager: JobManager

    @Mock
    private lateinit var fileStorage: FileStorage

    @Mock
    private lateinit var excelExporter: ExcelExporter

    @Mock
    private lateinit var config: ExcelExportConfig

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private fun worker(): AsyncExportWorker {
        return AsyncExportWorker(jobManager, fileStorage, excelExporter, config, eventPublisher, NoopStreamSheetTracer)
    }

    @Mock
    private lateinit var schema: ExcelSchema<Any>

    @Mock
    private lateinit var dataSource: StreamingDataSource<Any>

    @Test
    @DisplayName("정상 처리 시 COMPLETED로 업데이트하고 완료 이벤트를 발행한다")
    fun `should complete export job`() {
        val jobId = "job-1"

        whenever(jobManager.isCancelRequested(eq(jobId))).thenReturn(false)
        whenever(fileStorage.save(any(), any(), any(), any())).thenReturn(URI.create("file:///tmp/test.xlsx"))

        worker().processExport(jobId, schema, dataSource)

        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.PROCESSING), isNull(), isNull())
        verify(excelExporter).export(eq(schema), eq(dataSource), any(), eq(config))
        verify(fileStorage).save(any(), any(), any(), any())
        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.COMPLETED), any(), isNull())
        verify(eventPublisher).publishEvent(any<ExportCompletedEvent>())
    }

    @Test
    @DisplayName("취소 예외 발생 시 CANCELLED로 업데이트한다")
    fun `should mark job cancelled when exporter throws ExportCancelledException`() {
        val jobId = "job-cancel"

        whenever(jobManager.isCancelRequested(eq(jobId))).thenReturn(false)
        doThrow(ExportCancelledException())
            .`when`(excelExporter).export(eq(schema), eq(dataSource), any(), eq(config))

        worker().processExport(jobId, schema, dataSource)

        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.PROCESSING), isNull(), isNull())
        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.CANCELLED), isNull(), isNull())
    }

    @Test
    @DisplayName("일반 예외 발생 시 FAILED로 업데이트한다")
    fun `should mark job failed when exporter throws`() {
        val jobId = "job-fail"

        whenever(jobManager.isCancelRequested(eq(jobId))).thenReturn(false)
        doThrow(RuntimeException("Export failed"))
            .`when`(excelExporter).export(eq(schema), eq(dataSource), any(), eq(config))

        worker().processExport(jobId, schema, dataSource)

        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.PROCESSING), isNull(), isNull())
        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.FAILED), isNull(), eq("Export failed"))
    }
}
