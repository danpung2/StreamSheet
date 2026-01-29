package com.streamsheet.spring.autoconfigure

import com.streamsheet.core.config.ExcelExportConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * StreamSheet 설정 프로퍼티
 * StreamSheet configuration properties
 *
 * NOTE: application.yml을 통해 엑셀 내보내기 동작을 제어합니다.
 * Controls Excel export behavior via application.yml.
 */
@ConfigurationProperties(prefix = "streamsheet")
data class StreamSheetProperties(
    /**
     * 메모리에 유지할 최대 행 수 (기본값: 100)
     * Maximum rows to keep in memory (default: 100)
     */
    val rowAccessWindowSize: Int = ExcelExportConfig.DEFAULT_ROW_ACCESS_WINDOW_SIZE,

    /**
     * 주기적 플러시 트리거 행 수 (기본값: 1000)
     * Rows per flush trigger (default: 1000)
     */
    val flushBatchSize: Int = ExcelExportConfig.DEFAULT_FLUSH_BATCH_SIZE,

    /**
     * 임시 파일 압축 여부 (기본값: true)
     * Whether to compress temp files (default: true)
     */
    val compressTempFiles: Boolean = true,

    /**
     * 헤더 스타일 적용 여부 (기본값: true)
     * Whether to apply header styling (default: true)
     */
    val applyHeaderStyle: Boolean = true,

    /**
     * 데이터 셀 테두리 적용 여부 (기본값: true)
     * Whether to apply data cell borders (default: true)
     */
    val applyDataBorders: Boolean = true,

    /**
     * 엑셀 수식 인젝션 방지 여부 (기본값: true)
     * Whether to prevent Excel formula injection (default: true)
     */
    val preventFormulaInjection: Boolean = true,

    /**
     * 성능 지표 로깅 여부 (기본값: false)
     * Whether to enable metrics logging (default: false)
     */
    val enableMetrics: Boolean = false,
    
    /**
     * 최대 행 추출 제한 (기본값: null - 무제한)
     * Maximum row extraction limit (default: null - unlimited)
     */
    /**
     * 최대 행 추출 제한 (기본값: null - 무제한)
     * Maximum row extraction limit (default: null - unlimited)
     */
    val maxRows: Int? = null,

    /**
     * 스토리지 설정
     * Storage configuration
     */
    @NestedConfigurationProperty
    val storage: StreamSheetStorageProperties = StreamSheetStorageProperties()
) {
    /**
     * ExcelExportConfig 객체로 변환
     * Convert to ExcelExportConfig object
     */
    fun toExcelExportConfig(): ExcelExportConfig {
        return ExcelExportConfig(
            rowAccessWindowSize = rowAccessWindowSize,
            flushBatchSize = flushBatchSize,
            compressTempFiles = compressTempFiles,
            applyHeaderStyle = applyHeaderStyle,
            applyDataBorders = applyDataBorders,
            preventFormulaInjection = preventFormulaInjection,
            enableMetrics = enableMetrics,
            maxRows = maxRows
        )
    }
}
