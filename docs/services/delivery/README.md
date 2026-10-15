# delivery 협업 창구

배송을 맡는 delivery-service의 계약과 현황이다. 이 서비스 코드를 고칠 때 지킬 규칙은 [`delivery-service/AGENTS.md`](../../../delivery-service/AGENTS.md)에 있다. API의 요청·응답 필드 원본은 [SA](../../SA/sparta-logistics-sa.md) 6장이다.

## 1. 서비스 정보

| 항목 | 값 |
|---|---|
| 담당자 | 미정 |
| 포트 | 8085 |
| 스키마·DB 계정 | `delivery_schema` / `delivery_app` |
| 책임 | 배송·배송 구간(집하·허브 이동·업체 배송) 생성, 담당자 자동 배정·재배정, 상태 관리 |
| 소유 테이블 | `p_deliveries`, `p_delivery_routes` (SA 5.6) |

## 2. 제공 API (계약)

| ID | Method·URL | 권한·호출자 | 상태 | SA와 다른 점·비고 |
|---|---|---|---|---|
| D-01 | `GET /api/v1/deliveries` | 로그인. 배송담당자는 담당 구간이 있는 배송만 | 구현 | 정렬 `createdAt`, `status` |
| D-02 | `GET /api/v1/deliveries/{deliveryId}` | 로그인. 배송담당자는 담당 구간이 있는 배송만(아니면 403) | 구현 |  |
| D-03 | `PATCH /api/v1/deliveries/{deliveryId}` | 업체 배송 구간 담당자, MASTER | 구현 | 두 필드 모두 없으면 400 `VALIDATION_ERROR`(SA에 없는 규칙) |
| D-04 | `POST /api/v1/deliveries/{deliveryId}/routes/{routeId}/manager` | MASTER | 구현 |  |
| D-05 | `PATCH /api/v1/deliveries/{deliveryId}/routes/{routeId}/status` | 그 구간 담당자, MASTER | 구현 | `status`에 `WAITING`·`CANCELED`를 보내면 409 `INVALID_STATUS_TRANSITION` |
| I-09 | `POST /internal/deliveries` | order | 구현 | 새로 만들면 201, 같은 orderId면 200 + 기존 값. 응답에 `orderId`도 담는다(SA는 deliveryId, status) |
| I-10 | `GET /internal/deliveries/{deliveryId}` | order | 구현 |  |
| I-11 | `POST /internal/deliveries/{deliveryId}/cancel` | order | 구현 | 응답 `deliveryId, orderId, status`. 이미 취소면 200 |
| I-12 | `POST /internal/deliveries/{deliveryId}/cancel-revert` | order | 구현 | 응답 `deliveryId, orderId, status`. 취소 상태가 아니면 no-op 200 |
| I-13 | `DELETE /internal/deliveries/orders/{orderId}` | order | 구현 | 204. 대상이 없어도 204 |

배송 생성·삭제는 내부 API로만 제공한다(SA 6.2). 요청·응답 상세는 [`delivery-api.md`](../../../delivery-service/docs/domain/delivery-api.md).

