# 0011. I-17 종료 확인 시 주문 생성 시도의 자동 종료

- 상태: 채택 (주문 담당자)
- 날짜: 2026-10-19
- 결정자: Junhyun An, 운영 리뷰 이슈 #14의 남은 조건(order의 RECOVERY_REQUIRED 자동 종료)을 설계 설명 뒤 승인

## 맥락

I-09의 결과를 모르는 실패(타임아웃·5xx)는 I-13 보상이 성공해도 항상 RECOVERY_REQUIRED(DELIVERY_RESULT_UNKNOWN)로 남았다.
I-13이 대상이 없을 때 204를 돌려주고 지연된 I-09가 뒤늦게 배송을 만드는 순서 역전이 가능했기 때문이다.
delivery가 이슈 #14로 종료 표시와 I-17을 제공한 뒤에도 order는 이를 쓰지 않아, 콜드 스타트에서 delivery의 하위 호출이 읽기 2초를 넘기면 배송이 0건이어도 사용자가 재주문하지 못했다.

## 결정

1. I-09 결과를 모를 때 I-13 보상이 성공하면 I-17(`GET /internal/deliveries/orders/{orderId}`)로 확인한다. `CLOSED`면 시도를 ABORTED(`SERVICE_UNAVAILABLE`)로 닫는다.
2. 보상 실패, I-17의 `ACTIVE`·`NONE`, I-17 조회 실패(통신 오류·해석 불가 응답)는 기존대로 RECOVERY_REQUIRED(DELIVERY_RESULT_UNKNOWN)다. 닫는 조건만 엄격하게 `CLOSED`로 한정한다.
3. 클라이언트 응답은 이전과 같은 503이고 진행 기록만 달라진다. 새 에러 코드는 없다.
4. I-17 호출은 전용 클라이언트(`order-delivery-state`)로 재시도 없이 연결 1초·읽기 2초다. 처리 단계 최악 시간에 2초가 더해질 뿐이고, 실패해도 RECOVERY_REQUIRED로 남으므로 짧게 끊는다.

## 결과와 한계

- delivery 코드와 API 계약은 바꾸지 않았다. I-17 소비만 추가했다(계약 기준: [DELIVERY-002](../../delivery/README.md#delivery-002-지연-생성과-삭제의-순서-역전-방지-계약-검토)).
- 이 변경 이전에 남은 RECOVERY_REQUIRED 기록은 자동으로 닫히지 않는다. ORDER_COMMIT_UNKNOWN·DELIVERY_DELETE_FAILED는 배송이 이미 만들어졌을 수 있어 대상이 아니다.
- 실제 콜드 스타트에서 이 경로가 일어나는 빈도는 측정하지 않았다. 검증은 단위 테스트 3개와 HTTP 통합 테스트 3건(CLOSED·ACTIVE·NONE)이다.
