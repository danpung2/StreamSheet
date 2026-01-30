package com.streamsheet.core.exporter

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.excelSchema
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@DisplayName("SXSSF 엑셀 내보내기 테스트")
class SxssfExcelExporterTest {

    data class TestEntity(val name: String, val age: Int)

    class TestDataSource(private val data: List<TestEntity>) : StreamingDataSource<TestEntity> {
        var isClosed = false
        override fun stream(): Sequence<TestEntity> = data.asSequence()
        override fun close() {
            isClosed = true
        }
    }

    @Test
    @DisplayName("데이터가 정상적으로 엑셀로 내보내져야 한다")
    fun `should export data correctly`() {
        val data = listOf(
            TestEntity("User1", 30),
            TestEntity("User2", 25)
        )
        val schema = excelSchema<TestEntity> {
            sheetName = "Users"
            column("Name") { it.name }
            column("Age") { it.age }
        }
        val dataSource = TestDataSource(data)
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()

        exporter.export(schema, dataSource, outputStream)

        // Verify output
        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheet("Users")
        assertNotNull(sheet)
        assertEquals(2, sheet.lastRowNum) // Header (0) + 2 rows = index 2? No, 0-based. 0=header, 1=Alice, 2=Bob.
        
        assertEquals("Name", sheet.getRow(0).getCell(0).stringCellValue)
        assertEquals("User1", sheet.getRow(1).getCell(0).stringCellValue)
        assertEquals(30.0, sheet.getRow(1).getCell(1).numericCellValue)
        
        // Verify resource closed
        assertTrue(dataSource.isClosed, "DataSource should be closed after export")
    }

    @Test
    @DisplayName("수식 인젝션을 방지해야 한다 (기본)")
    fun `should prevent formula injection`() {
        val data = listOf(TestEntity("=SUM(1+1)", 10))
        val schema = excelSchema<TestEntity> {
            column("Name") { it.name }
        }
        val dataSource = TestDataSource(data)
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(preventFormulaInjection = true)

        exporter.export(schema, dataSource, outputStream, config)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        val cellValue = sheet.getRow(1).getCell(0).stringCellValue
        
        // Should be escaped with single quote
        assertEquals("'=SUM(1+1)", cellValue)
    }

    @Test
    @DisplayName("공백이 포함된 수식 인젝션도 방지해야 한다")
    fun `should prevent formula injection with whitespace`() {
        val data = listOf(TestEntity("  =cmd|' /C calc'!A0", 10))
        val schema = excelSchema<TestEntity> {
            column("Name") { it.name }
        }
        val dataSource = TestDataSource(data)
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(preventFormulaInjection = true)

        exporter.export(schema, dataSource, outputStream, config)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        val cellValue = sheet.getRow(1).getCell(0).stringCellValue
        
        // Should be escaped
        assertEquals("'  =cmd|' /C calc'!A0", cellValue)
    }

    @Test
    @DisplayName("최대 행 제한 설정을 준수해야 한다")
    fun `should respect max rows limit`() {
        val data = (1..100).map { TestEntity("User $it", it) }
        val schema = excelSchema<TestEntity> { column("Name") { it.name } }
        val dataSource = TestDataSource(data)
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(maxRows = 10)

        exporter.export(schema, dataSource, outputStream, config)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        
        // Header (1) + 10 rows
        assertEquals(10, sheet.lastRowNum) 
    }

    @Test
    @DisplayName("예외 발생 시에도 데이터 소스를 닫아야 한다")
    fun `should close datasource even if exception occurs`() {
        val schema = excelSchema<TestEntity> { column("Name") { it.name } }
        val dataSource = object : StreamingDataSource<TestEntity> {
            var isClosed = false
            override fun stream(): Sequence<TestEntity> {
                throw RuntimeException("Stream error")
            }
            override fun close() {
                isClosed = true
            }
        }
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()

        assertThrows(RuntimeException::class.java) {
            exporter.export(schema, dataSource, outputStream)
        }

        assertTrue(dataSource.isClosed, "DataSource should be closed even after exception")
    }

    @Test
    @DisplayName("탭 문자로 시작하는 수식 인젝션을 방지해야 한다")
    fun `should prevent formula injection with tab character`() {
        val data = listOf(TestEntity("\t=cmd|'/C calc'!A0", 10))
        val schema = excelSchema<TestEntity> {
            column("Name") { it.name }
        }
        val dataSource = TestDataSource(data)
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(preventFormulaInjection = true)

        exporter.export(schema, dataSource, outputStream, config)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        val cellValue = sheet.getRow(1).getCell(0).stringCellValue

        // Should be escaped even with tab prefix
        assertEquals("'\t=cmd|'/C calc'!A0", cellValue)
    }

    @Test
    @DisplayName("개행 문자로 시작하는 수식 인젝션을 방지해야 한다")
    fun `should prevent formula injection with newline character`() {
        val data = listOf(TestEntity("\n=HYPERLINK(\"http://evil.com\")", 10))
        val schema = excelSchema<TestEntity> {
            column("Name") { it.name }
        }
        val dataSource = TestDataSource(data)
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(preventFormulaInjection = true)

        exporter.export(schema, dataSource, outputStream, config)

        val workbook = XSSFWorkbook(ByteArrayInputStream(outputStream.toByteArray()))
        val sheet = workbook.getSheetAt(0)
        val cellValue = sheet.getRow(1).getCell(0).stringCellValue

        // Should be escaped even with newline prefix
        assertEquals("'\n=HYPERLINK(\"http://evil.com\")", cellValue)
    }

    @Test
    @DisplayName("최대 행 제한 도달 시 스트림 소비를 즉시 중단해야 한다")
    fun `should stop consuming stream immediately when max rows reached`() {
        var consumedCount = 0
        val schema = excelSchema<TestEntity> { column("Name") { it.name } }
        val dataSource = object : StreamingDataSource<TestEntity> {
            override fun stream(): Sequence<TestEntity> = generateSequence(0) { it + 1 }
                .map {
                    consumedCount++
                    TestEntity("User $it", it)
                }
            override fun close() {}
        }
        val outputStream = ByteArrayOutputStream()
        val exporter = SxssfExcelExporter()
        val config = ExcelExportConfig(maxRows = 10)

        exporter.export(schema, dataSource, outputStream, config)

        // consumedCount should be 11 (10 rows + 1 for takeWhile check that returns false)
        // Not 100 or more which would indicate the loop bug
        assertTrue(consumedCount <= 11, "Stream should stop consuming after max rows. Consumed: $consumedCount")
    }
}
