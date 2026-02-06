## streamsheet-spring-boot-starter

StreamSheet Spring Boot 연동 모듈입니다(자동 설정, 비동기 내보내기, 스토리지, 다운로드 REST 엔드포인트).

참고: `../README.ko.md`

### 설치
```gradle
dependencies {
  implementation("io.github.danpung2:streamsheet-spring-boot-starter:1.0.0")
}
```

### 주요 설정
- `streamsheet.storage.type`: `LOCAL`(기본) | `S3` | `GCS`
- `streamsheet.security.enabled`: (기본 true) 다운로드 엔드포인트 인증
- `streamsheet.job-store`: `IN_MEMORY`(기본) | `REDIS`
- `streamsheet.enable-metrics`: Micrometer 메트릭 활성화 (`MeterRegistry` Bean 필요)
- `streamsheet.retry-enabled`: `FileStorage` 작업 재시도 래퍼 활성화

### 보안 설정 (Security Configuration)
기본적으로 `streamsheet.security.enabled`는 `true`입니다. Spring Security가 존재하면 다운로드 엔드포인트(`/api/v1/streamsheet/download/**`)는 보호됩니다.

다음과 같이 보안 설정을 커스터마이징할 수 있습니다:

```kotlin
@Bean
fun streamSheetFilterChain(http: HttpSecurity): SecurityFilterChain {
    http.securityMatcher("/api/v1/streamsheet/download/**")
        .authorizeHttpRequests { it.anyRequest().authenticated() }
        .httpBasic(Customizer.withDefaults())
    return http.build()
}
```

### 테스트
```bash
./gradlew :streamsheet-spring-boot-starter:test
```

### 라이선스
Apache License 2.0. `../LICENSE`, `../NOTICE`를 참고하세요.
