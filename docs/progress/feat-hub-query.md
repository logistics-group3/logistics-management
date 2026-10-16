# feat/hub-query

## 지금 하는 일
hub-service 허브·경로 조회 구현(SA 5.3·6.5·10.1, I-02·I-03): 경로 272개 생성과 H-03·H-04·I-03이 남았다.

## 진행 상태
- 1차 작업(`feat/hub-crud`, `dev` 병합 완료)의 상세 기록은 [`feat-hub-crud.md`](feat-hub-crud.md)에 있다. 아래 완료 항목은 그 요약이다.
- 기준은 SA(`docs/SA/sparta-logistics-sa.md`)와 협업 창구(`docs/services/hub/README.md`)다. 범위는 허브·경로 조회(H-01~H-04), 시드(허브 17개, 경로 272건), 내부 조회(I-02, I-03)이며 재고(H-05·H-06, I-04~I-06)는 고도화라 기본 범위가 아니다.
- [x] 기반 확인(2026-10-15): `hub-service`에 common·JPA·Validation·PostgreSQL·공통 testFixtures 의존성이 있고, 루트 빌드에서 Flyway·QueryDSL 설정을 제공한다. 작업 시작 시 Java 소스는 `HubServiceApplication`, `FeignConfig`뿐이었다.
- [x] SA 5.3·6.3·6.5·10.1과 코딩·학습·품질 기준을 대조해 범위와 구현 순서를 정리했다. 2026-10-16에 `dev`(b25e2d8)를 병합한 뒤 다시 대조했고 hub 관련 SA 절은 바뀌지 않았다.
- [x] `Hub`, `HubRoute` Entity와 `V1__create_hub_tables.sql`(CHECK 3개, 두 허브 FK, 활성 경로 부분 유니크 인덱스)을 작성했다(2026-10-15). `HubRoute.create`의 불변식 위반은 `BusinessException(CommonErrorCode.INVALID_REQUEST)`다.
- [x] 허브 17개 시드를 `V2__seed_hubs.sql`로 적재했다(2026-10-15). 좌표는 OpenStreetMap Nominatim 지오코딩 결과(소수 7자리)이며 SA 10.1의 "지오코딩해 SQL에 고정"을 따랐다. 건물로 일치한 곳(인천·대전·세종·전북 등)과 도로·행정구역으로 근사한 곳(부산·울산·강원·충남·경기 남부 등)이 섞여 있어 거리 계산용으로는 충분하나 정밀 좌표는 아니다. 도로명 주소 4건(충북·전남·경북·경남 도청)은 청사 이름으로 조회했다. UUID는 임의 생성해 SQL에 고정했고 다른 서비스 담당자와 합의하지 않았다. `HubSeedIntegrationTest`로 17개 이름, 한국 범위 좌표, 중복 없음, SYSTEM Audit을 확인했다.
- [x] `HubErrorCode`(`HUB_NOT_FOUND`, `HUB_ROUTE_NOT_FOUND`)를 작성하고 `docs/error-codes.md` 상태를 구현으로 갱신했다(2026-10-15).
- [x] H-01·H-02·I-02를 구현했다(2026-10-15). `HubRepository`(`findById`, `search`), QueryDSL `HubQueryRepositoryImpl`(keyword 이름·주소 부분 일치, 정렬 허용 `createdAt`·`name`, PK를 마지막 정렬 기준으로 추가, count 분리), `HubQueryService`, `HubController`, `InternalHubController`, DTO 2개. 서비스 단위 5, MVC 6, PostgreSQL 통합 9개로 검색·정렬·페이징·논리 삭제 제외·404·잘못된 정렬 400·내부 응답 비래핑을 확인했다. 설명은 `../learning/seungkeun/steps`.
- [x] 경로 조회 저장소를 구현했다(2026-10-15). `HubRouteRepository`(`findById`, `search`)와 QueryDSL `HubRouteQueryRepositoryImpl`(출발·도착 허브 ID 선택 필터, 정렬 허용 `createdAt`·`distanceKm`·`durationMinutes`에 PK tie-breaker, 두 허브 fetch join, count 분리, 단건은 `@EntityGraph`). `HubRouteRepositoryIntegrationTest` 7개를 포함해 `:hub-service:test` 30개 통과.
- [x] 경로 272개 생성(SA 10.1, 2026-10-16): 시스템은 저장된 경로를 읽기만 하므로 계산 코드를 두지 않고 값을 고정한 `V3__seed_hub_routes.sql`로 적재했다(사용자 결정). 허브 좌표(V2)로 Haversine × 1.3(소수 둘째 자리 HALF_UP), 소요시간 = 반올림한 거리 ÷ 70km/h의 분 올림 값을 한 번 계산해 272행과 `hub_route_id`를 고정했다. 처음에는 SA 10.1대로 `ApplicationRunner`가 기동 시 생성하게 구현했으나(동시 기동 시 경합, 매 기동 조회) 마이그레이션 방식으로 바꿨고 계산기·러너·도메인 저장소 추가 메서드는 모두 제거했다. SA 10.1의 "기동 시 ApplicationRunner" 문구와 다르지만 같은 절이 "실제 값을 산출해 고정해도 된다"고 허용한다. 사용자 지시로 `docs/decisions/`에는 기록하지 않았다. `HubRouteSeedIntegrationTest` 3개(272건과 허브별 출발·도착 16건, 같은 공식을 SQL로 재계산한 거리 대조와 시간 올림 대조, 역방향 대칭. 자기 경로·양수·중복 방향은 V1 CHECK·유니크 인덱스가 막으므로 검사하지 않는다)를 추가했고 `:hub-service:test` 33개가 통과했다. 경로가 생기면서 `HubQueryIntegrationTest`가 `DELETE FROM p_hubs`에서 FK로 실패해 setUp에서 경로부터 비우도록 고쳤다. 보정계수·시속·좌표를 바꾸려면 새 마이그레이션으로 UPDATE한다. 거리 반올림 HALF_UP은 팀 결정이 아니다(아래 메모의 미결정 항목).
- [x] H-03 `GET /api/v1/hub-routes`, H-04 `GET /api/v1/hub-routes/{hubRouteId}` 구현(SA 6.5, 2026-10-16). `HubRouteResult`(중첩 `HubSummary{hubId,name}`), `HubRouteQueryService`(`search`, `getHubRoute`, 경로 없음은 404 `HUB_ROUTE_NOT_FOUND`), `HubRouteController`를 만들었다. 컨트롤러는 application `HubRouteResult`를 presentation `HubRouteResponse`(`presentation/dto`)로 바꿔 반환한다(`controller.md` 규칙, SA 6.5의 응답 객체 이름과 일치). H-01·H-02도 같은 방식으로 `HubResponse`(`presentation/dto`)로 분리했다(2026-10-16, `HubController`가 `HubResult`를 변환해 반환, 응답 JSON 변화 없음). I-02는 `InternalHubResult`를 컨트롤러가 그대로 반환하는 상태로 두었고 `InternalHubResponse`로 분리하는 정리는 다음 작업(I-03)에서 함께 한다. 서비스 단위 4, MVC 6, PostgreSQL 통합 10개(시드 272건 기준 전체·출발/도착/둘 다 필터·없는 허브 빈 페이지·거리 정렬·createdAt 정렬 방향·페이지 겹침 없음·허용하지 않은 정렬 400·단건 404)를 추가했고 `:hub-service:test` 53개가 통과했다. `script/docs-check.sh`는 통과했고 `docs/services/hub/README.md`의 제공 API·구현 현황(H-01~H-04, I-02 구현)을 갱신했다. 2026-10-16에 루트 `./gradlew build`(2분 51초, 성공)와 hub-service 이미지만 다시 빌드한 실제 기동(Flyway V3 적용, `p_hubs` 17행, `p_hub_routes` 272행)을 확인했고, Gateway(8080)를 거쳐 H-03·H-04를 호출했다: 토큰 없음·잘못된 토큰 401, 토큰 있으면 전체 272건(`sort` `createdAt,DESC`), 출발+도착 필터 1건, 단건 조회, 없는 ID 404 `HUB_ROUTE_NOT_FOUND`, 허용 안 된 정렬 400 `VALIDATION_ERROR`(`errors` 배열 포함), `distanceKm,asc` 정렬, `/internal/**` 외부 호출 404. 재기동 후 행 수 유지(재기동 중복 없음)는 이 기동에서 마이그레이션이 한 번만 적용된 것으로 보았고 두 번째 재기동은 하지 않았다. H-01·H-02도 `HubResponse` 분리 후 hub-service를 다시 띄워 Gateway로 호출했다(토큰 없음 401, 목록 17건, `keyword` 경기 2건·서울 1건·덕평로 1건, 단건 200, 없는 ID 404 `HUB_NOT_FOUND`, 잘못된 UUID 400, 허용 안 된 정렬 `latitude` 400, `name` 정렬 200). Windows 셸의 `curl`은 한글 인자를 CP949로 보내 `keyword`가 0건이 되므로 `%EA%B2%BD%EA%B8%B0`처럼 UTF-8로 인코딩해 호출한다.
- [ ] I-03 `GET /internal/hub-routes?departureHubId=&arrivalHubId=` 구현(SA 6.3, 응답 `hubRouteId`, `distanceKm`, `durationMinutes`, DTO 그대로).
- [ ] 협업 로그 OPEN 요청 두 건에 답한다(메모 참고).
- [ ] 검색·정렬·페이징·오류 응답과 DB 제약 검증, `./gradlew :hub-service:test`와 `./gradlew build`, `script/error-codes-check.sh`.
- [ ] 실제 기동과 Gateway(8080) 경유 호출(H-01~H-04, I-02, I-03), 재기동 후 `p_hubs` 17행·`p_hub_routes` 272행 확인.
- [ ] 구현 완료 시 `docs/services/hub/README.md`의 제공 API 표·구현 현황과 `docs/services/hub/notes.md`를 갱신한다.
- 면접 포인트: 같은 서비스의 FK와 서비스 간 UUID 참조는 왜 구분하는가? 조회 전용 서비스에서 Flyway와 초기 데이터 적재를 어떻게 나누는가?

