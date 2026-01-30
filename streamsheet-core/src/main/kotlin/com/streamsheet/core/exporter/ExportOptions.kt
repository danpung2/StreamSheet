package com.streamsheet.core.exporter

import com.streamsheet.core.cancel.CancellationToken
import com.streamsheet.core.progress.ExportProgressListener

data class ExportOptions(
    val cancellationToken: CancellationToken = CancellationToken.NONE,
    val progressListener: ExportProgressListener = ExportProgressListener.NOOP,
) {
    companion object {
        val DEFAULT = ExportOptions()
    }
}
