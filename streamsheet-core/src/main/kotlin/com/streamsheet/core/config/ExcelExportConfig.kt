package com.streamsheet.core.config

/**
 * 엑셀 내보내기 설정
 * Excel export configuration
 *
 * NOTE: SXSSF 메모리 관리 및 스타일링 옵션을 정의합니다.
 * Defines SXSSF memory management and styling options.
 */
data class ExcelExportConfig(
    /**
     * 메모리에 유지할 최대 행 수 / Maximum rows to keep in memory
     * NOTE: 이 수를 초과하면 디스크로 플러시됩니다.
     * Rows exceeding this are flushed to disk.
     */
    val rowAccessWindowSize: Int = DEFAULT_ROW_ACCESS_WINDOW_SIZE,

    /**
     * 주기적 플러시 트리거 행 수 / Rows per flush trigger
     * NOTE: 이 수마다 명시적으로 플러시하여 메모리를 해제합니다.
     * Explicitly flush at this interval to release memory.
     */
    val flushBatchSize: Int = DEFAULT_FLUSH_BATCH_SIZE,

    /**
     * 임시 파일 압축 여부 / Whether to compress temp files
     * NOTE: true로 설정하면 디스크 사용량이 줄어들지만 CPU 사용량이 증가합니다.
     * Setting to true reduces disk usage but increases CPU usage.
     */
    val compressTempFiles: Boolean = true,

    /**
     * 헤더 스타일 적용 여부 / Whether to apply header styling
     */
    val applyHeaderStyle: Boolean = true,

    /**
     * 데이터 셀 테두리 적용 여부 / Whether to apply data cell borders
     */
    val applyDataBorders: Boolean = true,

    /**
     * 엑셀 수식 인젝션 방지 여부 / Whether to prevent Excel formula injection
     * NOTE: true인 경우 '=', '+', '-', '@'로 시작하는 데이터 앞에 싱글 쿼트(')를 붙입니다.
     * If true, prepends a single quote (') to data starting with '=', '+', '-', '@'.
     */
    val preventFormulaInjection: Boolean = true,

    /**
     * 성능 지표 로깅 여부 / Whether to enable metrics logging
     */
    val enableMetrics: Boolean = false,

    /**
     * 최대 행 추출 제한 / Maximum row extraction limit
     * NOTE: null이면 제한이 없으며, 설정 시 해당 행 수를 초과하면 추출이 중단됩니다.
     */
    val maxRows: Int? = null
) {
    init {
        require(rowAccessWindowSize > 0) { "rowAccessWindowSize must be positive" }
        require(flushBatchSize > 0) { "flushBatchSize must be positive" }
        maxRows?.let {
            require(it > 0) { "maxRows must be positive" }
        }
    }

    companion object {
        const val DEFAULT_ROW_ACCESS_WINDOW_SIZE = 100
        const val DEFAULT_FLUSH_BATCH_SIZE = 1000

        /**
         * 기본 설정 / Default configuration
         */
        val DEFAULT = ExcelExportConfig()

        /**
         * 고성능 설정 (낮은 메모리 사용) / High performance (low memory usage)
         */
        val HIGH_PERFORMANCE = ExcelExportConfig(
            rowAccessWindowSize = 50,
            flushBatchSize = 500,
            compressTempFiles = true,
            applyHeaderStyle = false,
            applyDataBorders = false,
            preventFormulaInjection = true
        )

        /**
         * 보안 강화 설정 / Security hardened configuration
         */
        val SECURITY_HARDENED = ExcelExportConfig(
            preventFormulaInjection = true,
            compressTempFiles = true
        )

        /**
         * 고품질 설정 (스타일링 포함) / High quality (with styling)
         */
        val HIGH_QUALITY = ExcelExportConfig(
            rowAccessWindowSize = 200,
            flushBatchSize = 2000,
            compressTempFiles = true,
            applyHeaderStyle = true,
            applyDataBorders = true
        )
    }
}
