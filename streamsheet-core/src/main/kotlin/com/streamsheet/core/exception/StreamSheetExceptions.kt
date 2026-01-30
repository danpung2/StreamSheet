package com.streamsheet.core.exception

/**
 * StreamSheet SDK 기본 예외 클래스
 * Base exception class for StreamSheet SDK
 *
 * NOTE: 모든 StreamSheet 관련 예외의 부모 클래스입니다.
 * 이 클래스를 catch하면 SDK에서 발생하는 모든 예외를 처리할 수 있습니다.
 * This is the parent class for all StreamSheet-related exceptions.
 * Catching this class will handle all exceptions from the SDK.
 */
open class StreamSheetException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Excel 내보내기 중 발생하는 예외
 * Exception thrown during Excel export
 *
 * @param message 오류 메시지 / Error message
 * @param rowNumber 오류 발생 행 번호 (선택) / Row number where error occurred (optional)
 * @param cause 원인 예외 / Cause exception
 */
class ExportException(
    message: String,
    val rowNumber: Int? = null,
    cause: Throwable? = null
) : StreamSheetException(
    if (rowNumber != null) "Export failed at row $rowNumber: $message" else "Export failed: $message",
    cause
)

/**
 * 데이터 소스 관련 예외
 * Exception related to data source operations
 *
 * @param message 오류 메시지 / Error message
 * @param sourceName 데이터 소스 이름 (선택) / Data source name (optional)
 * @param cause 원인 예외 / Cause exception
 */
class DataSourceException(
    message: String,
    val sourceName: String? = null,
    cause: Throwable? = null
) : StreamSheetException(
    if (sourceName != null) "DataSource [$sourceName] error: $message" else "DataSource error: $message",
    cause
), RetryableException

/**
 * 스키마 관련 예외 (어노테이션 파싱, 컬럼 매핑 등)
 * Exception related to schema operations (annotation parsing, column mapping, etc.)
 *
 * @param message 오류 메시지 / Error message
 * @param entityType 관련 엔티티 타입 (선택) / Related entity type (optional)
 * @param cause 원인 예외 / Cause exception
 */
class SchemaException(
    message: String,
    val entityType: String? = null,
    cause: Throwable? = null
) : StreamSheetException(
    if (entityType != null) "Schema error for [$entityType]: $message" else "Schema error: $message",
    cause
)

/**
 * 설정 관련 예외
 * Exception related to configuration
 *
 * @param message 오류 메시지 / Error message
 * @param propertyName 잘못된 설정 속성 이름 (선택) / Invalid property name (optional)
 * @param cause 원인 예외 / Cause exception
 */
class ConfigurationException(
    message: String,
    val propertyName: String? = null,
    cause: Throwable? = null
) : StreamSheetException(
    if (propertyName != null) "Configuration error [$propertyName]: $message" else "Configuration error: $message",
    cause
)

/**
 * 유효성 검사 실패 예외
 * Exception for validation failures
 *
 * @param message 오류 메시지 / Error message
 * @param fieldName 검증 실패한 필드 (선택) / Field that failed validation (optional)
 * @param invalidValue 잘못된 값 (선택) / Invalid value (optional)
 */
class ValidationException(
    message: String,
    val fieldName: String? = null,
    val invalidValue: Any? = null
) : StreamSheetException(
    buildString {
        append("Validation failed")
        if (fieldName != null) append(" for field [$fieldName]")
        append(": $message")
        if (invalidValue != null) append(" (value: $invalidValue)")
    }
)

/**
 * 셀 값 관련 예외
 * Exception related to cell value operations
 *
 * @param message 오류 메시지 / Error message
 * @param rowNumber 행 번호 / Row number
 * @param columnIndex 열 인덱스 / Column index
 * @param cause 원인 예외 / Cause exception
 */
class CellValueException(
    message: String,
    val rowNumber: Int,
    val columnIndex: Int,
    cause: Throwable? = null
) : StreamSheetException(
    "Cell error at row $rowNumber, column $columnIndex: $message",
    cause
)

/**
 * 리소스 정리 중 발생하는 예외
 * Exception thrown during resource cleanup
 *
 * @param message 오류 메시지 / Error message
 * @param resourceName 리소스 이름 / Resource name
 * @param cause 원인 예외 / Cause exception
 */
class ResourceCleanupException(
    message: String,
    val resourceName: String,
    cause: Throwable? = null
) : StreamSheetException(
    "Failed to cleanup resource [$resourceName]: $message",
    cause
)

/**
 * 재시도 가능한 예외를 나타내는 마커 인터페이스
 * Marker interface for exceptions that can be retried
 *
 * NOTE: 이 인터페이스를 구현한 예외는 일시적인 오류로 인해 발생했으며,
 * 재시도 시 성공할 가능성이 있음을 나타냅니다.
 * Exceptions implementing this interface indicate transient failures
 * that may succeed on retry.
 */
interface RetryableException

/**
 * 스트림 처리 중 발생하는 예외
 * Exception thrown during stream processing
 *
 * @param message 오류 메시지 / Error message
 * @param processedCount 처리된 항목 수 / Number of processed items
 * @param cause 원인 예외 / Cause exception
 */
class StreamProcessingException(
    message: String,
    val processedCount: Long = 0,
    cause: Throwable? = null
) : StreamSheetException(
    "Stream processing failed after $processedCount items: $message",
    cause
), RetryableException
