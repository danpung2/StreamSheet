package com.streamsheet.core.exporter

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.ExcelSchema
import java.io.OutputStream

/**
 * Java/Kotlin 친화적 exporter 파사드.
 * Java/Kotlin friendly exporter facade.
 */
object ExcelExporters {
    @JvmStatic
    fun <T> export(
        exporter: ExcelExporter,
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        output: OutputStream,
        config: ExcelExportConfig = ExcelExportConfig.DEFAULT,
        options: ExportOptions = ExportOptions.DEFAULT,
    ) {
        if (exporter is ExcelExporterWithOptions) {
            exporter.export(schema, dataSource, output, config, options)
            return
        }
        exporter.export(schema, dataSource, output, config)
    }

    @JvmStatic
    fun <T> export(
        exporter: ExcelExporter,
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        filter: Map<String, Any>,
        output: OutputStream,
        config: ExcelExportConfig = ExcelExportConfig.DEFAULT,
        options: ExportOptions = ExportOptions.DEFAULT,
    ) {
        if (exporter is ExcelExporterWithOptions) {
            exporter.export(schema, dataSource, filter, output, config, options)
            return
        }
        exporter.export(schema, dataSource, filter, output, config)
    }
}