## 다음 단계
1. (완료) 경로 272건 시드(V3). SA 10.1 문구(기동 시 러너)와 다르므로 팀에 공유가 필요하다. 결정 기록은 하지 않기로 했다.
2. 실제 기동 후 `p_hub_routes` 272행 확인은 Docker 기동 단계에서 한다.
3. (완료) H-03·H-04 구현과 테스트.
4. I-03을 구현한다. 출발·도착 허브 ID 필수·UUID 검증, 없는 경로의 오류 코드는 delivery 요청에 답하며 정한다.
5. 검증을 실행하고 결과를 이 파일에 누적한다. 규칙은 `hub-service/AGENTS.md`, 공통 기준은 `docs/agents/quality-gates.md`다.

## 직접 해보기
1. 현재 상태를 확인한다.
   - CLI: `git branch --show-current`, `git status --short`.
   - 확인: 브랜치는 `feat/hub-query`이고 `hub-service`에 Hub·HubRoute, V1·V2 마이그레이션, 허브 조회 API가 있다.
2. 설계와 스키마를 대조한다.
   - CLI: SA의 5.3·6.3·6.5·10.1과 `hub-service/src/main/resources/db/migration`을 나란히 읽는다.
   - 확인: Entity와 SQL의 컬럼·타입·제약이 같고, 두 허브 FK와 방향별 경로 유니크 조건을 설명할 수 있다.
