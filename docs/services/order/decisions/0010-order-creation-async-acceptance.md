# 0010. 주문 생성의 비동기 접수와 처리 시간 예산

- 상태: 채택 (주문 담당자)
- 날짜: 2026-10-19
- 결정자: Junhyun An, 운영 리뷰 이슈 #16(주문 처리 전체 시간 예산과 결과 확인 계약)에서 "비동기 접수까지" 구현을 선택

## 맥락

O-01은 요청 스레드 하나가 업체·상품 조회 → 진행 기록 → I-09 → 주문 저장 → 실패 시 보상을 끝까지 이어서 처리했다.
호출마다 연결 5초·읽기 20초이고 멱등 호출은 최대 3회 재시도하므로, 브라우저는 20초에 포기해도 서버는 더 오래 일하며 요청 스레드와 DB 연결을 점유할 수 있었다.
이 값들을 단순 합산한 값이 관측된 응답시간은 아니다. 실제 최대 소요시간과 부하에서의 고갈은 측정하지 않았다.

## 결정

1. **접수와 처리를 나눈다.** 접수 단계(요청 스레드)는 같은 키 재요청 판정, 업체·상품 조회, 권한 확인, 진행 기록 시작까지만 하고 `202 Accepted {orderId, status: PROCESSING}`로 응답한다.
   처리 단계는 별도 스레드에서 I-09 → 주문 저장 → 실패 시 I-13 보상을 끝까지 하고 결과를 진행 기록(`p_order_creations`)에 남긴다. 처리 로직과 보상 규칙은 ADR 0007을 그대로 따른다.
2. **진행 조회 O-07**: `GET /api/v1/orders/creations/{orderId}`(주문자 본인·MASTER). `status`는 PROCESSING·COMPLETED·ABORTED·RECOVERY_REQUIRED이고, COMPLETED는 최초 응답 스냅샷의 `order`를, ABORTED는 `failureCode`·`failureMessage`를 담는다. RECOVERY_REQUIRED의 `failureCode`는 `CreationFailureReason`이다.
   요청 응답으로 돌려주던 실패 원인(예: 배송담당자 없음 404)은 이제 접수 뒤에 생기므로 이 조회가 보여 준다. 이를 위해 `p_order_creations`에 `failure_code`·`failure_message`를 추가했다(V6).
3. **같은 키 재요청**: 처리 중이면 `202`와 같은 orderId, 완료면 `201`과 최초 응답, ABORTED·RECOVERY_REQUIRED는 기존 409다. HTTP 응답으로는 `ORDER_CREATION_IN_PROGRESS`가 더 이상 오지 않는다(동기 실행 `OrderService.create`는 기존대로 409다).
4. **유한한 실행기**: `OrderCreationExecutor`가 스레드 4개·대기열 50개로 처리 단계를 실행한다(`order.creation.executor.*`). 가득 차면 원격 호출 전이므로 시도를 ABORTED(`SERVICE_UNAVAILABLE`)로 닫고 503이다(접수 전 조기 거절로 보완: [ADR 0012](0012-order-shed-load-before-accept.md)). 종료 시 최대 30초 기다린다.
5. **시간 예산**(order-service 로컬 설정만 바꾼다. 공통 Feign 설정·Gateway·프런트 타임아웃은 그대로다):

| 단계 | 호출 | 연결·읽기 | 재시도 | 호출 1건 최악 | 단계 최악 |
|---|---|---|---|---|---|
| 접수(요청 스레드) | 업체·상품 조회 3건(order-company) | 1초·1.5초 | 공통 정책 최대 3회 | 약 8초 | 접수 마감 8초 + 호출 1건 = 약 16초. 브라우저 20초·Gateway 60초 안 |
| 처리(별도 스레드) | I-09(order-delivery-create) | 1초·12초 | 공통 정책 최대 3회 | 약 39초 | I-09 약 39초 + I-13 보상 3회 x (1초+3초) = 약 51초 |

   접수 단계는 호출 사이마다 8초 마감을 확인해 넘으면 다음 호출 없이 503으로 끝낸다. 위 값은 설정에서 계산한 상한이며 실제 측정값이 아니다.
6. **지연된 원격 작업을 실패로 단정하지 않는다.** order가 I-09를 먼저 끊고 보상해도 이후 도착한 I-09는 delivery의 종료 표시가 409로 거절한다(delivery 결정 로그 23). 새 멱등키로 재주문하지 않는다.

## 결과와 한계

- 서버가 처리 중에 종료되면 시도가 PROCESSING으로 남는다. 동기 요청 중 서버가 죽을 때와 같은 상태이고, 오래된 PROCESSING을 시간만으로 해제하지 않는다는 원칙을 유지한다. 확인은 I-17(`GET /internal/deliveries/orders/{orderId}`)과 failure-handling.md의 SQL로 한다. 자동 복구·운영 복구 API는 범위 밖이다.
- 처리 단계의 실행 순서와 처리량은 스레드 수에 묶인다. 부하 시험으로 값을 정하지 않았다.
- 응답이 201에서 202로 바뀌므로 호출자(프런트)가 진행 조회를 폴링해야 한다. 프런트는 같은 PR에서 맞췄다.
