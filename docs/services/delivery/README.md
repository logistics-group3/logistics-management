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
| D-03 | `PATCH /api/v1/deliveries/{deliveryId}` | 현재 배송담당자인 업체 배송 구간 담당자, MASTER | 구현 | 지정 해제 후 배정 ID만 같아도 403. 두 필드 모두 없으면 400 `VALIDATION_ERROR`(SA에 없는 규칙) |
| D-04 | `POST /api/v1/deliveries/{deliveryId}/routes/{routeId}/manager` | MASTER | 구현 |  |
| D-05 | `PATCH /api/v1/deliveries/{deliveryId}/routes/{routeId}/status` | 현재 배송담당자인 그 구간 담당자, MASTER | 구현 | 지정 해제 후 배정 ID만 같아도 403. `status`에 `WAITING`·`CANCELED`를 보내면 409 `INVALID_STATUS_TRANSITION` |
| I-09 | `POST /internal/deliveries` | order | 구현 | 새로 만들면 201, 같은 orderId면 200 + 기존 값. 응답에 `orderId`도 담는다(SA는 deliveryId, status) |
| I-10 | `GET /internal/deliveries/{deliveryId}` | order | 구현 |  |
| I-11 | `POST /internal/deliveries/{deliveryId}/cancel` | order | 구현 | 응답 `deliveryId, orderId, status`. 이미 취소면 200 |
| I-12 | `POST /internal/deliveries/{deliveryId}/cancel-revert` | order | 구현 | 응답 `deliveryId, orderId, status`. 취소 상태가 아니면 no-op 200 |
| I-13 | `DELETE /internal/deliveries/orders/{orderId}` | order | 구현 | 204. 대상이 없어도 204 |
| I-15 | `GET /internal/deliveries/{deliveryId}/notification-recipients` | order | 로컬 구현·팀 리뷰 대기 | `{deliveryId, orderId, status, managerIds}`. WAITING·MOVING 담당자 중복 제거, 완료·취소는 빈 목록, 없음·삭제는 404 DELIVERY_NOT_FOUND. [ADR 0016](../../decisions/0016-delivery-manager-slack-dm.md) |

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
- [x] I-15 알림 수신자 조회. delivery 테스트 39개와 실제 주문→I-15→DM 1건 확인(2026-10-16). [T06](../../../delivery-service/docs/tasks/T06.md)
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

### DELIVERY-003 주문 커밋 후 DM용 배송담당자 읽기 계약

- 상태: OPEN (기능 구현 요청에 따른 로컬 변경, 제공 담당자 리뷰 대기)
- 작성: order / Junhyun An 작업 / 2026-10-15
- 내용: 사용자의 이메일 DM 구현 요청에 따라 I-15를 추가한다. 기존 I-10은 유지하고 별도 내부 조회로 배송·주문 ID, 상태, 중복 없는 구간 담당자 ID를 제공한다.
- 관련: [ADR 0016](../../decisions/0016-delivery-manager-slack-dm.md). 팀 전체 승인·제공 담당자의 검토 완료를 뜻하지 않는다.
- 구현·검증: 2026-10-16 QueryDSL 조회 결과를 재사용해 구현했고 중복 제거·완료 구간 제외·취소·삭제 테스트를 통과했다. 테이블·I-10·배정 규칙은 유지했다. 실제 같은 허브 2구간 담당자가 DM 1건을 받는 경로를 확인했으며 재배정·취소와 발송의 원자성은 보장하지 않는다.
- U-01 후속 변경: 이메일만으로 가입한 사용자는 I-01·I-14의 slackId가 null이다. 자동 배정은 userId만 사용하고 수령인 ID 조회는 Optional.empty로 처리할 수 있어 기존 배송 코드는 유지한다. 배송의 recipientSlackId도 null일 수 있으며 DM은 I-16 이메일로 발송한다. 제공 담당자가 소비 계약을 함께 확인한다.

### DELIVERY-004 배송·구간 동시 변경 보호(낙관적 잠금) 검토

- 상태: OPEN
- 작성: 공통 인프라(멘토 가이드 검증) / Junhyun An 작업 / 2026-10-16
- 관련: D-04·D-05, I-11, DELIVERY-001의 "행 잠금·버전 필드" 확인 항목
- 내용: `Delivery`·`DeliveryRoute`에 `@Version`이 없고 명령용 조회에도 잠금이 없다(`grep @Version` 0건). DELIVERY-001의 취소(I-11)·집하 출발(D-05) 경쟁 외에 재배정(D-04)·도착(D-05) 경쟁도 있다. 재배정은 도착·취소만 막는다(`changeManager`). Hibernate는 바뀐 Entity의 모든 컬럼을 UPDATE하므로, MOVING을 읽은 재배정이 늦게 커밋하면 이미 ARRIVED된 구간을 MOVING으로 되돌릴 수 있다. 그러면 배송 상태와 구간 상태가 어긋난다.
- 검토 요청: 두 Entity에 `@Version`을 두고 충돌을 409로 응답하는 방안, 또는 명령용 `findById`에 `PESSIMISTIC_WRITE`를 거는 방안 중 정한다. 구간 변경은 `refreshStatus()`가 배송 행도 바꾸므로 `Delivery` 버전으로 D-05끼리·D-05와 I-11의 충돌은 잡힌다. 배송 행을 바꾸지 않는 D-04는 `DeliveryRoute` 버전이 필요하다.
- 근거: 코드 정적 확인. 동시 실행 재현은 하지 않았다.
- 결정:

