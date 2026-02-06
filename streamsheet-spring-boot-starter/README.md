## streamsheet-spring-boot-starter

Spring Boot integration for StreamSheet (auto-configuration, async export, storage, REST download endpoint).

See also: `../README.md`

### Installation
```gradle
dependencies {
  implementation("io.github.danpung2:streamsheet-spring-boot-starter:1.0.0")
}
```

### Key properties
- `streamsheet.storage.type`: `LOCAL` (default) | `S3` | `GCS`
- `streamsheet.security.enabled`: (default true) download endpoint auth
- `streamsheet.job-store`: `IN_MEMORY` (default) | `REDIS`
- `streamsheet.enable-metrics`: enable Micrometer-based metrics (requires `MeterRegistry` bean)
- `streamsheet.retry-enabled`: enable retry wrapper around `FileStorage`

### Security Configuration
By default, `streamsheet.security.enabled` is `true`. If Spring Security is present, the download endpoint (`/api/v1/streamsheet/download/**`) is protected.

You can customize the security configuration:

```kotlin
@Bean
fun streamSheetFilterChain(http: HttpSecurity): SecurityFilterChain {
    http.securityMatcher("/api/v1/streamsheet/download/**")
        .authorizeHttpRequests { it.anyRequest().authenticated() }
        .httpBasic(Customizer.withDefaults())
    return http.build()
}
```

### Testing
```bash
./gradlew :streamsheet-spring-boot-starter:test
```

### License
Apache License 2.0. See `../LICENSE` and `../NOTICE`.
