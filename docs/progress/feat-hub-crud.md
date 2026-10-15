# seungkeun/hub

## 지금 하는 일
hub-service의 허브·허브 간 경로 조회 기능 구현을 준비한다.

## 진행 상태
- [x] 현재 브랜치와 작업 트리를 확인했다. `git branch --show-current` 결과는 `seungkeun/hub`이며, 이 브랜치의 기존 progress 파일은 없다(2026-10-15).
- [x] 기존 구현을 확인했다. `hub-service`의 Java 소스는 `HubServiceApplication`, `infrastructure/config/FeignConfig`뿐이며, 서비스 전용 Entity·조회 API·마이그레이션·테스트는 없다.
- [x] 공통 기반을 확인했다. `hub-service/build.gradle`에 common·JPA·Validation·PostgreSQL·공통 testFixtures 의존성이 있고, 루트 빌드에서 Flyway와 QueryDSL 설정을 제공한다. 실행 검증은 하지 않았다.
- [x] SA 5.3·6.3·6.5·10.1과 코딩·학습·품질 기준을 대조해 작업 범위와 아래 구현 순서를 문서로 정리했다.
- [x] 허브 17개 시드를 `V2__seed_hubs.sql`로 적재했다(2026-10-15). 좌표는 OpenStreetMap Nominatim 지오코딩 결과(소수 7자리)다. 청사 등 건물로 일치한 곳(인천·대전·세종·전북 등)과 도로·행정구역 단위로 근사한 곳(부산·울산·강원·충남·경기 남부 등)이 섞여 있어 거리 계산용으로는 충분하나 정밀 지도 좌표는 아니다. 도로명 주소 4건(충북·전남·경북·경남 도청)은 주소 조회가 비어 청사 이름으로 조회했다. UUID는 임의 생성해 SQL에 고정했고 아직 company·user·delivery 담당자와 합의하지 않았다. `HubSeedIntegrationTest`(17개 이름, 한국 범위 좌표·중복 없음, SYSTEM Audit)와 기존 조회 테스트를 합해 `:hub-service:test` 23개 통과.
- [x] `Hub`, `HubRoute` Entity와 `V1__create_hub_tables.sql`(CHECK 3개, 두 허브 FK, 부분 유니크 인덱스)을 작성했다(2026-10-15). `HubRoute.create`의 불변식 위반은 `BusinessException(CommonErrorCode.INVALID_REQUEST)`로 던진다(처음엔 `IllegalArgumentException`으로 했다가 교체). 컴파일만 확인했고 Flyway·Hibernate validate·DB 제약은 통합 테스트 전까지 미검증이다.
- [x] `HubErrorCode`(`HUB_NOT_FOUND`, `HUB_ROUTE_NOT_FOUND`)를 작성하고 `docs/error-codes.md` 상태를 구현으로 갱신했다(2026-10-15).
- [x] `HubRepository`(domain)와 `HubJpaRepository`(infrastructure/persistence)를 메서드 없이 골격만 만들었다(2026-10-15). 조회 메서드는 사용하는 단계에서 추가한다.
- [x] 허브 조회 API H-01·H-02·I-02를 구현했다(2026-10-15). `HubRepository`(`findById`, `search`), QueryDSL `HubQueryRepositoryImpl`(keyword 이름·주소 부분 일치, 정렬 허용 `createdAt`·`name`, PK를 마지막 정렬 기준으로 추가, count 분리), `HubQueryService`, `HubController`, `InternalHubController`, DTO 2개. `.\gradlew.bat :hub-service:test`로 20개 통과(서비스 단위 5, MVC 6, PostgreSQL 통합 9). 통합 테스트는 직접 넣은 허브로 검색·정렬·페이징·논리 삭제 제외·404·잘못된 정렬 400·내부 응답 비래핑을 확인했다. 설명은 `docs/learning/steps/step-22-hub-query-api.md`.
- [ ] `HubRouteRepository`와 경로 조회 메서드(`findById`, H-03 `search`)를 작성한다.
- [ ] 빈 경로 테이블에 방향별 경로 272개를 생성한다(허브 17개 적재는 위에서 완료).
- [ ] 경로 조회 API H-03·H-04와 내부 조회 API I-03을 구현한다(H-01·H-02·I-02는 위에서 완료).
- [ ] 검색·정렬·페이징·오류 응답과 DB 제약을 검증한다.
- [ ] 모듈 테스트·전체 빌드·실제 기동·Gateway 경유 호출을 검증하고 결과를 누적한다.
- 면접 포인트: 같은 서비스의 FK와 서비스 간 UUID 참조는 왜 구분하는가? 조회 전용 서비스에서 Flyway와 초기 데이터 적재를 어떻게 나누는가?

