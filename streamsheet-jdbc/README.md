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

### Testing
```bash
./gradlew :streamsheet-jdbc:test
```
