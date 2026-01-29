package com.streamsheet.core.exporter

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.excelSchema
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@DisplayName("SXSSF 엑셀 내보내기 품질 및 경계값 테스트")
class SxssfExcelExporterQualityTest {

    data class TestEntity(val value: String)

    class SimpleDataSource(private val data: List<TestEntity>) : StreamingDataSource<TestEntity> {
        override fun stream(): Sequence<TestEntity> = data.asSequence()
        override fun close() {}
    }

    @Test
    @DisplayName("다양한 패턴의 수식 인젝션을 방지해야 한다 (@, +, -, =)")
    fun `should prevent various formula injection patterns`() {
        val patterns = listOf("=SUM(1,2)", "+SUM(1,2)", "-SUM(1,2)", "@SUM(1,2)")
        val data = patterns.map { TestEntity(it) }
        val schema = excelSchema<TestEntity> {
            column("Value") { it.value }
        }
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(preventFormulaInjection = true)
        val outputStream = ByteArrayOutputStream()

        exporter.export(schema, SimpleDataSource(data), outputStream, config)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        
        patterns.forEachIndexed { index, pattern ->
            val cellValue = sheet.getRow(index + 1).getCell(0).stringCellValue
            assertEquals("'$pattern", cellValue, "Pattern '$pattern' should be escaped")
        }
    }

    @Test
    @DisplayName("매우 긴 문자열(32,767자)을 정상적으로 처리해야 한다")
    fun `should handle very long strings at cell limit`() {
        // Excel cell string limit is 32,767 characters
        val longString = "A".repeat(32767)
        val data = listOf(TestEntity(longString))
        val schema = excelSchema<TestEntity> {
            column("Value") { it.value }
        }
        val exporter = SxssfExcelExporter()
        val outputStream = ByteArrayOutputStream()

        exporter.export(schema, SimpleDataSource(data), outputStream)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        val cellValue = sheet.getRow(1).getCell(0).stringCellValue
        
        assertEquals(32767, cellValue.length)
        assertEquals(longString, cellValue)
    }

    @Test
    @DisplayName("시트 이름에 금지된 문자가 포함된 경우 안전하게 변환해야 한다")
    fun `should handle unsafe sheet names`() {
        val unsafeName = "Sales:2024/Jan*Report?"
        val data = listOf(TestEntity("Data"))
        val schema = excelSchema<TestEntity> {
            sheetName = unsafeName
            column("Value") { it.value }
        }
        val exporter = SxssfExcelExporter()
        val outputStream = ByteArrayOutputStream()

        exporter.export(schema, SimpleDataSource(data), outputStream)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheetName = workbook.getSheetName(0)
        
        // Apache POI WorkbookUtil.createSafeSheetName replaces : / \ * ? [ ] with ' '
        assertEquals("Sales 2024 Jan Report ", sheetName)
    }

    @Test
    @DisplayName("대용량 행(10,000건) 내보내기 시 메모리 윈도우 및 플러시가 정상 작동해야 한다")
    fun `should handle large number of rows with flushing`() {
        val rowCount = 10000
        val data = (1..rowCount).map { TestEntity("Row $it") }
        val schema = excelSchema<TestEntity> {
            column("Value") { it.value }
        }
        val exporter = SxssfExcelExporter()
        val outputStream = ByteArrayOutputStream()
        // Small window and batch size to force frequent flushing
        val config = ExcelExportConfig(rowAccessWindowSize = 100, flushBatchSize = 500)

        exporter.export(schema, SimpleDataSource(data), outputStream, config)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        assertEquals(rowCount, sheet.lastRowNum)
    }
}
