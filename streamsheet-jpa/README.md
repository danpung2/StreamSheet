## streamsheet-jpa

JPA streaming datasource module for StreamSheet.

### Installation
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-jpa:<version>")
}
```

### Basic usage
```kotlin
val dataSource = JpaStreamingDataSource(
  entityManager = entityManager,
  query = "select u from User u",
  detachEntities = true,
)
```
### Resource Management
`JpaStreamingDataSource` uses Hibernate cursors. Even though it handles `EntityManager` lifecycle, explicit closing is required to release the cursor immediately if consumption stops early.
Using `use` block is mandatory for safety.

```kotlin
JpaStreamingDataSource(...).use { dataSource ->
    exporter.export(..., dataSource, ...)
}
```

### Testing
```bash
./gradlew :streamsheet-jpa:test
```
