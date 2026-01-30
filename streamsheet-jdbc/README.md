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

### Testing
```bash
./gradlew :streamsheet-jdbc:test
```
