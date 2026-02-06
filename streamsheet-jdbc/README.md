## streamsheet-jdbc

JDBC streaming datasource module for StreamSheet.

See also: `../README.md`

### Installation
```gradle
dependencies {
  implementation("io.github.danpung2:streamsheet-jdbc:1.0.0")
}
```

### Basic usage

```kotlin
val dataSource = JdbcStreamingDataSource(
  jdbcTemplate = namedParameterJdbcTemplate,
  sql = "select id, name from users",
  rowMapper = { rs -> UserRow(rs.getLong("id"), rs.getString("name")) }
)
```
### Resource Management
`JdbcStreamingDataSource` manages database connections or cursors. It is critical to close the data source to prevent resource leaks, especially when the stream is not fully consumed.
Always use the `use` block (Kotlin) or try-with-resources (Java).

```kotlin
JdbcStreamingDataSource(...).use { dataSource ->
    exporter.export(..., dataSource, ...)
}
```

### Transaction Management

When streaming large datasets, it is important to manage database resources efficiently.

1.  **Read-Only Operations**: Use `@Transactional(readOnly = true)`. This reduces transaction overhead and allows some databases to utilize MVCC snapshots.
2.  **Cursor Lifespan**: The DB cursor and transaction remain active during streaming. Extremely long-running queries can increase UNDO log usage etc. Adjust `fetchSize` appropriately.
3.  **Query Timeout**: Set `queryTimeout` to prevent unexpectedly long queries.

```kotlin
@Transactional(readOnly = true)
fun exportData() {
    val dataSource = JdbcStreamingDataSource(
        jdbcTemplate = jdbcTemplate,
        sql = "SELECT ...",
        rowMapper = rowMapper,
        fetchSize = 1000,       // Rows per fetch (memory control)
        queryTimeout = 300      // Query timeout in seconds
    )
    
    // ...
}
```

### Retry Pattern

It is recommended to use `Spring Retry` to handle transient database connection errors (e.g., network instability).
However, retries are **not possible once streaming has started**. Apply retry logic only during the data source creation and query initialization phase.

```kotlin
@Service
class ExportService {

    @Retryable(
        value = [TransientDataAccessException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000)
    )
    fun createDataSource(): JdbcStreamingDataSource<UserRow> {
        return JdbcStreamingDataSource(...)
    }
}
```

### Testing
```bash
./gradlew :streamsheet-jdbc:test
```

### License
Apache License 2.0. See `../LICENSE` and `../NOTICE`.
