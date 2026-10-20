# 스텝 54 — 결과를 모르는 주문 생성 시도를 I-17 종료 확인으로 자동 종료

- 날짜: 2026-10-19.
- 브랜치: fix/order-auto-abort-closed, PR [#23](https://github.com/logistics-group3/logistics-management/pull/23) 병합(dev `e55b2a3`).
- 결정 기록: [order ADR 0011](../../../services/order/decisions/0011-order-creation-auto-abort-on-closure.md). 이슈 #14의 남은 조건 중 order 쪽이다.

## 무엇을 바꿨나

I-09의 타임아웃·5xx로 결과를 모를 때 order는 I-13 보상을 시도한 뒤 항상 `RECOVERY_REQUIRED`로 남겼다. 이제 보상이 성공하고 I-17(`GET /internal/deliveries/orders/{orderId}`)이 `CLOSED`이면 시도를 `ABORTED`(`SERVICE_UNAVAILABLE`)로 닫는다. 보상 실패, `ACTIVE`, `NONE`, I-17 조회 실패는 기존대로 `RECOVERY_REQUIRED`다. 클라이언트 응답은 이전과 같은 503이다.

변경 위치: `OrderService.handleUnknownDelivery`, `DeliveryGateway.isCreationClosed`, 전용 Feign `DeliveryOrderStateClient`(`order-delivery-state`), `config-repo/order-service.yml`의 타임아웃(연결 1초·읽기 2초).

## 왜 필요한가

콜드 스타트 재현에서 delivery의 하위 호출이 읽기 2초를 넘겨 I-09가 503이 됐다. order의 보상은 성공했고 배송은 0건이며 종료 표시도 있었는데, 기록은 `RECOVERY_REQUIRED`로 남아 사용자가 재주문하지 못했다.

delivery가 이슈 #14로 종료 표시를 만든 뒤에는 `CLOSED`가 "이후 I-09는 배송을 만들지 않는다"는 뜻이다. 그래서 I-13의 204만으로는 단정하지 못하던 지연 생성 가능성을 I-17로 닫을 수 있다. 닫는 조건을 `CLOSED` 하나로 좁혀, 확신이 없으면 지금처럼 사람이 확인하게 두었다.

## 설계에서 고른 것

- I-17을 기존 읽기 클라이언트에 붙이지 않고 전용 클라이언트로 분리했다. 기존 클라이언트는 읽기 20초·3회 재시도라 처리 단계의 시간 예산(ADR 0010)을 늘린다. I-17은 실패해도 `RECOVERY_REQUIRED`로 남을 뿐이라 재시도 없이 짧게 끊는다.
- 새 에러 코드를 만들지 않고 기존 `SERVICE_UNAVAILABLE`로 닫았다.

## 검증

- `OrderServiceTest` 3개(CLOSED면 abort, 미확인·조회 실패면 recovery, 보상 실패면 I-17 미호출)와 `OrderHttpIntegrationTest` 3건(CLOSED·ACTIVE·NONE)을 추가했다. `./gradlew :order-service:test` 469개와 `./gradlew build` 통과.
- 처음에는 HTTP 테스트가 실패했다. 테스트의 Feign URL 목록에 새 `contextId`가 없어 요청이 대역 서버에 닿지 않았고, 조회 실패로 처리돼 `RECOVERY_REQUIRED`가 남았다. 실패해도 안전한 방향(복구 필요)으로 떨어진다는 설계가 이 경우에도 지켜졌다.
- 실제 콜드 스타트에서 이 경로가 얼마나 자주 일어나는지는 측정하지 못했다.

## 직접 확인

1. `OrderService.handleUnknownDelivery`와 `isCreationClosed`를 읽고, 어떤 경우에 abort 대신 recovery로 떨어지는지 표로 그려 본다.
2. `./gradlew :order-service:test --tests '*OrderHttpIntegrationTest'`에서 CLOSED·ACTIVE·NONE 세 경우를 실행한다.
3. 보상(I-13)이 성공했는데 I-17이 `NONE`인 상황을 상상해 보고, 왜 종료로 단정하면 안 되는지 설명해 본다.

면접 포인트: 불확실한 원격 결과의 처리(확정 가능한 때만 닫고 아니면 보수적으로 유지), 종료 표시(tombstone)로 지연된 요청을 막는 방식, 보상 트랜잭션(Saga)의 실패 분기 설계.
