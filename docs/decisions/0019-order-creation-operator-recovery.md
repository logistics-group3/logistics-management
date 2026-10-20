# 0019. 주문 생성의 운영자 복구와 배송 조건부 종료

- 상태: 로컬 구현 채택, 팀 리뷰 대기.
- 날짜: 2026-10-20.
- 결정자: Junhyun An의 설계 승인·구현 및 F1/F2 전후 검증 요청. 팀 전체 승인으로 해석하지 않는다.

## 맥락

현재 F1·F2는 재기동 후 PROCESSING으로 남는다. I-13은 진행 중인 배송도 삭제하므로 복구에 그대로 사용할 수 없다.
주문 원문·가격·주소 전체를 보존하지 않아 과거 주문을 재생성하는 대신 안전하게 중단 처리한다.
근거는 [개선 전 재현 결과](../reviews/order-creation-recovery/2026-10-20-baseline-results.md)다.

## 결정

1. MASTER가 미해결 생성 시도를 조회하고, 원래 실행 중단을 확인한 뒤 복구 요청 ID·attemptId·예상 생성 상태·사유로 실행한다.
2. O-08 `GET /api/v1/orders/creation-recoveries/candidates`, O-09 `POST /api/v1/orders/{orderId}/creation-recoveries`, O-10 `GET /api/v1/orders/creation-recoveries/{recoveryId}`를 추가한다.
3. 생성 기록 잠금을 잡아 주문·논리 삭제 이력 부재를 확인하고 RECOVERY_REQUIRED로 전환한다. 정상 저장과 같은 잠금이므로 복구가 먼저면 원래 작업은 주문을 저장하지 못한다.
4. 복구 작업과 단계별 감사 이벤트를 DB에 보존한다. 같은 요청·내용은 같은 결과를 재사용한다. 한 시도에 복구 작업 하나만 두며 서로 다른 요청 ID는 충돌이다.
5. RUNNING 작업의 중복 요청은 외부 동작을 다시 실행하지 않는다. 이전 복구 실행이 중단됐을 때만 운영자가 현재 executionId를 resumeExecutionId로 제출해 명시적으로 재개한다. 잠금 안에서 실행 ID를 교체하고 이후 저장마다 확인해 과거 작업자의 기록을 차단한다. 시간 만료로 실행권을 넘기지 않는다.
6. I-18 `DELETE /internal/deliveries/orders/{orderId}/creation?expectedDeliveryId=...`은 orderId advisory lock → 배송 행 잠금 순서로 확인한다. 배송 없음 또는 같은 배송의 PICKUP_WAITING·모든 구간 WAITING만 종료한다. 진행·완료·취소·ID 불일치는 409이며 종료 표시도 만들지 않는다. 이미 종료면 204다.
7. 생성 실패 보상도 I-18로 전환해 늦게 돌아온 생성 작업의 무조건 삭제를 없앤다. O-06의 완료 배송 삭제에는 기존 I-13을 유지한다.
8. I-17 전체 결과를 검증하고 I-18 이후 CLOSED를 다시 확인한 경우에만 시도를 ABORTED로 닫는다. 조회 실패·거절·모순은 DEFERRED와 RECOVERY_REQUIRED로 보존한다. 정상 주문을 완료 처리하거나 Slack 이벤트를 재발행하지 않는다.
9. 원격 호출 동안 order DB 트랜잭션을 유지하지 않는다. 짧은 실행권 획득·관찰 기록·마지막 확정으로 나눈다.

## 요청과 상태

- O-09 본문: recoveryId, attemptId, expectedStatus(PROCESSING/RECOVERY_REQUIRED), reason(1~500자), originalExecutionStopped(true), resumeExecutionId(선택).
- originalExecutionStopped는 운영자의 확인 진술이다. 서버가 다른 프로세스의 종료나 이전 버전 I-13의 소멸을 증명하는 기능은 없다. 살아 있는 작업을 시간만으로 강제 종료하는 기능도 아니다.
- 결과: recoveryId·orderId·attemptId·status(RUNNING/DEFERRED/COMPLETED)·executionId·message. 상세 조회는 관찰·시각·실행 ID별 이벤트를 제공한다.
- 진행 조회·기존 멱등 요청은 기존 의미를 유지한다. 복구 완료 뒤 같은 키는 409 ORDER_CREATION_ABORTED다.

## 검토한 대안

- 원본 주문 재생성: 과거 업무 스냅샷이 없어 제외했다.
- I-17 조회 후 I-13 삭제: 조회와 출발 사이의 경쟁을 막지 못해 제외했다.
- 원격 호출 내내 행 잠금 유지: DB 연결·잠금을 장시간 점유해 제외했다.
- lease 만료 자동 인계: 예전 작업이 여전히 실행 중일 수 있어 첫 버전에서 제외했다.

## 결과

order V7에 복구 작업·이벤트·생성 기록의 복구 참조를 추가한다. 기존 failureReason은 감사 이벤트에 보존하고 ABORTED 전환 시 현재 필드에서는 비운다.
배송 테이블은 추가하지 않는다. 기존 종료 표시·잠금·논리 삭제를 재사용한다.
새 기능과 같은 F1·F2, 중복 실행·늦은 저장·배송 출발 경쟁·확인 실패·재개를 검증한다. 실제 결과는 별도 실행 기록을 따른다.
