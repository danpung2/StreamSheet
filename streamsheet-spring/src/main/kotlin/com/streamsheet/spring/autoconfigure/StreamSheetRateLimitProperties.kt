package com.streamsheet.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Rate Limiting 설정
 *
 * @property enabled Rate Limiting 활성화 여부
 * @property limit 윈도우 내 최대 요청 수
 * @property windowSeconds Rate Limit 윈도우 크기 (초)
 * @property key Rate Limit 키 결정 방식
 * @property maxKeys 캐시할 최대 키 수
 * @property trustProxy 프록시 헤더(X-Forwarded-For) 신뢰 여부
 * @property trustedProxies 신뢰할 프록시 IP 목록 (비어있으면 모든 프록시 신뢰)
 * @property forwardedHeader 클라이언트 IP를 추출할 헤더 이름
 */
@ConfigurationProperties(prefix = "streamsheet.rate-limit")
data class StreamSheetRateLimitProperties(
    val enabled: Boolean = false,
    val limit: Long = 60,
    val windowSeconds: Long = 60,
    val key: RateLimitKey = RateLimitKey.PRINCIPAL_OR_IP,
    val maxKeys: Long = 10_000,
    /**
     * 프록시 헤더(X-Forwarded-For) 신뢰 여부
     * true: X-Forwarded-For 헤더에서 클라이언트 IP 추출
     * false: request.remoteAddr 사용 (기본값)
     */
    val trustProxy: Boolean = false,
    /**
     * 신뢰할 프록시 IP 목록
     * 비어있으면 모든 프록시 신뢰 (trustProxy=true일 때)
     * 설정 시 해당 IP에서 온 요청만 X-Forwarded-For 파싱
     */
    val trustedProxies: List<String> = emptyList(),
    /**
     * 클라이언트 IP를 추출할 헤더 이름
     * 기본값: X-Forwarded-For
     * AWS ALB: X-Forwarded-For
     * Cloudflare: CF-Connecting-IP
     */
    val forwardedHeader: String = "X-Forwarded-For",
) {
    enum class RateLimitKey {
        PRINCIPAL_OR_IP,
        IP,
        PRINCIPAL,
    }
}
