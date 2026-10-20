# company 협업 창구

업체와 상품을 맡는 company-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`company-service/AGENTS.md`](../../../company-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | kej-2356 |
| 포트 | 8083 |
| 스키마·DB 계정 | `company_schema` / `company_app` |
| 책임 | 업체 CRUD, 상품(업체 하위 리소스) CRUD |
| 소유 테이블 | `p_companies`, `p_products` (SA 5.4) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| C-01~C-05 | `/api/v1/companies/**` | 로그인, 업체 관리자, MASTER | 구현 | C-01에서 MASTER가 managerId를 비우면 400 `VALIDATION_ERROR` |
| P-01~P-05 | `/api/v1/products`, `/api/v1/companies/{companyId}/products/**` | 로그인, 업체 관리자, MASTER | 구현 |  |
| I-07 | `GET /internal/companies/{companyId}` | order | 구현 | 응답 `companyId, name, hubId, address, managerId`, 없거나 삭제됨 404 `COMPANY_NOT_FOUND` |
| I-08 | `GET /internal/products/{productId}` | order | 구현 | 응답 `productId, companyId, name, price`, 없거나 삭제됨 404 `PRODUCT_NOT_FOUND` |

I-07·I-08 구현 전 [주문 연동 실패 계약](../order/integration-contract.md#company-담당자)을 확인한다. 현재 주문은 내부 DTO 직접 응답, 정수 원화 가격, 공통 4xx 오류 코드와 통신 재시도 정책을 사용한다. 실제 팀 서비스 연동은 아래 COMPANY-001에서 확인한다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-01 `GET /internal/users/{userId}` | user | 업체 관리자 지정 시 사용자 확인. `UserClient` 구현 | GET이라 가능(`IdempotentRetryConfig`) |
| I-02 `GET /internal/hubs/{hubId}` | hub | 소속 허브 확인. `HubClient` 구현, hub 쪽은 아직 미구현(hub 협업 로그 HUB-001) | GET이라 가능(`IdempotentRetryConfig`) |

## 4. 구현 현황

- [x] 업체·상품 Entity와 Flyway 마이그레이션
- [x] 업체·상품 검색(QueryDSL), user·hub FeignClient
- [x] C-01~C-05, P-01~P-05
- [x] I-07, I-08 내부 API

서비스 결정은 [decisions/](decisions/README.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### COMPANY-001 주문 연동용 조회 응답과 오류 계약 확인

- 상태: OPEN
- 작성: order / Junhyun An 작업 / 2026-10-15 (사용자의 타 서비스 인계 기록 요청)
- 관련: I-07·I-08, O-01 생성 전 업체·상품·권한 검증
- 내용: [연동 계약](../order/integration-contract.md)의 응답 필드·가격 타입·4xx 코드·헤더/Audit 전제를 구현 시 확인한다. 주문은 GET 통신 실패에 최대 3회 호출하며 조회 실패 시 배송을 만들지 않는다. 현재 검증은 HTTP 대역이고 실제 구현·업무 데이터 연동은 미확인이다.
- 요청: 제공 API가 준비되면 구현 위치와 실제 연동 결과를 남긴다. 기존 SA 계약의 구현 확인 요청이며 새 응답 필드를 임의로 추가하라는 요청은 아니다.
- PR 준비 확인: dev `1384a5d`의 InternalCompanyResponse·InternalProductResponse를 대조해 요청 필드와 Long 원화 가격 계약이 일치함을 확인했다. 실제 HTTP 연동·권한·오류 응답 검증은 아직 별도 확인 대상이다.
- 결정: 담당자 확인 대기. order 작성만으로 DECIDED·DONE 처리하지 않는다.

### COMPANY-002 재고 초기화(I-04) 호출 요청과 hub의 I-07·I-08 호출 알림
- 상태: OPEN
- 작성: hub / 최승근 / 2026-10-19 (2026-10-20: hub의 I-07 호출과 업체 관리자 입고 내용 추가)
- 관련: hub I-04·H-06, SA 5.3·6.3·6.5, [hub ADR 0003](../hub/decisions/0003-stock-allocation-supplier-hub-only.md)
- 내용:
  - I-04: hub-service가 재고 고도화(`feat/hub-adv`)로 `POST /internal/stocks`(요청 `hubId, productId`, 응답 `hubStockId, quantity`)를 구현했다. 상품 생성 시 업체 관리 허브에 호출해 수량 0 재고를 만들어야 한다. 이미 있으면 기존 값을 반환하므로 재호출해도 된다.
  - I-08: hub는 H-06 입고 때 `GET /internal/products/{productId}`를 호출한다. MASTER 입고는 재고 레코드가 없을 때만, 업체 관리자 입고는 매번 호출한다.
  - I-07: 업체 관리자 입고는 `GET /internal/companies/{companyId}`도 호출해 `managerId`와 `hubId`로 권한을 판단한다. company 응답은 바꾸지 않았다.
  - 두 API의 호출자에 hub가 추가되고 company↔hub 양방향 호출이 된다.
- 요청: (1) I-04 호출 시점(상품 생성, 기존 상품 보정 여부)을 정해 달라. (2) hub가 I-07·I-08을 호출해도 되는지 확인해 달라. 구현 전이라 company 코드는 바꾸지 않았다.
- 결정:

