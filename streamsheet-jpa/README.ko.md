## streamsheet-jpa

StreamSheet JPA 스트리밍 데이터소스 모듈입니다.

### 설치
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-jpa:<version>")
}
```

### 기본 사용 예
```kotlin
val dataSource = JpaStreamingDataSource(
  entityManager = entityManager,
  query = "select u from User u",
  detachEntities = true,
)
```

### 리소스 관리
`JpaStreamingDataSource`는 Hibernate 커서를 사용합니다. `EntityManager` 라이프사이클을 관리하더라도, 소비가 조기에 중단되는 경우 커서를 즉시 해제하려면 명시적 종료가 필요합니다.
안전한 사용을 위해 `use` 블록 사용이 필수적입니다.

```kotlin
JpaStreamingDataSource(...).use { dataSource ->
    exporter.export(..., dataSource, ...)
}
```

### 테스트
```bash
./gradlew :streamsheet-jpa:test
```
