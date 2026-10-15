# order 협업 창구

주문을 맡는 order-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`order-service/AGENTS.md`](../../../order-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | Junhyun An (2026-10-15 본인 확인) |
| 포트 | 8084 |
| 스키마·DB 계정 | `order_schema` / `order_app` |
| 책임 | 주문 생성(Saga 오케스트레이션)·취소·삭제, Slack 알림 |
| 소유 테이블 | `p_orders` (SA 5.5) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| O-01~O-06 | `/api/v1/orders/**` | 요청업체 관리자, 주문자, MASTER | 예정 | O-01은 Saga(SA 7.1), O-05는 취소 흐름(SA 7.2) |

금액은 정수 원 단위의 `long`·`BIGINT`이며 SA 5.5·6.7과 일치한다. BigDecimal 전환은 사용자 요청으로 되돌렸고 현재 기준은 [order ADR 0005](decisions/0005-order-money-integer-krw.md)다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-07 `GET /internal/companies/{companyId}` | company | 요청·공급 업체 확인 | GET이라 가능 |
| I-08 `GET /internal/products/{productId}` | company | 상품·가격 확인 | GET이라 가능 |
| I-09 `POST /internal/deliveries` | delivery | 배송 생성(orderId 멱등) | 멱등이라 가능 |
| I-10~I-12 | delivery | 배송 조회·취소·취소 원복 | I-10(GET)·I-12(멱등)만 재시도. I-11 취소는 재시도하지 않는다(SA 3.5) |
| I-13 `DELETE /internal/deliveries/orders/{orderId}` | delivery | 주문 생성 실패 보상, 배송 논리 삭제 | 생성 보상은 OrderService가 총 3회 시도. 향후 어댑터는 단일 호출로 연결해 재시도를 중첩하지 않는다 |

I-07·I-08은 `CompanyReader`, I-09·I-13은 `DeliveryGateway` 인터페이스로 정의했다. 실제 Feign 어댑터는 아직 연결하지 않았으며 현재 테스트에서는 Mock으로 대체한다.

## 4. 구현 현황

- [x] 주문 Entity와 Flyway 마이그레이션 (`Order`, `OrderStatus`, `V1__create_p_orders.sql`)
- [x] 저장·단건 조회 Repository와 기반 테스트 (원복 후 단위 24개, PostgreSQL 통합 18개 통과)
- [x] 정수 원화 금액 long·BIGINT 복원, 곱셈 오버플로 방어 유지 (ADR 0005)
- [x] O-01 application 생성 흐름·권한 검증·보상 (외부 서비스 Mock, 단위 테스트 23개)
- [x] 실제 주문 DB 커밋·flush 실패·커밋 실패·트랜잭션 경계 검증 (PostgreSQL 통합 테스트 4개)
- [ ] O-01 실제 Feign 어댑터·Spring 빈 조립·Controller 연결 및 Gateway 검증
- [ ] O-02~O-06
- [ ] Slack 알림

서비스 결정은 [decisions/](decisions/README.md)에 있다.
구현 과정과 검증 근거는 [구현 노트](notes.md)에 있다. 주문 모듈 테스트는 총 69개가 통과했다. 생성 흐름은 구현했으며 외부 API·실제 서비스 연동·Slack 알림은 다음 단계다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

아직 기록이 없다.
