package com.streamsheet.spring.async

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.schema.ExcelSchema
import com.streamsheet.spring.storage.FileStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

@ExtendWith(MockitoExtension::class)
@DisplayName("AsyncExportService 테스트")
class AsyncExportServiceTest {

    @Mock
    private lateinit var jobManager: JobManager

    @Mock
    private lateinit var fileStorage: FileStorage

    @Mock
    private lateinit var excelExporter: ExcelExporter

    // NOTE: @InjectMocks가 생성자 주입을 위해 config 빈을 필요로 하므로 Mock 정의
    // NOTE: Define Mock as @InjectMocks requires config bean for constructor injection
    @Mock
    private lateinit var config: ExcelExportConfig

    @InjectMocks
    private lateinit var asyncExportService: AsyncExportService

    @Mock
    private lateinit var schema: ExcelSchema<Any>

    @Mock
    private lateinit var dataSource: StreamingDataSource<Any>

    @Test
    @DisplayName("startExport 호출 시 Job을 생성하고 PROCESS 상태로 업데이트한 뒤 반환한다")
    fun `startExport() should create job and return jobId`() {
        // Given
        val jobId = "test-job-123"
        `when`(jobManager.createJob()).thenReturn(jobId)

        // Mocking behavior inside processExport
        `when`(fileStorage.save(
            any(), 
            any(), 
            any())
        ).thenReturn(URI.create("s3://bucket/file.xlsx"))

        // When
        val returnedJobId = asyncExportService.startExport(schema, dataSource)

        // Then
        assertEquals(jobId, returnedJobId)
        
        // Verify flow
        verify(jobManager).createJob()
        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.PROCESSING),  isNull(), isNull())
        verify(excelExporter).export(eq(schema), eq(dataSource), any(), eq(config))
        verify(fileStorage).save(any(), any(), any())
        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.COMPLETED), any(), isNull())
    }

    @Test
    @DisplayName("엑셀 생성 중 예외 발생 시 Job 상태를 FAILED로 업데이트해야 한다")
    fun `processExport() should handle exceptions and update status to FAILED`() {
        // Given
        val jobId = "failed-job"
        `when`(jobManager.createJob()).thenReturn(jobId)
        
        // Use exact mocks to avoid ambiguity and null issues
        doThrow(RuntimeException("Export failed"))
            .`when`(excelExporter).export(
                eq(schema), 
                eq(dataSource), 
                any(), 
                eq(config)
            )

        // When
        asyncExportService.startExport(schema, dataSource)

        // Then
        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.PROCESSING), isNull(), isNull())
        verify(jobManager).updateStatus(eq(jobId), eq(JobStatus.FAILED), isNull(), eq("Export failed"))
    }
}
