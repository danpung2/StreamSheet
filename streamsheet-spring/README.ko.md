## streamsheet-spring

StreamSheet Spring Boot 연동 모듈입니다(자동 설정, 비동기 내보내기, 스토리지, 다운로드 REST 엔드포인트).

### 설치
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-spring:<version>")
}
```

### 주요 설정
- `streamsheet.storage.type`: `LOCAL`(기본) | `S3` | `GCS`
- `streamsheet.security.enabled`: (기본 true) 다운로드 엔드포인트 인증
- `streamsheet.job-store`: `IN_MEMORY`(기본) | `REDIS`
- `streamsheet.enable-metrics`: Micrometer 메트릭 활성화 (`MeterRegistry` Bean 필요)
- `streamsheet.retry-enabled`: `FileStorage` 작업 재시도 래퍼 활성화

### 테스트
```bash
./gradlew :streamsheet-spring:test
```
