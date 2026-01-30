## streamsheet-mongodb

StreamSheet MongoDB 스트리밍 데이터소스 모듈입니다.

### 설치
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-mongodb:<version>")
}
```

### 기본 사용 예
```kotlin
val dataSource = MongoStreamingDataSource(
  template = mongoTemplate,
  entityClass = User::class.java,
  sourceName = "users"
)
```

### 테스트
```bash
./gradlew :streamsheet-mongodb:test
```
