# order-service 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/order/README.md`](../docs/services/order/README.md)에 있다.

## 책임과 경계
- 맡는 것: 주문 생성(Saga 오케스트레이션)·조회·수정·취소·삭제(O-01~O-06), 주문 생성 Slack 알림
- 맡지 않는 것: 배송 생성·상태(delivery), 업체·상품 정보(company), 재고(고도화)

## 작업 전에 읽을 것
- [`docs/services/order/README.md`](../docs/services/order/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 4.5·5.5(order_schema), 6.7(API 상세), 7장(Saga, 취소, 멱등성)
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)
- 서비스 결정: [`docs/services/order/decisions/`](../docs/services/order/decisions/README.md)

## 모듈 규칙
- 패키지 `com.sparta.logistics.order`, 스키마 `order_schema`, DB 계정 `order_app`.
- 주문 생성은 order가 지휘하는 Saga다(SA 7.1). 실패한 단계까지 역순으로 보상하고, 클라이언트에는 실패 단계에 맞는 에러 코드 하나를 돌려준다.
- `orderId`는 저장 전에 선발급한다. 배송 생성·취소 원복·삭제 호출은 orderId로 멱등하므로 재시도할 수 있고, 그 외 POST는 재시도하지 않는다.
- 주문 한 건은 한 품목이다. 요청업체 관리자 본인 또는 MASTER만 그 업체 명의로 주문한다. 재고는 확인·차감하지 않는다.
- 취소는 집하 시작 전(`PICKUP_WAITING`)에만 가능하다(SA 7.2). 상태 위반은 `INVALID_STATUS_TRANSITION`(공통 코드)이다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :order-service:test` (DB 통합 테스트는 Docker가 켜져 있어야 한다)
- API를 바꿨으면 `script/run.sh order-service`로 다시 띄워 Gateway(8080)로 호출해 본다.
- 에러 코드를 바꿨으면 `script/error-codes-check.sh`
