package com.streamsheet.core.cancel

/**
 * 내보내기(export)가 취소되었을 때 발생하는 예외.
 * Thrown when an export is cancelled.
 */
class ExportCancelledException(
    message: String = "Export cancelled",
    cause: Throwable? = null
) : RuntimeException(message, cause)
