# 0003. 상품 가격 타입: SA를 따라 BIGINT(Long)

- 상태: 채택
- 날짜: 2026-10-15
- 결정자: kej-2356 (company-service 담당)

## 맥락
- SA v2.0 5.4는 `p_products.price`를 `BIGINT NOT NULL, CHECK >= 0`, 단위 원으로 정한다.
- 팀 규칙 문서 `docs/agents/rules/domain.md`는 금액을 `BigDecimal`, `docs/agents/rules/migration.md`는 `NUMERIC(15,2)`로 정한다.
- 설계 기준은 SA이고(AGENTS.md), 두 문서가 다르면 구현 전에 정해야 한다.

## 결정
상품 가격은 SA를 따라 DB `BIGINT`, Java `Long`으로 둔다. 원 단위 정수만 다룬다.

## 검토한 대안
- `BigDecimal` / `NUMERIC(15,2)`: 규칙 문서와는 맞지만 SA와 다르다. 원화에는 소수점이 없어 얻는 것이 없고, I-08로 가격을 받는 order와도 타입을 다시 맞춰야 한다.

## 결과
- SA, Entity, 마이그레이션이 같은 타입을 쓴다. order는 I-08 응답의 `price`를 `Long`으로 받는다.
- 규칙 문서의 금액 규칙과 다르다. 규칙 문서를 SA에 맞출지는 팀 리뷰에서 정한다.
