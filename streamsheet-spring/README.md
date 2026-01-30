## streamsheet-spring

Spring Boot integration for StreamSheet (auto-configuration, async export, storage, REST download endpoint).

### Installation
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-spring:<version>")
}
```

### Key properties
- `streamsheet.storage.type`: `LOCAL` (default) | `S3` | `GCS`
- `streamsheet.security.enabled`: (default true) download endpoint auth
- `streamsheet.job-store`: `IN_MEMORY` (default) | `REDIS`
- `streamsheet.enable-metrics`: enable Micrometer-based metrics (requires `MeterRegistry` bean)
- `streamsheet.retry-enabled`: enable retry wrapper around `FileStorage`

### Testing
```bash
./gradlew :streamsheet-spring:test
```