## 다음 단계
1. 기준 문서를 읽고 구현 범위를 고정한다.
   - 설계 정본: `docs/SA/Sparta_logistics_SA_Master .md`의 5.3(스키마), 6.1(공통 응답·검색), 6.3(내부 API), 6.5(허브 API), 10.1(초기 데이터)을 따른다.
   - 구현 전 `docs/agents/coding-conventions.md`, 검증 전 `docs/agents/quality-gates.md`를 읽는다.
   - 허브·경로는 초기 적재 후 조회 전용이다. 생성·수정·삭제 API, 재고·입출고·재고 이력, 경로 최적화는 기본 범위에 포함하지 않는다.
   - SA와 다른 구현이 필요하면 먼저 사용자에게 확인하고 `docs/decisions/`에 근거를 남긴다.
2. DB 스키마와 도메인을 구현한다.
   - 위치: `hub-service/src/main/java/com/sparta/logistics/hub/` 아래 `presentation / application / domain / infrastructure`를 따른다.
   - `domain`에 `Hub`, `HubRoute`, 필요한 저장소 인터페이스, `HubErrorCode`를 둔다. `infrastructure`에 JPA·검색 구현을 둔다.
   - 두 Entity는 `BaseEntity`를 상속하고 각 Entity에 논리 삭제 조회 제외 조건을 적용한다. UUID PK와 공통 Audit 컬럼 6개를 맞춘다.
   - `p_hubs`: 이름 `VARCHAR(50)` NOT NULL·UNIQUE, 주소 `VARCHAR(255)` NOT NULL, 위도·경도 `DECIMAL(10,7)` NOT NULL을 반영한다.
   - `p_hub_routes`: 출발·도착 허브 UUID NOT NULL·FK, 거리 `DECIMAL(8,2)`·소요시간 INT NOT NULL 및 각각 양수 CHECK, 출발·도착 상이 CHECK를 반영한다.
   - 활성 경로의 `(departure_hub_id, arrival_hub_id)`에 `WHERE deleted_at IS NULL` 부분 유니크 인덱스를 둔다. 두 허브는 같은 서비스 테이블이므로 실제 FK를 둔다.
   - SQL은 `hub-service/src/main/resources/db/migration/`의 `V{번호}__{설명}.sql`로 추가한다. 현재 hub 마이그레이션은 없으므로 V1부터 시작할 수 있다. SQL에 스키마명을 하드코딩하지 않고 Hibernate `ddl-auto: validate`를 유지한다.
   - DB 제약은 PostgreSQL 통합 테스트로 검증한다. Entity 매핑 방식 등 세부 구현은 SA의 제약과 계층 규칙 안에서 정한다.
3. 초기 데이터와 거리 계산을 구현한다.
   - SA 10.1의 허브명·주소 17개를 그대로 사용한다. 주소를 한 번 지오코딩한 실제 좌표를 SQL에 고정한다. SA 응답 예시의 위·경도는 실제 시드 값으로 간주하지 않는다.
   - UUID는 다른 서비스가 참조하므로 적재 전 기존 공유 데이터·팀 약속을 확인하고, 재기동마다 바뀌지 않게 시드 SQL에 고정한다.
   - 경로는 `ApplicationRunner`가 경로 테이블이 비어 있을 때 생성한다. 자기 자신을 제외한 순서쌍으로 A→B와 B→A를 각각 저장한다(17 × 16 = 272건).
   - SA 계산식: `이동거리(km) = Haversine 직선거리 × 1.3`, `소요시간(분) = ceil(이동거리 / 70 × 60)`이다. 거리의 소수 둘째 자리 반올림 방식과 시간 계산에 쓰는 정밀도는 구현 시 명시하고 경계값을 테스트한다.
   - 계산 규칙은 단위 테스트를 먼저 작성한다. 초기 적재의 트랜잭션과 재기동 중복 방지를 검증한다. 기동 시 Audit 작성자가 `SYSTEM`으로 채워지는지도 확인한다.
   - 일부 데이터만 남은 상태와 동시 기동 시 동작은 미확인이다. 자동 삭제·재생성 정책을 임의로 추가하지 않고 문제를 드러내는 검증부터 한다.
