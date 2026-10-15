# 주문 실패·예외 처리 설계

- 작성: Junhyun An 작업 / 2026-10-15
- 기준: 현재 O-01~O-05 코드, [SA 7장](../../SA/sparta-logistics-sa.md), [주문 ADR 0006](decisions/0006-order-cancellation-progress.md)
- 상태: O-05의 진행 기록·결과 확인·보상은 구현했다. 아래에서 `후속 설계`로 표시한 생성 보강·복구 API는 아직 구현하지 않았다.

## 1. 오류 코드보다 먼저 결정할 것

실패 응답을 받았다는 사실과 데이터가 바뀌지 않았다는 사실은 다르다. **변경 결과가 확정됐는지**를 먼저 구분한다.

| 분류 | 예 | 처리 원칙 |
|---|---|---|
| 변경 전 거절 | 입력 오류, 타인 주문, 배송 시작 후 취소 409 | 원래 오류를 반환한다. 바뀐 것이 없으므로 보상하지 않는다. |
| 변경 성공 확인 | 배송 취소 응답의 status=CANCELED | 다음 단계를 진행할 수 있다. 이후 단계 실패 시 보상 대상이다. |
| 로컬 롤백 확인 | 새 트랜잭션에서 주문 ORDERED·시도 PROCESSING 확인 | 앞서 성공한 배송 취소를 원복한다. |
| 결과 불확실 | 타임아웃, 5xx, 커밋 중 연결 단절, 잘못된 성공 본문 | 성공·실패를 추측하지 않는다. 실행권을 보존하고 확인 대상으로 남긴다. |
| 핵심 커밋 후 부가 기능 실패 | Slack 타임아웃·큐 포화 | 주문 성공을 유지한다. 주문·배송을 되돌리지 않는다. |

외부 서비스가 4xx를 반환한 경우 해당 변경이 롤백되었다는 내부 API 계약을 전제로 한다. 상태를 바꾼 뒤 4xx를 반환하는 구현이라면 제공 서비스에서 먼저 계약을 바로잡아야 한다.

```mermaid
flowchart TD
    A[오류 발생] --> B{변경 전의 명확한 거절인가}
    B -->|예| C[원래 오류 코드 반환]
    B -->|아니오| D{변경 결과를 확인할 수 있는가}
    D -->|아니오| E[진행 기록 유지와 복구 대기]
    D -->|예| F{핵심 작업이 커밋됐는가}
    F -->|예| G[정상 결과 유지]
    F -->|아니오| H[앞서 성공한 단계만 보상]
    H --> I{보상 성공 확인}
    I -->|예| J[시도 종료와 최초 실패 반환]
    I -->|아니오| E
    E --> K[새 실행 차단과 수동 확인]
```

## 2. 계층별 책임

| 계층 | 책임 | 현재 연결 |
|---|---|---|
| Controller·Request | 형식·필수값 검증, 성공 응답 변환 | @Valid, ApiResponse, 전용 Response |
| Entity | 허용 상태 전이·값의 불변식 | Order.cancel, updateRequestNote, OrderCancellation 전이 |
| application | 단계 순서, 소유자 확인, 트랜잭션·보상 판단 | OrderService, OrderWriter, OrderCancellationService·Writer |
| Feign 어댑터 | HTTP 계약, 응답 확인, API별 재시도 정책 | DeliveryClientAdapter, Cancel·Revert 클라이언트 |
| 공통 예외 처리 | 예외를 일관된 HTTP 오류 본문으로 변환 | BusinessException, FeignErrorDecoder, GlobalExceptionHandler |

Controller마다 try/catch를 반복하지 않는다. application에서는 **보상 여부나 후속 상태가 달라지는 경계**에서 예외를 잡는다.
예측 가능한 업무 거절은 BusinessException으로 구분한다. 예상하지 못한 오류도 application의 복구 판단을 거친 뒤 공통 500으로 반환한다.

## 3. 주문 생성 O-01: 현재 동작과 보강 지점

