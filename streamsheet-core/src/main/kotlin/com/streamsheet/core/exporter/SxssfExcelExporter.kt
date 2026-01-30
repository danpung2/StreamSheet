package com.streamsheet.core.exporter

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.ExcelSchema
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.streaming.SXSSFSheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Date

/**
 * Apache POI SXSSF 기반 엑셀 내보내기 엔진
 * Apache POI SXSSF-based Excel export engine
 *
 * NOTE: SXSSF는 메모리에 유지하는 행 수를 제한하여 대용량 데이터 처리 시 OOM을 방지합니다.
 * SXSSF limits the number of rows kept in memory, preventing OOM on large datasets.
 */
class SxssfExcelExporter : ExcelExporter {
    private val logger = LoggerFactory.getLogger(SxssfExcelExporter::class.java)

    override fun <T> export(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        output: OutputStream,
        config: ExcelExportConfig
    ) {
        export(schema, dataSource, emptyMap(), output, config)
    }

    override fun <T> export(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        filter: Map<String, Any>,
        output: OutputStream,
        config: ExcelExportConfig
    ) {
        // NOTE: 데이터 소스를 최상위에서 관리하여 예외 발생 시에도 리소스 해제 보장
        dataSource.use { ds ->
            // NOTE: SXSSFWorkbook 생성 - 메모리 윈도우 크기 설정
            val workbook = SXSSFWorkbook(config.rowAccessWindowSize).apply {
                isCompressTempFiles = config.compressTempFiles
            }

            try {
                // NOTE: 시트 이름 안전화 (엑셀 금지 문자 및 길이 제한 처리)
                val safeSheetName = WorkbookUtil.createSafeSheetName(schema.sheetName)
                val sheet = workbook.createSheet(safeSheetName)

                // NOTE: 컬럼 너비 설정
                setColumnWidths(sheet, schema)

                // NOTE: 스타일 생성
                val headerStyle = if (config.applyHeaderStyle) createHeaderStyle(workbook) else null
                val dataStyle = if (config.applyDataBorders) createDataStyle(workbook) else null
                
                // NOTE: 스타일 관리자 생성
                val styleManager = StyleManager(workbook, dataStyle)

                // NOTE: 헤더 행 작성
                writeHeaderRow(sheet, schema, headerStyle, config.preventFormulaInjection)

                // NOTE: 데이터 스트리밍 및 작성
                var rowNum = 1
                val startTime = System.currentTimeMillis()

                val dataSequence = if (filter.isEmpty()) {
                    ds.stream()
                } else {
                    ds.stream(filter)
                }

                // NOTE: takeWhile을 사용하여 행 제한 도달 시 스트림을 즉시 중단
                // Using takeWhile to immediately stop the stream when row limit is reached
                dataSequence
                    .takeWhile {
                        // NOTE: 엑셀 하드 limit (1,048,576행) 체크
                        // Excel hard limit check (1,048,576 rows). Last index is 1,048,575.
                        if (rowNum >= 1_048_576) {
                            logger.error("Excel hard limit reached (1,048,576 rows). Halting export to prevent failure.")
                            return@takeWhile false
                        }

                        // NOTE: 사용자 설정 최대 행 추출 제한 확인
                        if (config.maxRows != null && (rowNum - 1) >= config.maxRows) {
                            logger.warn("Maximum row limit reached: {}. Halting export.", config.maxRows)
                            return@takeWhile false
                        }

                        true
                    }
                    .forEach { entity ->
                        writeDataRow(sheet, schema, entity, rowNum, styleManager, config.preventFormulaInjection)
                        rowNum++

                        // NOTE: 주기적 플러시
                        if (rowNum % config.flushBatchSize == 0) {
                            sheet.flushRows(config.rowAccessWindowSize)
                        }
                    }

                val endTime = System.currentTimeMillis()
                if (config.enableMetrics) {
                    logger.info("Excel export completed: {} rows, {} ms", rowNum - 1, endTime - startTime)
                }

                // NOTE: 최종 출력
                workbook.write(output)
                output.flush()

            } finally {
                // NOTE: 자원 해제 최우선 보장 (컴파일 경고 억제)
                // NOTE: 특정 환경에서 발생할 수 있는 자원 해제 경고를 억제합니다.
                // Suppress deprecation warnings for resource cleanup in certain environments.
                @Suppress("DEPRECATION")
                try {
                    workbook.dispose()
                } catch (e: Exception) {
                    // dispose 실패 시 로그 외에 할 수 있는 일이 제한적임
                }
                workbook.close()
            }
        }
    }

