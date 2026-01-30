## streamsheet-core

Core module for StreamSheet.

### Features
- Streaming Excel export (Apache POI SXSSF)
- Schema definitions (annotation-based and DSL)
- Formula injection prevention

### Installation
Gradle:

```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-core:<version>")
}
```

### Basic usage

```kotlin
val schema = excelSchema<MyRow> {
  sheetName = "Report"
  column("ID") { it.id }
  column("Name") { it.name }
}

val dataSource: StreamingDataSource<MyRow> = ...

SxssfExcelExporter().export(schema, dataSource, outputStream)
```

### Testing
```bash
./gradlew :streamsheet-core:test
```
