# order-service 결정 기록

order-service 안의 되돌리기 어려운 결정이다. 여러 서비스에 걸친 결정은 [공통 결정 기록](../../../decisions/README.md)에 있다. 형식은 `.agents/skills/adr/SKILL.md`를 따르고, 번호는 이 서비스 안에서 매긴다.

현재 기록은 최승근의 Notion ADR을 옮긴 것이다. 팀 승인 기록이 없어 모두 "제안"이며, SA v2.0과 다른 결정은 서비스 담당자가 채택 여부를 정한다.

| 번호 | 제목 | 상태 | 원문 |
|---|---|---|---|
| 0001 | [주문 생성: 오케스트레이션 Saga + 보상](0001-order-creation-orchestration-saga.md) | 제안 (SA와 다름) | 최승근 ADR-09 |
| 0002 | [주문 취소: HUB_WAITING일 때만, 재고 복원은 재시도](0002-order-cancel-before-departure.md) | 제안 (SA와 다름) | 최승근 ADR-10 |
| 0003 | [Slack 알림: 커밋 후 비동기 이벤트](0003-slack-notification-after-commit.md) | 제안 | 최승근 ADR-14 |
