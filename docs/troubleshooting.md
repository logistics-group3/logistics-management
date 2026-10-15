# 문제 해결 기록

막혔던 문제와 해결 과정을 남긴다. 발표 자료의 근거가 되므로 해결한 즉시 쓴다. 에이전트는 `troubleshooting` 스킬로 기록한다.

- 1장은 자주 겪는 증상과 대처를 한 줄씩 모은 표다. 원본은 haneol `doc/guide/15-troubleshooting.md`, kej README 8장이고 이 저장소에 맞는 항목만 옮겼다.
- 2장은 사례 기록이다. 형식은 kmh `Docs/07-트러블슈팅.md`를 따른다. TS-001~005는 kmh 브랜치에서 옮겼고, 수정 위치가 이 저장소와 다르면 "이 저장소"로 적었다.

## 1. 자주 겪는 증상
| 증상 | 원인 | 해결 |
|---|---|---|
| compose 실행 시 `USER_DB_PASSWORD가 필요하다` | `.env`가 없거나 값이 비어 있다 | `script/run.sh`가 `.env.example`을 복사한다. 직접 하려면 `cp .env.example .env` |
| 서비스 기동 시 `Could not locate PropertySource`, `Connection refused :8888` | config-server가 떠 있지 않다(`fail-fast`) | `docker compose up -d config-server` 후 서비스 실행(IDE 포함) |
| `Schema-validation: missing table`, `wrong column type` | Entity와 마이그레이션 SQL이 다르다(`ddl-auto: validate`) | 새 `V{n}__*.sql`로 고친다. 이미 적용된 파일을 고쳤다면 로컬은 `script/run.sh --reset` |
| `Migration checksum mismatch` | 이미 적용된 마이그레이션 파일을 고쳤다 | 고친 내용을 되돌리고 새 버전 파일로 만든다. 로컬에서만 쓰던 DB면 `script/run.sh --reset` |
| `infra/postgres/init.sh`나 DB 비밀번호를 바꿨는데 반영되지 않는다 | 초기화 스크립트는 볼륨이 비어 있을 때만 돈다 | `script/run.sh --reset` (DB 데이터 삭제) |
| `config-repo` 수정이 반영되지 않는다 | 설정이 config-server jar 안에 있고, 서비스는 기동할 때만 설정을 읽는다 | `docker compose up -d --build config-server` → `docker compose restart {서비스}` ([설정 보안 가이드](guides/config-security.md) 4장) |
| `.env` 수정이 반영되지 않는다 | 환경 변수는 컨테이너를 만들 때 정해진다 | `docker compose up -d {서비스}` (`restart`로는 안 바뀐다) |
| 기동·교체 직후 Gateway가 `503 SERVICE_UNAVAILABLE` | 서비스가 아직 Eureka에 등록되지 않았거나 Gateway가 목록을 다시 받기 전이다 | 몇 초 뒤 다시 호출한다. http://localhost:8761 에서 `UP`인지 확인한다 |
| 로그인한 토큰으로 모든 요청이 `401 TOKEN_ROLE_MISMATCH` | 권한 변경·탈퇴 뒤 이전 토큰을 쓰거나, DB를 초기화해 토큰의 사용자가 없다 | 다시 로그인한다(권한 재확인 캐시 최대 60초) |
| 서비스를 직접 호출하면 `401 UNAUTHORIZED` | Gateway를 거치지 않아 `X-User-*` 헤더가 없다 | `:8080` Gateway로 호출한다 |
| 외부에서 `/internal/...` 호출이 404 | 정상이다. Gateway가 막는다 | 서비스 간 Feign으로만 호출한다 |
| `port is already allocated` (5432, 8080 등) | 로컬 PostgreSQL이나 다른 compose 프로젝트가 포트를 쓰고 있다 | 기존 프로세스를 끄거나 `.env`의 `*_PORT`를 바꾼다(루트 `README.md` "기존 환경과 병행하기") |
| `cannot find symbol Q{Entity}` | QueryDSL annotation processing이 돌지 않았다 | `./gradlew :{service}-service:compileJava`, IDE는 Gradle로 빌드를 위임한다 |
| IDE로 띄운 서비스가 DB 접속에 실패하거나 다른 DB에 붙는다 | `.env`의 `POSTGRES_PORT`를 바꿨는데 IDE 실행 설정에 `DB_PORT`가 없어 5432로 접속한다 | IDE 실행 설정에 `DB_PORT`를 `POSTGRES_PORT`와 같은 값으로 넣는다([로컬 실행 가이드](guides/local-run.md) 3.4) |
| Windows Docker 빌드에서 `./gradlew: not found`, `/bin/sh^M` | `gradlew`가 CRLF로 체크아웃됐다 | `.gitattributes`가 LF로 고정한다. 이미 clone했다면 `git rm --cached -r . && git reset --hard` |