4. 조회 흐름과 외부 API를 구현한다.

   | ID | GET 경로 | 입력·응답 및 검증 |
   |---|---|---|
   | H-01 | `/api/v1/hubs` | `keyword`로 이름 또는 주소 부분 일치 검색. 정렬 허용 필드는 `createdAt`, `name`이다. |
   | H-02 | `/api/v1/hubs/{hubId}` | `hubId`, `name`, `address`, `latitude`, `longitude`를 반환한다. 없으면 404 `HUB_NOT_FOUND`다. |
   | H-03 | `/api/v1/hub-routes` | 선택 입력 `departureHubId`, `arrivalHubId`로 검색한다. 정렬 허용 필드는 `createdAt`, `distanceKm`, `durationMinutes`다. |
   | H-04 | `/api/v1/hub-routes/{hubRouteId}` | H-03의 경로 항목과 같은 형태다. 없으면 404 `HUB_ROUTE_NOT_FOUND`다. |

   - 경로 응답은 `hubRouteId`, `departureHub: {hubId, name}`, `arrivalHub: {hubId, name}`, `distanceKm`, `durationMinutes`를 포함한다. 허브명 조회에서 N+1이 생기지 않게 쿼리와 응답 매핑을 확인한다.
   - 외부 API는 로그인 사용자에게 제공하며 인증은 Gateway를 거친다. MASTER 전용 제한을 새로 추가하지 않는다.
   - 외부 성공 응답은 `ApiResponse`, 목록 본문은 `PageResponse`를 사용한다. 기본 `page=0`, `size=10`, `sort=createdAt,DESC`이며 size는 10·30·50만 허용하고 나머지는 공통 처리에 따라 10으로 보정한다.
   - `SearchPageableArgumentResolver`, `SortAllowList` 또는 `QuerydslSupport`를 재사용한다. 허용하지 않은 정렬은 400 `VALIDATION_ERROR`로 처리한다.
   - Controller는 입력 검증과 변환을 담당하고, application은 조회 흐름과 DTO를 담당한다. request DTO를 application에 그대로 넘기지 않으며, 조회 트랜잭션은 application public 메서드에 `readOnly = true`로 둔다.
   - 검색 결과 없음은 빈 페이지, 단건 없음은 404로 구분해 테스트한다. 잘못된 UUID의 400 응답과 논리 삭제 데이터 제외도 검증한다.
5. 내부 API를 구현하고 소비 서비스와 계약을 맞춘다.

   | ID | GET 경로 | 성공 본문 | 호출 서비스 |
   |---|---|---|---|
   | I-02 | `/internal/hubs/{hubId}` | `hubId`, `name`, `address` | company, user |
   | I-03 | `/internal/hub-routes?departureHubId=…&arrivalHubId=…` | `hubRouteId`, `distanceKm`, `durationMinutes` 단건 | delivery |

   - 내부 성공 응답은 DTO를 그대로 반환한다(ADR 0010). 외부 API와 같은 오류 형식을 사용한다.
   - I-03은 출발·도착 허브 ID를 필수로 받고 두 필드의 UUID 형식을 검증한다. 존재하지 않는 경로의 오류 계약은 delivery 담당자와 확인한다.
   - 같은 허브끼리 주문하면 delivery가 허브 이동 구간을 생략한다는 SA 규칙을 따른다. 자기 자신으로 가는 0km 경로를 새로 만들지 않는다. I-03에 같은 허브 ID를 직접 보낸 경우의 오류는 계약 확인 항목으로 남긴다.
   - hub-service는 다른 업무 서비스를 호출하지 않는 최하위 서비스다. 조회 기능을 위해 새 외부 서비스 호출을 추가할 필요는 없다.
   - Gateway 경유 `/internal/**` 차단과 서비스 내부 호출을 구분해 확인한다.
