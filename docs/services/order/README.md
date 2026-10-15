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
| O-01 | `POST /api/v1/orders` | 요청업체 관리자 본인, MASTER | 구현 | 생성·보상·Feign·Controller·커밋 후 Slack 구현. 외부 서버는 HTTP 대역으로 검증, 실제 Slack 채널은 미발송 |
| O-02~O-06 | `/api/v1/orders/**` | 주문자, MASTER | 예정 | O-05는 취소 흐름(SA 7.2) |

금액은 정수 원 단위의 `long`·`BIGINT`이며 SA 5.5·6.7과 일치한다. BigDecimal 전환은 사용자 요청으로 되돌렸고 현재 기준은 [order ADR 0005](decisions/0005-order-money-integer-krw.md)다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-07 `GET /internal/companies/{companyId}` | company | 요청·공급 업체 확인 | 통신 실패 시 총 3회 시도 (`order-company`) |
| I-08 `GET /internal/products/{productId}` | company | 상품·가격 확인 | 통신 실패 시 총 3회 시도 (`order-company`) |
| I-09 `POST /internal/deliveries` | delivery | 배송 생성(orderId 멱등) | 통신 실패 시 같은 orderId로 총 3회 시도 (`order-delivery-create`) |
| I-10~I-12 | delivery | 배송 조회·취소·취소 원복 | I-10(GET)·I-12(멱등)만 재시도. I-11 취소는 재시도하지 않는다(SA 3.5) |
| I-13 `DELETE /internal/deliveries/orders/{orderId}` | delivery | 주문 생성 실패 보상, 배송 논리 삭제 | OrderService가 총 3회 시도. `order-delivery-delete` Feign은 재시도하지 않는다 |

`CompanyClientAdapter`와 `DeliveryClientAdapter`가 application 인터페이스를 실제 Feign에 연결한다. 주소는 Eureka 서비스 이름으로 찾고 URL을 하드코딩하지 않는다. 5xx 응답은 재시도하지 않고 공통 디코더가 `SERVICE_UNAVAILABLE`로 바꾼다.

## 4. 구현 현황

- [x] 주문 Entity와 Flyway 마이그레이션 (`Order`, `OrderStatus`, `V1__create_p_orders.sql`)
- [x] 저장·단건 조회 Repository와 기반 테스트 (원복 후 단위 24개, PostgreSQL 통합 18개 통과)
- [x] 정수 원화 금액 long·BIGINT 복원, 곱셈 오버플로 방어 유지 (ADR 0005)
- [x] O-01 application 생성 흐름·권한 검증·보상 (외부 서비스 Mock, 단위 테스트 23개)
- [x] 실제 주문 DB 커밋·flush 실패·커밋 실패·트랜잭션 경계 검증 (PostgreSQL 통합 테스트 4개)
- [x] O-01 실제 Feign 어댑터·Spring 빈 조립·Controller 연결
- [x] Controller 11개·실제 HTTP/Feign 13개 추가 검증. 주문 모듈 전체 93개 통과
- [x] 격리 Gateway·JWT·주문 서버·PostgreSQL 검증: 401·201·400·503, 위조 헤더 제거, 실패 보상 (user·company·delivery 응답은 HTTP 테스트 대역)
- [ ] 팀 company·delivery 구현·실제 데이터와 정상 주문 연동
- [ ] O-02~O-06
- [x] 커밋 후 비동기 Slack 알림, 타임아웃·HTTP 오류·큐 포화 시 주문 201 유지
- [x] 현재 주문 테스트 108개 통과. 커밋 전·실패 시 알림 미실행, 비동기·헤더 격리·비밀값 로그 제외 확인
- [ ] 실제 Slack 채널 발송과 기존 Compose에 새 이미지·설정 반영

서비스 결정은 [decisions/](decisions/README.md)에 있다.
구현 과정과 검증 근거는 [구현 노트](notes.md)에 있다. 팀 서비스의 실제 구현·데이터 연동과 실제 Slack 채널 발송은 남아 있다.

Slack 설정은 `config-repo/order-service.yml`의 `slack.webhook-url`이며 기존 `SLACK_WEBHOOK_URL` 환경 변수를 사용한다. 빈 값은 발송 생략이다. 풀은 2개 스레드·100개 대기열, 연결·읽기 제한은 1초·2초이며 재시도하지 않는다. 메모리 이벤트이므로 프로세스 종료·전송 실패·포화 시 알림 유실이 가능하다. 적용 절차는 [스텝 39](../../learning/junhyun/steps/step-39-order-slack-after-commit.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

아직 기록이 없다.
