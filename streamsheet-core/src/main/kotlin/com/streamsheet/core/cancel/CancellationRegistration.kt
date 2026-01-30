package com.streamsheet.core.cancel

/**
 * 취소 콜백 등록 핸들.
 * Represents a cancellation callback registration.
 */
fun interface CancellationRegistration : AutoCloseable {
    override fun close()

    companion object {
        val NOOP: CancellationRegistration = CancellationRegistration { }
    }
}
