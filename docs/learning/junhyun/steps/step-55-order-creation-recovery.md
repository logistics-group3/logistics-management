# 스텝 55 — 접수 후 멈춘 주문 생성의 안전한 운영자 복구

- 날짜: 2026-10-20.
- 브랜치: feat/docs-learning-evidence, 로컬 미커밋.
- 결정: [ADR 0019](../../../decisions/0019-order-creation-operator-recovery.md).
- 실행·증거: [개선 전](../../../reviews/order-creation-recovery/2026-10-20-baseline-results.md), [구현과 전후 비교](../../../reviews/order-creation-recovery/2026-10-20-implementation-results.md).

## 무엇을 만들었나

MASTER가 미해결 생성 시도를 찾아 원래 실행 중단을 확인하고 안전하게 종료하는 API다.
O-08 목록·O-09 실행/재개·O-10 이력과 order V7의 복구 작업·단계 이벤트를 추가했다. 원래 주문을 다시 생성하지 않는다.
배송 I-18은 배송 없음 또는 집하·모든 구간 대기만 조건부로 정리한다. 기존 완료 배송 삭제 I-13은 유지했다.

## 처리 과정과 이유

1. 생성 기록을 잠가 예상 상태·attemptId·주문과 삭제 이력 부재를 검증하고 RECOVERY_REQUIRED로 인계한다. 정상 Writer도 같은 생성 기록을 잠그므로 늦게 저장하지 못한다.
2. DB 트랜잭션을 닫은 뒤 I-17로 원격 결과를 본다. NONE은 현재 배송이 없을 뿐 종료가 확정된 것은 아니다.
3. I-18이 orderId → 배송 행 잠금 안에서 출발 여부·배송 ID를 검사해 정리한다. 조회 뒤에 담당자가 출발하는 경쟁도 막는다.
4. I-17 CLOSED를 확인한 뒤 로컬 주문 부재와 현재 실행 ID를 다시 검사해 ABORTED·복구 COMPLETED·이력을 함께 저장한다.
5. 조회 실패나 진행 배송은 DEFERRED로 남긴다. 중단된 복구는 같은 요청과 직전 executionId로 명시적으로 재개하며 오래된 작업자의 저장은 거절한다.

최초 사유·예상 상태·요청자도 조회로 읽는다. 클라이언트가 원래 요청을 잃어도 추측으로 재개하지 않도록 하기 위해서다.

## 검증

- 최종 backend build 47초 성공, order 484개 재실행. 전체 XML 857개 실패·오류·skip 0에는 기존 결과 재사용이 포함된다. delivery 93개는 앞선 동일 핵심 로직의 전체 빌드에서 실행했다.
- 프런트 build·lint·format·단위 56개 통과. 복구 UI·브라우저 E2E는 이번 범위가 아니다.
- 실제 Gateway·Feign·PostgreSQL에서 C0 두 건과 F1/F2 각각 검증했다. F1/F2 복구 후 ABORTED·CLOSED·활성 주문/배송/구간 0, 동일 키 409, 지연 배송 생성 409, 동일 복구 요청 무부작용 재사용을 확인했다.
- 초기 F2의 커널 OOM은 판정 제외하고 환경을 제한한 뒤 새 ID로 재검증했다. [TS-031](../../../troubleshooting.md#ts-031-격리-프로젝트의-주문-장애-실험이-의도한-종료-전에-oom으로-중단됨)에 기록했다.

## 직접 확인

1. `OrderWriter.save`, `OrderCreationRecoveryWriter.claim`에서 같은 생성 기록 잠금을 찾는다. 저장과 인계 중 먼저 끝난 쪽에 따라 뒤의 동작이 달라지는지 설명한다.
2. `DeliveryService.closeCreation`의 두 잠금과 `Delivery.closeCreation`의 대기 검사를 읽는다. 조회 결과만 믿고 I-13을 호출하면 왜 위험한지 비교한다.
3. 실행별 `commands.jsonl`·HTTP 영수증·DB 집계를 보고 HTTP 200과 복구 COMPLETED·ABORTED가 다른 사실임을 구분한다.

면접 포인트: 멱등 접수와 영속 작업 실행의 차이, 분산 상태의 불확실성, 작업 실행권과 DB 잠금의 차이, 상태 확인·변경의 원자성, 실패 실험의 판정 제외 기준.
