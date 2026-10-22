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

### TS-006 Windows에서 `script/run.sh --check`가 "5분 안에 준비되지 않았다(등록: [])"로 실패
- 서비스·작성: 인프라 / 권민혁 / 2026-10-15
- 문제: 모든 컨테이너가 떠 있고 Eureka 대시보드에도 서비스가 UP인데, `script/e2e-check.sh`가 `5분 안에 준비되지 않았다. 등록: [], 로그인 응답: 401`로 끝났다.
- 원인: 스크립트가 JSON을 `python3`으로 읽는다. Python을 설치하지 않은 Windows에서는 `python3`이 Microsoft Store로 연결되는 실행 파일(`AppData/Local/Microsoft/WindowsApps/python3`)이라 아무것도 출력하지 않는다. 그래서 Eureka 등록 목록이 빈 값이 된다. 로그인 401은 준비 확인용으로 없는 계정을 보낸 결과라 정상이다.
- 해결: Python 3를 설치하고 "앱 실행 별칭"에서 Store 연결을 끈 뒤 `python3 --version`이 버전을 출력하는지 확인한다. 설치 전에는 같은 항목을 curl로 직접 호출해 확인한다(`docs/progress/feat-user-management.md` 메모).
- 배운 점: 셸 스크립트가 쓰는 외부 명령(python3, jq 등)은 실행 전에 있는지 확인하고, 없으면 그 이유로 실패하게 만드는 편이 원인 찾기가 빠르다.

### TS-007 Mockito verify의 인자에서 다른 verify를 호출하면 검증이 중단됨

- 서비스·작성: order-service / Junhyun An / 2026-10-15
- 문제: `OrderServiceTest`의 보상 검증 6개가 `UnfinishedVerificationException`으로 실패했다. `verify(gateway).deleteByOrderId(captureCreatedOrderId())`에서 인자 헬퍼도 `verify`를 호출했다.
- 원인: 바깥 `verify`를 시작한 뒤 인자를 평가하면서 다른 `verify`가 실행돼, 첫 검증을 마치기 전에 다음 검증을 시작했다.
- 해결: 주문 ID를 지역 변수로 먼저 캡처한 뒤 별도 문장으로 삭제 호출을 검증했다. 업무 코드는 변경하지 않았고 application 단위 테스트 23개가 통과했다.
- 배운 점: Mockito 검증을 포함하는 헬퍼는 다른 `verify`의 인자 안에서 실행하지 않는다.

### TS-008 HTTP 테스트 드라이버가 503을 재전송해 재시도 횟수가 두 배로 측정됨

- 서비스·작성: order-service / Junhyun An / 2026-10-15
- 문제: Feign·보상 호출이 예상 3회 대신 6회, 1회 대신 2회로 관측됐다. 서로 다른 orderId가 두 묶음으로 나타났다.
- 원인: TestRestTemplate이 선택한 Apache HTTP 클라이언트가 주문 API의 503 응답을 자동 재전송했다. `HttpRequestRetryExec` 로그에서 1초 후 요청 재실행을 확인했다.
- 해결: 테스트 드라이버에 `SimpleClientHttpRequestFactory`를 지정했다. 내부 Feign 정책은 유지했고 HTTP 통합 테스트 13개가 통과했다.
- 배운 점: 최초 주문 요청의 재시도와 하위 API 재시도를 구분해야 한다. 기대 호출 횟수를 늘려 테스트를 통과시키지 않는다.

### TS-009 지정 서비스 기동 시 의존 PostgreSQL도 재생성됨

- 서비스·작성: order-service 로컬 실행 / Junhyun An / 2026-10-15
- 문제: `script/run.sh order-service` 실행 중 PostgreSQL 컨테이너도 재생성됐다.
- 원인: 스크립트의 `docker compose up -d order-service`는 의존 서비스도 조정한다. PostgreSQL 재생성을 촉발한 정확한 구성·이미지 차이는 미확인이다.
- 확인: 기존 pgdata 볼륨 재사용·초기화 생략, `.env` 해시 유지, 나머지 7개 컨테이너 ID·시작 시각 유지, 비즈니스 서비스 5개와 Gateway health UP을 확인했다. 볼륨 삭제·초기화 명령은 실행하지 않았다.
- 후속 실행 방법: 의존 서비스가 이미 정상인 경우 주문 이미지 빌드 뒤 `docker compose up -d --no-deps --no-build order-service`로 대상만 교체한다. 공동 스크립트의 기본 동작은 별도 팀 검토 대상으로 두고 이번에 수정하지 않았다.

