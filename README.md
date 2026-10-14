# logistics-management

물류 관리 MSA 프로젝트. Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.3, PostgreSQL 16.

## 빠른 실행
```bash
cp .env.example .env
docker compose up -d --build  # 빌드는 Dockerfile 안에서 한다(로컬 Gradle 빌드 불필요)
script/e2e-check.sh           # 공통 인프라 완료 기준 6개 자동 확인 (--up을 붙이면 기동까지 한다)
script/db-check.sh            # 서비스 계정 5개의 실제 인증·스키마 접근 권한 확인
# 권한 재확인 60초 캐시와 만료 후 재조회까지 보려면 --with-cache-ttl 추가 (약 65초)
```
- Eureka 대시보드: http://localhost:8761 (관리용 포트는 이 PC에서만 열린다)
- API 진입점(Gateway): http://localhost:8080
- 종료: `docker compose down` (DB까지 지우려면 `docker compose down -v`)
- 서비스 스키마·계정은 `infra/postgres/init.sh`가 빈 DB에서 처음 한 번 만들고, 테이블 변경은 Flyway가 관리한다(`*/src/main/resources/db/migration`).
- `.env`의 DB 비밀번호는 초기화 스크립트와 서비스 접속 설정에 함께 사용된다. 기존 볼륨의 계정 비밀번호는 `.env`를 바꿔도 자동 갱신되지 않는다.
- Flyway 도입 전의 DB가 있으면 데이터를 백업하고 기존 스키마와 마이그레이션 이력을 먼저 확인한다. 기존 볼륨을 자동으로 삭제하지 않는다.
- MASTER 계정: user-service가 기동할 때 `.env`의 `MASTER_USERNAME`/`MASTER_PASSWORD`(기본 `master` / `Master!2026`)로 만든다. 이미 있으면 그대로 둔다. 가입한 계정을 승격하려면 `script/promote-master.sh <username>` 실행 후 다시 로그인한다.

## 기존 환경과 병행하기

`.env`의 `COMPOSE_PROJECT_NAME`과 `POSTGRES_PORT`, `CONFIG_SERVER_PORT`, `EUREKA_PORT`, `GATEWAY_PORT`를 다르게 지정한다. 예를 들어 프로젝트 이름은 `logistics-integration`, 포트는 각각 `15432`, `18888`, `18761`, `18080`로 정한다. 컨테이너 내부 포트와 서비스 간 주소는 그대로다.

검증 스크립트는 현재 Compose 프로젝트를 사용하며 E2E는 실제 공개 포트를 조회한다. `GATEWAY`·`EUREKA` 환경 변수로 검증 URL을 직접 지정할 수도 있다. IDE에서 `bootRun`을 함께 쓰는 경우에는 해당 서비스의 포트와 DB·Config Server·Eureka 접속 주소를 별도로 지정한다.

## 직접 확인하기
```bash
docker compose ps                         # postgres, config-server가 healthy인지
open http://localhost:8761                # Eureka: 서비스 6개 UP
open http://localhost:8080/swagger-ui.html

# 회원가입 → 로그인 → 토큰을 변수에 저장
curl -s -X POST localhost:8080/api/v1/auth/signup -H 'Content-Type: application/json' \
  -d '{"username":"hong01","password":"Sparta!2026","name":"홍길동","slackId":"U01","affiliationType":"COMPANY","affiliationName":"부산수산도매"}'
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"hong01","password":"Sparta!2026"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

curl -s localhost:8080/api/v1/users/sample/me    -H "Authorization: Bearer $TOKEN"   # 200, 내 정보
curl -s localhost:8080/api/v1/users/sample/admin -H "Authorization: Bearer $TOKEN"   # 403 FORBIDDEN (USER)
curl -s localhost:8080/api/v1/users/sample/me                                        # 401 UNAUTHORIZED (토큰 없음)
curl -s localhost:8080/internal/users/x          -H "Authorization: Bearer $TOKEN"   # 404 (외부 차단)

MASTER=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"master","password":"Master!2026"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -s localhost:8080/api/v1/users/sample/admin -H "Authorization: Bearer $MASTER"  # 200 (기동 시 만든 MASTER)

docker compose logs -f user-service                                                  # 로그 보기
docker compose exec gateway-service curl -s http://user-service:8081/actuator/health # 내부 헬스 체크
```

## 구성
| 모듈 | 포트 | 역할 |
|---|---|---|
| config-server | 8888 | 설정 제공(`config-repo/`) |
| eureka-server | 8761 | 서비스 디스커버리 |
| gateway-service | 8080 | Spring Security(WebFlux): JWT 검증, 권한 재확인(60초 캐시), 사용자 헤더 주입, `/internal` 차단 |
| user-service | 8081 | 회원가입, 로그인(JWT 발급), 내부 사용자 조회 |
| hub / company / order / delivery-service | 8082~8085 | 서비스별 담당자가 구현 |
| common-core | - | 웹 스택과 무관한 공통 계약: 에러 코드, 에러·성공 응답, 사용자 헤더 이름 (gateway와 서비스가 함께 사용) |
| common | - | MVC 서비스 공통: 예외 처리기, BaseEntity, `@CurrentUser`, `@RequireRole`, Feign 오류·헤더 처리, 페이지 규칙 |

## 문서
- 설계: `docs/SA/`
- 공통 인프라 범위: `docs/progress/공통 인프라 개발 범위.md`
- 구현 스텝별 설명: `docs/learning/steps/`
- 결정 기록: `docs/decisions/`
- 에이전트·작업 규칙: `AGENTS.md`, `docs/agents/`
- 브랜치 선별 근거·검증 결과: `docs/reviews/base-setting-integration.md`
- 문서별 기준 위치와 미확정 계약: `docs/agents/document-map.md`
