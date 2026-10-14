@agent.md

# order-service 세부 규칙

> 루트 `CLAUDE.md`(대전제·공통 규칙)를 전제로 한다. 서비스 정보·API 계약·커뮤니케이션 로그는 위에서 불러온 `agent.md`에 있다.
> 원본: `Docs/reference/SA_Document.md` §5, 루트 `CLAUDE.md` §3-7, §5

## 1. 주문 규칙
- **주문은 한 번에 한 품목**. 요청(수령)업체 관리자 본인 또는 MASTER만 그 업체 명의로 주문 가능.
- **주문 금액 `total_price`** 는 주문 생성 시 서버가 `unit_price × quantity`로 계산해 저장 (클라이언트 입력 금지, `Math.multiplyExact` 오버플로 시 400 `INVALID_REQUEST`). 수량은 주문 후 변경 불가.
- `Order` PK는 `@GeneratedValue`를 쓰지 않고 Saga를 위해 **서비스에서 UUID 선발급**.

## 2. 주문 생성 Saga (오케스트레이션, 확정)
- 코레오그래피가 아닌 **오케스트레이션**: 동기 Feign 중심 스택, Kafka는 고도화 단계, 주문 → 재고 → 배송이 직선 흐름이므로.
- 내부적으로는 실패한 단계까지 **역순으로 하나씩 보상**하고, 클라이언트에는 **최종 에러 하나만** 응답.

순서: `orderId 선발급` → 업체·상품 조회(I-07, I-08) → 요청업체 관리자 본인 확인(MASTER 제외) → 재고 할당(I-05) → 배송 생성(I-09) → 주문 저장 → 커밋 후 Slack 비동기 발송

| 실패 지점 | 처리 | 응답 |
| --- | --- | --- |
| 업체·상품 조회 | 즉시 실패 | 404 / 503 |
| 재고 할당: 재고 부족 | 즉시 실패 | 409 |
| 재고 할당: 타임아웃 | 재고 복원(I-06) | 503 |
| 배송 생성 실패/타임아웃 | 배송 삭제(I-13) → 재고 복원(I-06) | 503 |
| 주문 저장 실패 | 로컬 롤백 → 배송 삭제(I-13) → 재고 복원(I-06) | 500 |
| Slack 실패 | 주문 유지, 에러 로그만 (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`) | 201 |
| 보상 실패 | 최대 3회 재시도 후 orderId 포함 ERROR 로그 | - |

- 재고 할당·복원, 배송 생성·삭제 API는 모두 **orderId 기준 멱등** (같은 orderId 재요청 시 기존 결과 반환 / 대상 없으면 no-op) — 루트 §5의 서비스 간 계약.

## 3. Feign 클라이언트
- Feign 재시도는 기본 꺼짐 → `agent.md` §3 표에서 재시도 O인 클라이언트(GET, orderId 멱등 API)에만 Retryer 설정 (최대 3회, 100ms부터 지수 백오프).
- 타임아웃: I-05·I-09는 read 5초, 나머지 조회는 기본(connect 1초, read 3초), Slack Webhook connect 2초·read 3초 (루트 §9).
- 하위 4xx는 그대로 전달(예: 409 `STOCK_INSUFFICIENT`), 5xx·타임아웃은 503.

## 4. 상태 전이
- 주문 상태: `ORDERED` / `CANCELED`.
- 취소는 허브 이동 시작 전 + 업체 배송 출발 전에만 가능, 취소 시 공급업체 허브 재고 복원. 처리 순서는 ORDER-001.
- 삭제는 MASTER만, 취소 또는 배송 완료된 주문만 (아니면 409 `ORDER_NOT_DELETABLE`).

## 5. 컬럼 길이
- `p_orders`: `product_name varchar(100)`, `request_note varchar(500)`, `unit_price bigint`, `total_price bigint`(≥0)