### TS-010 Slack 통합 테스트에서 공통 사용자 헤더 상수를 찾지 못함

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: `:order-service:compileTestJava`가 `cannot find symbol: UserHeaders.IS_DELIVERY_MANAGER`로 실패했다.
- 원인: 새 테스트가 공통 클래스에 없는 상수 이름을 참조했다. 정본 이름은 `UserHeaders.DELIVERY_MANAGER`다.
- 해결: 공통 계약을 확인하고 테스트의 참조를 수정했다. 주문 108개 테스트와 전체 빌드가 통과했다.
- 배운 점: 공통 헤더 이름은 추측하지 않고 `common-core`의 `UserHeaders`를 확인한다. 문자열을 새로 만들거나 공통 계약을 바꾸어 우회하지 않는다.

### TS-011 실제 검색 size 기본값은 10인데 Swagger에는 20이 표시됨

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: OpenAPI 계약 테스트가 `expected: 10 but was: 20`으로 실패했다. 실제 검색 API는 공통 리졸버를 거쳐 size=10을 사용했다.
- 원인: Springdoc의 Pageable 문서 기본값이 커스텀 SearchPageableArgumentResolver의 기본값을 자동으로 반영하지 않았다.
- 해결: 조회 Controller에 `@PageableDefault(size=10, sort="createdAt", direction=DESC)`를 명시하고 OpenAPI의 파라미터 목록·size 기본값을 회귀 테스트로 확인했다. 공통 리졸버는 바꾸지 않았다.
- 배운 점: 실행 결과뿐 아니라 Swagger가 안내하는 입력 계약도 실제 API와 맞는지 검증한다.

### TS-012 검색 테스트 데이터가 기존 커밋 실패 테스트의 임시 유니크 제약과 충돌함

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: 전체 빌드에서 OrderCreationIntegrationTest의 `ALTER TABLE ... UNIQUE (orderer_id, request_note)`가 DuplicateKeyException으로 실패했다. API 호출 전 임시 제약 생성 단계의 실패였다.
- 원인: 새 HTTP 검색 테스트가 동일 주문자·요청사항의 정상 주문을 여러 건 만들고 공유 Testcontainers DB에 남겼다. 실행 순서에 따라 이 데이터가 기존 테스트의 임시 제약과 충돌했다.
- 해결: 새 테스트가 생성한 주문 UUID만 기록하고 `@AfterEach`의 finally에서 해당 fixture 행을 정리한다. 전체 테이블이나 다른 테스트의 데이터는 삭제하지 않는다.
- 확인: 전체 build 성공, 주문 152개·전체 XML 250개, 실패·오류·skip 0. Swagger 계약 테스트도 함께 통과했다.
- 배운 점: HTTP 테스트는 서버 스레드에서 별도 트랜잭션을 쓰므로 테스트 메서드의 롤백만으로 격리되지 않는다. 공유 DB를 쓰면 자신이 만든 데이터의 수명도 관리한다.

### TS-013 POST 401 테스트에서 응답 본문이 null로 전달됨

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: OrderCancellationHttpIntegrationTest에서 미인증 POST의 상태는 401이었지만 body가 null이어서 ObjectMapper.readTree가 `argument "content" is null`로 실패했다.
- 원인: 테스트 드라이버 차이로 확인했다. SimpleClientHttpRequestFactory에서는 본문이 null이고 같은 API를 JdkClientHttpRequestFactory로 호출하면 공통 UNAUTHORIZED 본문을 읽었다. HttpURLConnection 내부의 정확한 본문 누락 경로는 미확인이다.
- 해결: 해당 테스트 드라이버를 Java 17 HttpClient 기반 JdkClientHttpRequestFactory로 바꾸고 연결 1초·읽기 10초 제한을 유지했다. 서비스의 인증·예외 응답 코드는 변경하지 않았다.
- 확인: HTTP/Feign 취소 테스트 18개와 전체 build 통과. I-11 1회·I-12 통신 실패 최대 3회·I-12 5xx 1회도 함께 확인해 드라이버의 상태 코드 재전송이 섞이지 않음을 검증했다.

### TS-014 주문 Entity와 Spring @Order의 이름 충돌로 테스트 컴파일 실패

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: 커밋 뒤 예외를 주입하는 테스트에서 domain.Order와 Spring의 Order 애너테이션을 함께 import해 `reference to Order is ambiguous`가 발생했다.
- 원인: 두 타입의 단순 이름이 같아 Java가 Entity와 애너테이션을 구분할 수 없었다.
- 해결: Entity import를 유지하고 테스트 aspect의 순서는 `@org.springframework.core.annotation.Order(...)`로 명시했다. 제품 코드의 타입 이름과 트랜잭션 설정은 바꾸지 않았다.
- 확인: 생성 기록 PostgreSQL 통합 테스트 15개와 전체 build 통과. 실제 커밋 뒤 예외·잠금 대기·롤백·생성 이벤트 1회 검증을 포함한다.

