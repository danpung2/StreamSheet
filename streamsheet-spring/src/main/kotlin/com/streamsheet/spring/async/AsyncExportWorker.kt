package com.streamsheet.spring.async

import com.streamsheet.core.cancel.CancellationTokenSource
import com.streamsheet.core.cancel.ExportCancelledException
import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.exporter.ExcelExporters
import com.streamsheet.core.exporter.ExportOptions
import com.streamsheet.core.progress.ExportPhase
import com.streamsheet.core.progress.ExportProgressListener
import com.streamsheet.core.schema.ExcelSchema
import com.streamsheet.spring.storage.FileStorage
import com.streamsheet.spring.tracing.StreamSheetTracer
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.file.Files

@Service
class AsyncExportWorker(
    private val jobManager: JobManager,
    private val fileStorage: FileStorage,
    private val excelExporter: ExcelExporter,
    private val config: ExcelExportConfig,
    private val eventPublisher: ApplicationEventPublisher,
    private val tracer: StreamSheetTracer,
) {

    private val logger = LoggerFactory.getLogger(AsyncExportWorker::class.java)

    @Async
    fun <T> processExport(
        jobId: String,
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
    ) {
        tracer.inSpan("streamsheet.export.job") {
            logger.info("Starting async export job: {}", jobId)

            if (jobManager.isCancelRequested(jobId)) {
                jobManager.updateStatus(jobId, JobStatus.CANCELLED)
                return@inSpan
            }

            jobManager.updateStatus(jobId, JobStatus.PROCESSING)

            val cancellation = CancellationTokenSource()

            val progressListener = ExportProgressListener { progress ->
                when (progress.phase) {
                    ExportPhase.FLUSHED_BATCH,
                    ExportPhase.WRITING_WORKBOOK,
                    ExportPhase.COMPLETED,
                    ExportPhase.CANCELLED,
                    ExportPhase.FAILED,
                    -> jobManager.updateProgress(jobId, progress.rowsWritten, progress.batchesFlushed)

                    ExportPhase.STARTING -> {
                        // NOTE: STARTING 단계는 JobManager 진행률 저장 대상이 아닙니다.
                        // STARTING phase is not persisted to JobManager progress.
                    }
                }

                if (progress.phase == ExportPhase.FLUSHED_BATCH || progress.phase == ExportPhase.WRITING_WORKBOOK) {
                    if (jobManager.isCancelRequested(jobId)) {
                        cancellation.cancel()
                    }
                }
            }

            val tempFile = Files.createTempFile("streamsheet-$jobId", ".xlsx").toFile()

            try {
                tracer.inSpan("streamsheet.export.xlsx") {
                    dataSource.use { ds ->
                        tempFile.outputStream().use { output ->
                            ExcelExporters.export(
                                exporter = excelExporter,
                                schema = schema,
                                dataSource = ds,
                                output = output,
                                config = config,
                                options = ExportOptions(
                                    cancellationToken = cancellation.token,
                                    progressListener = progressListener,
                                ),
                            )
                        }
                    }
                }

                val resultUri = tracer.inSpan("streamsheet.export.upload") {
                    tempFile.inputStream().use { input ->
                        fileStorage.save(
                            fileName = "${schema.sheetName}-$jobId.xlsx",
                            inputStream = input,
                            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            contentLength = tempFile.length(),
                        )
                    }
                }

                jobManager.updateStatus(jobId, JobStatus.COMPLETED, resultUri = resultUri)
                logger.info("Async export job completed: {}", jobId)
                eventPublisher.publishEvent(ExportCompletedEvent(jobId, resultUri, true))

            } catch (e: ExportCancelledException) {
                logger.info("Async export job cancelled: {}", jobId)
                jobManager.updateStatus(jobId, JobStatus.CANCELLED)
            } catch (e: Exception) {
                logger.error("Async export job failed: {}", jobId, e)
                jobManager.updateStatus(jobId, JobStatus.FAILED, errorMessage = e.message)
            } finally {
                runCatching { tempFile.delete() }
            }
        }
    }
}
