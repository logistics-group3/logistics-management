# order 협업 창구

주문을 맡는 order-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`order-service/AGENTS.md`](../../../order-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 미정 |
| 포트 | 8084 |
| 스키마·DB 계정 | `order_schema` / `order_app` |
| 책임 | 주문 생성(Saga 오케스트레이션)·취소·삭제, Slack 알림 |
| 소유 테이블 | `p_orders` (SA 5.5) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| O-01~O-06 | `/api/v1/orders/**` | 요청업체 관리자, 주문자, MASTER | 예정 | O-01은 Saga(SA 7.1), O-05는 취소 흐름(SA 7.2) |

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-07 `GET /internal/companies/{companyId}` | company | 요청·공급 업체 확인 | GET이라 가능 |
| I-08 `GET /internal/products/{productId}` | company | 상품·가격 확인 | GET이라 가능 |
| I-09 `POST /internal/deliveries` | delivery | 배송 생성(orderId 멱등) | 멱등이라 가능 |
| I-10~I-13 | delivery | 배송 조회·취소·취소 원복·삭제 | I-10(GET)·I-12·I-13(멱등)만 재시도. I-11 취소는 재시도하지 않는다(SA 3.5) |

## 4. 구현 현황

- [ ] 주문 Entity와 Flyway 마이그레이션
- [ ] O-01 주문 생성 Saga와 보상
- [ ] O-02~O-06
- [ ] Slack 알림

서비스 결정은 [decisions/](decisions/README.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

아직 기록이 없다.