6. 테스트와 실행 검증을 하고 결과를 기록한다.
   - 단위 테스트: 거리·시간 계산, 조회 성공·없음, 검색 조건 조합과 오류 흐름을 검증한다. 도메인 계산은 실패하는 테스트부터 시작한다.
   - MVC 테스트: 외부·내부 응답 형태, 필수 파라미터·UUID 검증, 페이징, 정렬 허용 목록, 404 오류 코드를 검증한다.
   - PostgreSQL 통합 테스트: `common` testFixtures의 `PostgresIntegrationTest`를 상속한다. Docker 실행 상태에서 Flyway·Entity 일치, FK·CHECK·부분 유니크 인덱스, 논리 삭제 제외, 17개·272개 적재와 재기동 중복 방지를 검증한다. H2로 대체하지 않는다.
   - 공통 테스트 기반은 Config Server·Eureka를 끈다. hub 전용 스키마 설정이 테스트에도 필요한지 확인하고 필요한 테스트 설정을 명시한다.
   - PowerShell에서 `.\gradlew.bat :hub-service:test`, `.\gradlew.bat build`를 순서대로 실행하고 통과·실패 수와 원인을 기록한다. JAVA_HOME은 바꾸지 않는다.
   - `HubErrorCode`를 구현하면 `docs/error-codes.md`의 두 코드 상태를 함께 갱신하고 Git Bash에서 `bash script/error-codes-check.sh`를 실행한다.
   - 실제 실행 후 허브 검색·단건·경로 검색·단건, 내부 API 두 개, 재기동 후 데이터 건수를 확인한다. 공통 `script/e2e-check.sh`만으로 신규 허브 API 검증을 대체하지 않는다.
   - 단계가 끝날 때 이 파일에 실제 변경·검증 결과를 누적한다. 구현 스텝의 설명과 검증 근거는 `docs/agents/learning-guide.md` 4절에 따라 `docs/learning/steps/`에도 기록한다.

## 직접 해보기
1. 현재 브랜치와 구현 시작점을 확인한다.
   - CLI: 저장소 루트에서 `git branch --show-current`, `git status --short`, `rg --files hub-service`를 실행한다.
   - IntelliJ: 오른쪽 아래 브랜치 표시와 Project 창의 `hub-service/src/main`을 확인한다.
   - 확인: 브랜치는 `seungkeun/hub`이고, 작업 시작 시 Java 소스는 기동 클래스와 `FeignConfig` 두 개다. 다른 변경이 있으면 먼저 diff를 확인한다.
2. 설계와 스키마를 대조한다.
   - CLI: `Get-Content -LiteralPath 'docs/SA/Sparta_logistics_SA_Master .md'`로 5.3·6.3·6.5·10.1을 읽고 `Get-ChildItem hub-service/src/main/resources`로 리소스를 확인한다.
   - IntelliJ: 해당 SA 문서를 열고 허브 컬럼·API 표를 확인한 뒤 `src/main/resources/db/migration`에 마이그레이션을 추가한다.
   - 확인: Entity와 SQL의 컬럼명·타입·제약이 같고, 두 허브 FK와 방향별 경로 유니크 조건을 설명할 수 있다.
3. 테스트를 먼저 실행하고 기능을 구현한다.
   - CLI: Docker Desktop을 실행한 뒤 `.\gradlew.bat :hub-service:test`를 실행한다.
   - IntelliJ: 테스트 클래스 옆 실행 버튼 또는 Gradle 창의 `hub-service > Tasks > verification > test`를 사용한다.
   - 확인: 구현 전에는 의도한 규칙에서 실패하고, 구현 후에는 같은 테스트가 통과한다. 구현 후 `hub-service/build/reports/tests/test/index.html`에서 실패가 없는지 확인한다. 테스트가 없는 현재 상태의 성공은 기능 검증으로 간주하지 않는다.
