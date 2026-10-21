# feat/hub-crud

## 지금 하는 일
1차 PR을 `dev`에 병합했다(2026-10-16 확인). 남은 일은 `feat-hub-query.md`로 옮겼고 이 파일은 1차 작업의 완료 기록이다.

## 진행 상태
- [x] 현재 브랜치와 작업 트리를 확인했다. `git branch --show-current` 결과는 `seungkeun/hub`이며, 이 브랜치의 기존 progress 파일은 없다(2026-10-15).
- [x] 기존 구현을 확인했다. `hub-service`의 Java 소스는 `HubServiceApplication`, `infrastructure/config/FeignConfig`뿐이며, 서비스 전용 Entity·조회 API·마이그레이션·테스트는 없다.
- [x] 공통 기반을 확인했다. `hub-service/build.gradle`에 common·JPA·Validation·PostgreSQL·공통 testFixtures 의존성이 있고, 루트 빌드에서 Flyway와 QueryDSL 설정을 제공한다. 실행 검증은 하지 않았다.
- [x] SA 5.3·6.3·6.5·10.1과 코딩·학습·품질 기준을 대조해 작업 범위와 아래 구현 순서를 문서로 정리했다.
- [x] 허브 17개 시드를 `V2__seed_hubs.sql`로 적재했다(2026-10-15). 좌표는 OpenStreetMap Nominatim 지오코딩 결과(소수 7자리)다. 청사 등 건물로 일치한 곳(인천·대전·세종·전북 등)과 도로·행정구역 단위로 근사한 곳(부산·울산·강원·충남·경기 남부 등)이 섞여 있어 거리 계산용으로는 충분하나 정밀 지도 좌표는 아니다. 도로명 주소 4건(충북·전남·경북·경남 도청)은 주소 조회가 비어 청사 이름으로 조회했다. UUID는 임의 생성해 SQL에 고정했고 아직 company·user·delivery 담당자와 합의하지 않았다. `HubSeedIntegrationTest`(17개 이름, 한국 범위 좌표·중복 없음, SYSTEM Audit)와 기존 조회 테스트를 합해 `:hub-service:test` 23개 통과.
- [x] `Hub`, `HubRoute` Entity와 `V1__create_hub_tables.sql`(CHECK 3개, 두 허브 FK, 부분 유니크 인덱스)을 작성했다(2026-10-15). `HubRoute.create`의 불변식 위반은 `BusinessException(CommonErrorCode.INVALID_REQUEST)`로 던진다(처음엔 `IllegalArgumentException`으로 했다가 교체). 컴파일만 확인했고 Flyway·Hibernate validate·DB 제약은 통합 테스트 전까지 미검증이다.
- [x] `HubErrorCode`(`HUB_NOT_FOUND`, `HUB_ROUTE_NOT_FOUND`)를 작성하고 `docs/error-codes.md` 상태를 구현으로 갱신했다(2026-10-15).
- [x] `HubRepository`(domain)와 `HubJpaRepository`(infrastructure/persistence)를 메서드 없이 골격만 만들었다(2026-10-15). 조회 메서드는 사용하는 단계에서 추가한다.
- [x] 허브 조회 API H-01·H-02·I-02를 구현했다 (2026-10-15). `HubRepository`(`findById`, `search`), QueryDSL `HubQueryRepositoryImpl`(keyword 이름·주소 부분 일치, 정렬 허용 `createdAt`·`name`, PK를 마지막 정렬 기준으로 추가, count 분리), `HubQueryService`, `HubController`, `InternalHubController`, DTO 2개. `.\gradlew.bat :hub-service:test`로 20개 통과 (서비스 단위 5, MVC 6, PostgreSQL 통합 9). 통합 테스트는 직접 넣은 허브로 검색·정렬·페이징·논리 삭제 제외·404·잘못된 정렬 400·내부 응답 비래핑을 확인했다. 설명은 `../learning/seungkeun/steps`.
- [x] 주문 취소·Saga 보상을 위한 허브 재고 처리 설계를 정리했다(2026-10-15, ADR은 아직 작성하지 않았고 사용자가 요청할 때 추가한다). 재고 부족 시 주문은 성공하고 `WAITING_STOCK`, 입고는 즉시 반영, 대기 주문은 order가 같은 `orderId`로 재시도한다. 문서 작업이며 코드는 바꾸지 않았다.
- [x] `HubRouteRepository`(`findById`, `search`)와 QueryDSL `HubRouteQueryRepositoryImpl`을 구현했다(2026-10-15). 출발·도착 허브 ID 선택 필터, 정렬 허용 `createdAt`·`distanceKm`·`durationMinutes`에 PK tie-breaker, 목록은 두 허브 fetch join(별칭 `departureHub`·`arrivalHub`), count는 분리했다. 단건은 `HubRouteJpaRepository.findById`를 `@EntityGraph`로 오버라이드했다. `HubRouteRepositoryIntegrationTest` 7개(필터 조합, 논리 삭제 제외, 정렬·tie-breaker, 허용하지 않은 정렬 거부, 목록·단건 쿼리 1회로 N+1 없음)를 포함해 `:hub-service:test` 30개 통과. I-03용 `findByDepartureHubIdAndArrivalHubId`는 쓰는 단계에서 추가한다.
- 면접 포인트: 같은 서비스의 FK와 서비스 간 UUID 참조는 왜 구분하는가? 조회 전용 서비스에서 Flyway와 초기 데이터 적재를 어떻게 나누는가?

## 다음 단계
남은 단계는 [`feat-hub-query.md`](feat-hub-query.md)에서 이어간다. 아래 "직접 해보기"와 "메모"는 1차 작업 당시 기록이다.

## 직접 해보기
1. 현재 브랜치와 구현 시작점을 확인한다.
   - CLI: 저장소 루트에서 `git branch --show-current`, `git status --short`, `rg --files hub-service`를 실행한다.
   - IntelliJ: 오른쪽 아래 브랜치 표시와 Project 창의 `hub-service/src/main`을 확인한다.
   - 확인: 브랜치는 `seungkeun/hub`이고, 작업 시작 시 Java 소스는 기동 클래스와 `FeignConfig` 두 개다. 다른 변경이 있으면 먼저 diff를 확인한다.
2. 설계와 스키마를 대조한다.
   - CLI: `Get-Content -LiteralPath 'docs/SA/sparta-logistics-sa.md'`로 5.3·6.3·6.5·10.1을 읽고 `Get-ChildItem hub-service/src/main/resources`로 리소스를 확인한다.
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
- 재고 팀 공유 대기: order(`WAITING_STOCK`, 재시도 스케줄러, I-05 "확보 안 됨" 처리), company(I-04 호출), SA 수정(결정 9, 5.3절, I-05, 주문 상태)은 다른 담당자·팀 정본 소관이라 개발 후 공유한다. 미결은 재고 있을 때 집하 구간 생략 여부와 입고 장기 지연 시 자동 취소다. hub가 order를 호출하지 않고 order가 재시도하는 이유(순환 호출·이중 쓰기 회피)와 대안(hub 직접 호출, Kafka 이벤트)은 ADR로 남길 때 정리한다.
- 인계 유지 규칙: 이 문서의 다섯 섹션 제목을 유지한다. 완료 체크에는 검증 근거를 붙이고, 실제 완료한 작업 상세는 지우지 않고 누적한다.
