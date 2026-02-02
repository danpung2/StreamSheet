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

### 리소스 관리
`MongoStreamingDataSource`는 MongoDB 서버에 대한 커서를 유지합니다. 데이터베이스 서버의 자원을 확보하기 위해 커서를 닫는 것이 중요합니다.
항상 `use` 블록을 사용하여 데이터 소스가 닫히도록 보장하세요.

```kotlin
MongoStreamingDataSource(...).use { dataSource ->
    exporter.export(..., dataSource, ...)
}
```

### 테스트
```bash
./gradlew :streamsheet-mongodb:test
```
