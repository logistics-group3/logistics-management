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

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-01 `GET /internal/users/{userId}` | user | I-09 수령인 Slack ID(404면 NULL로 진행), D-04 재배정 대상 확인 | 가능(`IdempotentRetryConfig`) |
| I-03 `GET /internal/hub-routes?departureHubId=&arrivalHubId=` | hub | 허브 이동 구간 예상 거리·시간. 같은 허브면 부르지 않는다 | 가능(`IdempotentRetryConfig`) |
| I-14 `GET /internal/users/delivery-managers?hubId=` | user | 담당자 자동 배정 후보. 구간 담당 허브마다 1번 | 가능(`IdempotentRetryConfig`) |

I-03, I-14는 2026-10-15 기준 제공 서비스에 아직 없다. delivery는 SA 6.3 계약대로 Feign 클라이언트를 만들어 두었다.

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

아직 기록이 없다.