### TS-015 DTO 변환 메서드 오버로드 뒤 Mockito 인자 타입 추론이 모호해짐

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: 최초 응답 스냅샷용 OrderResult.from 오버로드를 추가한 뒤 기존 테스트의 `OrderResult.from(invocation.getArgument(0))`가 `reference to from is ambiguous`로 컴파일에 실패했다.
- 원인: Mockito getArgument의 제네릭 반환 타입과 두 from 오버로드가 겹쳐 Java가 Order와 OrderCreationSnapshot 중 인자 타입을 확정하지 못했다.
- 해결: 테스트에서 `invocation.getArgument(0, Order.class)`로 실제 Writer 인자 타입을 명시했다. 제품 코드의 DTO 변환 계약은 유지했다.
- 확인: 주문 347개와 전체 build 통과. 타입을 명시한 기존 생성 서비스 테스트도 포함한다.

### TS-016 삭제 스모크의 장애 주입 DDL이 요청과 순환 대기를 만듦

- 서비스·작성: order Gateway 격리 검증 / Junhyun An 작업 / 2026-10-15
- 문제: 정상 삭제는 통과했으나 로컬 커밋 실패 시나리오에서 드라이버가 20초 후 TimeoutError를 냈다. I-13 HTTP 대역 내부에서 p_order_deletions에 CHECK를 추가하도록 작성했다.
- 원인: O-06은 주문·진행 기록을 읽은 트랜잭션에서 I-13 응답을 기다린다. 대역의 ALTER TABLE은 그 트랜잭션의 테이블 잠금이 끝나기를 기다려 서로 진행하지 못했다. 제품의 재요청 로직이 아니라 장애 주입 위치의 문제였다.
- 해결: CHECK를 DELETE 요청 전에 설치했다. 대역은 원격 삭제 응답만 처리하고, 로컬 완료 기록 저장에서 CHECK가 실패하게 했다. 격리 서버의 Feign 타임아웃도 명시했다.
- 확인: Gateway에서 첫 요청 500·재요청 200, 재요청의 I-10 생략·I-13 반복과 로컬 삭제 완료를 확인했다. 초기 정리에서 대기 중이던 자체 JVM은 종료를 재확인했고 최종 실행의 임시 JVM·DB도 정리했다.

### TS-017 I-13의 void Feign 반환이 202도 삭제 완료로 받아들임

- 서비스·작성: order / Junhyun An 작업 / 2026-10-15
- 문제: I-13 대역이 200·202를 반환하는 회귀 테스트에서 O-06이 503 대신 200으로 완료돼 두 테스트가 실패했다.
- 원인: 기존 DeliveryDeleteClient의 void 반환은 정상 2xx 처리 후 종료해 명세의 204 여부를 검사하지 않았다. 특히 202는 접수 상태이므로 삭제 완료를 뜻하지 않는다.
- 해결: infrastructure에서 ResponseEntity<Void>를 받고 Adapter에서 204를 확인한다. 다른 2xx는 SERVICE_UNAVAILABLE로 올려 로컬 삭제를 롤백하고 진행 기록을 보존한다. application의 DeliveryGateway와 생성 보상 재시도 횟수는 유지했다.
- 확인: 두 회귀 테스트를 포함한 전체 build 성공. 주문 421개·전체 XML 519개, 실패·오류·skip 0이다.

### TS-018 운영 UI의 입력 라벨·모달 알림·텍스트 대비 오류

- 서비스·작성: frontend / Junhyun An 작업 / 2026-10-15
- 문제: 브라우저 E2E가 공급업체 선택의 정확한 라벨을 찾지 못했고, 모달 안에서 저장 완료 알림을 접근성 트리로 확인하지 못했다. axe는 작은 보조 텍스트의 대비 부족을 보고했다.
- 원인: label이 select와 option 전체를 감싸 접근 가능한 이름이 길어졌다. Radix의 모달 배경 숨김이 React 루트 안의 토스트까지 숨겼다. 밝은 배경에 회색 글자가 충분히 어둡지 않았다.
- 해결: Field에서 useId·htmlFor로 라벨·입력을 분리하고 힌트는 aria-describedby로 연결했다. 알림은 body Portal의 항상 존재하는 aria-live 영역으로 옮겼다. 보조 글자와 상태 텍스트의 대비를 보강했다.
- 추가 확인: Dialog.Trigger 없이 연 확인창은 기본 triggerRef로 초점이 복귀하지 않았다. 열릴 때 활성 요소를 기억하고 닫힐 때 연결된 요소로 복귀하도록 고쳤다. 모바일에서 닫힌 사이드바는 visibility:hidden으로 초점 대상에서 제외했다.
- 확인: 실제 주문 생성·요청사항 저장·취소·삭제와 중첩 확인창 초점 복귀·모바일 폼의 키보드 테스트를 통과했다. 대시보드·모바일 주문 폼의 axe WCAG A/AA 검사에서 위반 0개였다. 애니메이션 완료 후 검사해 중간 프레임의 대비 측정을 피했다.

