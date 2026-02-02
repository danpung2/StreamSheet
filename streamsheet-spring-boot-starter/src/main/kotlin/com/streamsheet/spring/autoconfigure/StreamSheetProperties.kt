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
     * 작업 결과 및 파일 보관 시간 (단위: 시간, 기본값: 24)
     * Retention time for job results and files (Unit: hours, default: 24)
     */
    val retentionHours: Long = 24,

    /**
     * 스토리지 작업 재시도 활성화 여부 (기본값: false)
     * Whether to enable retry for storage operations (default: false)
     */
    val retryEnabled: Boolean = false,

    /**
     * 재시도 최대 시도 횟수 (기본값: 3)
     * Max retry attempts (default: 3)
     */
    val retryMaxAttempts: Int = 3,

    /**
     * 재시도 초기 지연(ms) (기본값: 200)
     * Initial backoff delay in ms (default: 200)
     */
    val retryInitialDelayMs: Long = 200,

    /**
     * 재시도 최대 지연(ms) (기본값: 2000)
     * Max backoff delay in ms (default: 2000)
     */
    val retryMaxDelayMs: Long = 2000,

    /**
     * 재시도 backoff multiplier (기본값: 2.0)
     * Backoff multiplier (default: 2.0)
     */
    val retryMultiplier: Double = 2.0,

    /**
     * 작업 저장소 종류 (기본값: IN_MEMORY)
     * Job store type (default: IN_MEMORY)
     */
    val jobStore: JobStore = JobStore.IN_MEMORY,

    /**
     * Redis JobManager에서 사용하는 키 prefix (기본값: streamsheet:job:v1:)
     * Key prefix for Redis JobManager (default: streamsheet:job:v1:)
     */
    val jobKeyPrefix: String = "streamsheet:job:v1:",

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

enum class JobStore {
    IN_MEMORY,
    REDIS
}
