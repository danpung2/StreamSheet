package com.streamsheet.core.cancel

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [CancellationToken]을 생성하고 취소를 트리거하는 소스.
 * Source for creating and triggering a [CancellationToken].
 */
class CancellationTokenSource : AutoCloseable {
    private val cancelled = AtomicBoolean(false)
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    val token: CancellationToken = object : CancellationToken {
        override val isCancellationRequested: Boolean
            get() = cancelled.get()

        override fun throwIfCancellationRequested() {
            if (isCancellationRequested) {
                throw ExportCancelledException()
            }
        }

        override fun onCancel(action: () -> Unit): CancellationRegistration {
            if (isCancellationRequested) {
                action()
                return CancellationRegistration.NOOP
            }

            callbacks.add(action)
            return CancellationRegistration {
                callbacks.remove(action)
            }
        }
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        callbacks.toList().forEach { it.invoke() }
        callbacks.clear()
    }

    override fun close() {
        cancel()
    }
}
