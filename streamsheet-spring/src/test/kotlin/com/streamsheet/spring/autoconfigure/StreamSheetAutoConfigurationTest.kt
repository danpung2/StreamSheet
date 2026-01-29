package com.streamsheet.spring.autoconfigure

import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.JobManager
import com.streamsheet.spring.storage.FileStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@DisplayName("StreamSheetAutoConfiguration 테스트")
class StreamSheetAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(StreamSheetAutoConfiguration::class.java))

    @Test
    @DisplayName("SxssfExcelExporter 클래스가 존재하면 기본 Bean들이 모두 등록되어야 한다")
    fun `should register default beans when SxssfExcelExporter is present`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(StreamSheetProperties::class.java)
            assertThat(context).hasSingleBean(SxssfExcelExporter::class.java)
            assertThat(context).hasSingleBean(JobManager::class.java)
            assertThat(context).hasSingleBean(FileStorage::class.java)
            assertThat(context).hasSingleBean(AsyncExportService::class.java)
        }
    }

    @Test
    @DisplayName("사용자가 Bean을 이미 정의했으면 자동 설정 Bean은 등록되지 않아야 한다")
    fun `should back off if user defines beans`() {
        contextRunner
            .withBean("customJobManager", JobManager::class.java, { 
                object : JobManager {
                    override fun createJob() = "custom"
                    override fun getJob(jobId: String) = null
                    override fun updateStatus(jobId: String, status: com.streamsheet.spring.async.JobStatus, resultUri: java.net.URI?, errorMessage: String?) {}
                }
            })
            .run { context ->
                assertThat(context).hasSingleBean(JobManager::class.java)
                assertThat(context).getBean("customJobManager").isInstanceOf(JobManager::class.java)
                // NOTE: @ConditionalOnMissingBean은 타입 기반으로 동작하므로, 이름이 달라도 사용자 정의 Bean이 있으면 자동 설정은 건너뜀
                // NOTE: @ConditionalOnMissingBean works by type, so auto-config backs off if user bean exists, even with different name
                
                // Let's verify instance type
                val bean = context.getBean(JobManager::class.java)
                assertThat(bean.createJob()).isEqualTo("custom")
            }
    }
}