### TS-019 모바일 모달을 열면 문서 폭이 화면 밖으로 늘어남

- 서비스·작성: frontend / Junhyun An 작업 / 2026-10-15
- 문제: 390px 주문 목록에서 상세 패널을 열면 문서 scrollWidth가 778px이 됐다. 모달 전에는 390px이었다.
- 원인: Radix 스크롤 잠금이 body를 relative로 만들면서 표의 절대 배치된 sr-only 설명이 표 스크롤 영역의 클리핑 밖에 놓였다.
- 해결: `.table-scroll`을 relative로 만들어 숨김 설명의 기준을 스크롤 영역 안에 뒀다. DOM에서 적용 전 778px·후 390px을 확인했다.
- 확인: 모바일 E2E에 모달이 열린 상태의 문서 폭 검증을 추가했고 통과했다. 실제 viewport 캡처도 확인했다.

### TS-020 Spring 통합 테스트를 추가하자 PostgreSQL 연결 수가 초과됨

- 서비스·작성: order·user / Junhyun An 작업 / 2026-10-16
- 문제: 새 DM 통합 테스트를 추가한 전체 build에서 기존 OrderRepositoryIntegrationTest 18개가 `too many clients already`로 실패했다. 같은 테스트를 단독으로 실행할 때의 도메인 오류가 아니었다.
- 원인: 캐시된 Spring 테스트 컨텍스트마다 Hikari 풀이 기본 10개 연결을 유지해 공유 테스트 PostgreSQL의 최대 연결 100개를 초과했다.
- 해결: 새 OrderDmNotificationIntegrationTest·SlackEmailIntegrationTest만 maximum-pool-size=2, minimum-idle=0으로 제한했다. 제품 설정·기존 공용 테스트 기반은 바꾸지 않았다.
- 확인: `./gradlew build` 727개 테스트, 실패·오류·skip 0. 실제 커밋·롤백·비동기 발송 검증을 유지했다.

### TS-021 Slack 설정 캡처에 Bot Token이 포함됨

- 서비스·작성: 로컬 Slack 설정 / Junhyun An 작업 / 2026-10-16
- 문제: OAuth 페이지에서 권한 표의 DOM 좌표로 캡처했으나 토큰 영역이 이미지에 포함돼 대화에 노출됐다. 캡처 좌표와 실제 이미지 범위가 달랐으며 도구 내부의 정확한 좌표 변환 원인은 미확인이다.
- 해결: 사용자에게 알리고 승인 후 auth.revoke로 해당 Bot Token만 폐기했다. 이전 인증 거절·새 토큰 발급·동일 권한·수신자 조회를 확인하고 `.env.ui-test`와 테스트 서버에 반영했다. 잘못된 이미지는 삭제하고 토큰이 없는 App Manifest 화면으로 교체했다.
- 추가 영향: 기존 개발 Webhook이 앱 목록에서 사라지고 404 no_service를 반환했다. 폐기 직전 상태를 확인하지 않아 정확한 무효화 시점은 미확인이다. 사용자 승인 후 같은 채널의 새 URL 한 줄만 `.env`에 반영하고 기존 이미지의 주문 컨테이너만 재생성했다. 나머지 8개 컨테이너·DB는 유지했다.
- 확인: 새 Bot 인증·조회 정상, 새 Webhook은 메시지 없는 GET에 400 invalid_payload, 두 주문 서버 health UP. 추가 메시지는 보내지 않았다. 증거와 제한은 [검증 기록](reviews/slack-dm.md)에 있다.
- 배운 점: 자격 증명이 보이는 페이지는 일부 영역이라도 캡처하지 않는다. 비밀값이 없는 별도 화면에서만 캡처하고 실제 이미지의 범위를 확인한 뒤 공유한다.

### TS-022 지정 해제된 배송담당자가 기존 배송을 변경함