| 실패 지점 | 현재 응답 | 현재 처리 |
|---|---|---|
| 요청 형식·수량 검증 | 400 VALIDATION_ERROR | Controller 단계에서 종료 |
| 요청업체 관리자 권한 | 403 FORBIDDEN | 배송 생성 전 종료 |
| 업체·상품 조회의 명확한 4xx | 하위 오류 코드 유지 | 배송 생성 전 종료 |
| 업체·상품 조회 통신 실패 | 503 SERVICE_UNAVAILABLE | GET 통신 재시도 종료 후 반환 |
| 상품의 공급업체 불일치 | 400 INVALID_REQUEST | 배송 생성 전 종료 |
| I-09 배송 생성의 명확한 4xx | 하위 오류 코드 유지 | 생성 거절로 처리, 삭제 보상 없음 |
| I-09 타임아웃·5xx·잘못된 성공 응답 | 503 SERVICE_UNAVAILABLE | 같은 orderId의 I-13 삭제 보상 |
| 배송 생성 뒤 Order 엔티티 규칙 위반 | 원래 업무 오류 | I-13 삭제 보상 |
| 주문 저장·커밋 예외 | 500 INTERNAL_SERVER_ERROR | I-13 삭제 보상 |
| I-13 보상도 실패 | 최초 오류 유지 | 총 3회 시도 후 COMPENSATION_FAILED와 orderId 로그 |
| 커밋 후 Slack 실패 | 201 유지 | 전송 실패 로그, 배송 보상 없음 |

현재 O-01은 로컬 저장의 flush 실패와 커밋 시점 실패를 테스트했다. **모든 커밋 예외가 실제 롤백이라고 보장하는 것은 아니다.**
OrderService는 저장 RuntimeException에 I-13을 호출한다. DB에서는 주문이 커밋됐으나 응답만 끊긴 경우라면 정상 주문의 배송을 삭제할 위험이 있다.
O-05에서 구현한 커밋 결과 재확인 방식을 O-01에도 적용하는 것이 첫 보강 대상이다.

다음 두 상황도 현재 코드만으로 자동 수렴한다고 보장하지 않는다.

1. 배송 생성이 타임아웃된 뒤 I-13이 먼저 no-op으로 끝나고, 지연된 생성이 나중에 커밋되는 경우. 내부 I-09 멱등성만으로 취소·생성의 역전 순서를 막을 수 없다.
2. 사용자가 O-01 응답을 못 받아 다시 POST한 경우. 외부 요청마다 새 orderId를 발급하므로 주문 두 건이 생길 수 있다. 내부 I-09의 멱등성과 외부 주문 API의 멱등성은 다르다.

**후속 설계:** 생성 요청 키와 생성 진행 기록을 주문 서비스에 둔다. 전달받은 Idempotency-Key·사용자·요청 해시를 묶고, 같은 키의 같은 요청에는 같은 주문 결과를 반환한다.
같은 키에 다른 본문이면 409, 처리 중이면 진행 상태 조회로 안내한다. 키 보관 기간·오류 코드·진행 상태 조회 API는 팀과 계약을 정한 뒤 구현한다.
배송의 지연 생성까지 자동 정리하려면 orderId별 종료 표시 또는 시도 토큰을 이용한 실행 차단 계약을 delivery와 함께 정해야 한다. 현재 I-09·I-13 계약에 있다고 가정하지 않는다.

## 4. 주문 취소 O-05: 구현한 실패 흐름

정상 경로는 `실행권 커밋 → I-11 → 주문 CANCELED와 시도 COMPLETED를 함께 커밋`이다. 원격 호출 중에는 DB 트랜잭션을 유지하지 않는다.

