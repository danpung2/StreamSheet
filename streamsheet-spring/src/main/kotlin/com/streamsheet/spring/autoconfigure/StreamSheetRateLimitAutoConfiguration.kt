package com.streamsheet.spring.autoconfigure

import com.streamsheet.spring.web.StreamSheetRateLimitingFilter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter

@AutoConfiguration(after = [StreamSheetAutoConfiguration::class])
@ConditionalOnWebApplication
@ConditionalOnClass(OncePerRequestFilter::class)
@ConditionalOnProperty(prefix = "streamsheet.rate-limit", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(StreamSheetRateLimitProperties::class)
class StreamSheetRateLimitAutoConfiguration {

    @Bean
    fun streamSheetRateLimitingFilter(
            streamSheetProperties: StreamSheetProperties,
            rateLimitProperties: StreamSheetRateLimitProperties,
    ): FilterRegistrationBean<StreamSheetRateLimitingFilter> {
        val filter = StreamSheetRateLimitingFilter(streamSheetProperties, rateLimitProperties)
        return FilterRegistrationBean<StreamSheetRateLimitingFilter>().apply {
            this.filter = filter
            // 최우선 순위보다 약간 뒤에 실행하여 필수 필터(예: 인코딩)가 먼저 실행되도록 보장
            // Ensure essential filters (e.g., encoding) run first by running slightly after highest
            // precedence
            this.order = Ordered.HIGHEST_PRECEDENCE + 10
        }
    }
}
