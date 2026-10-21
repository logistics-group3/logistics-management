# hub 협업 창구

허브와 허브 간 경로를 맡는 hub-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`hub-service/AGENTS.md`](../../../hub-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 미정 |
| 포트 | 8082 |
| 스키마·DB 계정 | `hub_schema` / `hub_app` |
| 책임 | 허브·허브 간 경로 조회(초기 데이터 적재), 재고 관리 고도화(H-05·H-06, I-04~I-06) |
| 소유 테이블 | `p_hubs`, `p_hub_routes`, `p_hub_stocks`, `p_hub_stock_histories` (SA 5.3) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| H-01~H-04 | `/api/v1/hubs/**`, `/api/v1/hub-routes/**` | 로그인 | 구현 | 허브 17개(V2)·경로 272건(V3)은 SQL 시드. 경로 시드는 SA 10.1의 기동 시 러너 대신 값을 고정한 마이그레이션이다. H-01~H-04는 Gateway 경유 호출로 확인했다(2026-10-16) |
| H-05 | `GET /api/v1/hubs/{hubId}/stocks` | 로그인 | 구현 | 허브 재고 검색. `productId` 선택 필터, 정렬 `createdAt`·`quantity`. 없는 허브는 404 `HUB_NOT_FOUND`. 서비스·MVC·PostgreSQL 통합 테스트와 Gateway 경유 호출로 확인했다(2026-10-19) |
| H-06 | `POST /api/v1/hubs/{hubId}/stocks/inbound` | MASTER, 상품 업체의 관리자 | 구현 | **SA 6.5와 다름([ADR 0003](decisions/0003-stock-allocation-supplier-hub-only.md), 제안).** SA는 MASTER 전용이다. 업체 관리자(USER)는 company I-08·I-07로 `managerId`가 본인이고 업체의 `hubId`가 요청 허브와 같을 때만 허용하며 아니면 403 `FORBIDDEN`이다. MASTER는 재고 레코드가 없을 때만 company I-08로 상품 존재를 확인하고, 업체 관리자는 매 입고마다 I-08·I-07을 호출한다(없으면 company의 404 `PRODUCT_NOT_FOUND`·`COMPANY_NOT_FOUND`가 그대로 올라오고 company 장애는 503). 수량 증가와 INBOUND 이력은 한 트랜잭션이다. MASTER 입고는 Gateway 경유와 실제 company 호출을 확인했다(2026-10-19). 업체 관리자 입고도 Gateway 경유와 실제 company 호출로 확인했다(2026-10-20, 결과는 `docs/progress/feat-hub-adv.md`) |
| I-02 | `GET /internal/hubs/{hubId}` | company, user | 구현 | 응답 `hubId, name, address`(DTO 그대로), 없으면 404 `HUB_NOT_FOUND`. `/internal/**`은 Gateway에 라우팅이 없어 외부 호출이 404다. 서비스 간 호출은 미확인 |
| I-03 | `GET /internal/hub-routes?departureHubId=&arrivalHubId=` | delivery | 구현 | 응답 `hubRouteId, distanceKm, durationMinutes`(DTO 그대로). 두 파라미터 필수(없거나 UUID가 아니면 400 `VALIDATION_ERROR`). 경로가 없으면 404 `HUB_ROUTE_NOT_FOUND`이고, 같은 허브 ID도 경로가 없으므로 같은 404다(같은 허브 처리는 팀 합의 전 임시 동작). delivery 컨테이너에서 서비스 간 호출로 확인했다(2026-10-16) |
| I-04 | `POST /internal/stocks` | company | 구현 | 요청 `hubId, productId`, 응답 `hubStockId, quantity`(감싸지 않음). 이미 있으면 기존 값 반환. 없는 허브는 404 `HUB_NOT_FOUND`. company 호출은 아직 없음(COMPANY-004) |
| I-05 | `POST /internal/stocks/allocate` | order | 구현 | **SA 6.3과 다름([ADR 0003](decisions/0003-stock-allocation-supplier-hub-only.md), 제안).** 요청 `orderId, productId, quantity, supplierHubId`(SA는 `destinationHubId`), 응답 `{hubId, quantity}` 한 건(SA는 `allocations` 목록). 공급업체 허브 한 곳만 차감하고 모자라거나 재고 레코드가 없으면 409 `STOCK_INSUFFICIENT`, 같은 `orderId` 재요청은 처음 결과를 반환하고, 이미 복원한 주문을 다시 할당하면 409 `STOCK_ALREADY_RESTORED`다(한 주문 ID는 할당과 복원이 한 번씩). order 호출은 아직 없음(ORDER-002) |
| I-06 | `POST /internal/stocks/restore` | order | 구현 | 요청 `orderId`, 응답 `restoredQuantity`(감싸지 않음). 차감 이력이 없거나 이미 복원했으면 0. order 호출은 아직 없음(ORDER-002) |

## 3. 호출하는 다른 서비스 API

| ID·URL | 제공 | 용도 | 재시도 |
|---|---|---|---|
| I-08 `GET /internal/products/{productId}` | company | H-06에서 MASTER는 재고 레코드가 없을 때 상품 존재 확인, 업체 관리자는 매 입고마다 소속 업체 확인(응답은 `productId`, `companyId`만 사용). `ProductClient` 구현 | GET이라 가능(`IdempotentRetryConfig`) |
| I-07 `GET /internal/companies/{companyId}` | company | H-06에서 MASTER가 아닌 사용자의 입고 권한 확인(`managerId`, `hubId`만 사용). `ProductClient`에 같이 둔다 | GET이라 가능(`IdempotentRetryConfig`) |

기본 범위에서는 호출하는 서비스가 없었고, 재고 고도화의 H-06만 company를 호출한다(SA 6.5, 업체 관리자 입고의 I-07 호출은 ADR 0003). company가 hub를 호출하고(I-02, I-04) hub도 company를 호출하게 되어 서비스 간 첫 양방향 관계가 된다. 요청 하나 안에서 서로를 다시 부르는 순환은 없다(I-02·I-04는 업체·상품 생성 때, I-07·I-08은 입고 때만 호출). 호출은 모두 GET이라 재시도한다(`IdempotentRetryConfig`).

## 4. 구현 현황

- [x] 허브·경로 Entity와 Flyway 마이그레이션, 초기 데이터(V1~V3)
- [x] H-01~H-04 조회·검색
- [x] I-02 내부 API
- [x] I-03 내부 API
- [x] 재고 고도화: V4 마이그레이션, H-05·H-06(업체 관리자 입고는 ADR 0003), I-04~I-06 내부 API

서비스 결정은 [decisions/](decisions/README.md)에 있다. 재고 관련 결정은 SA v2.0에서 고도화 범위다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### HUB-001 I-02 허브 조회 구현 일정과 404 코드 확인
- 상태: OPEN
- 작성: company / kej-2356 / 2026-10-15
- 관련: I-02, company C-01·C-04(업체 생성·수정 시 허브 존재 확인)
- 내용: company-service가 `HubClient`로 `GET /internal/hubs/{hubId}`를 호출하도록 만들었다. SA 6.3대로 응답은 감싸지 않은 `hubId, name, address`이고, 없는 허브는 404 `HUB_NOT_FOUND`라고 가정했다. 계약이 다르면 알려 주고, 구현 일정을 공유해 주면 그때 Gateway로 통합 확인한다.

### HUB-001 I-03 허브 간 경로 조회 구현과 응답 확인 요청
- 상태: OPEN
- 작성: delivery / 한얼 / 2026-10-15
- 관련: I-03, SA 6.3, delivery I-09 배송 생성
- 내용: delivery는 배송 생성(I-09) 때 출발 허브와 목적지 허브가 다르면 I-03을 호출해 허브 이동 구간의 예상 거리·시간을 채운다. delivery 쪽 Feign은 `GET /internal/hub-routes?departureHubId=&arrivalHubId=` → `{ hubRouteId, distanceKm, durationMinutes }`(DTO 그대로, ADR 0010)로 구현했다. I-03이 아직 없어 I-09가 실제 연동에서는 실패한다. (1) 구현 일정 (2) 경로가 없을 때 에러 코드 이름(delivery는 받은 에러를 그대로 order에 전달한다)을 알려 달라.
- 결정:

### HUB-002 허브·경로 단건 조회 캐시(hub.cache.type)
- 상태: OPEN (성능 측정용 로컬 구현, 제공 담당자 리뷰 대기)
- 작성: kmh / 2026-10-19
- 내용: H-02, I-02, H-04, I-03에 `@Cacheable`을 붙였다. `hub.cache.type`(`HUB_CACHE_TYPE`) 기본 `none`은 지금과 같다. `local`은 Caffeine, `redis`는 Redis다. 쓰기 API가 없어 TTL(기본 10분)로만 갱신된다. 404는 캐시하지 않는다. 목록 검색(H-01, H-03)은 캐시하지 않는다.
- 관련: [ADR 0019](../../decisions/0019-redis-cache-measured-adoption.md), [측정 기록](../../reviews/performance/README.md) B1 조건.