- 서비스·작성: delivery / Junhyun An 작업 / 2026-10-19
- 문제: deliveryManager=false이고 기존 managerId와 ID가 같은 USER의 D-03·D-05 요청이 허용됐다. 거절 회귀 테스트는 단위·HTTP 4건, 프런트 2건이 실패했다.
- 원인: 현재 자격을 검사하지 않고 배정 ID 일치 또는 MASTER만 확인했다.
- 해결: DeliveryService.validateAssignedManager가 MASTER 또는 현재 배송담당자인 해당 구간 담당자만 허용한다. 프런트 nextRouteAction도 같은 조건으로 맞췄다.
- 확인: 배송 61개·프런트 단위 48개 통과. 거절 후 값 보존과 MASTER false 허용을 검증했다. 실제 Gateway·기존 JWT·캐시 만료 뒤 403·MASTER 재배정 회귀도 단독 실행 통과했다. 상세 범위는 [T07](../delivery-service/docs/tasks/T07.md)이다.

### TS-023 /users/me로 Gateway 권한 캐시 만료를 잘못 판단함

- 서비스·작성: frontend E2E / Junhyun An 작업 / 2026-10-19
- 문제: 해제 후 /users/me가 false여서 곧바로 배송 API의 403을 기대했으나 200이었다. 첫 전체 E2E는 기존 18개 통과·신규 1개 실패였다.
- 원인: /users/me는 user-service의 최신 DB 값을 응답한다. Gateway의 로컬 캐시가 주입하는 자격은 아직 true일 수 있다.
- 해결: 실제 배송 상태 API에 금지된 WAITING 전이를 보낸다. 배정과 자격이 유효하면 상태 검사에서 409, 해제가 Gateway에 반영되면 인가에서 403이다. 데이터를 바꾸지 않고 실제 60초 TTL 반영을 기다린다. 원상 복구도 같은 방식으로 확인한다. UI 버튼 부재는 구간 데이터 로딩 후 검사한다.
- 확인: 수정한 Gateway 회귀 단독 실행 1개 통과(약 2.1분). 제품 TTL·캐시 검사는 변경하지 않았다.

### TS-024 업체 삭제 후 동시 생성 상품이 활성 상태로 남음

