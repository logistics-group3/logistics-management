# order 협업 창구

주문을 맡는 order-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`order-service/AGENTS.md`](../../../order-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | Junhyun An (2026-10-15 본인 확인) |
| 포트 | 8084 |
| 스키마·DB 계정 | `order_schema` / `order_app` |
| 책임 | 주문 생성(Saga 오케스트레이션)·조회·요청사항 수정·취소·삭제, Slack 알림 |
| 소유 테이블 | `p_orders`, `p_order_cancellations`, `p_order_creations`, `p_order_deletions`, `p_order_creation_recoveries`, `p_order_creation_recovery_events` (SA 5.5·11, ADR 0019) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| O-01 | `POST /api/v1/orders` | 요청업체 관리자 본인, MASTER | 구현 | Idempotency-Key 필수·최초 응답 재사용·동시 생성 차단. **비동기 접수**: 접수 전 검증 통과 시 202와 orderId, 배송 생성·저장·보상은 별도 스레드(ADR 0010, 이슈 #16). 같은 키 재요청은 처리 중 202·완료 201. 생성 진행 기록·커밋 재확인·보상·커밋 후 Slack |
| O-07 | `GET /api/v1/orders/creations/{orderId}` | 주문자 본인, MASTER | 구현 | 생성 진행 조회(PROCESSING·COMPLETED·ABORTED·RECOVERY_REQUIRED). 실패 사유 `failureCode`·`failureMessage`(ADR 0010) |
| O-08 | `GET /api/v1/orders/creation-recoveries/candidates` | MASTER | 로컬 구현 | PROCESSING·RECOVERY_REQUIRED 조사 목록, createdAt·updatedAt 정렬 |
| O-09 | `POST /api/v1/orders/{orderId}/creation-recoveries` | MASTER | 로컬 구현 | 실행 중단 확인·복구 ID·attemptId·예상 상태·사유, 같은 ID 재사용·명시적 재개 |
| O-10 | `GET /api/v1/orders/creation-recoveries/{recoveryId}` | MASTER | 로컬 구현 | RUNNING·DEFERRED·COMPLETED, 실행 ID와 단계별 감사 이력 |
| O-02 | `GET /api/v1/orders` | 로그인, USER는 본인 주문만 | 구현 | 선택 필터·QueryDSL·페이지·정렬, 내용과 count에 같은 권한 조건 |
| O-03 | `GET /api/v1/orders/{orderId}` | 주문자 본인, MASTER | 구현 | 없음·삭제 404 ORDER_NOT_FOUND, 타인 주문 403 FORBIDDEN |
| O-04 | `PATCH /api/v1/orders/{orderId}` | 주문자 본인, MASTER | 구현 | requestNote 필수·공백 불가·최대 500자. 취소된 주문은 409 INVALID_STATUS_TRANSITION |
| O-05 | `POST /api/v1/orders/{orderId}/cancel` | 주문자 본인, MASTER | 구현 | 배송 취소 → 주문 커밋, 확인된 롤백만 원복. 진행 기록·중복 실행 차단·미확정 결과 복구 대기 |
| O-06 | `DELETE /api/v1/orders/{orderId}` | MASTER | 구현 | 취소 또는 배송 완료만 논리 삭제. 삭제 허가 근거·진행 기록·커밋 재확인, 미해결 취소와 충돌 차단 |

금액은 정수 원 단위의 `long`·`BIGINT`이며 SA 5.5·6.7과 일치한다. BigDecimal 전환은 사용자 요청으로 되돌렸고 현재 기준은 [order ADR 0005](decisions/0005-order-money-integer-krw.md)다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-07 `GET /internal/companies/{companyId}` | company | 요청·공급 업체 확인 | 통신 실패 시 총 3회 시도 (`order-company`) |
| I-08 `GET /internal/products/{productId}` | company | 상품·가격 확인 | 통신 실패 시 총 3회 시도 (`order-company`) |
| I-09 `POST /internal/deliveries` | delivery | 배송 생성(orderId 멱등) | 통신 실패 시 같은 orderId로 총 3회 시도 (`order-delivery-create`) |
| I-10 `GET /internal/deliveries/{deliveryId}` | delivery | 새 ORDERED 삭제의 배송 완료 확인 | 응답 없는 통신 실패에 최대 3회 (`order-delivery-read`), 5xx는 1회 |
| I-11 `POST /internal/deliveries/{deliveryId}/cancel` | delivery | 집하 시작 여부를 판단하고 배송·구간 취소 | 1회 (`order-delivery-cancel`), 응답 status=CANCELED 확인 |
| I-12 `POST /internal/deliveries/{deliveryId}/cancel-revert` | delivery | 확인된 로컬 롤백의 배송 원복 | 응답 없는 통신 실패에 최대 3회 (`order-delivery-cancel-revert`), 5xx 응답은 재시도 없음 |
| I-13 `DELETE /internal/deliveries/orders/{orderId}` | delivery | 생성 실패 보상·O-06 배송 삭제 | 생성 보상은 OrderService가 총 3회. O-06은 요청당 1회, 실패 후 같은 DELETE로 재개. Feign 자체 재시도 없음 |
| I-15 `GET /internal/deliveries/{deliveryId}/notification-recipients` | delivery | 커밋 후 DM의 현재 담당자 조회 | 통신 실패 시 총 3회 (`order-dm-delivery`), 5xx는 1회 |
| I-18 `DELETE /internal/deliveries/orders/{orderId}/creation` | delivery | 생성 실패 보상·운영자 복구 조건부 종료 | Feign 재시도 없음, 생성 보상만 최대 3회 |
| I-17 `GET /internal/deliveries/orders/{orderId}` | delivery | 배송 ID·상태와 CLOSED 확인 | 1회 (`order-delivery-state`) |
| I-16 `GET /internal/users/{userId}/notification-contact` | user | 배송담당 여부·Slack 이메일 조회 | 통신 실패 시 총 3회 (`order-dm-user`), 5xx는 1회 |

`CompanyClientAdapter`와 `DeliveryClientAdapter`가 application 인터페이스를 실제 Feign에 연결한다. 주소는 Eureka 서비스 이름으로 찾고 URL을 하드코딩하지 않는다. 5xx 응답은 재시도하지 않고 공통 디코더가 `SERVICE_UNAVAILABLE`로 바꾼다.

## 4. 구현 현황

- [x] 이메일 기반 배송담당자 Slack DM: 별도 AFTER_COMMIT 리스너·비동기 전송, 워크스페이스·허용 목록 검증, 같은 이벤트의 수신자 중복 제거, 외부 실패 격리. 주문 451개·전체 727개 테스트와 실제 Slack 접수 1건 확인(2026-10-16). [ADR 0016](../../decisions/0016-delivery-manager-slack-dm.md), [설정·실패 처리](../../guides/slack-dm.md)

- [x] 주문 Entity와 Flyway 마이그레이션 (`Order`, `OrderStatus`, `V1__create_p_orders.sql`)
- [x] 저장·단건 조회 Repository와 기반 테스트 (원복 후 단위 24개, PostgreSQL 통합 18개 통과)
- [x] 정수 원화 금액 long·BIGINT 복원, 곱셈 오버플로 방어 유지 (ADR 0005)
- [x] O-01 application 생성 흐름·권한 검증·보상 (외부 서비스 Mock, 단위 테스트 23개)
- [x] 실제 주문 DB 커밋·flush 실패·커밋 실패·트랜잭션 경계 검증 (PostgreSQL 통합 테스트 4개)
- [x] O-01 실제 Feign 어댑터·Spring 빈 조립·Controller 연결
- [x] O-01 생성 진행 기록·V3, 저장 예외 후 같은 DB의 진행 기록 잠금·커밋 결과 재확인
- [x] O-01 필수 Idempotency-Key·V4: 사용자별 키 UNIQUE·요청 지문·최초 응답 JSONB, 완료 재사용·처리 중/실패/복구 대기 409
- [x] 멱등성 단계 테스트 55개 추가. 당시 주문 347개·전체 XML 445개, 실패·오류·skip 0. 전체 build 통과
- [x] 실제 PostgreSQL 동시 INSERT 경쟁·유니크 실패 후 재조회, 다른 사용자·대소문자 구분, 삭제 이력 차단, V3 기존 행 유지 검증
- [x] 격리 Gateway·JWT·Feign·PostgreSQL 재요청 검증: 201·400·401·409·500·503, 헤더 전달·원래 응답 보존·배송 1회 확인
- [x] 로컬 주문 서비스만 교체·Flyway V4·키 UNIQUE·JSONB·health UP·Gateway 필수 헤더 문서 확인. 기존 3개 테이블·나머지 8개 컨테이너·.env·Webhook 유지
- [x] 주문 INSERT·생성 COMPLETED·이벤트를 같은 트랜잭션으로 처리. 실제 커밋 확인 시 결과 재사용·삭제 및 이벤트 재발행 없음
- [x] I-09 미확정 결과는 I-13 성공 후에도 복구 대기. 확인된 미저장만 삭제 결과에 따라 ABORTED 또는 DELIVERY_DELETE_FAILED
- [x] Controller 11개·실제 HTTP/Feign 13개 추가 검증. 주문 모듈 전체 93개 통과
- [x] 격리 Gateway·JWT·주문 서버·PostgreSQL 검증: 401·201·400·503, 위조 헤더 제거, 실패 보상 (user·company·delivery 응답은 HTTP 테스트 대역)
- [ ] 팀 company·delivery 구현·실제 데이터와 정상 주문 연동
- [x] O-02 검색·O-03 단건 조회, 논리 삭제 제외·주문자 범위·날짜·페이지·정렬 검증
- [x] O-04 요청사항 수정: 본인·MASTER 권한, 취소 상태 거부, 잠금 조회·변경 감지·Audit 응답
- [x] O-05 취소: 진행 기록·부분 유니크·권한·상태·Feign·로컬 커밋 재확인·원복
- [x] O-06 MASTER 삭제·I-10 연결·V5·삭제 허가 근거 보존·I-13 실패 뒤 재요청·커밋 재확인
- [x] 삭제 테스트 74개 추가: 도메인 7개·application 10개·MVC 8개·PostgreSQL 21개·HTTP/Feign 28개. 주문 421개·전체 XML 519개, 실패·오류·skip 0, 전체 build 통과
- [x] 실제 DB flush·완료 기록·커밋 실패, 커밋 뒤 반환 예외, 다른 연결에서 시작 기록 확인, 동시 삭제 잠금과 새 취소 차단 검증
- [x] I-13의 204 이외 2xx를 완료로 오인하지 않도록 검증. 기존 생성 보상·취소·멱등키 회귀 테스트 통과
- [x] 격리 Gateway·JWT 생성→취소→삭제와 배송 완료 삭제, 원격 삭제 후 로컬 롤백·같은 DELETE 재개·기존 생성 응답 재사용 확인
- [x] 로컬 주문 최종 이미지·V5·같은 스키마 FK·health UP·Gateway O-06 문서·미인증 DELETE 401 확인. 다른 8개 컨테이너·기존 데이터·.env·Webhook 유지
- [x] 커밋 후 비동기 Slack 알림, 타임아웃·HTTP 오류·큐 포화 시 주문 201 유지
- [x] Slack 구현 단계의 주문 테스트 108개 통과. 커밋 전·실패 시 알림 미실행, 비동기·헤더 격리·비밀값 로그 제외 확인
- [x] 조회 API 테스트 44개 추가. 조회 단계 주문 152개·전체 테스트 XML 250개, 실패·오류·skip 0
- [x] 수정 API 테스트 39개 추가. 수정 단계 주문 191개·전체 테스트 XML 289개, 실패·오류·skip 0
- [x] 취소 테스트 61개 추가. 취소 단계 주문 252개·전체 XML 350개, 실패·오류·skip 0. 전체 build 통과
- [x] 생성 보강 단계 테스트 40개 추가. 당시 주문 292개·전체 XML 390개, 실패·오류·skip 0. 전체 build 통과
- [x] 실제 커밋 뒤 반환 예외·재확인 실패·잠금 대기·이벤트 1회·완료 기록 실패 롤백·지연 배송 생성 기록 보존 확인
- [x] 격리 Gateway·JWT·실제 Feign·PostgreSQL 생성 보강 검증: 201·400·401·500·503, 시작 기록 실패 시 원격 호출 없음
- [x] 로컬 주문 서비스·V3 반영, 생성 기록 FK 없음·health UP·Gateway O-01 문서·미인증 401 확인. 기존 주문·취소 기록·다른 컨테이너·.env 유지
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

O-01은 배송 생성 전에 p_order_creations를 커밋하고 주문과 COMPLETED를 함께 저장한다. 저장 예외가 나도 커밋이 확인되면 201을 반환하며, 조회 실패·상태 불일치에는 배송을 삭제하지 않는다. I-09 타임아웃은 I-13 보상을 시도하고, I-17이 CLOSED로 확인하면 ABORTED로 닫는다. 확인하지 못하면 지연 생성이 끝났다는 보장이 없어 RECOVERY_REQUIRED를 유지한다(ADR 0011).
O-01 호출자는 `Idempotency-Key`를 반드시 보낸다. 새 주문에 새 키를 만들고 응답 유실·네트워크 재시도에는 **같은 키와 같은 본문**을 사용한다. UUID를 권장하며 1~128자 영문·숫자·`.`·`_`·`:`·`-`만 허용한다. 사용자 ID별로 구분하며 대소문자를 구별한다.
같은 키·본문의 완료 요청은 최초 생성 응답으로 201을 반환한다. 요청사항 수정·취소 이후에도 생성 응답은 그대로이며 현재 상태는 O-03에서 조회한다. 다른 본문·처리 중·실패 종료·복구 대기는 각 전용 코드로 409다. 키는 자동 만료하지 않는다([ADR 0008](decisions/0008-order-idempotency-key.md)).
전체 원본 요청 저장·자동 재실행·복구 API는 아직 없다. 생성 기록·커밋 재확인은 [스텝 44](../../learning/junhyun/steps/step-44-order-creation-progress.md), 멱등키 구현 이유는 [스텝 45](../../learning/junhyun/steps/step-45-order-idempotency-key.md), 타 서비스의 호출·재시도 규칙은 [연동 실패 계약](integration-contract.md)에 정리했다.

O-06은 MASTER가 본문 없이 호출하며 성공은 200·data=null이다. CANCELED 주문은 I-10 없이, ORDERED 주문은 I-10의 주문·배송 ID와 DELIVERED를 확인한 뒤 삭제한다. 미해결 취소가 있으면 409 ORDER_NOT_DELETABLE이다.
삭제 가능 근거를 먼저 커밋하고 주문 잠금 안에서 논리 삭제·I-13·완료 기록을 처리한다. 배송만 삭제된 뒤 DB가 롤백돼도 같은 DELETE 재요청은 저장된 근거로 이어가며, 삭제된 배송을 다시 GET하지 않는다. 이미 삭제된 주문의 새 요청은 404다.
삭제 시작 뒤 새 취소는 409 INVALID_STATUS_TRANSITION으로 막는다. 삭제는 기존 생성 멱등키·최초 응답을 지우지 않고 Slack도 발행하지 않는다. 근거는 [ADR 0009](decisions/0009-order-deletion-progress.md), 재현과 남은 범위는 [스텝 46](../../learning/junhyun/steps/step-46-order-deletion.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

타 서비스 연동 요청은 받는 서비스의 창구에 남겼다. 기존 SA 계약을 사용하는 주문 내부 구현과 실제 제공 서비스 검증을 구분한다.

- [COMPANY-001](../company/README.md#company-001-주문-연동용-조회-응답과-오류-계약-확인): I-07·I-08 필드·가격·오류 전파와 실제 연동 확인, OPEN.
- [DELIVERY-001](../delivery/README.md#delivery-001-주문-saga의-내부-api와-실패-계약-연동-확인): 생성·취소·원복·삭제의 기존 계약과 실제 연동 확인, OPEN.
- [DELIVERY-002](../delivery/README.md#delivery-002-지연-생성과-삭제의-순서-역전-방지-계약-검토): 지연 생성과 삭제 순서 역전·종료 표시·시도 토큰·결과 확인 수단 검토, OPEN. 추가 보장은 미확정이다.

### ORDER-001 주문 취소 가능 상태 변경 확인 요청 (delivery ADR 0003)
- 상태: OPEN
- 작성: delivery / 한얼 / 2026-10-15
- 관련: I-11, I-12, SA 2.5·7.2, [delivery ADR 0003](../delivery/decisions/0003-delivery-three-legs-sa-v2.md), [order ADR 0002](decisions/0002-order-cancel-before-departure.md)
- 내용: delivery는 SA v2.0의 3구간 모델을 채택했다(delivery ADR 0003, 0001 대체). 배송은 `PICKUP_WAITING`에서 시작하고, I-11 배송 취소는 `PICKUP_WAITING`에서만 성공한다. 그 밖의 상태에서는 409 `INVALID_STATUS_TRANSITION`을 반환한다. order ADR 0002는 "모든 배송은 HUB_WAITING으로 시작(ADR-11)"을 근거로 취소 조건을 `HUB_WAITING`으로 두고 있어 근거가 바뀌었다. order 쪽 취소 조건과 ADR 0002 채택 여부를 확인해 달라.
- 주문 측 확인(2026-10-15): 현재 O-05는 SA v2.0의 PICKUP_WAITING 기준을 따르며 집하 시작 여부는 I-11이 원자적으로 판단한다. order ADR 0002는 제안 상태로 채택하지 않았다. 주문의 HTTP 대역 검증은 통과했고 제공 서비스와의 실제 통합은 별도 확인 대상이다.
- 결정: 주문 측 구현 기준을 답변했다. 제공 담당자 확인과 실제 연동 결과는 후속 기록으로 남긴다.

### ORDER-002 재고 할당·복원(I-05, I-06) 계약 변경 알림과 호출 요청
- 상태: OPEN
- 작성: hub / 최승근 / 2026-10-19
- 관련: hub I-05·I-06, SA 6.3, [hub ADR 0003](../hub/decisions/0003-stock-allocation-supplier-hub-only.md)
- 내용: hub-service가 재고 고도화로 I-05·I-06을 구현했다. SA 6.3과 다르게 **공급업체 허브 한 곳**에서만 차감한다. I-05 요청은 `orderId, productId, quantity, supplierHubId`(공급업체 허브, I-07의 `hubId`이며 delivery I-09의 `departureHubId`와 같다), 응답은 `{hubId, quantity}` 한 건이다. 재고가 모자라거나 재고 레코드가 없으면 409 `STOCK_INSUFFICIENT`, 같은 `orderId` 재요청은 처음 결과를 반환한다. I-06은 요청 `orderId`, 응답 `restoredQuantity`이며 이력이 없거나 이미 복원했으면 0이다.
- 2026-10-20 추가: 복원(I-06)까지 끝난 주문을 같은 `orderId`로 다시 I-05하면 409 `STOCK_ALREADY_RESTORED`다(한 주문 ID는 할당과 복원이 한 번씩). 취소 뒤 같은 `orderId`로 재할당하는 흐름이 있으면 알려 달라.
- 요청: 재고를 켤 때 주문 생성에서 I-05, 실패·취소 보상에서 I-06을 호출할지와 시점을 정해 달라. SA와 다르므로 이 계약을 받아들일지도 확인해 달라. 구현 전이라 order 코드는 바꾸지 않았다.
- 결정:

## 2026-10-20 생성 복구 로컬 확장

[ADR 0019](../../decisions/0019-order-creation-operator-recovery.md)에 따라 두 서비스의 통합 구현을 진행했다. 팀 전체 승인을 뜻하지 않는다.
V7은 생성 기록 recovery_id·RECOVERY_TAKEOVER 사유와 p_order_creation_recoveries·p_order_creation_recovery_events를 추가한다.
O-09 본문·응답·재개 계약은 ADR과 [복구 운영 안내](recovery.md)를 따른다. 외부 호출 중 order DB 트랜잭션을 유지하지 않는다.
생성 보상은 I-18을 사용한다. 기존 주문 삭제 O-06의 I-13은 유지한다. 기존 생성·진행 조회 응답은 바꾸지 않는다.
