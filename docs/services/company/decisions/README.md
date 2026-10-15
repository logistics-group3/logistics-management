# company-service 결정 기록

company-service 안의 되돌리기 어려운 결정이다. 여러 서비스에 걸친 결정은 [공통 결정 기록](../../../decisions/README.md)에 있다. 형식은 `.agents/skills/adr/SKILL.md`를 따르고, 번호는 이 서비스 안에서 매긴다.

현재 기록은 최승근의 Notion ADR을 옮긴 것이다. 팀 승인 기록이 없어 모두 "제안"이며, SA v2.0과 다른 결정은 서비스 담당자가 채택 여부를 정한다.

| 번호 | 제목 | 상태 | 원문 |
|---|---|---|---|
| 0001 | [입고 진입점: company-service가 받아 hub에 반영](0001-stock-inbound-via-company.md) | 제안 (SA와 다름) | 최승근 ADR-07 |
| 0002 | [상품·업체 삭제와 재고: 판매 중지, 수량 보존, 실패 시 롤백](0002-delete-stops-stock-sales.md) | 제안 (SA와 다름) | 최승근 ADR-13 |
| 0003 | [상품 가격 타입: SA를 따라 BIGINT(Long)](0003-price-bigint.md) | 채택 (규칙 문서와 다름) | |

0001, 0002는 재고가 고도화 범위라 기본 구현(`feat/company-crud`)에 넣지 않는다. 채택 여부는 재고 고도화를 시작할 때 다시 정한다.