- 서비스·작성: company / Junhyun An 작업 / 2026-10-19.
- 문제: 실제 PostgreSQL에서 생성 선행·삭제 선행 모두 삭제된 업체 아래 활성 상품이 남거나 생성이 성공했다. 수정 전 동시성 회귀 3건 중 2건이 실패했다.
- 원인: C-05는 보이는 상품만 조회해 삭제하고 P-01은 일반 조회 후 저장했다. 두 경로가 같은 업체의 활성 상태와 커밋 순서를 보호하지 않았다. FK는 물리 업체 행이 남아 있어 통과한다.
- 해결: 두 서비스의 기존 트랜잭션이 CompanyRepository.findByIdForUpdate·PESSIMISTIC_WRITE를 공유한다. 잠금 획득 후 권한을 검사하고 커밋까지 유지한다.
- 확인: company 50개 통과. 실제 pg_blocking_pids와 최종 DB 값으로 두 실행 순서·다른 업체 독립 진행을 확인했다. 상세는 [#15 검증](reviews/2026-10-19-issue-15-verification.md)이다.

### TS-025 통합 테스트 컨텍스트가 늘자 PostgreSQL 연결이 고갈돼 무관한 테스트 18개가 실패함

- 서비스·작성: order(테스트) / Junhyun An 작업 / 2026-10-19
- 문제: 비동기 접수(#16) 구현 뒤 `./gradlew :order-service:test --rerun-tasks`가 463개 중 18개 실패했다. 18개가 모두 `OrderRepositoryIntegrationTest`였고 이 변경과 무관한 클래스다. 핵심 원인 줄은 `ApplicationContext` 로드 실패 아래의 `FlywaySqlException: Unable to obtain connection from database: FATAL: sorry, too many clients already`였다.
- 원인: 통합 테스트는 Testcontainers PostgreSQL 한 개를 모든 Spring 컨텍스트가 공유하고, Spring은 프로퍼티·테스트 설정이 다른 컨텍스트를 캐시해 둔다. 캐시된 컨텍스트마다 Hikari 풀이 열려 있고 기본값(`maximumPoolSize` 10, `minimumIdle`은 `maximumPoolSize`와 같음)이라 컨텍스트 하나가 연결 10개를 계속 쥔다. 컨텍스트가 늘면 PostgreSQL 기본 `max_connections`(100, 공식 문서 기본값)에 닿는다. 수정 후 실행에서 한 JVM에 풀 12개(`HikariPool-12`)가 만들어졌다. 새 비동기 통합 테스트가 컨텍스트 1개를 더 만든 것이 한계를 넘긴 계기라고 추정한다(변경 전 컨텍스트 수는 측정하지 않았다).
- 해결: 1차로 order 테스트 전용 풀 상한(PR #18)을 두었다. 근본 대책으로 `common` 테스트 지원에 `spring.properties`(`spring.test.context.cache.maxSize=8`)를 추가해 캐시 컨텍스트를 8개로 제한하고 order 로컬 풀 상한은 제거했다. 최악 연결은 8 x 10 = 80개로 한도 안이다.
- 확인: 같은 코드·같은 명령에서 `script/test-db-connections.sh :order-service:test --rerun-tasks`로 연결 수를 잰 결과 캐시 상한 없이는 최대 91개·96개·91개(idle, 3회)에서 BUILD FAILED, 상한을 두면 최대 72개·80개·80개(3회)에서 BUILD SUCCESSFUL이었다. 컨테이너 `max_connections`는 100(예약 3)이다. 측정 과정은 [작업 계획](reviews/2026-10-19-test-connection-exhaustion-plan.md), 배경은 [STAR 기록](reviews/2026-10-19-test-connection-exhaustion.md)에 있다.
- 배운 점: 실패한 테스트가 변경과 무관해 보이면 테스트 자체보다 자원 공유를 의심한다. 에이전트 여러 개가 같은 `build` 디렉터리에서 Gradle을 동시에 돌리면 `Could not write XML test results`로 실패하는데, 이는 연결 고갈과 별개의 원인(추정)이므로 병렬 실행을 피한다.

### TS-026 실험용 설정으로 기동하자 postgres가 뜨지 않음
- 서비스·작성: infra / 한얼 / 2026-10-16
- 문제: `script/run.sh --reset` 중 `✘ Container logistics-management-postgres-1 Error dependency postgres failed to start`. 뒤 서비스가 모두 기동하지 못했다.
- 원인: `.env`가 2026-10-13에 만들어진 뒤 `.env.example`에 추가된 `POSTGRES_USER`, `POSTGRES_PASSWORD`가 없었다. `docker-compose.yml`은 이 값을 기본값 없이 넘기므로 postgres 이미지가 관리자 비밀번호 없이 시작하다 종료된다. `script/run.sh`는 `.env`가 없을 때만 복사하므로 기존 `.env`는 갱신되지 않는다.
- 해결: `.env.example`의 두 줄을 `.env`에 추가하고 다시 기동했다.
- 배운 점: pull 뒤 `.env.example`이 바뀌었으면 키를 비교한다. `comm -13 <(grep -v '^#' .env | cut -d= -f1 | sort) <(grep -v '^#' .env.example | cut -d= -f1 | sort)`가 `.env`에 없는 키를 보여 준다.

### TS-027 장애 격리 실험(S1)에서 배송 생성이 장애 전부터 100% 실패
- 서비스·작성: delivery / 한얼 / 2026-10-19
- 문제: `load-test/fault-isolation.sh` 결과에서 생성 확인 4,773건 모두 실패(약 30ms), 장애 중 조회 실패가 3~4초에 몰림.
- 원인: `COMPOSE_FILE`을 export하지 않은 터미널에서 `script/run.sh --reset`을 실행해 덧붙임 파일이 빠졌다. 부하 테스트 API가 꺼져 405, Gateway 권한 캐시도 기본 60초라 user가 멈춘 동안 만료되면 Gateway가 3초 뒤 503을 냈다. k6 요약에는 상태 코드가 없어 증상과 설정으로 추정했다.
- 해결: 실행 스크립트가 시작 전에 `docker compose config`로 실험 설정 두 개를 확인하고, k6 `setup()`이 부하 테스트 API에 빈 본문을 보내 400이 아니면 멈추게 했다.
- 배운 점: 실험 전제 조건은 사람이 기억하지 않고 스크립트가 확인하게 한다.

### TS-028 기동 직후 부하 테스트에서 delivery 커넥션 풀이 바닥나 모든 요청이 10초 타임아웃
- 서비스·작성: delivery / 한얼 / 2026-10-19
- 문제: 장애를 넣기 전부터 생성·조회의 84~96%가 k6 타임아웃(10초). delivery 로그 `HikariPool-1 - Connection is not available, request timed out after 30009ms (total=10, active=9, idle=1, waiting=157)`. 부하가 끝날 때까지 회복하지 않았다.
- 원인: 기동 1분 안에 초당 20+20건을 넣어 예열 안 된 JVM이 요청마다 CPU를 많이 썼다(delivery 551%, user 319%, gateway 296%). 처리가 느려져 커넥션 10개가 금방 찼고, k6가 10초에 포기한 뒤에도 서버는 그 요청을 끝까지 처리해(서버 기준 201 1,668건) 밀린 일이 계속 쌓였다. 같은 속도의 두 번째 실행은 CPU가 10분의 1이었고 GC는 원인이 아니었다(누적 0.6초, 힙 30% 이하).
- 해결: 측정 전 같은 속도로 1분씩 2번 이상 예열하고, 측정 사이에는 `--reset` 대신 `load-test/reset-delivery-data.sh`로 배송 테이블만 비운다. 실행 스크립트가 부하 중 컨테이너 CPU·커넥션 풀·GC를 `-stats.txt`로 남긴다. 상태 수집은 `load-test/diagnose.sh`.
- 배운 점: 로컬 성능 측정은 JVM 예열 상태를 통제하지 않으면 측정값이 코드가 아니라 예열 정도를 보여 준다. k6 타임아웃만 보면 원인을 알 수 없어 서버 쪽 지표를 같이 남겨야 한다.
- 재발(2026-10-19 12:30~12:35): 서킷 설정을 반영하려고 delivery를 재시작한 뒤 예열 실행부터 초당 40+40으로 돌렸다(앞 실험에서 셸에 남은 `CREATE_RATE`·`READ_RATE` 40으로 추정). 예열 실행에서 이미 CPU 781%·커넥션 대기 101이었고, 쉬지 않고 이어 돌린 cap4-40(시작 40초에 대기 170)과 s1-resilience-40b도 장애 전부터 90% 안팎이 10초 타임아웃이었다. k6가 포기한 요청을 서버가 계속 처리해 밀린 일이 다음 실행으로 넘어갔다. 처음에는 맥 용량 한계로 봤지만, 예열된 11:59에는 같은 부하를 CPU 76%로 처리했고 13:30 예열 후 재측정도 CPU 15%·실패 0.4%였다. 데이터 누적(배송 6,733건)도 원인에서 뺐다. 대처: 재시작 뒤에는 속도를 명시해(`CREATE_RATE=10 READ_RATE=10`) 낮은 속도부터 예열하고, 무너진 실행 뒤에는 쉬었다가 배송 데이터를 비운 뒤 다시 잰다.

### TS-029 장애 격리 실험 뒤 user-service가 멈춘 채 남아 복구 구간이 무효
- 서비스·작성: delivery / 한얼 / 2026-10-19
- 문제: S1에서 user-service를 되살린 뒤에도 배송 생성이 12ms 만에 100% 실패했다. `docker compose ps`에서 user-service가 `paused`였다.
- 원인: 실행 스크립트가 장애 중 상태를 모으려고 멈춘 user-service에 `docker compose exec`를 했고, 실패가 `set -euo pipefail`에 걸려 스크립트가 그 자리에서 끝났다. 뒤의 `unpause`가 실행되지 않았다. k6는 백그라운드라 계속 돌아 복구 구간과 그 뒤를 잘못 쟀다.
- 해결: 상태 수집 실패가 스크립트를 끝내지 않게 하고(`|| true`, 실행 중이 아닌 컨테이너 건너뜀), `trap restore EXIT`로 어떻게 끝나든 user-service를 되살린다. 복구 40초 뒤 상태도 수집한다. 멈춘 채 남았다면 `docker compose unpause user-service`.
- 배운 점: 장애를 주입하는 스크립트는 정상 종료 경로가 아니라 `trap`으로 원상 복구를 보장한다.

### TS-030 resilience4j를 넣자 delivery 통합 테스트가 모두 컨텍스트 로드 실패
- 서비스·작성: delivery / 한얼 / 2026-10-19
- 문제: `./gradlew :delivery-service:test`에서 통합 테스트 4개 클래스(16개)가 `Failed to load ApplicationContext`. 원인 줄은 `ClassNotFoundException: io.github.resilience4j.spring6.fallback.RxJava3FallbackDecorator`(`FallbackConfigurationOnMissingBean` 조건 처리 중).
- 원인: `resilience4j-spring-boot3`만 버전(2.3.0)을 적었다. 그 의존성인 `resilience4j-spring6` 등은 Spring Cloud BOM이 관리하는 다른 버전으로 올라와, spring-boot3 자동 설정이 참조하는 클래스가 spring6 jar에 없었다(버전 불일치로 판단, 의존성 트리로 확인 필요).
- 해결: delivery `build.gradle`에 `resilience4j-bom:2.3.0`을 dependencyManagement로 가져와 resilience4j 모듈 버전을 한 번에 맞췄다. 의존성에는 버전을 적지 않는다.
- 배운 점: 여러 모듈로 나뉜 라이브러리는 한 모듈만 버전을 올리지 말고 BOM으로 맞춘다. 확인: `./gradlew :delivery-service:dependencies --configuration runtimeClasspath | grep resilience4j`


### TS-031 격리 프로젝트의 주문 장애 실험이 의도한 종료 전에 OOM으로 중단됨

- 서비스·작성: order 장애 재현 도구 / Junhyun An 작업 / 2026-10-20.
- 문제: 구현 후 F2-01에서 `docker kill`이 container is not running으로 실패했다. inspect의 OOMKilled=true·exit 137과 Docker oom 이벤트를 확인했다. 의도한 SIGKILL 실험으로 세지 않고 판정 제외했다.
- 원인: Docker VM의 물리 메모리는 7.748GiB였으며 기존 UI 스택과 새 실험 스택이 자원을 공유했다. 실험 JVM의 커널 OOM 종료는 확인했다. 기존 UI 컨테이너 일부의 짧아진 uptime도 관찰했지만 해당 재시작의 원인과 정확한 기여 프로세스는 확인하지 못했다.
- 해결: 전용 프로젝트만 내려 메모리를 확보했다. 로컬 override에서 전용 일반 JVM의 heap 128MiB·컨테이너 448MiB, order heap 192MiB·컨테이너 512MiB, 메타스페이스 160MiB·코드 캐시 64MiB·CPU 수 2·스택 512KiB로 제한하고 자동 재시작을 껐다. 기본 Compose·기존 UI 설정·업무 시간 제한은 바꾸지 않았다.
- 확인: 새 runId의 C0-F1-02·C0-F2-02와 AFTER-F1-02·AFTER-F2-02를 다시 실행해 정상 대조군과 실제 종료·복구를 확인했다. 두 의도한 종료는 OOMKilled=false·exit 137이었다. 기존 컨테이너 12개 ID·현재 실행 상태와 전용 볼륨 보존을 확인했다. 기존 환경에 영향이 없었다고 주장하지 않는다.
- 배운 점: Compose 프로젝트·볼륨·포트를 나눠도 VM 메모리는 공유된다. 환경 조정 전후를 구분하고, 이번 비교는 데이터 상태와 안전한 복구의 비교로 한정한다. 처리 시간·처리량의 성능 비교로 사용하지 않는다.
- 근거: [복구 전후 결과](reviews/order-creation-recovery/2026-10-20-implementation-results.md), `.local/recovery-experiments/implementation-F2-interruption-audit/`와 `implementation-memory-containment/`의 명령·inspect·Docker 이벤트.

### TS-032 과거 배송 100만 건에서 측정 사이 배송 삭제가 공유 메모리 부족으로 실패
- 서비스·작성: delivery 성능 측정(T10) / 한얼 / 2026-10-22
- 문제: 성능 마스터 측정의 첫 칸에서 `load-test/reset-delivery-data.sh`가 `ERROR: could not resize shared memory segment "/PostgreSQL.…" to 67145312 bytes: No space left on device`로 끝났다. 과거 배송 30만 건으로 잴 때는 없던 오류다.
- 원인: 측정 사이에 구간 테이블(294만 건)을 `VACUUM ANALYZE`한다. PostgreSQL 16은 인덱스 정리를 병렬 작업자로 나누고, 그 작업 공간(약 64MB)을 동적 공유 메모리(`/dev/shm`)에 잡는다. Docker 컨테이너의 `/dev/shm` 기본값이 64MB라 모자랐다. 병렬 쿼리도 같은 영역을 쓰므로 측정 중에 실패할 수도 있었다.
- 해결: 측정용 덧붙임(`load-test/docker-compose.perf.yml`)에서 postgres에 `shm_size: 256m`을 주고 컨테이너를 다시 만들었다(데이터는 볼륨에 남는다). 팀 공유 `docker-compose.yml`은 바꾸지 않았다. 다시 실행한 삭제·VACUUM은 1.6초에 끝났다.
- 배운 점: 데이터를 키우면 쿼리 계획만이 아니라 DB 프로세스의 자원 한계(여기서는 공유 메모리)도 바뀐다. 컨테이너 안 PostgreSQL은 `docker compose exec postgres df -h /dev/shm`으로 크기를 먼저 본다.
