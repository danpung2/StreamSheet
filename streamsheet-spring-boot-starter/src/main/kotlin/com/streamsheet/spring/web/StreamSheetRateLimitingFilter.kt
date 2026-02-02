package com.streamsheet.spring.web

import com.github.benmanes.caffeine.cache.Caffeine
import com.streamsheet.spring.autoconfigure.StreamSheetProperties
import com.streamsheet.spring.autoconfigure.StreamSheetRateLimitProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class StreamSheetRateLimitingFilter(
    private val streamSheetProperties: StreamSheetProperties,
    private val rateLimitProperties: StreamSheetRateLimitProperties,
) : OncePerRequestFilter() {

    private val counters = Caffeine.newBuilder()
        .maximumSize(rateLimitProperties.maxKeys)
        .expireAfterWrite(rateLimitProperties.windowSeconds, TimeUnit.SECONDS)
        .build<String, AtomicLong>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val endpoint = normalizeEndpoint(streamSheetProperties.storage.local.endpoint)
        val uri = request.requestURI ?: ""
        if (uri != endpoint && !uri.startsWith("$endpoint/")) {
            filterChain.doFilter(request, response)
            return
        }

        val key = resolveKey(request)
        if (key == null) {
            response.status = 429
            return
        }

        val counter = counters.get(key) { AtomicLong(0) }
        val current = counter.incrementAndGet()
        if (current > rateLimitProperties.limit) {
            response.status = 429
            response.setHeader("Retry-After", rateLimitProperties.windowSeconds.toString())
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveKey(request: HttpServletRequest): String? {
        val clientIp = resolveClientIp(request)
        return when (rateLimitProperties.key) {
            StreamSheetRateLimitProperties.RateLimitKey.PRINCIPAL_OR_IP -> request.userPrincipal?.name ?: clientIp
            StreamSheetRateLimitProperties.RateLimitKey.IP -> clientIp
            StreamSheetRateLimitProperties.RateLimitKey.PRINCIPAL -> request.userPrincipal?.name
        }
    }

    /**
     * 클라이언트 IP 주소를 추출합니다.
     *
     * trustProxy=true인 경우 X-Forwarded-For (또는 설정된 헤더)에서 추출합니다.
     * trustedProxies가 설정된 경우, remoteAddr이 신뢰할 수 있는 프록시인지 확인합니다.
     *
     * X-Forwarded-For 형식: "client, proxy1, proxy2"
     * 첫 번째 IP가 원본 클라이언트 IP입니다.
     */
    internal fun resolveClientIp(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr?.takeIf { it.isNotBlank() } ?: "unknown"

        if (!rateLimitProperties.trustProxy) {
            return remoteAddr
        }

        // trustedProxies가 설정된 경우, remoteAddr이 신뢰할 수 있는 프록시인지 확인
        if (rateLimitProperties.trustedProxies.isNotEmpty()) {
            val normalizedRemoteAddr = normalizeIp(remoteAddr)
            val isTrustedProxy = rateLimitProperties.trustedProxies.any { trusted ->
                normalizeIp(trusted) == normalizedRemoteAddr
            }
            if (!isTrustedProxy) {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "Request from untrusted proxy, using remoteAddr: remoteAddr=$remoteAddr, " +
                        "trustedProxies=${rateLimitProperties.trustedProxies}"
                    )
                }
                return remoteAddr
            }
        }

        // X-Forwarded-For 헤더 파싱
        val forwardedHeader = request.getHeader(rateLimitProperties.forwardedHeader)
        if (forwardedHeader.isNullOrBlank()) {
            if (logger.isDebugEnabled) {
                logger.debug(
                    "No ${rateLimitProperties.forwardedHeader} header found, using remoteAddr: $remoteAddr"
                )
            }
            return remoteAddr
        }

        // X-Forwarded-For: client, proxy1, proxy2
        // 첫 번째 IP가 원본 클라이언트
        val clientIp = forwardedHeader.split(",")
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && isValidIp(it) }

        if (clientIp == null) {
            if (logger.isWarnEnabled) {
                logger.warn(
                    "Invalid ${rateLimitProperties.forwardedHeader} header value, using remoteAddr: " +
                    "header=$forwardedHeader, remoteAddr=$remoteAddr"
                )
            }
            return remoteAddr
        }

        if (logger.isDebugEnabled) {
            logger.debug(
                "Resolved client IP from ${rateLimitProperties.forwardedHeader}: " +
                "clientIp=$clientIp, fullHeader=$forwardedHeader"
            )
        }
        return clientIp
    }

    /**
     * IP 주소 유효성 검증 (IPv4, IPv6)
     */
    private fun isValidIp(ip: String): Boolean {
        // IPv4: 0-255.0-255.0-255.0-255
        val ipv4Regex = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        // IPv6: 간단한 검증 (콜론으로 구분된 헥스)
        val ipv6Regex = Regex("^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$")
        // IPv6 압축 형식 (::)
        val ipv6CompressedRegex = Regex("^::([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4}$|^([0-9a-fA-F]{1,4}:)*::([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{0,4}$")
        // IPv4-mapped IPv6 (::ffff:192.168.0.1)
        val ipv4MappedRegex = Regex("^::ffff:(\\d{1,3}\\.){3}\\d{1,3}$", RegexOption.IGNORE_CASE)

        return ipv4Regex.matches(ip) ||
                ipv6Regex.matches(ip) ||
                ipv6CompressedRegex.matches(ip) ||
                ipv4MappedRegex.matches(ip) ||
                ip == "::1" // localhost IPv6
    }

    /**
     * IP 주소 정규화 (비교용)
     * IPv4-mapped IPv6 주소를 IPv4로 변환
     */
    private fun normalizeIp(ip: String): String {
        // ::ffff:192.168.0.1 -> 192.168.0.1
        val ipv4MappedPrefix = "::ffff:"
        if (ip.lowercase().startsWith(ipv4MappedPrefix)) {
            return ip.substring(ipv4MappedPrefix.length)
        }
        return ip.lowercase()
    }

    private fun normalizeEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()
        val normalized = when {
            trimmed.isEmpty() -> "/api/streamsheet/download"
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }
        return normalized.trimEnd('/')
    }
}