| 상황 | HTTP | 주문·시도 상태 | 보상과 재요청 |
|---|---|---|---|
| 없음·타인 주문·이미 취소 | 404·403·409 | 새 시도 없음 | 배송 호출 없음 |
| PROCESSING·RECOVERY_REQUIRED가 존재 | 409 | 기존 상태 유지 | 새 원격 취소 없음 |
| 실행권 기록의 DB 예외 | 500 | PROCESSING이 남았을 수도 있음 | 배송을 호출하지 않음. 로그의 attemptId로 확인 |
| I-11의 명확한 4xx | 원래 코드 | ORDERED·ABORTED | 원복 없음. 조건 해결 후 새 시도 가능 |
| I-11 타임아웃·5xx·잘못된 성공 응답 | 503 | ORDERED·RECOVERY_REQUIRED | DELIVERY_RESULT_UNKNOWN, 자동 원복·재실행 없음 |
| 주문과 완료 기록의 커밋 성공 | 200 | CANCELED·COMPLETED | 이후 같은 주문 취소는 409 |
| 커밋 예외 후 실제 완료 재확인 | 200 | CANCELED·COMPLETED | 정상 취소를 원복하지 않음 |
| 로컬 롤백 확인, I-12 성공 | 500 | ORDERED·ABORTED | 원복 완료 후 새 취소 가능 |
| 로컬 롤백 확인, I-12 실패·결과 불확실 | 500 | ORDERED·RECOVERY_REQUIRED | REVERT_FAILED, 새 취소 차단 |
| 로컬 커밋 결과 확인 불가 | 500 | 재확인 필요·RECOVERY_REQUIRED | ORDER_COMMIT_UNKNOWN, 자동 원복 없음 |
| 종료·복구 기록 갱신 실패 | 최초 코드 유지 | PROCESSING 등이 남음 | 실행권을 시간만으로 해제하지 않음 |

배송 상태를 먼저 GET한 뒤 취소 가능하다고 단정하지 않는다. GET과 취소 사이에 집하가 시작될 수 있기 때문이다.
집하 시작 여부의 판단과 배송·구간 취소는 I-11 제공자인 delivery가 한 로컬 트랜잭션에서 처리해야 한다.

주문 저장 실패를 잡은 뒤 `resolveCompletion`은 새로운 트랜잭션에서 주문과 시도를 잠가 확인한다.
둘 다 취소 완료이면 성공으로 반환하고, ORDERED·PROCESSING이면 롤백으로 판단해 I-12를 호출한다. 이외의 조합이나 DB 조회 실패는 수동 확인 대상으로 남긴다.

취소 진행 기록이 있으면 여러 요청의 원격 취소와 보상이 겹치지 않는다. 부분 유니크 인덱스는 같은 주문에 미해결 시도가 둘 생기는 것을 DB에서도 막는다.
메모리 잠금에 의존하지 않으며 서버 재시작 후에도 기록이 남는다. 실제 다중 JVM 동시 요청·강제 종료 시험은 후속 검증 대상이다.

## 5. 재시도 정책과 시간 예산

| 호출 | 담당 위치 | 횟수·조건 |
|---|---|---|
| 외부 O-01 생성 | 현재 클라이언트 자동 재시도 금지 권고 | 외부 멱등키가 없으므로 재요청이 새 주문이 될 수 있음 |
| 외부 O-05 취소 | 진행 기록으로 중복 실행 차단 | 처리 중·미확정·이미 취소는 409. 무조건 반복하지 않음 |
| I-07·I-08 GET | IdempotentRetryConfig | 응답 없는 통신 실패에 최대 3회 |
| I-09 배송 생성 | IdempotentRetryConfig | 같은 orderId, 응답 없는 통신 실패에 최대 3회 |
| I-11 배송 취소 | DeliveryNoRetryFeignConfig | 1회. 서버의 중복 CANCELED 처리와 별개로 SA 3.5 정책 유지 |
| I-12 원복 | IdempotentRetryConfig | 같은 deliveryId, 응답 없는 통신 실패에 최대 3회. 5xx 응답은 재시도하지 않음 |
| I-13 삭제 보상 | 기존 OrderService 루프 | 실패 시 총 3회. Feign 자체 재시도는 없음 |
| Slack | 전용 비동기 전송기 | 자동 재시도 없음, 주문 결과 유지 |

