package com.streamsheet.spring.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.util.StringUtils

/**
 * StreamSheet 보안 자동 설정
 * StreamSheet security auto-configuration
 *
 * NOTE: Spring Security가 클래스패스에 있고 streamsheet.security.enabled=true(또는 미설정)인 경우에만 활성화됩니다.
 * Only activated when Spring Security is on the classpath and streamsheet.security.enabled=true (or missing).
 *
 * 사용 예시 (Example usage):
 * ```yaml
 * streamsheet:
 *   security:
 *     enabled: true
 *     download-role: EXPORT_DOWNLOAD
 * ```
 *
 * JWT 또는 OAuth2 인증을 사용하려면 별도의 SecurityFilterChain Bean을 등록하세요.
 * For JWT or OAuth2 authentication, register a separate SecurityFilterChain bean.
 */
@AutoConfiguration(
    before = [SecurityAutoConfiguration::class],
    after = [StreamSheetAutoConfiguration::class]
)
@ConditionalOnClass(name = ["org.springframework.security.config.annotation.web.configuration.EnableWebSecurity"])
@ConditionalOnProperty(prefix = "streamsheet.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication
@EnableConfigurationProperties(StreamSheetSecurityProperties::class)
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class StreamSheetSecurityAutoConfiguration {

    /**
     * StreamSheet 엔드포인트에 대한 기본 보안 필터 체인
     * Default security filter chain for StreamSheet endpoints
     *
     * NOTE: 이 Bean은 기본적인 역할 기반 접근 제어만 제공합니다.
     * 커스텀 인증(JWT, OAuth2 등)이 필요한 경우 이 Bean을 오버라이드하세요.
     * This bean only provides basic role-based access control.
     * Override this bean if custom authentication (JWT, OAuth2, etc.) is needed.
     */
    @Bean
    @Order(SecurityProperties.BASIC_AUTH_ORDER - 10)
    fun streamSheetSecurityFilterChain(
        http: HttpSecurity,
        streamSheetProperties: StreamSheetProperties,
        properties: StreamSheetSecurityProperties,
    ): SecurityFilterChain {
        val endpoint = normalizeEndpoint(streamSheetProperties.storage.local.endpoint)
        val endpointPattern = "${endpoint}/**"

        http
            .securityMatcher(endpointPattern)
            .authorizeHttpRequests { authorize ->
                when {
                    properties.permitAll -> {
                        authorize.anyRequest().permitAll()
                    }
                    properties.permitAuthenticated -> {
                        authorize.anyRequest().authenticated()
                    }
                    else -> {
                        authorize.anyRequest().hasRole(properties.downloadRole)
                    }
                }
            }
            .csrf { csrf ->
                // NOTE: REST API이므로 CSRF 보호 비활성화
                // Disable CSRF protection for REST API
                csrf.disable()
            }

        return http.build()
    }

    private fun normalizeEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()

        val normalized = when {
            trimmed.isEmpty() -> "/api/streamsheet/download"
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }

        return StringUtils.trimTrailingCharacter(normalized, '/')
    }
}
