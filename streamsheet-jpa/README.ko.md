## streamsheet-jpa

StreamSheet JPA 스트리밍 데이터소스 모듈입니다.

참고: `../README.ko.md`

### 설치
```gradle
dependencies {
  implementation("io.github.danpung2:streamsheet-jpa:1.0.0")
}
```

### 기본 사용 예
```kotlin
val dataSource = JpaStreamingDataSource(
  entityManager = entityManager,
  query = "select u from User u",
  detachEntities = true,
)
```

### 리소스 관리
`JpaStreamingDataSource`는 Hibernate 커서를 사용합니다. `EntityManager` 라이프사이클을 관리하더라도, 소비가 조기에 중단되는 경우 커서를 즉시 해제하려면 명시적 종료가 필요합니다.
안전한 사용을 위해 `use` 블록 사용이 필수적입니다.

```kotlin
JpaStreamingDataSource(...).use { dataSource ->
    exporter.export(..., dataSource, ...)
}
```

### 필수 요구사항 및 주의점
1. **활성 트랜잭션 필수 (Active Transaction Required)**
   - JPA 스트리밍은 데이터베이스 커서를 열린 상태로 유지합니다.
   - 반드시 `@Transactional(readOnly = true)`와 같은 활성 트랜잭션 내에서 실행해야 합니다.
   - 트랜잭션이 없으면 커넥션 풀 등의 설정에 따라 스트림 생성 시 또는 데이터 조회 중 예외가 발생할 수 있습니다.

2. **`detachEntities`와 지연 로딩 (Lazy Loading)**
   - 대량 데이터 처리 시 메모리 사용을 최적화하기 위해 기본적으로 `detachEntities`는 `true`로 설정됩니다.
   - 이는 엔티티가 읽히자마자 영속성 컨텍스트에서 분리됨을 의미합니다.
   - **주의**: 초기화되지 않은 지연 로딩 필드에 접근하면 `LazyInitializationException`이 발생합니다. 쿼리 단계에서 `JOIN FETCH`를 사용하여 필요한 데이터를 미리 로딩하십시오.

### 테스트
```bash
./gradlew :streamsheet-jpa:test
```

### 라이선스
Apache License 2.0. `../LICENSE`, `../NOTICE`를 참고하세요.
