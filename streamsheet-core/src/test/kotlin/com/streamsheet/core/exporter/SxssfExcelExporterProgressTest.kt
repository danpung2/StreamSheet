package com.streamsheet.core.exporter

import com.streamsheet.core.cancel.CancellationTokenSource
import com.streamsheet.core.cancel.ExportCancelledException
import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.progress.ExportPhase
import com.streamsheet.core.progress.ExportProgress
import com.streamsheet.core.progress.ExportProgressListener
import com.streamsheet.core.schema.excelSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList

@DisplayName("SXSSF progress/cancellation 테스트")
class SxssfExcelExporterProgressTest {

    data class TestEntity(val name: String)

    class TestDataSource(private val data: List<TestEntity>) : StreamingDataSource<TestEntity> {
        var isClosed = false

        override fun stream(): Sequence<TestEntity> = data.asSequence()

        override fun close() {
            isClosed = true
        }
    }

    @Test
    @DisplayName("flushBatchSize 기준으로 progress 이벤트가 발행되어야 한다")
    fun `should emit progress events at flush boundaries`() {
        val data = (1..5).map { TestEntity("User$it") }
        val schema = excelSchema<TestEntity> {
            sheetName = "Users"
            column("Name") { it.name }
        }

        val events = CopyOnWriteArrayList<ExportProgress>()
        val listener = ExportProgressListener { progress ->
            events.add(progress)
        }

        val config = ExcelExportConfig(
            rowAccessWindowSize = 10,
            flushBatchSize = 2,
        )

        val exporter = SxssfExcelExporter()
        exporter.export(
            schema = schema,
            dataSource = TestDataSource(data),
            output = ByteArrayOutputStream(),
            config = config,
            options = ExportOptions(progressListener = listener),
        )

        assertTrue(events.isNotEmpty())
        assertEquals(ExportPhase.STARTING, events.first().phase)
        assertEquals(ExportPhase.WRITING_WORKBOOK, events.first { it.phase == ExportPhase.WRITING_WORKBOOK }.phase)
        assertEquals(ExportPhase.COMPLETED, events.last().phase)

        val flushed = events.filter { it.phase == ExportPhase.FLUSHED_BATCH }
        assertEquals(2, flushed.size)
        assertEquals(2L, flushed[0].rowsWritten)
        assertEquals(1L, flushed[0].batchesFlushed)
        assertEquals(4L, flushed[1].rowsWritten)
        assertEquals(2L, flushed[1].batchesFlushed)
    }

    @Test
    @DisplayName("취소 요청 시 ExportCancelledException이 발생하고 CANCELLED 이벤트가 발행되어야 한다")
    fun `should cancel export cooperatively`() {
        val data = (1..10).map { TestEntity("User$it") }
        val schema = excelSchema<TestEntity> {
            sheetName = "Users"
            column("Name") { it.name }
        }

        val cancellation = CancellationTokenSource()
        val events = CopyOnWriteArrayList<ExportProgress>()
        val listener = ExportProgressListener { progress ->
            events.add(progress)
            if (progress.phase == ExportPhase.FLUSHED_BATCH) {
                cancellation.cancel()
            }
        }

        val dataSource = TestDataSource(data)
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(
            rowAccessWindowSize = 10,
            flushBatchSize = 2,
        )

        assertThrows<ExportCancelledException> {
            exporter.export(
                schema = schema,
                dataSource = dataSource,
                output = ByteArrayOutputStream(),
                config = config,
                options = ExportOptions(
                    cancellationToken = cancellation.token,
                    progressListener = listener,
                ),
            )
        }

        assertTrue(dataSource.isClosed, "DataSource should be closed on cancellation")
        assertTrue(events.any { it.phase == ExportPhase.CANCELLED }, "CANCELLED progress should be emitted")
        val cancelled = events.last { it.phase == ExportPhase.CANCELLED }
        assertEquals(2L, cancelled.rowsWritten)
        assertEquals(1L, cancelled.batchesFlushed)
    }
}