재시도 횟수는 최초 호출을 포함한다. I-12에 application 재시도 루프를 추가하면 Feign 재시도와 곱해지므로 중첩시키지 않는다.

현재 공통 Feign은 연결 5초·읽기 20초이고 Gateway 응답 제한은 60초다. 읽기 제한 20초를 세 번 사용하는 것만으로도 60초에 도달하며 연결·이전 단계·보상 시간은 추가된다.
따라서 Gateway의 시간 초과가 서버 작업 종료나 롤백을 뜻하지 않는다. 요청 전체 제한과 보상 시간 예산, 202·진행 상태 조회 방식은 후속 계약으로 검토한다.
이번에는 팀 공통 타임아웃이나 Gateway 설정을 바꾸지 않았다.

## 6. 복구 대상을 찾고 판단하는 순서

현재는 기록과 로그를 이용한 수동 확인 단계다. 복구 API·자동 재처리 스케줄러는 구현하지 않았다.

먼저 아래 읽기 전용 쿼리로 미해결 시도를 찾는다. PROCESSING은 전송 전 중단, 전송 중 중단, 원격 성공 후 중단을 모두 포함할 수 있다.

```sql
SELECT c.order_cancellation_id AS attempt_id,
       c.order_id, c.delivery_id, c.status AS attempt_status,
       c.failure_reason, o.status AS order_status,
       o.deleted_at AS order_deleted_at, c.created_at, c.updated_at
FROM order_schema.p_order_cancellations c
JOIN order_schema.p_orders o ON o.order_id = c.order_id
WHERE c.deleted_at IS NULL
  AND c.status IN ('PROCESSING', 'RECOVERY_REQUIRED')
ORDER BY c.created_at;
```

1. orderId·attemptId로 원격 요청과 DB 오류 로그를 맞춘다. 주문의 실제 상태, 배송 I-10 상태, 원래 요청이 아직 실행 중인지 확인한다.
2. DB 커밋 결과부터 확정한다. 주문 CANCELED·시도 COMPLETED이면 원복하지 않는다. 읽기 복제본의 지연된 결과로 판단해서는 안 된다.
3. 원래 배송 요청의 종료가 확인되지 않으면 기록을 유지한다. I-10 한 번의 PICKUP_WAITING 응답만으로 지연 취소가 끝났다고 단정하지 않는다.
4. 결과가 확정됐을 때 아래 결정표를 적용한다. 수정 작업은 잠금·예상 상태 비교·시도 ID 검증·Audit를 갖춘 복구 유스케이스로 수행하도록 설계한다.
5. 외부 조치와 로컬 상태가 맞는지 다시 읽어 검증한 뒤 기록을 종료한다. DB 복구 중 실패하면 실행권을 계속 보존한다.

| 확정된 조합 | 복구 판단 |
|---|---|
| 주문 CANCELED·시도 COMPLETED | 정상 종료. 배송 원복 금지 |
| 주문 ORDERED·배송 PICKUP_WAITING·원래 호출 종료 | 취소가 적용되지 않았거나 원복 완료. ABORTED로 종료 가능 |
| 주문 ORDERED·배송 CANCELED·취소 의도 유효 | 원래 취소를 완료하는 방안 검토. 주문 CANCELED·시도 COMPLETED를 같은 트랜잭션으로 기록 |
| 주문 ORDERED·배송 CANCELED·로컬 취소를 완료할 수 없음 | I-12 원복을 확인한 후 ABORTED. 원복 결과 불명확 시 유지 |
| 주문 CANCELED·배송 진행 중, 또는 삭제·참조 불일치 | 자동 재실행 금지. 원인·배송 영향부터 확인 |

