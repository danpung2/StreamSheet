package com.streamsheet.core.schema

/**
 * 엑셀 스키마 정의 인터페이스
 * Excel schema definition interface
 *
 * NOTE: 이 인터페이스를 구현하여 어떤 데이터 타입이든 엑셀로 변환할 수 있습니다.
 * Implement this interface to convert any data type to Excel.
 *
 * @param T 엑셀로 변환할 엔티티 타입 / Entity type to convert to Excel
 */
interface ExcelSchema<T> {
    /**
     * 시트 이름 / Sheet name
     */
    val sheetName: String

    /**
     * 헤더 목록 (컬럼명) / List of headers (column names)
     */
    val headers: List<String>

    /**
     * 컬럼 너비 목록 (문자 단위) / List of column widths (in characters)
     * NOTE: POI에서는 256을 곱해서 사용합니다.
     * In POI, multiply by 256.
     */
    val columnWidths: List<Int>
    
    /**
     * 컬럼별 데이터 포맷 패턴 (Optional)
     * Data format pattern for each column
     */
    val columnPatterns: List<String?> get() = emptyList()

    /**
     * 엔티티를 셀 값 리스트로 변환
     * Convert entity to list of cell values
     *
     * @param entity 변환할 엔티티 / Entity to convert
     * @return 셀 값 리스트 (순서는 headers와 동일해야 함) / List of cell values (order must match headers)
     */
    fun toRow(entity: T): List<Any?>

    /**
     * 기본 컬럼 너비 (헤더 수에 맞게 자동 생성)
     * Default column widths (auto-generated to match header count)
     */
    companion object {
        const val DEFAULT_COLUMN_WIDTH = 15
    }
}
