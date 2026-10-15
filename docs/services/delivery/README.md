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
| D-01~D-05 | `/api/v1/deliveries/**` | 로그인, 구간 담당자, MASTER | 예정 |  |
| I-09~I-13 | `/internal/deliveries/**` | order | 예정 | 배송 생성·삭제는 내부 API로만 제공(SA 6.2) |

I-09~I-13 구현 전 [주문 연동 실패 계약](../order/integration-contract.md#delivery-담당자)을 확인한다. 특히 4xx 거절의 로컬 롤백, I-09 멱등성, I-11 원자적 상태 판단, I-12 원복, I-13과 지연 생성의 순서 역전을 구분한다.

## 3. 호출하는 다른 서비스 API

| API | 제공 서비스 | 용도 | 재시도 |
|---|---|---|---|
| I-01 `GET /internal/users/{userId}` | user | 재배정 대상 확인 | GET이라 가능 |
| I-03 `GET /internal/hub-routes` | hub | 허브 이동 구간 정보 | GET이라 가능 |
| I-14 `GET /internal/users/delivery-managers?hubId=` | user | 담당자 자동 배정 후보 | GET이라 가능 |

## 4. 구현 현황

- [ ] 배송·구간 Entity와 Flyway 마이그레이션
- [ ] I-09 배송 생성과 담당자 자동 배정
- [ ] D-01~D-05, I-10~I-13

서비스 결정은 [decisions/](decisions/README.md)에 있다.

## 5. 협업 로그

작성 규칙은 [서비스 협업 창구](../README.md#협업-로그-규칙)를 따른다. 다른 서비스에 요청할 때는 **그 서비스의** 협업 로그에 쓴다.

### DELIVERY-001 주문 Saga의 내부 API와 실패 계약 연동 확인

- 상태: OPEN
- 작성: order / Junhyun An 작업 / 2026-10-15 (사용자의 타 서비스 인계 기록 요청)
- 관련: I-09·I-11·I-12·I-13, O-01·O-05
- 내용: [연동 계약](../order/integration-contract.md)의 기존 SA 응답·멱등키·재시도·보상 전제를 확인한다. 주문은 I-09 성공 deliveryId와 I-11 성공 status=CANCELED를 검사하며, I-12는 성공 코드로 원복을 확인한다. 명확한 4xx에는 변경이 남지 않아야 한다.
- 요청: 배송·구간 취소와 원복의 원자성, 반복 요청, 실패 코드, X-User-* Audit를 실제 구현과 함께 검증한다. 주문 측 테스트는 HTTP 대역이며 제공 서비스 자체를 검증한 것은 아니다.
- 결정: 담당자 확인 대기. 기존 SA 구현·연동 확인 요청이다.

### DELIVERY-002 지연 생성과 삭제의 순서 역전 방지 계약 검토

- 상태: OPEN
- 작성: order / Junhyun An 작업 / 2026-10-15
- 관련: I-09 타임아웃·재시도, I-13 배송 삭제, 생성 실패 복구
- 내용: I-13이 없음으로 204를 반환한 뒤 지연된 I-09가 생성될 수 있다. 주문은 삭제 보상을 시도하더라도 RECOVERY_REQUIRED를 유지하고, 현재 계약만으로 자동 복구 완료를 선언하지 않는다.
- 검토 요청: 같은 orderId의 종료 표시 보존, 삭제 이력을 포함한 멱등키 수명, 시도 토큰으로 오래된 실행을 차단하는 방안 중 제공 가능한 보장을 정한다. orderId로 미확정 생성 결과를 확인할 수단도 함께 검토한다. 필요한 요청·응답 변경은 결정 후 합의한다.
- 현재 영향: 주문의 진행 기록·커밋 재확인 구현은 기존 계약으로 진행한다. 추가 자동 복구와 미확정 기록 종료는 이 정책 확인 전까지 보장하지 않는다.
- 결정: 미정. 새 토큰 필드나 종료 표시가 이미 구현됐다고 가정하지 않는다.
