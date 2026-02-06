## streamsheet-core

Core module for StreamSheet.

See also: `../README.md`

### Features
- Streaming Excel export (Apache POI SXSSF)
- Schema definitions (annotation-based and DSL)
- Formula injection prevention

### Installation
Gradle:

```gradle
dependencies {
  implementation("io.github.danpung2:streamsheet-core:1.0.0")
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

### License
Apache License 2.0. See `../LICENSE` and `../NOTICE`.
