## streamsheet-mongodb

MongoDB streaming datasource module for StreamSheet.

### Installation
```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-mongodb:<version>")
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

### Testing
```bash
./gradlew :streamsheet-mongodb:test
```
