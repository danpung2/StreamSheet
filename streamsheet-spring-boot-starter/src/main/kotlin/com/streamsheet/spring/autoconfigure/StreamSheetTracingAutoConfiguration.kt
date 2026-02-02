package com.streamsheet.spring.autoconfigure

import com.streamsheet.spring.tracing.MicrometerObservationStreamSheetTracer
import com.streamsheet.spring.tracing.StreamSheetTracer
import io.micrometer.observation.ObservationRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@AutoConfiguration(before = [StreamSheetAutoConfiguration::class])
@ConditionalOnClass(ObservationRegistry::class)
@ConditionalOnProperty(prefix = "streamsheet.tracing", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(StreamSheetTracingProperties::class)
class StreamSheetTracingAutoConfiguration {

    @Bean
    @Primary
    fun streamSheetTracer(observationRegistry: ObservationRegistry): StreamSheetTracer {
        return MicrometerObservationStreamSheetTracer(observationRegistry)
    }
}
