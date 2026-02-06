## streamsheet-jpa

JPA streaming datasource module for StreamSheet.

See also: `../README.md`

### Installation
```gradle
dependencies {
  implementation("io.github.danpung2:streamsheet-jpa:1.0.0")
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

### Requirements & Caveats
1. **Active Transaction Required**
   - JPA streaming keeps a database cursor open.
   - You MUST execute the export logic within an active transaction (e.g., `@Transactional(readOnly = true)`).
   - If no transaction is active, the `Stream` may fail to open or close unexpectedly as the DB connection might be closed by the connection pool.

2. **Lazy Loading with `detachEntities`**
   - By default, `detachEntities` is set to `true` to optimize memory usage during large exports.
   - This means entities are detached from the persistence context immediately after being read.
   - **Warning**: Accessing uninitialized lazy-loaded fields will throw `LazyInitializationException`. Ensure you `JOIN FETCH` all necessary data in your query.

### Testing
```bash
./gradlew :streamsheet-jpa:test
```

### License
Apache License 2.0. See `../LICENSE` and `../NOTICE`.
