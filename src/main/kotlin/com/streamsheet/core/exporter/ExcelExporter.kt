package com.streamsheet.core.exporter

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.ExcelSchema
import java.io.OutputStream

/**
 * 엑셀 내보내기 엔진 인터페이스
 * Excel export engine interface
 *
 * NOTE: 이 인터페이스는 스키마와 데이터 소스를 받아 엑셀 파일을 생성합니다.
 * This interface takes a schema and data source to generate an Excel file.
 */
interface ExcelExporter {
    /**
     * 엑셀 파일을 생성하여 출력 스트림에 작성
     * Generate Excel file and write to output stream
     *
     * @param T 엔티티 타입 / Entity type
     * @param schema 엑셀 스키마 정의 / Excel schema definition
     * @param dataSource 데이터 소스 / Data source
     * @param output 출력 스트림 / Output stream
     * @param config 내보내기 설정 (선택) / Export configuration (optional)
     */
    fun <T> export(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        output: OutputStream,
        config: ExcelExportConfig = ExcelExportConfig.DEFAULT
    )

    /**
     * 필터와 함께 엑셀 파일을 생성
     * Generate Excel file with filter
     *
     * @param T 엔티티 타입 / Entity type
     * @param schema 엑셀 스키마 정의 / Excel schema definition
     * @param dataSource 데이터 소스 / Data source
     * @param filter 필터 조건 / Filter conditions
     * @param output 출력 스트림 / Output stream
     * @param config 내보내기 설정 (선택) / Export configuration (optional)
     */
    fun <T> export(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        filter: Map<String, Any>,
        output: OutputStream,
        config: ExcelExportConfig = ExcelExportConfig.DEFAULT
    )
}
