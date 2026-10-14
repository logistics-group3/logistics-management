@agent.md

# hub-service 세부 규칙

> 루트 `CLAUDE.md`(대전제·공통 규칙)를 전제로 한다. 서비스 정보·API 계약·커뮤니케이션 로그는 위에서 불러온 `agent.md`에 있다.
> 원본: `Docs/reference/SA_Document.md`, 루트 `CLAUDE.md` §3-7

## 1. 재고 소유 원칙
- **상품은 공급업체 소속 허브에만 존재**한다 (상품당 재고 행 1개, `p_hub_stocks.product_id` UNIQUE). 다른 허브에는 재고가 없다.
- 출고 허브는 항상 공급업체 허브. 흐름: 공급업체 허브 재고 차감 → 공급업체 허브 → 요청업체 허브 이동(P2P, 같은 허브면 생략) → 요청업체로 배송.
- 재고 부족 시 **주문 실패(409 `STOCK_INSUFFICIENT`), 아무것도 변경하지 않는다.** 재고 수량은 0 이상.
- H-06 입고는 상품 소속 허브로만 가능 (다르면 400 `INVALID_REQUEST`). H-05 허브 재고 검색은 **범위 제외**.
- I-04(상품 생성 시 재고 0개 행 생성)는 이미 있으면 기존 값 반환(멱등).

## 2. 할당·복원 (`p_stock_allocations`)
- `p_hub_stock_histories`(재고 변경 이력)는 **확장 기능으로 제외**. 할당·복원의 멱등성과 복원 근거는 **`p_stock_allocations`** 가 담당한다.
  - 컬럼: `stock_allocation_id` PK, `order_id` UNIQUE, `hub_stock_id` FK, `quantity`(≥1), `status`(`ALLOCATED`/`RESTORED`)
- **I-05 할당**: ① 상품 재고 행을 `FOR UPDATE`로 잠금 → ② `order_id`로 기존 할당 조회, 있으면 그대로 반환 → ③ 없으면 수량 검사(부족 시 409) 후 차감 + `ALLOCATED` 저장
- **I-06 복원**: ① 락 없이 `order_id`로 할당 조회, 없으면 0 반환 → ② 해당 재고 행 `FOR UPDATE` → ③ `ALLOCATED`면 복원 후 `RESTORED`, 이미 복원됐으면 0
- **락 순서는 항상 "재고 행 → 할당 내역"**. 순서를 바꾸면 교착 위험.
- I-05 read timeout 5초(order 쪽)에 맞춰 DB `lock_timeout` 3초.
- I-05/I-06은 order-service Saga의 핵심 계약이다 (orderId 기준 멱등, 루트 §5).

## 3. 허브 데이터
- 기동 시 허브 17개·경로 272개 시드 적재 (허브 데이터는 조회 전용). 출처·적재 방식은 HUB-001.
- I-03은 P2P 단일 경로 조회(`departureHubId`, `arrivalHubId`).

## 4. 컬럼 길이
- `p_hubs`: `name varchar(50)`, `latitude numeric(10,7)`, `longitude numeric(10,7)`
- `p_hub_routes`: `distance_km numeric(8,2)`, `duration_minutes int`
