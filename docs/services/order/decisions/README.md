# order-service 결정 기록

order-service 안의 되돌리기 어려운 결정이다. 여러 서비스에 걸친 결정은 [공통 결정 기록](../../../decisions/README.md)에 있다. 형식은 `.agents/skills/adr/SKILL.md`를 따르고, 번호는 이 서비스 안에서 매긴다.

0001~0003은 최승근의 Notion ADR을 옮긴 것이다. 0001·0002는 팀 승인 기록이 없어 "제안"이며, 0003은 2026-10-15 주문 담당자의 구현 요청으로 채택했다. SA v2.0과 다른 결정은 서비스 담당자가 채택 여부를 정한다. 0004~0005는 주문 담당자의 금액 타입 결정이며 현재 기준은 0005다. 0006은 주문 담당자가 명시적으로 승인한 취소 진행 기록 보강이다.

| 번호 | 제목 | 상태 | 원문 |
|---|---|---|---|
| 0001 | [주문 생성: 오케스트레이션 Saga + 보상](0001-order-creation-orchestration-saga.md) | 제안 (SA와 다름) | 최승근 ADR-09 |
| 0002 | [주문 취소: HUB_WAITING일 때만, 재고 복원은 재시도](0002-order-cancel-before-departure.md) | 제안 (SA와 다름) | 최승근 ADR-10 |
| 0003 | [Slack 알림: 커밋 후 비동기 이벤트](0003-slack-notification-after-commit.md) | 채택 (주문 담당자) | 최승근 ADR-14, Junhyun An 적용 요청 |
| 0004 | [주문 금액: BigDecimal·NUMERIC(15,2)](0004-order-money-bigdecimal.md) | 대체됨 (0005) | Junhyun An 요청 |
| 0005 | [정수 원화 주문 금액: long·BIGINT](0005-order-money-integer-krw.md) | 채택 | Junhyun An 원복 요청 |
| 0006 | [주문 취소 진행 기록과 중복 실행 보호](0006-order-cancellation-progress.md) | 채택 (주문 담당자) | Junhyun An 진행 기록 추가 승인 |
| 0007 | [주문 생성 진행 기록과 커밋 결과 재확인](0007-order-creation-progress.md) | 채택 (주문 담당자) | Junhyun An O-01 보강 구현 요청 |
| 0008 | [주문 생성 멱등키와 최초 응답 재사용](0008-order-idempotency-key.md) | 채택 (주문 담당자) | Junhyun An 중복 주문 방지 요청 |
| 0009 | [주문 삭제 가능 여부와 진행 기록 보존](0009-order-deletion-progress.md) | 채택 (주문 담당자) | Junhyun An 삭제 기록 포함 구현·검증·커밋 승인 |
