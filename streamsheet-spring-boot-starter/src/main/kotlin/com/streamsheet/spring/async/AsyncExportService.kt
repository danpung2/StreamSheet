package com.streamsheet.spring.async

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.ExcelSchema
import org.springframework.stereotype.Service

/**
 * 비동기 엑셀 내보내기 서비스
 * Async Excel export service
 */
@Service
class AsyncExportService(
    private val jobManager: JobManager,
    private val worker: AsyncExportWorker,
) {

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
        worker.processExport(jobId, schema, dataSource)

        return jobId
    }

    fun requestCancel(jobId: String) {
        jobManager.requestCancel(jobId)
    }
}
