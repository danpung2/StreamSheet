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

### 트랜잭션 관리 및 주의사항 (Transaction Management)

대용량 데이터를 스트리밍할 때는 데이터베이스 리소스를 효율적으로 관리하기 위해 다음 사항을 준수해야 합니다.

1.  **읽기 전용 트랜잭션 사용**: 데이터 변경이 없는 내보내기 작업은 `@Transactional(readOnly = true)`를 사용하여 트랜잭션 오버헤드를 줄이고, 일부 DB에서는 MVCC 스냅샷을 활용할 수 있게 하세요.
2.  **커서 유지 시간**: 스트리밍이 진행되는 동안 DB 커서와 트랜잭션이 활성 상태로 유지됩니다. 너무 오랜 시간 수행되는 쿼리는 UNDO 로그 증가 등의 부하를 줄 수 있으므로 적절한 `fetchSize` 설정이 중요합니다.
3.  **쿼리 타임아웃**: 예상치 못한 롱 쿼리를 방지하기 위해 `queryTimeout` 옵션을 설정할 수 있습니다.

```kotlin
@Transactional(readOnly = true)
fun exportData() {
    val dataSource = JdbcStreamingDataSource(
        jdbcTemplate = jdbcTemplate,
        sql = "SELECT ...",
        rowMapper = rowMapper,
        fetchSize = 1000,       // 한 번에 가져올 행 수 (메모리 조절)
        queryTimeout = 300      // 쿼리 제한 시간 (초 단위)
    )
    
    // ...
}
```

### 테스트
```bash
./gradlew :streamsheet-jdbc:test
```
