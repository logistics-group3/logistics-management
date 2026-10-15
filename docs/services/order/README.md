# order 협업 창구

주문을 맡는 order-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`order-service/AGENTS.md`](../../../order-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | Junhyun An (2026-10-15 본인 확인) |
| 포트 | 8084 |
| 스키마·DB 계정 | `order_schema` / `order_app` |
| 책임 | 주문 생성(Saga 오케스트레이션)·조회·요청사항 수정·취소·삭제, Slack 알림 |
| 소유 테이블 | `p_orders`, `p_order_cancellations` (SA 5.5·주문 ADR 0006) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| O-01 | `POST /api/v1/orders` | 요청업체 관리자 본인, MASTER | 구현 | 생성·보상·Feign·Controller·커밋 후 Slack 구현. company·delivery는 HTTP 대역으로 검증, 실제 Slack Webhook 테스트는 200·ok 확인 |
| O-02 | `GET /api/v1/orders` | 로그인, USER는 본인 주문만 | 구현 | 선택 필터·QueryDSL·페이지·정렬, 내용과 count에 같은 권한 조건 |
| O-03 | `GET /api/v1/orders/{orderId}` | 주문자 본인, MASTER | 구현 | 없음·삭제 404 ORDER_NOT_FOUND, 타인 주문 403 FORBIDDEN |
| O-04 | `PATCH /api/v1/orders/{orderId}` | 주문자 본인, MASTER | 구현 | requestNote 필수·공백 불가·최대 500자. 취소된 주문은 409 INVALID_STATUS_TRANSITION |
| O-05 | `POST /api/v1/orders/{orderId}/cancel` | 주문자 본인, MASTER | 구현 | 배송 취소 → 주문 커밋, 확인된 롤백만 원복. 진행 기록·중복 실행 차단·미확정 결과 복구 대기 |
| O-06 | `DELETE /api/v1/orders/{orderId}` | MASTER | 예정 | 취소 또는 배송 완료 주문만 삭제(SA 6.7) |

금액은 정수 원 단위의 `long`·`BIGINT`이며 SA 5.5·6.7과 일치한다. BigDecimal 전환은 사용자 요청으로 되돌렸고 현재 기준은 [order ADR 0005](decisions/0005-order-money-integer-krw.md)다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-07 `GET /internal/companies/{companyId}` | company | 요청·공급 업체 확인 | 통신 실패 시 총 3회 시도 (`order-company`) |
| I-08 `GET /internal/products/{productId}` | company | 상품·가격 확인 | 통신 실패 시 총 3회 시도 (`order-company`) |
| I-09 `POST /internal/deliveries` | delivery | 배송 생성(orderId 멱등) | 통신 실패 시 같은 orderId로 총 3회 시도 (`order-delivery-create`) |
| I-10 `GET /internal/deliveries/{deliveryId}` | delivery | 배송 조회, 향후 삭제·복구 판단 | 아직 미연결. GET 통신 재시도 대상 |
| I-11 `POST /internal/deliveries/{deliveryId}/cancel` | delivery | 집하 시작 여부를 판단하고 배송·구간 취소 | 1회 (`order-delivery-cancel`), 응답 status=CANCELED 확인 |
| I-12 `POST /internal/deliveries/{deliveryId}/cancel-revert` | delivery | 확인된 로컬 롤백의 배송 원복 | 응답 없는 통신 실패에 최대 3회 (`order-delivery-cancel-revert`), 5xx 응답은 재시도 없음 |
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
- [x] O-02 검색·O-03 단건 조회, 논리 삭제 제외·주문자 범위·날짜·페이지·정렬 검증
- [x] O-04 요청사항 수정: 본인·MASTER 권한, 취소 상태 거부, 잠금 조회·변경 감지·Audit 응답
- [x] O-05 취소: 진행 기록·부분 유니크·권한·상태·Feign·로컬 커밋 재확인·원복
- [ ] O-06 삭제
- [x] 커밋 후 비동기 Slack 알림, 타임아웃·HTTP 오류·큐 포화 시 주문 201 유지
- [x] Slack 구현 단계의 주문 테스트 108개 통과. 커밋 전·실패 시 알림 미실행, 비동기·헤더 격리·비밀값 로그 제외 확인
- [x] 조회 API 테스트 44개 추가. 조회 단계 주문 152개·전체 테스트 XML 250개, 실패·오류·skip 0
- [x] 수정 API 테스트 39개 추가. 수정 단계 주문 191개·전체 테스트 XML 289개, 실패·오류·skip 0
- [x] 취소 테스트 61개 추가. 현재 주문 252개·전체 XML 350개, 실패·오류·skip 0. 전체 build 통과
- [x] 실제 PostgreSQL flush·커밋 실패, 동시 실행·독립 진행·요청사항 보존, 부분 유니크·CHECK 검증
- [x] 실제 Feign I-11 1회·I-12 통신 실패 최대 3회·5xx 1회, 늦은 취소 응답과 복구 대기 검증
- [x] 격리 Gateway·JWT·Order·PostgreSQL 취소 검증: 200·400·401·403·404·409·500·503, 저장 롤백 후 원복·지연 완료 차단
- [x] 로컬 주문 서비스·Flyway V2 반영, health UP·Gateway O-05 OpenAPI·미인증 401 확인. 기존 주문 데이터·다른 8개 컨테이너·.env·Webhook 값 유지
- [x] PostgreSQL 동시 취소·삭제 대기 후 409·404, 실패·롤백 시 기존 데이터 유지, 응답·DB Audit 일치 확인
- [x] 격리 Gateway·JWT·Order·PostgreSQL 수정 검증: 200·400·401·403·404·409, 위조 헤더 차단, 회사·배송·알림 호출 없음
- [x] 로컬 주문 이미지 반영 후 health UP, Gateway O-04 OpenAPI 200·필수 requestNote/최대 500·미인증 PATCH 401 확인. 다른 8개 컨테이너와 .env·Webhook 값 유지
- [x] 격리 Gateway·JWT·Order·PostgreSQL 조회 검증: 200·400·401·403·404, 위조 사용자 헤더 차단, 본인 범위와 count 확인
- [x] 현재 Compose에 주문 서비스만 반영. health UP, Gateway의 O-02·O-03 문서·size=10·미인증 401 확인
- [x] config-server·order-service 새 이미지·환경 변수 반영, health UP·Gateway OpenAPI 200·미인증 401 확인
- [x] 주문 컨테이너에서 실제 Slack Webhook 테스트 1건 발송, HTTP 200·ok 확인 (주문 생성 없는 연결 테스트)

서비스 결정은 [decisions/](decisions/README.md)에 있다.
구현 과정과 검증 근거는 [구현 노트](notes.md)에 있다. 팀 서비스의 실제 구현·데이터로 주문 생성부터 Slack 알림까지 연결하는 전체 검증은 남아 있다.

이전 구현 기록: [주문 기반·금액 결정](notes/foundation-and-money.md), [생성·Feign·실패 보상](notes/create-and-compensation.md), [Slack 알림·실제 연결](notes/slack-notification.md).

Slack 설정은 `config-repo/order-service.yml`의 `slack.webhook-url`이며 기존 `SLACK_WEBHOOK_URL` 환경 변수를 사용한다. 빈 값은 발송 생략이다. 풀은 2개 스레드·100개 대기열, 연결·읽기 제한은 1초·2초이며 재시도하지 않는다. 메모리 이벤트이므로 프로세스 종료·전송 실패·포화 시 알림 유실이 가능하다. 적용 절차는 [스텝 39](../../learning/junhyun/steps/step-39-order-slack-after-commit.md)에 있다.

실제 환경 반영과 발송 결과는 [스텝 40](../../learning/junhyun/steps/step-40-slack-live-connection.md)에 있다. 실제 URL은 저장소에 기록하지 않는다.

검색은 `status`, `supplierCompanyId`, `receiverCompanyId`, `productId`, `startDate`, `endDate`를 지원한다. 날짜는 시작·종료일을 포함하며 역전된 기간은 400 INVALID_REQUEST다. page는 0부터, size는 10·30·50(그 외 10 보정), 정렬은 createdAt·quantity만 허용하고 기본은 createdAt,DESC다. 조회는 주문에 저장된 스냅샷을 읽으며 회사·배송·Slack을 호출하지 않는다. 상세 설명은 [스텝 41](../../learning/junhyun/steps/step-41-order-query-api.md)에 있다.

O-04 본문은 `{"requestNote":"오후 도착 희망"}`이다. 누락·null·빈 문자열·공백·500자 초과는 400 VALIDATION_ERROR, 타인 주문은 403 FORBIDDEN, 없음·삭제는 404 ORDER_NOT_FOUND, 취소 상태는 409 INVALID_STATUS_TRANSITION이다. 성공은 수정된 OrderResponse를 담은 200이다. 상품·수량·가격·배송 정보는 수정하지 않으며 회사·배송 API와 Slack을 호출하지 않는다. 잠금·트랜잭션·Audit 근거는 [스텝 42](../../learning/junhyun/steps/step-42-order-request-note-update.md)에 있다.

O-05는 본문 없이 호출하며 성공 데이터는 orderId·status=CANCELED다. 취소 여부는 배송 I-11이 원자적으로 판단한다. 진행 중·복구 대기인 주문은 409로 새 실행을 막으며 타임아웃·5xx를 실패 확정으로 취급하지 않는다.
진행 기록은 2026-10-15 주문 담당자의 명시적 승인으로 V2에 추가했고 SA의 주문 영역에 반영했다. 구현 이유·재현은 [스텝 43](../../learning/junhyun/steps/step-43-order-cancellation.md), 생성·취소의 상세 실패 행렬과 후속 보강안은 [예외 처리 설계](failure-handling.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

아직 기록이 없다.
