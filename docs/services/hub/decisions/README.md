# hub-service 결정 기록

hub-service 안의 되돌리기 어려운 결정이다. 여러 서비스에 걸친 결정은 [공통 결정 기록](../../../decisions/README.md)에 있다. 형식은 `.agents/skills/adr/SKILL.md`를 따르고, 번호는 이 서비스 안에서 매긴다.

0001·0002는 최승근의 Notion ADR을 옮긴 것이고 0003은 `feat/hub-adv`에서 새로 썼다. 팀 승인 기록이 없어 모두 "제안"이며, SA v2.0과 다른 결정은 서비스 담당자가 채택 여부를 정한다.

| 번호 | 제목 | 상태 | 원문 |
|---|---|---|---|
| 0001 | [재고 모델: 공급업체 허브 1곳의 위탁 재고](0001-stock-model-supplier-hub-consignment.md) | 제안 (SA와 다름) | 최승근 ADR-06 |
| 0002 | [재고 동시성: 조건부 UPDATE](0002-stock-concurrency-conditional-update.md) | 제안 (SA와 다름) | 최승근 ADR-08 |
| 0003 | [재고 할당은 공급업체 허브 한 곳에서만 하고, 입고는 공급업체 관리자에게도 허용한다](0003-stock-allocation-supplier-hub-only.md) | 제안 (SA와 다름) | 2026-10-19 |
