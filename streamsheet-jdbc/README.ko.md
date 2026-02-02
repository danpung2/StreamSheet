## streamsheet-jdbc

StreamSheet JDBC 스트리밍 데이터소스 모듈입니다.

### 설치
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-jdbc:<version>")
}
```

### 기본 사용 예

```kotlin
val dataSource = JdbcStreamingDataSource(
  jdbcTemplate = namedParameterJdbcTemplate,
  sql = "select id, name from users",
  rowMapper = { rs -> UserRow(rs.getLong("id"), rs.getString("name")) }
)
```

### 리소스 관리
`JdbcStreamingDataSource`는 데이터베이스 연결이나 커서를 관리합니다. 스트림이 완전히 소비되지 않았을 때 리소스 누수를 방지하기 위해 데이터 소스를 닫는 것이 중요합니다.
항상 `use` 블록(Kotlin)이나 try-with-resources(Java)를 사용하세요.

```kotlin
JdbcStreamingDataSource(...).use { dataSource ->
    exporter.export(..., dataSource, ...)
}
```

### 테스트
```bash
./gradlew :streamsheet-jdbc:test
```
