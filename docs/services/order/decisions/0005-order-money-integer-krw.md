# 0005. 정수 원화 주문 금액을 long과 BIGINT로 유지

- 상태: 채택
- 날짜: 2026-10-15
- 결정자: Junhyun An (주문 담당자, 원화만 다루는 전제로 기존 구현 복원 요청)
- 대체하는 결정: [0004 BigDecimal 전환](0004-order-money-bigdecimal.md)

## 맥락

주문 담당자가 원화만 다룬다는 전제를 확인하고 BigDecimal 전환을 되돌려 달라고 요청했다. 현재 SA의 단가는 원 단위 정수이며 수량도 정수다. 소수 금액 계산 요구는 없다.

## 결정

- `unitPrice`, `totalPrice`와 생성 팩토리의 단가 인자를 `long`으로 복원한다. DB 컬럼은 `BIGINT`로 복원한다.
- 총액은 `Math.multiplyExact(unitPrice, quantity)`로 계산하고 오버플로를 `INVALID_REQUEST`로 변환한다.
- 단가 0 이상, 수량 1 이상과 기존 주문 불변식을 유지한다. 소수 자릿수·반올림 정책은 적용하지 않는다.
- BigDecimal 전환 직전의 단위·PostgreSQL 통합 테스트로 복원한다. Repository, Audit, 논리 삭제와 다른 서비스 코드는 변경하지 않는다.
- V1은 미커밋 초안이므로 파일을 복원한다. 유지되는 DB의 데이터·Flyway 이력은 변경하지 않는다.

## 검토한 대안

- BigDecimal 유지: 소수 금액에 유용하지만 현재 범위에는 scale·반올림 정책이 필요하지 않다.
- 일반 long 곱셈: 오버플로가 조용히 발생하므로 `Math.multiplyExact`를 유지한다.

## 결과

금액 타입은 SA 5.5·6.7의 `BIGINT`·`Long` 계약과 다시 일치한다. 일반 레이어 가이드의 BigDecimal 예시보다 이 도메인의 구체적인 요구와 사용자 결정을 우선한다.
향후 원 미만 금액이나 비율 계산이 요구되면 금액·반올림 정책을 별도로 결정한다. ADR 0004와 스텝 36은 철회된 변경 이력으로 남긴다.
