# 15. 문제 해결 — 로컬 실행

> 최종 수정: 2026-10-12 · 대상: **전원**. 새로 겪은 문제는 아래 표에 한 줄 추가한다.

| 증상 | 원인 | 해결 |
|---|---|---|
| `script/run.sh` → `.env가 없습니다` | `.env` 미생성 | `cp .env.example .env` |
| compose 실행 시 `JWT_SECRET를 .env에 설정하세요` | `JWT_SECRET` 비어 있음 | `.env`에 32자 이상 값 |
| 서비스 기동 시 `Could not locate PropertySource` / `Connection refused :8888` | config-server가 안 떠 있음 | `docker compose up -d config-server` 후 서비스 실행 (IDE 포함) |
| `Schema-validation: missing table [p_xxx]` / `wrong column type` | DDL과 Entity 불일치 (`ddl-auto: validate`) | `doc/sql/{NN}-{service}.sql` 수정 → `script/run.sh --profile domain --reset` |
| DDL을 고쳤는데 DB에 반영 안 됨 | init 스크립트는 볼륨이 비어 있을 때만 실행 | `script/run.sh --reset` (DB 데이터 삭제됨) |
| `config/` 수정이 반영 안 됨 (IDE로 config-server 실행 시) | IDE 실행은 jar 안 복사본(`classpath:/config/`)을 읽음 | config-server는 compose로 실행 (볼륨 마운트) |
| `config/` 수정이 반영 안 됨 (compose) | 서비스는 기동 시에만 설정을 받음 | 서비스 재시작 (refresh 도입 후: `script/config-refresh.sh`) — `12-config-ops.md` |
| Gateway에서 `503 SERVICE_UNAVAILABLE` (기동 직후) | 서비스가 아직 Eureka에 등록 안 됨 / LoadBalancer 캐시 | 30~60초 후 재시도, `:8761`에서 등록 확인 |
| 로그인 후 API가 `401 TOKEN_ROLE_MISMATCH` | 권한 변경·탈퇴 후 이전 토큰 사용 | 재로그인 (권한 캐시 최대 60초) |
| 호출 시 `401 UNAUTHORIZED` (서비스 직접 호출) | X-User-* 헤더 없음 (Gateway 미경유) | `:8080` Gateway로 호출 |
| `/internal/...` 외부 호출이 404 | 정상 (Gateway가 차단) | 서비스 간 Feign으로만 호출 |
| 포트 충돌 `5433 already allocated` | 로컬 PostgreSQL 등 | 기존 프로세스 종료 또는 compose 포트 변경 |
| `./gradlew` 빌드 시 QueryDSL `Q클래스` 없음 | annotation processing 미실행 | `./gradlew :{service}-service:compileJava`, IDE에서 Gradle로 빌드 위임 |
