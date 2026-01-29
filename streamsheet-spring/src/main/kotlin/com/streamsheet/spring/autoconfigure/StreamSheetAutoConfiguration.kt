package com.streamsheet.spring.autoconfigure

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.exporter.SxssfExcelExporter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.InMemoryJobManager
import com.streamsheet.spring.async.JobManager
import com.streamsheet.spring.storage.FileStorage
import com.streamsheet.spring.storage.LocalFileStorage
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.nio.file.Path

/**
 * StreamSheet 자동 설정
 * StreamSheet auto-configuration
 *
 * NOTE: StreamSheetProperties가 존재하면 자동으로 설정을 로드합니다.
 * Automatically loads configuration if StreamSheetProperties exists.
 */
@AutoConfiguration
@EnableAsync
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


    @Bean
    @ConditionalOnMissingBean
    fun jobManager(): JobManager {
        return InMemoryJobManager()
    }

    @Bean
    @ConditionalOnMissingBean
    fun fileStorage(): FileStorage {
        // 기본값: 임시 디렉토리 사용 / Default: Use temporary directory
        return LocalFileStorage(Path.of(System.getProperty("java.io.tmpdir")))
    }

    @Bean
    @ConditionalOnMissingBean
    fun asyncExportService(
        jobManager: JobManager,
        fileStorage: FileStorage,
        excelExporter: ExcelExporter,
        config: ExcelExportConfig
    ): AsyncExportService {
        return AsyncExportService(jobManager, fileStorage, excelExporter, config)
    }
}
