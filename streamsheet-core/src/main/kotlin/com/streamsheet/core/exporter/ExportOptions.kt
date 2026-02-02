package com.streamsheet.core.exporter

import com.streamsheet.core.cancel.CancellationToken
import com.streamsheet.core.progress.ExportProgressListener

/**
 * 엑셀 내보내기 수행 시 사용할 수 있는 추가 옵션
 * Additional options available when performing an Excel export.
 *
 * @param cancellationToken
 *      내보내기 작업을 외부에서 취소할 수 있는 토큰입니다.
 *      기본값은 취소 불가능한 [CancellationToken.NONE]입니다.
 *      Token allowing external cancellation of the export task.
 *      Defaults to [CancellationToken.NONE] which cannot be cancelled.
 * @param progressListener
 *      내보내기 진행 상황(청크 단위, 행 단위)을 수신하는 리스너입니다.
 *      기본값은 아무 동작도 하지 않는 [ExportProgressListener.NOOP]입니다.
 *      Listener receiving export progress (chunk-wise, row-wise).
 *      Defaults to [ExportProgressListener.NOOP] which limits overhead.
 */
data class ExportOptions(
    val cancellationToken: CancellationToken = CancellationToken.NONE,
    val progressListener: ExportProgressListener = ExportProgressListener.NOOP,
) {
    companion object {
        /**
         * 기본 옵션 인스턴스 (취소 불가, 진행률 추적 안 함)
         * Default options instance (Non-cancellable, No progress tracking)
         */
        val DEFAULT = ExportOptions()
    }
}