### DELIVERY-005 D-05 실제 거리·소요시간 검증과 자동 계산

- 상태: OPEN
- 작성: 공통 인프라(멘토 가이드 검증) / Junhyun An 작업 / 2026-10-16
- 관련: SA 6.8 D-05, `DeliveryRoute.arrive`, `ChangeRouteStatusRequest`, 프런트 `DeliverySheet.tsx`의 `needActual`
- 내용: 2026-10-16 개발 스택에서 허브 간 주문 1건으로 시험했다(시험 주문은 삭제). 허브 이동 도착에 값이 없거나 거리만 있거나 음수면 400이다. 하지만 **0km·0분은 200으로 저장된다**(`@PositiveOrZero`). 업체 배송 도착에 거리만 보내면 거리 5.0·시간 null로 저장된다. 구간에 출발·도착 시각 컬럼이 없어 실제 소요시간을 사람이 입력해야 하고, 입력값을 검증할 수 없다.
- 제안: `departed_at`·`arrived_at`을 기록하고 실제 소요시간은 계산한다. 실제 거리는 선택으로 받되 `@Positive`로 받는다. 모든 구간에서 거리·시간을 둘 다 받거나 둘 다 비우게 한다(`RouteInfo`). SA D-05와 프런트 입력칸을 함께 고친다.
- 근거: [질문 노트](../../learning/junhyun/qna/2026-10-16-delivery-actual-distance-review.md) 2절 시험 결과
- 결정:

### DELIVERY-006 일반 사용자 배송 조회 응답의 개인정보 범위

- 상태: OPEN
- 작성: 공통 인프라(멘토 가이드 검증) / Junhyun An 작업 / 2026-10-16
- 관련: D-01·D-02, `DeliveryQueryService.isScopedToOwnRoutes`, `DeliverySummaryResult`
- 내용: 조회 범위를 좁히는 대상은 배송담당자뿐이다. 일반 USER는 모든 배송의 집하지·배송지 주소, 수령인, `recipientSlackId`를 받는다. 발제의 "조회·검색은 모든 로그인 사용자"는 지키지만 개인정보가 넓게 노출된다.
- 검토 요청: 최소한 `recipientSlackId`를 외부 응답에서 빼는 방안과, 일반 USER에게 주소·수령인을 가리는 역할별 응답을 검토한다. 프런트 배송 화면이 쓰는 필드라 계약을 바꾸면 프런트도 함께 고친다.
- 결정:

### DELIVERY-007 배송담당자 지정 해제 시 진행 중 구간 처리

- 상태: 로컬 구현, 팀 리뷰 대기 (#13)
- 작성: 공통 인프라(멘토 가이드 검증) / Junhyun An 작업 / 2026-10-16
- 관련: user U-07, D-04·D-05, I-14
- 내용: D-05 권한은 `route.managerId`만 비교한다. 배송담당자 지정을 해제한 사용자가 맡던 WAITING·MOVING 구간은 그대로 남는다. 그 사용자는 계속 처리할 수 있고 조회 범위 제한은 풀린다. 자동 재배정은 없다.
- 검토 요청: 해제를 막을지(진행 중 구간이 있으면 409), 해제 시 재배정을 요구할지, D-05에서 현재 배송담당자인지 다시 확인할지 user 담당자와 함께 정한다.
- 결정: 2026-10-19 사용자가 상세 수정안을 확인하고 #13 구현을 요청했다. D-03·D-05는 MASTER 또는 현재 배송담당자이면서 해당 구간 담당자만 허용한다. 기존 배정이 남아 있어도 현재 자격이 false면 403이다. 요청·응답 DTO·DB·내부 API는 그대로다.
- 인계: 진행 중인 WAITING·MOVING 구간은 MASTER가 기존 D-04로 같은 구간 담당 허브의 현재 배송담당자에게 수동 재배정한다. 해제 자체 거절·자동 인계는 추가하지 않는다. Gateway의 60초 캐시 반영 지연과 일반 USER 전체 조회 정책(#12)은 별도 범위다. 팀 전체 승인·병합 완료를 뜻하지 않는다.
- 검증 기록: [T07](../../../delivery-service/docs/tasks/T07.md).

### DELIVERY-008 배송 검색 보조 정렬과 HTTP·I-03 계약 테스트

- 상태: OPEN
- 작성: 공통 인프라(멘토 가이드 검증) / Junhyun An 작업 / 2026-10-16
- 관련: D-01, `DeliveryQueryRepositoryImpl.search`, `DeliveryIntegrationTest`
- 내용: 배송 검색은 요청 정렬만 쓴다. `createdAt`이 같으면 페이지 사이에 중복·누락이 생길 수 있다(주문 검색은 ID로 보조 정렬). D-01~D-05의 HTTP 계층 테스트가 없어 `@RequireRole`·요청 검증은 간접적으로만 확인된다. 통합 테스트는 `HubRouteReader`를 mock으로 바꾼다. hub I-03(PR #9)이 dev에 들어왔으니 응답 형식 계약 테스트를 추가할 수 있다.
- 결정:
