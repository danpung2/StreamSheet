package com.streamsheet.spring.async

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.ExcelSchema
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("AsyncExportService 테스트")
class AsyncExportServiceTest {

    @Mock
    private lateinit var jobManager: JobManager

    @Mock
    private lateinit var worker: AsyncExportWorker

    @InjectMocks
    private lateinit var asyncExportService: AsyncExportService

    @Mock
    private lateinit var schema: ExcelSchema<Any>

    @Mock
    private lateinit var dataSource: StreamingDataSource<Any>

    @Test
    @DisplayName("startExport 호출 시 Job을 생성하고 worker에 위임한 뒤 jobId를 반환한다")
    fun `startExport() should create job and delegate to worker`() {
        val jobId = "test-job-123"
        whenever(jobManager.createJob()).thenReturn(jobId)

        val returnedJobId = asyncExportService.startExport(schema, dataSource)

        assertEquals(jobId, returnedJobId)
        verify(jobManager).createJob()
        verify(worker).processExport(eq(jobId), eq(schema), eq(dataSource))
    }
}