I-09~I-13 구현 전 [주문 연동 실패 계약](../order/integration-contract.md#delivery-담당자)을 확인한다. 특히 4xx 거절의 로컬 롤백, I-09 멱등성, I-11 원자적 상태 판단, I-12 원복, I-13과 지연 생성의 순서 역전을 구분한다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-01 `GET /internal/users/{userId}` | user | I-09 수령인 Slack ID(404면 NULL로 진행), D-04 재배정 대상 확인 | 가능(`IdempotentRetryConfig`) |
| I-03 `GET /internal/hub-routes?departureHubId=&arrivalHubId=` | hub | 허브 이동 구간 예상 거리·시간. 같은 허브면 부르지 않는다 | 가능(`IdempotentRetryConfig`) |
| I-14 `GET /internal/users/delivery-managers?hubId=` | user | 담당자 자동 배정 후보. 구간 담당 허브마다 1번 | 가능(`IdempotentRetryConfig`) |

delivery 초기 구현 시 I-03·I-14는 미구현이었다. 최종 대조한 dev `df419a4`에는 user I-14·hub I-02가 반영됐고 hub I-03 Controller는 아직 없다. 실제 호출 검증은 허브 구현·데이터 준비 후 확인한다.

## 4. 구현 현황

- [x] 배송·구간 Entity와 Flyway 마이그레이션(V1, 2026-10-15)
- [x] 상태 전이(구간 상태 → 배송 상태), 취소·원복, 재배정 규칙을 Entity 메서드로(2026-10-15)
- [x] I-09 배송 생성과 담당자 자동 배정, orderId 멱등(동시 요청 포함)
- [x] D-01~D-05, I-10~I-13
- [ ] Gateway 경유 호출 확인(user I-14, hub I-03 구현 후)

서비스 결정은 [decisions/](decisions/README.md)에 있다.
- 조회(D-01, D-02, I-10, 담당 건수)는 QueryDSL `@QueryProjection`으로 DTO를 바로 읽고, 명령은 JPA(`findById`, `save`)로 Entity를 다룬다(2026-10-15, 계약 변경 없음). 이유는 `delivery-service/docs/domain/delivery-decision-log.md` 17
- 응답은 Result 하나면 그대로, 여러 Result를 합칠 때만 `Response`(D-02~D-05 `DeliveryResponse`)로 응답한다. 외부·내부 API JSON 구조는 바뀌지 않았다(2026-10-15 MockMvc 비교). 이유는 결정 로그 18

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### DELIVERY-001 주문 Saga의 내부 API와 실패 계약 연동 확인

- 상태: OPEN
- 작성: order / Junhyun An 작업 / 2026-10-15 (사용자의 타 서비스 인계 기록 요청)
- 관련: I-09·I-10·I-11·I-12·I-13, O-01·O-05·O-06
- 내용: [연동 계약](../order/integration-contract.md)의 기존 SA 응답·멱등키·재시도·보상 전제를 확인한다. 주문은 I-09 성공 deliveryId와 I-11 성공 status=CANCELED를 검사하며, I-12는 성공 코드로 원복을 확인한다. 명확한 4xx에는 변경이 남지 않아야 한다.
- 요청: 배송·구간 취소와 원복의 원자성, 반복 요청, 실패 코드, X-User-* Audit를 실제 구현과 함께 검증한다. 주문 측 테스트는 HTTP 대역이며 제공 서비스 자체를 검증한 것은 아니다.
- O-06 추가 확인: I-10의 deliveryId·orderId·status 일치, I-13의 배송·구간 동시 논리 삭제와 없음·이미 삭제도 204를 확인한다. 주문은 204 이외의 2xx를 완료로 처리하지 않는다. 배송 삭제 후 주문 DB 롤백 시 같은 I-13이 반복되며, 주문 내부 진행 기록으로 재개하므로 I-10에 삭제 이력을 반환하는 새 계약은 요구하지 않는다.
- PR 준비 확인: dev `1384a5d`의 내부 DTO·204 계약은 주문과 정적으로 일치한다. 다만 I-11·D-05가 동일 배송 상태를 조회·변경할 때 현재 저장소·Entity에 행 잠금·버전 필드가 없어 동시 집하 시작·취소의 보호를 추가 확인해야 한다. 주문 서비스의 잠금이 제공 서비스 내부 경쟁을 막지는 않는다.
- 결정: 담당자 확인 대기. 기존 SA 구현·연동 확인 요청이다.

### DELIVERY-002 지연 생성과 삭제의 순서 역전 방지 계약 검토

- 상태: OPEN
- 작성: order / Junhyun An 작업 / 2026-10-15
- 관련: I-09 타임아웃·재시도, I-13 배송 삭제, 생성 실패 복구
- 내용: I-13이 없음으로 204를 반환한 뒤 지연된 I-09가 생성될 수 있다. 주문은 삭제 보상을 시도하더라도 RECOVERY_REQUIRED를 유지하고, 현재 계약만으로 자동 복구 완료를 선언하지 않는다.
- 검토 요청: 같은 orderId의 종료 표시 보존, 삭제 이력을 포함한 멱등키 수명, 시도 토큰으로 오래된 실행을 차단하는 방안 중 제공 가능한 보장을 정한다. orderId로 미확정 생성 결과를 확인할 수단도 함께 검토한다. 필요한 요청·응답 변경은 결정 후 합의한다.
- 현재 영향: 주문의 진행 기록·커밋 재확인 구현은 기존 계약으로 진행한다. 추가 자동 복구와 미확정 기록 종료는 이 정책 확인 전까지 보장하지 않는다.
- 결정: 미정. 새 토큰 필드나 종료 표시가 이미 구현됐다고 가정하지 않는다.
