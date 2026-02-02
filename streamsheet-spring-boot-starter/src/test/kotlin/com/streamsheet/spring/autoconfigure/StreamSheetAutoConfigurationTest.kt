package com.streamsheet.spring.autoconfigure

import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.AsyncExportWorker
import com.streamsheet.spring.async.JobManager
import com.streamsheet.spring.storage.FileStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.HashOperations
import java.util.function.Supplier

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
            assertThat(context).hasSingleBean(AsyncExportWorker::class.java)
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

    @Test
    @DisplayName("streamsheet.storage.type=S3 설정 시 S3 관련 Bean들이 등록되어야 한다")
    fun `should register S3 beans when storage type is S3`() {
        contextRunner
            .withPropertyValues(
                "streamsheet.storage.type=S3",
                "streamsheet.storage.s3.bucket=test-bucket",
                "streamsheet.storage.s3.region=ap-northeast-2",
                "streamsheet.storage.s3.access-key=test",
                "streamsheet.storage.s3.secret-key=test"
            )
            .run { context ->
                assertThat(context).hasSingleBean(com.streamsheet.spring.storage.S3FileStorage::class.java)
                assertThat(context).hasSingleBean(software.amazon.awssdk.services.s3.S3Client::class.java)
            }
    }

    @Test
    @DisplayName("streamsheet.storage.type=GCS 설정 시 GCS 관련 Bean들이 등록되어야 한다")
    fun `should register GCS beans when storage type is GCS`() {
        contextRunner
            .withPropertyValues(
                "streamsheet.storage.type=GCS",
                "streamsheet.storage.gcs.bucket=test-bucket"
            )
            .run { context ->
                assertThat(context).hasSingleBean(com.streamsheet.spring.storage.GcsFileStorage::class.java)
                assertThat(context).hasSingleBean(com.google.cloud.storage.Storage::class.java)
            }
    }

    @Test
    @DisplayName("streamsheet.job-store=REDIS 설정 시 RedisJobManager가 등록되어야 한다")
    fun `should register RedisJobManager when job store is REDIS`() {
        contextRunner
            .withPropertyValues(
                "streamsheet.job-store=REDIS"
            )
            .withBean(
                StringRedisTemplate::class.java,
                Supplier {
                    val template = mock<StringRedisTemplate>()
                    val hashOps = mock<HashOperations<String, String, String>>()
                    whenever(template.opsForHash<String, String>()).thenReturn(hashOps)
                    template
                }
            )
            .run { context ->
                assertThat(context).hasSingleBean(JobManager::class.java)
                assertThat(context.getBean(JobManager::class.java))
                    .isInstanceOf(com.streamsheet.spring.async.RedisJobManager::class.java)
            }
    }

    @Test
    @DisplayName("streamsheet.enable-metrics=true 설정 시 MicrometerStreamSheetMetrics가 등록되어야 한다")
    fun `should register MicrometerStreamSheetMetrics when enableMetrics is true`() {
        contextRunner
            .withPropertyValues(
                "streamsheet.enable-metrics=true"
            )
            .withBean(MeterRegistry::class.java, Supplier { SimpleMeterRegistry() })
            .run { context ->
                val config = context.getBean(com.streamsheet.core.config.ExcelExportConfig::class.java)
                assertThat(config.enableMetrics).isTrue()
                assertThat(config.metrics)
                    .isInstanceOf(com.streamsheet.spring.metrics.MicrometerStreamSheetMetrics::class.java)
            }
    }
    @Test
    @DisplayName("S3 엔드포인트가 화이트리스트에 없으면 애플리케이션 시작이 실패해야 한다")
    fun `should fail to start when S3 endpoint is not in whitelist`() {
        contextRunner
            .withPropertyValues(
                "streamsheet.storage.type=S3",
                "streamsheet.storage.s3.bucket=test-bucket",
                "streamsheet.storage.s3.endpoint=http://evil-site.com",
                "streamsheet.storage.s3.allowed-endpoints=my-minio.internal,s3.trusted.com",
                "streamsheet.storage.s3.region=us-east-1",
                "streamsheet.storage.s3.access-key=test",
                "streamsheet.storage.s3.secret-key=test"
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasRootCauseInstanceOf(IllegalArgumentException::class.java)
                    .hasMessageContaining("S3 endpoint host 'evil-site.com' is not in the allowed list")
            }
    }

    @Test
    @DisplayName("S3 엔드포인트가 화이트리스트에 있으면 애플리케이션 시작이 성공해야 한다")
    fun `should succeed when S3 endpoint is in whitelist`() {
        contextRunner
            .withPropertyValues(
                "streamsheet.storage.type=S3",
                "streamsheet.storage.s3.bucket=test-bucket",
                "streamsheet.storage.s3.endpoint=http://my-minio.internal:9000",
                "streamsheet.storage.s3.allowed-endpoints=my-minio.internal",
                "streamsheet.storage.s3.region=us-east-1",
                "streamsheet.storage.s3.access-key=test",
                "streamsheet.storage.s3.secret-key=test"
            )
            .run { context ->
                assertThat(context).hasSingleBean(software.amazon.awssdk.services.s3.S3Client::class.java)
            }
    }
}
