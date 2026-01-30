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

### 테스트
```bash
./gradlew :streamsheet-jpa:test
```