## 2. 사례 기록

### 작성 방법
- 맨 아래에 추가한다. ID는 `TS-{번호}`다.
- 코드 문제뿐 아니라 설계 판단을 바꾼 경우(처음 방식이 동시성 문제로 실패해 락 방식으로 바꾼 경우 등)도 남긴다.
- 원인을 찾지 못했으면 "원인: 미확인"으로 쓴다.

```
### TS-000 제목 (한 줄로 증상)
- 서비스·작성: {서비스} / {이름} / YYYY-MM-DD
- 문제: 무엇이 어떻게 실패했나 (에러 메시지, 재현 조건)
- 원인: 왜 그랬나
- 해결: 무엇을 바꿨나 (관련 파일·커밋)
- 배운 점: (선택) 다음에 같은 실수를 피하려면
```

### TS-001 Gateway에서 정상 요청까지 401로 막힘 (WebFlux `switchIfEmpty`)
- 서비스·작성: gateway-service / 권민혁 / 2026-10-13
- 문제: 유효한 토큰으로 요청해도 "사용자 없음" 분기가 실행돼 401이 반환됐다.
- 원인: 필터 체인의 정상 처리 결과인 `Mono<Void>`는 항상 비어 있다. 그래서 "비어 있으면 401"로 쓴 `switchIfEmpty`가 정상 요청 뒤에도 실행됐다.
- 해결: 사용자 조회 결과를 찾음, 없음, 조회 실패 세 상태로 먼저 확정한 뒤 분기하도록 바꿨다. 이 저장소의 Gateway는 Spring Security로 구현해 구조가 다르다(ADR 0005).
- 배운 점: Reactor에서 "비어 있음"은 "값 없음"과 "완료 신호만 있음"을 구분하지 않는다. 분기 판단은 체인 결과가 아니라 명시적인 상태 값으로 한다.

### TS-002 record accessor 이름 충돌로 컴파일 실패
- 서비스·작성: gateway-service / 권민혁 / 2026-10-13
- 문제: `UserLookup` record가 컴파일되지 않았다.
- 원인: 필드 `failed`의 자동 생성 accessor `failed()`와 같은 이름의 정적 팩토리 메서드가 충돌했다.
- 해결: 팩토리 메서드 이름을 `lookupFailed`로 바꿨다.

### TS-003 `BaseEntity`를 상속한 엔티티의 Q클래스 생성 실패
- 서비스·작성: common / 권민혁 / 2026-10-13
- 문제: 서비스 엔티티가 `BaseEntity`를 상속하자 `QBaseEntity`를 찾을 수 없다는 컴파일 오류가 났다.
- 원인: `common` 모듈에 QueryDSL 어노테이션 프로세서가 없어 `QBaseEntity`가 생성되지 않았다.
- 해결: common에도 querydsl-apt를 추가했다. 이 저장소는 루트 `build.gradle`이 common과 JPA 서비스에 함께 건다. 서비스 쪽은 조치할 필요 없다.

### TS-004 `docker compose up --build` 시 여러 모듈 동시 빌드 실패
- 서비스·작성: 인프라 / 권민혁 / 2026-10-13
- 문제: 전체 빌드 중 일부 컨테이너의 Gradle 빌드가 무작위로 실패했다.
- 원인: 여러 이미지 빌드가 같은 Gradle 캐시 마운트를 동시에 쓰면서 충돌했다.
- 해결: kmh 브랜치는 캐시 마운트를 `sharing=locked`로 바꿨다. 이 저장소의 Dockerfile은 빌드 단계에서 `MODULE` 인자를 쓰지 않아 8개 이미지가 같은 빌드 단계 하나를 공유하고, BuildKit이 그 단계를 한 번만 빌드한다(`Dockerfile` 주석).