4. 허브 서비스를 실제로 띄워 확인한다(구현 후).
   - CLI: `.env`가 없다면 `.env.example`을 복사해 개인 로컬 값으로 준비한다. 루트에서 `docker compose up -d --build`를 실행하고 `docker compose ps`, `docker compose logs hub-service`를 확인한다. 기동 후 다시 `docker compose restart hub-service`를 실행해 중복 적재가 없는지 확인한다.
   - IntelliJ: DB·Config Server·Eureka가 준비된 환경에서 `HubServiceApplication.main`을 실행한다. Compose에서도 hub-service가 실행 중이면 중복 실행하지 않도록 먼저 정리하고, README의 포트·DB·Config Server 접속 설정을 따른다.
   - 확인: Flyway와 Hibernate 검증 오류 없이 기동하고 Eureka에 hub-service가 등록된다. 초기 및 재기동 후 DB의 `p_hubs`는 17행, `p_hub_routes`는 272행이다.
5. API 계약과 전체 빌드를 확인한다(구현 후).
   - CLI: `.\gradlew.bat build`와 Git Bash의 `bash script/error-codes-check.sh`를 실행한다.
   - UI: Gateway의 `http://localhost:8080/swagger-ui.html`에서 로그인 토큰을 설정하고 H-01~H-04를 호출한다. 포트를 바꾼 환경에서는 실제 Gateway 포트를 사용한다.
   - 확인: 허브 검색 결과는 17개이고 검색어·페이지 조건에 따라 결과가 달라진다. 경로는 출발·도착 필터를 지원한다. 단건 없음은 지정한 404 코드, 허용하지 않은 정렬은 400이며, 외부 성공 응답은 공통 래퍼를 갖는다. 내부 응답은 별도 내부 API 테스트로 래퍼가 없음을 확인한다.

## 메모
- 작성일: 2026-10-15. 이번 작업은 작업 문서 작성이며 서비스 코드는 변경하지 않았다.
- 검증 범위: 브랜치·작업 트리, 소스·설정·의존성, SA와 공통 코드의 정적 확인이다. Gradle 빌드·테스트·Docker 기동·DB 건수·HTTP 호출은 실행하지 않았으며 미확인이다. 다른 progress 파일의 이전 검증 결과를 이 브랜치의 검증 결과로 옮기지 않았다.
- 기존 미추적 파일 `order-service/src/main/java/com/sparta/logistics/order/domain/OrderService`가 있다. 이번 작업 대상이 아니며 내용을 수정하거나 삭제하지 않았다.
- 실제 좌표 데이터와 출처, 공유 허브 UUID, 거리 반올림 정책, I-03의 같은 허브 입력·없는 경로 오류 계약은 구현 전 확인 항목이다. 미확인 값을 팀 합의로 기록하지 않는다.
- 현재 설정은 `config-repo/hub-service.yml`의 포트 8082·`hub_schema`, 공통 설정의 `ddl-auto: validate`를 사용한다. 로컬 접속 포트와 환경 값은 실행 환경에 맞춰 확인한다.
- Git 소유권 검사로 기본 조회가 차단되어 조회 명령에만 `-c safe.directory=D:/workspace/project/logistics-management`를 붙였다. 전역 Git 설정은 변경하지 않았다.
- 관련 결정: `docs/decisions/0007-flyway-schema-migration.md`, `docs/decisions/0009-common-conditional-auto-configuration.md`, `docs/decisions/0010-internal-api-unwrapped-response.md`. 구현 시 원문을 확인한다. 이번 문서 작성으로 새 설계 결정은 확정하지 않았다.
- 문자열 정렬 순서는 DB 콜레이션에 따른다. 컨테이너 PostgreSQL 16에서 `name,asc`가 가나다 순이 아니어서 통합 테스트는 순서 대신 오름·내림차순이 역순인지를 검증한다. 시드 후 허브 목록을 가나다 순으로 보여줘야 하면 콜레이션 지정 여부를 결정해야 한다(미결정).
- 허브 조회 API는 시드가 없어 실제 기동 호출·Gateway 경유·Swagger 확인은 하지 않았다. 시드 적재 후 검증한다.
- 인계 유지 규칙: 이 문서의 다섯 섹션 제목을 유지한다. 완료 체크에는 검증 근거를 붙이고, 실제 완료한 작업 상세는 지우지 않고 누적한다.