**후속 복구 API 설계:** 관리자 또는 내부 운영 경로에서 attemptId, 기대 주문·시도 상태, 복구 동작, 조치 사유를 받는다.
복구 동작은 `FINALIZE_CANCELLATION`, `CONFIRM_ABORTED`, `REVERT_DELIVERY`처럼 명시하고, 완료된 같은 복구 요청은 동일 결과를 반환하도록 한다.
기존 Writer는 RECOVERY_REQUIRED에서 임의로 완료·중단할 수 없게 막는다. DB 상태를 직접 바꾸어 이를 우회하는 SQL을 정상 운영 절차로 삼지 않는다.
지연된 원격 작업까지 자동 처리하려면 배송 측 시도 토큰 계약이 필요하며, 이 부분은 팀 합의 후 별도 구현한다.

## 7. 클라이언트와 운영자가 보는 정보

- 클라이언트는 공통 status·code·message·path·timestamp와 검증 오류의 errors를 받는다. 내부 예외 종류와 외부 응답 본문을 그대로 반환하지 않는다.
- 400은 입력 수정, 401은 인증, 403은 권한, 404는 대상 확인, 409는 현재 상태 확인으로 안내한다. 500·503을 무조건 재시도하라고 안내하지 않는다.
- 현재 O-05의 복구 로그는 orderId·attemptId·reason·예외 종류를 포함한다. DB에는 원문 예외 대신 정해진 failureReason을 저장한다.
- 후속으로 requestId·traceId와 단계·소요 시간·시도 횟수를 구조화하면 요청과 보상을 연결하기 쉽다. 실제 Webhook·토큰·인증 헤더는 기록 대상에서 제외한다.
- PROCESSING 장기 체류·RECOVERY_REQUIRED 발생·보상 실패를 관측 대상으로 삼는다. 모니터·알림·자동 작업은 별도 구현 대상이다.

## 8. 검증 근거와 남은 시험

O-05 신규 61개 테스트와 주문 전체 252개 테스트, 전체 build가 통과했다. 실제 PostgreSQL·HTTP 테스트와 별도 Gateway·JWT 검증을 사용했다.

| 항목 | 현재 근거 |
|---|---|
| 취소 권한·이미 취소·집하 시작 거절 | 실제 HTTP/Feign·DB 테스트 |
| 로컬 flush 실패·커밋 시점 실패 | PostgreSQL CHECK·지연 제약 트리거 주입 |
| 같은 주문 중복·다른 주문 독립 진행·동시 요청사항 수정 | 실제 트랜잭션과 동시 호출 테스트 |
| I-11 타임아웃 뒤 지연 완료 | HTTP 대역 지연, RECOVERY_REQUIRED와 재실행 차단 확인 |
| I-12 통신 3회·5xx 1회, 복구 기록 | 실제 Feign·HTTP 호출 횟수 확인 |
| 커밋 예외 뒤 실제 COMMITTED·UNKNOWN 분기 | application 단위 테스트. 실제 커밋 응답 패킷 유실 주입은 미실행 |
| 다중 JVM·프로세스 강제 종료·DB 전체 장애 | 미실행, 후속 장애 시험 |
| 팀 delivery의 실제 취소·구간 변경·원복 | 미확인. 이번 외부 응답은 HTTP 대역 |

## 9. 다음 구현 순서 제안

1. **O-01 커밋 결과 확인과 생성 진행 기록.** 실제 커밋됐을 수 있는 주문에 배송 삭제를 실행하는 위험부터 줄인다.
2. **외부 주문 생성 멱등키와 진행 상태 조회.** 사용자 재시도로 중복 주문이 생기는 문제와 타임아웃 이후 확인 경로를 해결한다.
3. **복구 유스케이스와 delivery 지연 처리 계약.** 수동 확인 결과를 안전하게 반영하고, 시도 토큰으로 오래된 요청의 영향을 제어한다.
4. **시간 예산·관측·알림 신뢰성.** Gateway 제한과 Saga 재시도를 맞추고, Slack 전달 보장이 필요해지면 Outbox·재전송을 검토한다.

1~4는 설계 제안이며 이번 O-05 구현에 몰아서 추가하지 않았다. 사용자·팀이 범위를 확정하면 단계별로 구현하고 장애 시험을 보탠다.