### TS-005 Windows Git Bash curl로 한글 JSON 전송 시 400
- 서비스·작성: 인프라 / 권민혁 / 2026-10-13
- 문제: 회원가입 요청 본문에 한글이 있으면 400이 반환됐다.
- 원인: Git Bash에서 `-d '...'`로 넘긴 한글이 UTF-8로 전달되지 않았다(서버 문제 아님).
- 해결: UTF-8 파일로 저장해 `--data-binary @file.json`으로 보내거나 Swagger·Postman을 사용한다.

### TS-006 Mockito verify의 인자에서 다른 verify를 호출하면 검증이 중단됨

- 서비스·작성: order-service / Junhyun An / 2026-10-15
- 문제: `OrderServiceTest`의 보상 검증 6개가 `UnfinishedVerificationException`으로 실패했다. `verify(gateway).deleteByOrderId(captureCreatedOrderId())`에서 인자 헬퍼도 `verify`를 호출했다.
- 원인: 바깥 `verify`를 시작한 뒤 인자를 평가하면서 다른 `verify`가 실행돼, 첫 검증을 마치기 전에 다음 검증을 시작했다.
- 해결: 주문 ID를 지역 변수로 먼저 캡처한 뒤 별도 문장으로 삭제 호출을 검증했다. 업무 코드는 변경하지 않았고 application 단위 테스트 23개가 통과했다.
- 배운 점: Mockito 검증을 포함하는 헬퍼는 다른 `verify`의 인자 안에서 실행하지 않는다.

### TS-007 HTTP 테스트 드라이버가 503을 재전송해 재시도 횟수가 두 배로 측정됨

- 서비스·작성: order-service / Junhyun An / 2026-10-15
- 문제: Feign·보상 호출이 예상 3회 대신 6회, 1회 대신 2회로 관측됐다. 서로 다른 orderId가 두 묶음으로 나타났다.
- 원인: TestRestTemplate이 선택한 Apache HTTP 클라이언트가 주문 API의 503 응답을 자동 재전송했다. `HttpRequestRetryExec` 로그에서 1초 후 요청 재실행을 확인했다.
- 해결: 테스트 드라이버에 `SimpleClientHttpRequestFactory`를 지정했다. 내부 Feign 정책은 유지했고 HTTP 통합 테스트 13개가 통과했다.
- 배운 점: 최초 주문 요청의 재시도와 하위 API 재시도를 구분해야 한다. 기대 호출 횟수를 늘려 테스트를 통과시키지 않는다.

### TS-008 지정 서비스 기동 시 의존 PostgreSQL도 재생성됨

- 서비스·작성: order-service 로컬 실행 / Junhyun An / 2026-10-15
- 문제: `script/run.sh order-service` 실행 중 PostgreSQL 컨테이너도 재생성됐다.
- 원인: 스크립트의 `docker compose up -d order-service`는 의존 서비스도 조정한다. PostgreSQL 재생성을 촉발한 정확한 구성·이미지 차이는 미확인이다.
- 확인: 기존 pgdata 볼륨 재사용·초기화 생략, `.env` 해시 유지, 나머지 7개 컨테이너 ID·시작 시각 유지, 비즈니스 서비스 5개와 Gateway health UP을 확인했다. 볼륨 삭제·초기화 명령은 실행하지 않았다.
- 후속 실행 방법: 의존 서비스가 이미 정상인 경우 주문 이미지 빌드 뒤 `docker compose up -d --no-deps --no-build order-service`로 대상만 교체한다. 공동 스크립트의 기본 동작은 별도 팀 검토 대상으로 두고 이번에 수정하지 않았다.

### TS-009 Slack 통합 테스트에서 공통 사용자 헤더 상수를 찾지 못함

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: `:order-service:compileTestJava`가 `cannot find symbol: UserHeaders.IS_DELIVERY_MANAGER`로 실패했다.
- 원인: 새 테스트가 공통 클래스에 없는 상수 이름을 참조했다. 정본 이름은 `UserHeaders.DELIVERY_MANAGER`다.
- 해결: 공통 계약을 확인하고 테스트의 참조를 수정했다. 주문 108개 테스트와 전체 빌드가 통과했다.
- 배운 점: 공통 헤더 이름은 추측하지 않고 `common-core`의 `UserHeaders`를 확인한다. 문자열을 새로 만들거나 공통 계약을 바꾸어 우회하지 않는다.
