@agent.md

# delivery-service 세부 규칙

> 루트 `CLAUDE.md`(대전제·공통 규칙)를 전제로 한다. 서비스 정보·API 계약·커뮤니케이션 로그는 위에서 불러온 `agent.md`에 있다.
> 원본: `Docs/reference/SA문서.md` §5 배송 상태 전이, 루트 `CLAUDE.md` §3-7, §7

## 1. 배송 구조
- **주문 1건 = 배송 1건.** 배송 생성(I-09)·삭제(I-13)는 내부 API로만 제공하며 **orderId 기준 멱등** (루트 §5).
- **업체 간 배송 구조**: 공급업체 → 공급업체 허브 → (허브 이동) → 요청업체 허브 → 요청업체. `p_delivery_routes`는 허브 이동 구간 **0~1행**(같은 허브면 0행, `sequence`는 중계 확장 대비).
- I-09에서 두 허브가 다르면 I-03으로 예상 거리·시간을 조회한다 (read 2초, I-09의 5초보다 짧게).
- `p_deliveries` 컬럼: `order_id` UK, `supplier_company_id`, `receiver_company_id`, `status`, `departure_hub_id`(공급업체 허브), `destination_hub_id`(요청업체 허브), `delivery_address`(기본값 요청업체 주소), `recipient_name`(기본값 요청업체명), `manager_id`(업체 배송 담당자), `out_for_delivery_at`, `delivered_at`
- `p_delivery_routes` 컬럼: `delivery_id` FK, `sequence`, `departure_hub_id`, `arrival_hub_id`, `expected/actual_distance_km`, `expected/actual_duration_minutes`, `status`, `manager_id`(허브 이동 담당자)

## 2. 상태 전이
- 다른 허브: `HUB_WAITING` → `HUB_MOVING` → `DEST_HUB_ARRIVED` → `OUT_FOR_DELIVERY` → `DELIVERED`
- 같은 허브: 생성 즉시 `DEST_HUB_ARRIVED` → `OUT_FOR_DELIVERY` → `DELIVERED`
- 허브 이동 구간 상태(`WAITING`/`MOVING`/`ARRIVED`/`CANCELED`)가 배송 상태와 1:1 자동 연동 (D-07)
- 전제 조건: `MOVING`은 허브 이동 담당자 배정 필수, `OUT_FOR_DELIVERY`는 `DEST_HUB_ARRIVED` + 업체 배송 담당자 배정 필수. 출발·완료 시 각 시각 기록
- `CANCELED`는 `HUB_WAITING` 또는 **허브 이동 없이 시작한** `DEST_HUB_ARRIVED`에서만. 허브 이동을 마치고 도착한 배송은 취소 불가 (재고는 공급업체 허브로 복원되는데 실물은 요청업체 허브에 있어 불일치). 취소 원복(I-12) 방식은 DELIVERY-001.
- 상태 전이 검증은 Entity 메서드에 둔다. 위반 시 409 `INVALID_STATUS_TRANSITION`.

## 3. 배송담당자 배정
- MASTER가 `isDeliveryManager = true`인 사용자 중에서 **수동 배정** (D-04, D-06 모두 PATCH, 자동 배정·배정용 내부 API 없음).
- 출발지 허브 소속일 때만 가능 — 허브 이동 구간 → 공급업체 허브, 업체 배송 → 목적지(요청업체) 허브. I-01로 배송담당자 여부·소속 허브 확인.
- 담당자 없음 404 `DELIVERY_MANAGER_NOT_FOUND`, 허브 불일치 400 `MANAGER_HUB_MISMATCH`.

## 4. 조회 권한 (D-01, D-02)
- MASTER 전체 / 배송담당자는 본인 담당(업체 배송 또는 허브 이동)만 / 업체 관리자는 공급·수령업체가 본인 업체인 배송만 (I-14로 관리 업체 조회).

## 5. 컬럼 길이
- `p_deliveries`: `recipient_name varchar(100)`(기본값이 업체명이므로 업체명 길이에 맞춤), `delivery_address varchar(255)`, `status varchar(30)`, `out_for_delivery_at`/`delivered_at timestamp`
- `p_delivery_routes`: `expected/actual_distance_km numeric(8,2)`
