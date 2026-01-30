package com.streamsheet.spring.autoconfigure

import com.streamsheet.spring.tracing.MicrometerObservationStreamSheetTracer
import com.streamsheet.spring.tracing.StreamSheetTracer
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier

@DisplayName("StreamSheetTracingAutoConfiguration 테스트")
class StreamSheetTracingAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                StreamSheetTracingAutoConfiguration::class.java,
                StreamSheetAutoConfiguration::class.java,
            )
        )

    @Test
    @DisplayName("streamsheet.tracing.enabled=true + ObservationRegistry 존재 시 tracer가 Micrometer 구현으로 주입되어야 한다")
    fun `should provide micrometer tracer when enabled`() {
        contextRunner
            .withPropertyValues("streamsheet.tracing.enabled=true")
            .withBean(ObservationRegistry::class.java, Supplier { ObservationRegistry.create() })
            .run { context ->
                val tracer = context.getBean(StreamSheetTracer::class.java)
                assertThat(tracer).isInstanceOf(MicrometerObservationStreamSheetTracer::class.java)
            }
    }
}
