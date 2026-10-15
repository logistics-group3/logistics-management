# delivery-service 결정 기록

delivery-service 안의 되돌리기 어려운 결정이다. 여러 서비스에 걸친 결정은 [공통 결정 기록](../../../decisions/README.md)에 있다. 형식은 `.agents/skills/adr/SKILL.md`를 따르고, 번호는 이 서비스 안에서 매긴다.

0001·0002는 최승근의 Notion ADR을 옮긴 것이다. 팀 승인 기록이 없어 "제안"으로 옮겼으며, SA v2.0과 다른 결정은 서비스 담당자가 채택 여부를 정한다.

| 번호 | 제목 | 상태 | 원문 |
|---|---|---|---|
| 0003 | [배송 구간·상태 모델: SA v2.0의 3구간, PICKUP_WAITING에서 시작](0003-delivery-three-legs-sa-v2.md) | 채택 | 한얼 |
| 0001 | [배송 구간·상태 모델: 2구간, 모두 HUB_WAITING에서 시작](0001-delivery-legs-and-status.md) | 대체됨 (0003) | 최승근 ADR-11 |
| 0002 | [배송담당자: 유저 플래그 + 소속 허브 규칙](0002-delivery-manager-user-flag.md) | 제안 | 최승근 ADR-12 |
