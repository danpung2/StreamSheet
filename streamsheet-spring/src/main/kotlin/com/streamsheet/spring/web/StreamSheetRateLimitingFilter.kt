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
        val remoteAddr = request.remoteAddr?.takeIf { it.isNotBlank() } ?: "unknown"
        return when (rateLimitProperties.key) {
            StreamSheetRateLimitProperties.RateLimitKey.PRINCIPAL_OR_IP -> request.userPrincipal?.name ?: remoteAddr
            StreamSheetRateLimitProperties.RateLimitKey.IP -> remoteAddr
            StreamSheetRateLimitProperties.RateLimitKey.PRINCIPAL -> request.userPrincipal?.name
        }
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
