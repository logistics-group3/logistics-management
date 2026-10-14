# order-service agent.md

> 이 서비스의 **협업 창구**다. 사람·AI 모두 이 서비스 코드를 수정하기 전에 루트 `CLAUDE.md`와 이 파일을 먼저 읽는다.

## 1. 서비스 정보
| 항목 | 값 |
| --- | --- |
| 담당자 | (미정) |
| 포트 / 스키마 / DB 계정 | 8084 / `order_schema` / `order_app` |
| 책임 | 주문 생성(**Saga 오케스트레이터**)·취소·삭제, Slack 알림 |
| 소유 테이블 | `p_orders` |

## 2. 제공 API (계약)
- 외부: O-01 주문 생성, O-02 검색(USER는 본인 주문만), O-03 단건, O-04 요청사항 수정, O-05 취소, O-06 삭제(MASTER)
- 내부: 없음

## 3. 의존 (이 서비스가 호출하는 내부 API)
| API | 제공 | 용도 | 재시도 |
| --- | --- | --- | --- |
| I-07 업체 조회 | company | 공급·요청업체 정보 | O (GET) |
| I-08 상품 조회 | company | 상품명·단가 스냅샷, 공급업체 소속 확인 | O (GET) |
| I-05 재고 할당 | hub | orderId 기준 차감 (read 5초) | O (멱등) |
| I-06 재고 복원 | hub | 보상·취소 | O (멱등) |
| I-09 배송 생성 | delivery | orderId 기준 생성 (read 5초) | O (멱등) |
| I-10 배송 조회 | delivery | 상태 확인 | O (GET) |
| I-11 배송 취소 | delivery | 주문 취소 | X |
| I-12 배송 취소 원복 | delivery | 취소 보상 | X |
| I-13 배송 삭제 | delivery | 보상·주문 삭제 | O (멱등) |

## 4. 서비스 고유 규칙 (원본: SA §5, CLAUDE.md §5)
- 주문 생성 순서: `orderId 선발급` → I-07·I-08 → 요청업체 관리자 본인 확인(MASTER 제외) → I-05 → I-09 → 주문 저장 → 커밋 후 Slack(`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`)
- 실패 시 **역순 보상**: 배송 생성 실패 → I-13 → I-06 / 주문 저장 실패 → I-13 → I-06 / 할당 타임아웃 → I-06. 보상 실패는 최대 3회 재시도 후 orderId 포함 ERROR 로그.
- `Order` PK는 `@GeneratedValue` 쓰지 않고 **선발급 UUID**.
- 한 번에 한 품목. `total_price = unit_price × quantity`를 서버에서 계산(`Math.multiplyExact`, 오버플로 400). 수량은 주문 후 변경 불가.
- 상태 `ORDERED`/`CANCELED`. 취소는 허브 이동 시작 전 + 업체 배송 출발 전만. 삭제는 취소 또는 배송 완료 주문만(`ORDER_NOT_DELETABLE`).
- Feign 재시도는 기본 꺼짐 → 위 표의 멱등 API 클라이언트에만 Retryer 설정 (최대 3회, 100ms부터 지수 백오프).
- 하위 4xx는 그대로 전달(예: 409 `STOCK_INSUFFICIENT`), 5xx·타임아웃은 503.

## 5. 구현 현황
- [ ] FeignClient (company, hub, delivery) + 클라이언트별 타임아웃·재시도
- [ ] O-01 Saga + 보상
- [ ] O-02~O-06
- [ ] Slack 비동기 알림

## 6. 커뮤니케이션 로그
작성 규칙: 맨 아래에 추가 · ID `ORDER-{번호}` · 상태 `OPEN` → `DECIDED` → `DONE` · 다른 서비스에 요청할 때는 **그 서비스의 agent.md**에 작성

```
### ORDER-000 제목
- 상태: OPEN
- 작성: 이름 / YYYY-MM-DD
- 관련: (API ID, 서비스, 파일)
- 내용:
- 결정:
```

### ORDER-001 주문 취소(O-05) 처리 순서와 보상
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: O-05, I-11, I-12, I-06, delivery·hub
- 내용: SA에는 I-11(배송 취소), I-12(취소 원복), I-06(재고 복원)이 있지만 취소 흐름의 순서가 명시돼 있지 않다. 제안: ① I-11 배송 취소(취소 불가면 409로 즉시 종료) → ② I-06 재고 복원 → ③ 주문 `CANCELED` 저장. ② 또는 ③ 실패 시 I-12로 배송 원복.
- 결정:

### ORDER-002 Slack 알림 내용과 수신 대상
- 상태: OPEN
- 작성: 공통 인프라 / 2026-10-13
- 관련: Slack Incoming Webhook, `SLACK_WEBHOOK_URL`
- 내용: 메시지 항목(주문 ID, 업체, 상품, 수량, 금액 등)과 수신 채널이 미정.
- 결정:
