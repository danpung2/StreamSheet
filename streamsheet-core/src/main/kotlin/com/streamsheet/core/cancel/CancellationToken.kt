package com.streamsheet.core.cancel

/**
 * 협력적(cooperative) 취소 토큰.
 * Cooperative cancellation token.
 */
interface CancellationToken {
    val isCancellationRequested: Boolean

    fun throwIfCancellationRequested()

    fun onCancel(action: () -> Unit): CancellationRegistration

    companion object {
        val NONE: CancellationToken = object : CancellationToken {
            override val isCancellationRequested: Boolean = false
            override fun throwIfCancellationRequested() {}
            override fun onCancel(action: () -> Unit): CancellationRegistration = CancellationRegistration.NOOP
        }
    }
}
