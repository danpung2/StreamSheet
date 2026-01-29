package com.streamsheet.spring.async

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.schema.ExcelSchema
import com.streamsheet.spring.storage.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/**
 * 비동기 엑셀 내보내기 서비스
 * Async Excel export service
 */
@Service
class AsyncExportService(
    private val jobManager: JobManager,
    private val fileStorage: FileStorage,
    private val excelExporter: ExcelExporter,
    private val config: ExcelExportConfig
) {

    private val logger = LoggerFactory.getLogger(AsyncExportService::class.java)

    /**
     * 비동기 내보내기 시작
     * Start async export
     */
    fun <T> startExport(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>
    ): String {
        val jobId = jobManager.createJob()
        
        // NOTE: 실제 처리는 비동기로 위임
        processExport(jobId, schema, dataSource)
        
        return jobId
    }

    @Async
    protected fun <T> processExport(
        jobId: String,
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>
    ) {
        logger.info("Starting async export job: {}", jobId)
        jobManager.updateStatus(jobId, JobStatus.PROCESSING)

        // 임시 파일 생성
        val tempFile = Files.createTempFile("streamsheet-$jobId", ".xlsx").toFile()

        try {
            dataSource.use { ds ->
                tempFile.outputStream().use { output ->
                    excelExporter.export(schema, ds, output, config)
                }
            }

            // 스토리지에 업로드
            val resultUri = tempFile.inputStream().use { input ->
                fileStorage.save(
                    fileName = "${schema.sheetName}-$jobId.xlsx",
                    inputStream = input,
                    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            }

            jobManager.updateStatus(jobId, JobStatus.COMPLETED, resultUri = resultUri)
            logger.info("Async export job completed: {}", jobId)

        } catch (e: Exception) {
            logger.error("Async export job failed: {}", jobId, e)
            jobManager.updateStatus(jobId, JobStatus.FAILED, errorMessage = e.message)
        } finally {
            // 임시 파일 삭제
            runCatching { tempFile.delete() }
        }
    }
}
