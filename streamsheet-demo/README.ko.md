# StreamSheet 데모

다양한 데이터 소스를 활용한 StreamSheet의 고성능 엑셀 내보내기 기능을 시연하는 종합 데모 애플리케이션입니다.

참고: `../README.ko.md`

## 주요 기능

- **웹 대시보드**: 내보내기 실행 및 작업 진행 상황을 모니터링할 수 있는 대화형 UI
- **다양한 데이터 소스**: MongoDB, PostgreSQL (JDBC/JPA), 인메모리 Mock 데이터 지원
- **비동기 내보내기**: 실시간 상태 폴링과 함께 논블로킹 엑셀 생성
- **스트리밍 아키텍처**: 데이터 크기와 무관하게 일정한 메모리 사용량 유지

## 사전 요구사항

- Java 17+
- Docker & Docker Compose (데이터베이스용)

## 빠른 시작

### 1. 인프라 시작

```bash
cd streamsheet-demo
docker-compose up -d
```

다음 서비스가 시작됩니다:
- **MongoDB**: `localhost:27017`
- **PostgreSQL**: `localhost:5432` (user: `user`, password: `password`)

### 2. 애플리케이션 실행

```bash
# 프로젝트 루트에서
./gradlew :streamsheet-demo:bootRun
```

### 3. 대시보드 열기

브라우저에서 [http://localhost:8080](http://localhost:8080)에 접속합니다.

## API 엔드포인트

### 데이터 시딩

| 메서드 | 엔드포인트 | 설명 |
|--------|----------|-------------|
| POST | `/api/demo/mongodb/seed?count=N` | MongoDB에 N개 레코드 시딩 |
| POST | `/api/demo/postgres/seed?count=N` | PostgreSQL에 N개 레코드 시딩 |

### 내보내기 엔드포인트

| 메서드 | 엔드포인트 | 설명 |
|--------|----------|-------------|
| POST | `/api/demo/export?count=N` | Mock 데이터 내보내기 (DB 없음) |
| POST | `/api/demo/mongodb/export` | MongoDB 스트리밍 내보내기 |
| POST | `/api/demo/mongodb/export/optimized` | MongoDB Projection 적용 |
| POST | `/api/demo/jdbc/export` | JDBC ResultSet 스트리밍 |
| POST | `/api/demo/jpa/export` | JPA Entity 스트리밍 |
| GET | `/api/demo/mongodb/export/download` | 동기 직접 다운로드 |

### 작업 관리

| 메서드 | 엔드포인트 | 설명 |
|--------|----------|-------------|
| GET | `/api/demo/status/{jobId}` | 작업 상태 및 다운로드 URL 조회 |

## 아키텍처

```
streamsheet-demo/
├── src/main/kotlin/com/streamsheet/demo/
│   ├── api/                    # REST 컨트롤러
│   │   └── DemoController.kt
│   ├── domain/
│   │   ├── model/              # 공유 모델
│   │   │   └── UserActivity.kt
│   │   ├── mongo/              # MongoDB 도큐먼트
│   │   │   ├── UserActivityDocument.kt
│   │   │   ├── UserActivityProjection.kt
│   │   │   └── UserActivityRepository.kt
│   │   └── jpa/                # JPA 엔티티
│   │       ├── UserActivityEntity.kt
│   │       └── UserActivityJpaRepository.kt
│   └── service/                # 비즈니스 로직 (추후)
├── src/main/resources/
│   ├── static/
│   │   └── index.html          # 웹 대시보드
│   └── application.yml         # 설정
└── docker-compose.yml          # 인프라
```

## 내보내기 흐름

1. **사용자 요청** → API 또는 대시보드를 통해 내보내기 시작
2. **작업 생성** → `AsyncExportService`가 작업을 생성하고 `jobId` 반환
3. **백그라운드 처리** → StreamSheet가 스트리밍 모드로 엑셀 작성
4. **상태 폴링** → 프론트엔드가 `/status/{jobId}`로 진행률 조회
5. **다운로드** → `COMPLETED` 상태가 되면 `downloadUrl` 사용 가능

## 설정

`application.yml`의 주요 설정:

```yaml
streamsheet:
  row-access-window-size: 100    # SXSSF 메모리 윈도우
  flush-batch-size: 1000         # 플러시당 행 수
  compress-temp-files: true      # 디스크 사용량 감소
  storage:
    type: LOCAL                  # LOCAL 또는 S3/GCS
    local:
      path: build/exports
      base-url: http://localhost:8080/api/streamsheet/download
  retention-hours: 1             # 1시간 후 자동 정리
```

## 라이선스

Apache License 2.0. `../LICENSE`, `../NOTICE`를 참고하세요.
