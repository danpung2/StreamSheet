## streamsheet-mongodb

MongoDB streaming datasource module for StreamSheet.

See also: `../README.md`

### Installation
```gradle
dependencies {
  implementation("io.github.danpung2:streamsheet-mongodb:1.0.0")
}
```

### Basic usage
```kotlin
val dataSource = MongoStreamingDataSource(
  template = mongoTemplate,
  entityClass = User::class.java,
  sourceName = "users"
)
```
### Resource Management
`MongoStreamingDataSource` keeps a cursor open to the MongoDB server. It is crucial to close the cursor to free up resources on the database server.
Always ensure the data source is closed using `use` block.

```kotlin
MongoStreamingDataSource(...).use { dataSource ->
    exporter.export(..., dataSource, ...)
}
```

### Testing
```bash
./gradlew :streamsheet-mongodb:test
```

### License
Apache License 2.0. See `../LICENSE` and `../NOTICE`.
