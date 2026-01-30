package com.streamsheet.core.exporter

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.ExcelSchema
import java.io.OutputStream

/**
 * 진행률 콜백과 취소 토큰을 지원하는 선택적 Exporter API.
 * Optional exporter API that supports progress reporting and cancellation.
 *
 * NOTE: 이 인터페이스는 additive API이며 기존 [ExcelExporter] 구현과 호환됩니다.
 * This interface is additive; existing [ExcelExporter] implementations remain valid.
 */
interface ExcelExporterWithOptions : ExcelExporter {
    fun <T> export(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        output: OutputStream,
        config: ExcelExportConfig = ExcelExportConfig.DEFAULT,
        options: ExportOptions = ExportOptions.DEFAULT
    )

    fun <T> export(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        filter: Map<String, Any>,
        output: OutputStream,
        config: ExcelExportConfig = ExcelExportConfig.DEFAULT,
        options: ExportOptions = ExportOptions.DEFAULT
    )
}
