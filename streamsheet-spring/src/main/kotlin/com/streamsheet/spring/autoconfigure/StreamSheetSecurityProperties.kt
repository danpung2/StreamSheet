package com.streamsheet.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * StreamSheet 보안 설정 프로퍼티
 * StreamSheet security configuration properties
 *
 * NOTE: application.yml을 통해 보안 동작을 제어합니다.
 * Controls security behavior via application.yml.
 *
 * 사용 예시 (Example usage):
 * ```yaml
 * streamsheet:
 *   security:
 *     enabled: true
 *     download-role: EXPORT_DOWNLOAD
 *     permit-all: false
 * ```
 */
@ConfigurationProperties(prefix = "streamsheet.security")
data class StreamSheetSecurityProperties(
    /**
     * 보안 기능 활성화 여부 (기본값: true)
     * Whether to enable security features (default: true)
     *
     * NOTE: true로 설정하면 Spring Security가 클래스패스에 있어야 합니다.
     * When set to true, Spring Security must be on the classpath.
     */
    val enabled: Boolean = true,

    /**
     * 다운로드 엔드포인트에 필요한 역할 (기본값: EXPORT_DOWNLOAD)
     * Role required for download endpoint (default: EXPORT_DOWNLOAD)
     *
     * NOTE: hasRole() 또는 hasAuthority()에서 사용됩니다.
     * 역할 접두사(ROLE_)는 자동으로 처리됩니다.
     * Used in hasRole() or hasAuthority(). The ROLE_ prefix is handled automatically.
     */
    val downloadRole: String = "EXPORT_DOWNLOAD",

    /**
     * 인증된 사용자에게 접근 허용 여부 (기본값: true)
     * Whether to permit access to authenticated users (default: true)
     *
     * NOTE: true로 설정하면 인증만 필요하고 특정 역할은 필요하지 않습니다.
     * When true, only authentication is required, no specific role needed.
     */
    val permitAuthenticated: Boolean = true,

    /**
     * 인증 없이 모든 접근 허용 (기본값: false)
     * Permit all access without authentication (default: false)
     *
     * WARNING: 프로덕션 환경에서는 사용하지 마세요.
     * DO NOT use in production environment.
     */
    val permitAll: Boolean = false
)
