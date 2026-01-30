package com.streamsheet.core.progress

fun interface ExportProgressListener {
    fun onProgress(progress: ExportProgress)

    companion object {
        val NOOP: ExportProgressListener = ExportProgressListener { }
    }
}
