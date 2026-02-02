package com.streamsheet.spring.web

import com.streamsheet.spring.autoconfigure.StreamSheetProperties
import com.streamsheet.spring.autoconfigure.StreamSheetRateLimitProperties
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@DisplayName("StreamSheetRateLimitingFilter 단위 테스트")
class StreamSheetRateLimitingFilterTest {

    private val defaultProperties = StreamSheetProperties()

    @Nested
    @DisplayName("X-Forwarded-For 파싱 테스트")
    inner class XForwardedForParsingTest {

        @Test
        @DisplayName("trustProxy=false일 때 remoteAddr을 반환해야 한다")
        fun `should return remoteAddr when trustProxy is false`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = false,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "203.0.113.50, 70.41.3.18"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("10.0.0.1")
        }

        @Test
        @DisplayName("trustProxy=true일 때 X-Forwarded-For의 첫 번째 IP를 반환해야 한다")
        fun `should return first IP from X-Forwarded-For when trustProxy is true`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "203.0.113.50, 70.41.3.18, 10.0.0.1"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("203.0.113.50")
        }

        @Test
        @DisplayName("X-Forwarded-For가 없을 때 remoteAddr을 반환해야 한다")
        fun `should return remoteAddr when X-Forwarded-For is missing`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "192.168.1.1",
                xForwardedFor = null
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("192.168.1.1")
        }

        @Test
        @DisplayName("X-Forwarded-For가 빈 문자열일 때 remoteAddr을 반환해야 한다")
        fun `should return remoteAddr when X-Forwarded-For is empty`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "192.168.1.1",
                xForwardedFor = "   "
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("192.168.1.1")
        }

        @Test
        @DisplayName("단일 IP가 포함된 X-Forwarded-For를 파싱해야 한다")
        fun `should parse single IP in X-Forwarded-For`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "203.0.113.50"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("203.0.113.50")
        }
    }

    @Nested
    @DisplayName("신뢰할 수 있는 프록시 테스트")
    inner class TrustedProxiesTest {

        @Test
        @DisplayName("신뢰할 수 있는 프록시에서 온 요청만 X-Forwarded-For를 파싱해야 한다")
        fun `should parse X-Forwarded-For only from trusted proxies`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
                trustedProxies = listOf("10.0.0.1", "10.0.0.2"),
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",  // 신뢰할 수 있는 프록시
                xForwardedFor = "203.0.113.50"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("203.0.113.50")
        }

        @Test
        @DisplayName("신뢰할 수 없는 프록시에서 온 요청은 remoteAddr을 사용해야 한다")
        fun `should use remoteAddr for untrusted proxies`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
                trustedProxies = listOf("10.0.0.1", "10.0.0.2"),
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "192.168.1.100",  // 신뢰할 수 없는 프록시
                xForwardedFor = "spoofed-ip"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("192.168.1.100")
        }

        @Test
        @DisplayName("trustedProxies가 비어있으면 모든 프록시를 신뢰해야 한다")
        fun `should trust all proxies when trustedProxies is empty`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
                trustedProxies = emptyList(),  // 모든 프록시 신뢰
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "192.168.1.100",
                xForwardedFor = "203.0.113.50"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("203.0.113.50")
        }
    }

    @Nested
    @DisplayName("커스텀 헤더 테스트")
    inner class CustomHeaderTest {

        @Test
        @DisplayName("Cloudflare CF-Connecting-IP 헤더를 사용할 수 있어야 한다")
        fun `should use CF-Connecting-IP header for Cloudflare`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
                forwardedHeader = "CF-Connecting-IP",
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mock(HttpServletRequest::class.java).apply {
                `when`(remoteAddr).thenReturn("10.0.0.1")
                `when`(getHeader("CF-Connecting-IP")).thenReturn("203.0.113.50")
                `when`(getHeader("X-Forwarded-For")).thenReturn("should-be-ignored")
            }

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("203.0.113.50")
        }

        @Test
        @DisplayName("X-Real-IP 헤더를 사용할 수 있어야 한다")
        fun `should use X-Real-IP header`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
                forwardedHeader = "X-Real-IP",
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mock(HttpServletRequest::class.java).apply {
                `when`(remoteAddr).thenReturn("10.0.0.1")
                `when`(getHeader("X-Real-IP")).thenReturn("203.0.113.50")
            }

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("203.0.113.50")
        }
    }

    @Nested
    @DisplayName("IP 주소 유효성 검증 테스트")
    inner class IpValidationTest {

        @Test
        @DisplayName("유효하지 않은 IP가 포함된 경우 remoteAddr을 반환해야 한다")
        fun `should return remoteAddr for invalid IP in header`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "invalid-ip-address"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("10.0.0.1")
        }

        @Test
        @DisplayName("IPv6 주소를 올바르게 파싱해야 한다")
        fun `should parse IPv6 address correctly`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
        }

        @Test
        @DisplayName("IPv6 localhost (::1)를 올바르게 파싱해야 한다")
        fun `should parse IPv6 localhost correctly`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "::1"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("::1")
        }

        @Test
        @DisplayName("IPv4-mapped IPv6 주소를 올바르게 파싱해야 한다")
        fun `should parse IPv4-mapped IPv6 address correctly`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "::ffff:192.168.1.1"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("::ffff:192.168.1.1")
        }
    }

    @Nested
    @DisplayName("공백 처리 테스트")
    inner class WhitespaceHandlingTest {

        @Test
        @DisplayName("IP 주소 앞뒤 공백을 제거해야 한다")
        fun `should trim whitespace from IP addresses`() {
            val rateLimitProperties = StreamSheetRateLimitProperties(
                enabled = true,
                trustProxy = true,
            )
            val filter = StreamSheetRateLimitingFilter(defaultProperties, rateLimitProperties)
            val request = mockRequest(
                remoteAddr = "10.0.0.1",
                xForwardedFor = "  203.0.113.50  , 70.41.3.18"
            )

            val clientIp = filter.resolveClientIp(request)

            assertThat(clientIp).isEqualTo("203.0.113.50")
        }
    }

    private fun mockRequest(remoteAddr: String?, xForwardedFor: String?): HttpServletRequest {
        return mock(HttpServletRequest::class.java).apply {
            `when`(this.remoteAddr).thenReturn(remoteAddr)
            `when`(getHeader("X-Forwarded-For")).thenReturn(xForwardedFor)
        }
    }
}
