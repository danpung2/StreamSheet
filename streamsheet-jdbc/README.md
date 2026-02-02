## streamsheet-jdbc

JDBC streaming datasource module for StreamSheet.

### Installation
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-jdbc:<version>")
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

### Testing
```bash
./gradlew :streamsheet-jdbc:test
```
