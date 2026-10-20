# 주문 생성 복구 운영 API

결정은 [ADR 0019](../../decisions/0019-order-creation-operator-recovery.md), 판단표는 [설계](../../reviews/2026-10-20-order-creation-recovery-plan.md)를 따른다.
현재 기능은 MASTER가 원래 실행 중단을 확인하고 안전하게 중단하는 수동 복구다. 원래 주문을 재생성하거나 자동으로 재처리하지 않는다.

## 조사와 실행

1. O-08 `GET /api/v1/orders/creation-recoveries/candidates?size=50`로 PROCESSING·RECOVERY_REQUIRED를 찾는다. 오래됐다는 사실만으로 중단 처리하지 않는다.
2. 원래 프로세스·해당 작업이 중단됐고 이전 버전의 무조건 삭제 I-13이 진행 중이지 않은지 확인한다. originalExecutionStopped는 운영자의 확인이며 서버가 다른 프로세스의 종료를 증명하지 않는다.
3. 새 recoveryId를 하나 만들고, 관찰한 attemptId와 생성 상태·사유를 O-09에 보낸다.

```json
{
  "recoveryId": "00000000-0000-0000-0000-000000000101",
  "attemptId": "00000000-0000-0000-0000-000000000102",
  "expectedStatus": "PROCESSING",
  "reason": "전용 order 프로세스 종료와 재기동을 확인했다.",
  "originalExecutionStopped": true
}
```

경로는 `POST /api/v1/orders/{orderId}/creation-recoveries`다. UUID는 예시이며 실제 조사 결과로 바꾼다. 토큰은 문서·로그에 넣지 않는다.
응답은 ApiResponse의 data에 recoveryId·orderId·attemptId·status·executionId·requestedBy·expectedStatus·reason·message·events를 담는다.

| 복구 상태 | 뜻·다음 행동 |
|---|---|
| COMPLETED | CLOSED와 주문 부재를 확인했고 생성 시도는 ABORTED다. 같은 주문 키를 재전송하면 기존 ORDER_CREATION_ABORTED 409다 |
| DEFERRED | 배송 진행·조회 실패·모순·저장 실패 등으로 확인을 마치지 못했다. 생성 시도는 RECOVERY_REQUIRED다. 원인을 확인한 뒤 같은 복구 ID·같은 내용으로 다시 요청한다 |
| RUNNING | 같은 복구가 실행 중이거나 중단 뒤 실행 기록이 남았다. 중복 요청은 다시 외부 조치를 하지 않는다 |

O-10 `GET /api/v1/orders/creation-recoveries/{recoveryId}`에서 단계와 확인 결과를 읽는다.
관찰 결과·조치 요청·응답 미확인·종료 검증·완료/보류를 이벤트로 남기며 원래 실패 사유는 CLAIMED 이벤트에 보존한다.
진행 중 배송이나 논리 삭제 주문 이력이 있으면 임의로 삭제·재생성하지 않는다.

## 중단된 복구를 명시적으로 재개

복구 실행 자체가 죽어 RUNNING으로 남으면 해당 실행 중단을 먼저 확인한다.
동일 recoveryId·attemptId·expectedStatus·reason에, 마지막 조회의 executionId를 resumeExecutionId로 추가해 O-09를 보낸다.
DB 잠금 안에서 실행 ID를 새 값으로 바꾼다. 같은 과거 ID의 두 재개 요청 중 나중 요청은 409다. 오래된 실행은 상태를 갱신하지 못한다.
현재 요청자는 최초 요청의 MASTER와 같아야 한다. 소유권 이전·자동 lease 회수는 제공하지 않는다.

같은 복구 ID의 내용 변경·다른 attemptId·예상 상태 불일치·이미 다른 복구가 있는 시도는 409다.
정상 완료 주문을 복구하려 하거나 로컬 주문·삭제 이력이 발견된 경우에도 원격 변경을 하지 않는다.

## 테스트·실험

재현 도구는 [script/recovery-experiment](../../../script/recovery-experiment/README.md)를 따른다.
복구 성공은 HTTP 200만으로 판단하지 않는다. data.status=COMPLETED, O-07 ABORTED, I-17 CLOSED, 활성 주문·배송·구간 0건, 감사 이력을 함께 확인한다.
이후 같은 orderId의 지연 생성 거절과 같은 복구 요청의 무부작용 재사용도 검증한다.