    /**
     * 컬럼 너비 설정
     */
    private fun <T> setColumnWidths(sheet: SXSSFSheet, schema: ExcelSchema<T>) {
        val widths = if (schema.columnWidths.size >= schema.headers.size) {
            schema.columnWidths
        } else {
            schema.columnWidths + List(schema.headers.size - schema.columnWidths.size) {
                ExcelSchema.DEFAULT_COLUMN_WIDTH
            }
        }

        widths.forEachIndexed { index, width ->
            sheet.setColumnWidth(index, width * 256)
        }
    }

    /**
     * 헤더 스타일 생성
     */
    private fun createHeaderStyle(workbook: SXSSFWorkbook): CellStyle {
        return workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN

            setFont(workbook.createFont().apply {
                bold = true
            })
        }
    }

    /**
     * 데이터 셀 스타일 생성
     */
    private fun createDataStyle(workbook: SXSSFWorkbook): CellStyle {
        return workbook.createCellStyle().apply {
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
    }

    /**
     * 헤더 행 작성
     */
    private fun <T> writeHeaderRow(
        sheet: SXSSFSheet,
        schema: ExcelSchema<T>,
        headerStyle: CellStyle?,
        preventFormulaInjection: Boolean
    ) {
        val headerRow = sheet.createRow(0)
        schema.headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                val finalHeader = if (preventFormulaInjection && isFormula(header)) {
                    "'$header"
                } else {
                    header
                }
                setCellValue(finalHeader)
                headerStyle?.let { cellStyle = it }
            }
        }
    }

    /**
     * 데이터 행 작성
     * @param preventFormulaInjection 수식 인젝션 방지 여부
     */
    private fun <T> writeDataRow(
        sheet: SXSSFSheet,
        schema: ExcelSchema<T>,
        entity: T,
        rowNum: Int,
        styleManager: StyleManager,
        preventFormulaInjection: Boolean
    ) {
        val row = sheet.createRow(rowNum)
        val cellValues = schema.toRow(entity)
        val patterns = schema.columnPatterns

        cellValues.forEachIndexed { index, value ->
            // NOTE: 정의된 패턴 확인 또는 타입별 기본 패턴 적용
            val pattern = if (index < patterns.size) patterns[index] else null
            val effectivePattern = pattern ?: when (value) {
                is LocalDate -> "yyyy-mm-dd"
                is LocalDateTime, is Date, is Calendar -> "yyyy-mm-dd hh:mm:ss"
                else -> null
            }

            row.createCell(index).apply {
                // 스타일 적용
                cellStyle = styleManager.getStyle(effectivePattern)

                when (value) {
                    null -> setCellValue("")
                    is Number -> setCellValue(value.toDouble())
                    is Boolean -> setCellValue(value)
                    is Date -> setCellValue(value)
                    is LocalDate -> setCellValue(value)
                    is LocalDateTime -> setCellValue(value)
                    is Calendar -> setCellValue(value)
                    else -> {
                        val stringValue = value.toString()
                        
                        // NOTE: 수식 인젝션 방지 처리 (문자열 타입에 대해서만 수행)
                        val finalValue = if (preventFormulaInjection && isFormula(stringValue)) {
                            "'$stringValue"
                        } else {
                            stringValue
                        }
                        setCellValue(finalValue)
                    }
                }
            }
        }
    }

    /**
     * 스타일 캐싱 및 적용 관리자
     */
    private class StyleManager(private val workbook: SXSSFWorkbook, private val baseStyle: CellStyle?) {
        private val styleCache = mutableMapOf<String, CellStyle>()

        fun getStyle(pattern: String?): CellStyle? {
            if (pattern == null) return baseStyle
            return styleCache.getOrPut(pattern) {
                workbook.createCellStyle().apply {
                    baseStyle?.let { cloneStyleFrom(it) }
                    val dataFormat = workbook.createDataFormat()
                    setDataFormat(dataFormat.getFormat(pattern))
                }
            }
        }
    }

    /**
     * 엑셀 수식 유도 문자 여부 확인
     * Check if value starts with Excel formula trigger characters
     *
     * NOTE: 탭(\t), 개행(\n, \r) 등 모든 공백 문자를 제거하여 우회 공격을 방지합니다.
     * Removes all whitespace characters (including tabs, newlines) to prevent bypass attacks.
     */
    private fun isFormula(value: String): Boolean {
        if (value.isBlank()) return false
        // NOTE: trim() 대신 trimStart { it.isWhitespace() }를 사용하여
        // 탭, 개행 등 모든 공백 문자를 앞에서 제거합니다.
        // Use trimStart with isWhitespace() instead of trim() to remove
        // all leading whitespace characters including tabs and newlines.
        val trimmed = value.trimStart { it.isWhitespace() }
        if (trimmed.isEmpty()) return false

        val firstChar = trimmed[0]
        return firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
    }
}
