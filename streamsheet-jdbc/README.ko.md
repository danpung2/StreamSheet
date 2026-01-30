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

### 테스트
```bash
./gradlew :streamsheet-jdbc:test
```