3. 테스트를 실행한다.
   - CLI: Docker Desktop을 켠 뒤 `.\gradlew.bat :hub-service:test`. 결과는 `hub-service/build/reports/tests/test/index.html`.
   - 확인: 경로 생성 구현 전에는 계산 테스트가 실패하고 구현 후 통과한다.
4. 실제로 띄워 확인한다(구현 후).
   - CLI: `docker compose up -d --build` 후 `docker compose restart hub-service`.
   - 확인: Flyway·Hibernate 검증 오류 없이 기동하고 재기동 후에도 `p_hubs` 17행, `p_hub_routes` 272행이다.
5. API 계약과 전체 빌드를 확인한다(구현 후).
   - CLI: `.\gradlew.bat build`, Git Bash에서 `bash script/error-codes-check.sh`.
   - UI: `http://localhost:8080/swagger-ui.html`에서 토큰을 설정하고 H-01~H-04를 호출한다.
   - 확인: 허브 검색 17개, 경로 출발·도착 필터, 없는 단건 404, 허용하지 않은 정렬 400. 내부 응답은 래퍼가 없다.

## 메모
- 협업 로그 OPEN 요청(`docs/services/hub/README.md` 5절)이 두 건이고 ID가 둘 다 `HUB-001`로 겹쳐 있어 정리가 필요하다.
  - company(kej-2356, 2026-10-15): I-02 구현 일정과 404 코드 확인. 구현은 끝났으므로 `HUB_NOT_FOUND`(404)와 응답 `hubId, name, address`가 맞다고 답할 수 있다. Gateway 통합 확인은 하지 않았다.
  - delivery(한얼, 2026-10-15): I-03 구현 일정과 경로 없음 에러 코드 이름 확인. 현재 `HUB_ROUTE_NOT_FOUND`(404)가 구현돼 있으나 I-03에 대한 합의는 없다. delivery는 받은 에러를 그대로 order에 전달한다.
- 확인이 필요한 항목(결정되지 않음, 합의로 기록하지 않는다): 거리 반올림 정책, I-03에 같은 허브 ID를 보냈을 때의 오류(SA는 같은 허브면 delivery가 허브 이동 구간을 생략한다), 허브 UUID 시드를 company·user·delivery 담당자와 합의하는 일.
- 문자열 정렬은 DB 콜레이션에 따른다. 컨테이너 PostgreSQL 16에서 `name,asc`가 가나다 순이 아니어서 통합 테스트는 오름·내림차순이 서로 역순인지만 검증한다. 가나다 순 노출이 필요하면 콜레이션 결정이 필요하다(미결정).
- SA 범위 밖: 재고(H-05·H-06, I-04~I-06)는 SA에서 고도화다. SA는 재고를 무한으로 가정(결정 9)하고, `docs/services/hub/decisions/0001`·`0002`는 재고가 있다는 전제의 최승근 ADR이다. 두 ADR은 팀 승인 기록이 없는 "제안"이라 이 작업의 근거와 범위에 반영하지 않는다. 고도화 착수와 ADR 채택을 팀이 합의하고 SA를 갱신한 뒤에 다룬다.
- 현재 설정은 `config-repo/hub-service.yml`의 포트 8082·`hub_schema`, 공통 설정의 `ddl-auto: validate`다. 이미 커밋한 마이그레이션 파일은 고치지 않는다.
- 관련 결정: `docs/decisions/0007-flyway-schema-migration.md`, `0009-common-conditional-auto-configuration.md`, `0010-internal-api-unwrapped-response.md`.
- 인계 유지 규칙: 이 문서의 다섯 섹션 제목을 유지한다. 완료 체크에는 검증 근거를 붙이고, 완료한 작업의 상세는 지우지 않고 누적한다.
