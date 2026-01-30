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

### Testing
```bash
./gradlew :streamsheet-jpa:test
```
