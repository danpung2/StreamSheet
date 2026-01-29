package com.streamsheet.spring.autoconfigure

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.exporter.SxssfExcelExporter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * StreamSheet 자동 설정
 * StreamSheet auto-configuration
 *
 * NOTE: StreamSheetProperties가 존재하면 자동으로 설정을 로드합니다.
 * Automatically loads configuration if StreamSheetProperties exists.
 */
@AutoConfiguration
@EnableConfigurationProperties(StreamSheetProperties::class)
@ConditionalOnClass(SxssfExcelExporter::class)
class StreamSheetAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun excelExportConfig(properties: StreamSheetProperties): ExcelExportConfig {
        return properties.toExcelExportConfig()
    }

    @Bean
    @ConditionalOnMissingBean
    fun excelExporter(config: ExcelExportConfig): ExcelExporter {
        // 현재는 SxssfExcelExporter를 기본 구현체로 사용
        return SxssfExcelExporter()
    }
}
