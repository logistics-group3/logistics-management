# 0004. 주문 금액을 BigDecimal과 NUMERIC(15,2)로 표현

- 상태: 대체됨 (0005)
- 날짜: 2026-10-15
- 결정자: Junhyun An (주문 담당자, unitPrice·totalPrice의 BigDecimal 전환 요청)

2026-10-15 주문 담당자가 정수 원화 범위를 확인하고 원복을 요청했다. 현재 기준은 [ADR 0005](0005-order-money-integer-krw.md)의 `long`·`BIGINT`다. 아래는 대체된 결정의 이력이다.

## 맥락

초기 구현은 SA v2.0 5.5·6.7의 `long`·`BIGINT`를 따랐다. 주문 담당자가 `unitPrice`, `totalPrice`를 `BigDecimal`로 바꾸자고 요청했다.
도메인 규칙은 금액을 `BigDecimal`로, 마이그레이션 규칙은 `NUMERIC(15,2)`로 정하고 있어 이 기준을 적용한다.
정수 원화만 다룰 때 `long`도 정확하다. 이번 변경은 소수 단위 금액을 정확히 표현하고 금액 자릿수 규칙을 명시하기 위한 선택이다.

## 결정

- Entity의 `unitPrice`, `totalPrice`와 `Order.create`의 단가 인자는 `BigDecimal`로 바꾼다.
- 두 DB 컬럼은 `NUMERIC(15,2)`, JPA 매핑은 `precision = 15, scale = 2`다. 허용 범위는 `0.00`부터 `9,999,999,999,999.99`까지다.
- 총액은 `단가.multiply(BigDecimal.valueOf(수량))`으로 정확하게 계산한다. 입력 총액을 신뢰하거나 `double`로 변환하지 않는다.
- 단가와 총액을 `setScale(2, RoundingMode.UNNECESSARY)`로 정규화한다. `12.3400`처럼 값 손실 없는 변환은 허용하고 `12.345`처럼 반올림이 필요한 값은 `INVALID_REQUEST`로 거부한다.
- null·음수 단가와 DB 범위를 넘는 단가·총액도 `INVALID_REQUEST`다. 반올림 방식은 임의로 추가하지 않는다.
- V1은 아직 미커밋이고 이 대화에서는 폐기되는 Testcontainers DB에만 적용했으므로 기존 초안을 수정한다. 커밋되거나 유지되는 DB에 적용된 이후 변경은 새 버전 마이그레이션으로 한다.

## 검토한 대안

- `long`·`BIGINT` 유지: 정수 금액에 단순하고 정확하지만 소수 단위 금액을 직접 표현할 수 없다.
- `BigDecimal`·무제한 `NUMERIC`: 저장 범위를 넓게 둘 수 있지만 현재 팀 가이드의 자릿수 규칙과 다르다.
- 저장 시 자동 반올림: 금액이 조용히 바뀔 수 있어 선택하지 않았다. PostgreSQL은 지정 scale을 넘는 값을 반올림할 수 있으므로 DB에 보내기 전 Entity에서 거부한다.

## 결과

- 단위 테스트에서 정확한 소수 곱셈, scale 정규화, 반올림 필요 값 거부, null·음수·최대 금액 경계를 확인한다. PostgreSQL 통합 테스트에서 소수 금액 왕복 저장과 실제 컬럼 precision·scale, 범위 초과 거부를 확인한다.
- `NUMERIC(15,2)`는 정수 부분이 13자리이므로 기존 `BIGINT`보다 최대 정수 금액이 작다. 현재 주문 가이드의 범위를 적용했으며 확장이 필요하면 Entity·마이그레이션·계약을 함께 변경한다.
- SA 5.5의 `BIGINT`, 6.7의 `Long`과 다른 점은 [주문 협업 창구](../README.md)에 표시한다. 팀 공통 SA 전체가 승인·수정된 것으로 간주하지 않는다.
- company의 상품 가격과 내부 API는 변경하지 않는다. 기존 정수 가격은 order 호출 DTO에서 BigDecimal로 수신하거나 `BigDecimal.valueOf(long)`으로 변환할 수 있다. 실제 연동은 미검증이다.
- O-01 등 외부 API는 아직 미구현이다. 구현 시 주문 응답 금액 DTO도 `BigDecimal`을 사용하고 이 계약을 문서화한다.

## 근거

- [domain 금액 규칙](../../../agents/rules/domain.md), [마이그레이션 금액 규칙](../../../agents/rules/migration.md)
- [Java 17 BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [PostgreSQL 16 Numeric Types](https://www.postgresql.org/docs/16/datatype-numeric.html)
