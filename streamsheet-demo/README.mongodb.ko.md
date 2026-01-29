# StreamSheet 데모: MongoDB 설정 가이드

이 데모 프로젝트는 실제 MongoDB를 사용하여 실시간 스트리밍 엑셀 내보내기 기능을 시연합니다.

## 1. MongoDB 실행
Docker를 사용하여 로컬에서 간편하게 MongoDB를 구동할 수 있습니다.

```bash
docker run -d --name streamsheet-mongo -p 27017:27017 mongo:latest
```

## 2. 애플리케이션 설정
`src/main/resources/application.yml` 파일에 MongoDB 연결 정보가 포함되어 있는지 확인합니다.

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/streamsheet_demo
```

## 3. 테스트 시나리오

### 단계 1: 데이터 시딩 (Seed)
먼저 테스트를 위해 몽고디비에 대량의 데이터를 채웁니다.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/seed?count=10000"
```

### 단계 2: 최적화된 비동기 내보내기 (JSON 반환)
특정 조건으로 필터링 및 **Projection**이 적용된 최적화된 엑셀 생성을 비동기로 요청합니다.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/export/optimized"
```
**결과:** `jobId`와 `statusUrl`이 반환됩니다.
```json
{
  "jobId": "...",
  "statusUrl": "http://localhost:8080/api/demo/status/..."
}
```

### 단계 3: 작업 상태 확인 및 다운로드 (GET)
비동기 작업의 진행 상태를 확인합니다. 완료(`COMPLETED`) 시 실제 다운로드 URL이 포함됩니다.
```bash
curl -X GET "http://localhost:8080/api/demo/status/{jobId}"
```

### 단계 4: 직접 동기 내보내기 (파일 즉시 다운로드)
작업 조회 과정 없이 즉시 파일을 내려받습니다 (브라우저용).
```bash
# 브라우저 주소창에 입력하거나 curl 사용 (GET)
curl -o result.xlsx http://localhost:8080/api/demo/mongodb/export/download
```

### 단계 5: 일반 비동기 내보내기
전체 필드를 포함하여 엑셀 생성을 요청합니다.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/export"
```
