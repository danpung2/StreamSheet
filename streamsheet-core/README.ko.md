## streamsheet-core

StreamSheet의 핵심(Core) 모듈입니다.

### 주요 기능
- Apache POI SXSSF 기반 스트리밍 Excel 내보내기
- 스키마 정의(어노테이션 기반 / DSL 기반)
- 수식 인젝션 방지

### 설치
Gradle:

```gradle
dependencies {
  implementation("com.streamsheet:streamsheet-core:<version>")
}
```

### 기본 사용 예

```kotlin
val schema = excelSchema<MyRow> {
  sheetName = "Report"
  column("ID") { it.id }
  column("Name") { it.name }
}

val dataSource: StreamingDataSource<MyRow> = ...

SxssfExcelExporter().export(schema, dataSource, outputStream)
```

### 테스트
```bash
./gradlew :streamsheet-core:test
```
