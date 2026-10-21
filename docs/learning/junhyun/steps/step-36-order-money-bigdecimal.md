# 36. 주문 금액 BigDecimal 검토와 원복

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 계기: 주문 담당자가 `unitPrice`, `totalPrice`를 BigDecimal로 바꾸자고 요청했다.
- 최종 상태: 원화만 다룬다는 전제와 사용자 원복 요청에 따라 long·BIGINT로 복원했다. 아래 BigDecimal 변경·검증은 중간 이력이며 현재 기준은 [ADR 0005](../../../services/order/decisions/0005-order-money-integer-krw.md)다.

## 바꾼 것과 이유

| 항목 | 변경 | 이유 |
|---|---|---|
| Entity 금액 | `long` → `BigDecimal` | 소수 단위도 정확히 표현한다 |
| DB 금액 | `BIGINT` → `NUMERIC(15,2)` | 팀 가이드와 JPA의 자릿수를 맞춘다 |
| 총액 계산 | 정수 곱셈 → `multiply` | 부동소수점 변환 없이 계산한다 |
| 금액 검증 | Long 범위 → 소수 자릿수·DB 범위 | 반올림과 저장 시 범위 초과를 미리 막는다 |

기존 long도 정수 원화에는 정확하다. 이번 변경은 double 오차를 고친 것이 아니라 소수 금액을 표현할 수 있게 하는 선택이다.
정규화·반올림 정책과 SA 차이, 저장 범위는 [ADR 0004](../../../services/order/decisions/0004-order-money-bigdecimal.md)에 기록했다.

## 검증

- 테스트를 먼저 바꿔 기존 long 타입과의 컴파일 실패를 확인한 뒤 Entity를 수정했다.
- `./gradlew :order-service:test --console=plain`: 57개 통과(단위 33개, PostgreSQL 통합 24개), 실패·오류·skip 0.
- `./gradlew build --console=plain`: 성공. 기존 테스트 결과를 재사용했으며 전체 XML 합계 155개, 실패·오류·skip 0이다.
- `script/docs-check.sh`, `script/error-codes-check.sh`, `git diff --check`, 변경 문서 링크 검사도 통과했다.
- `0.10 × 3 = 0.30`, 소수 둘째 자리 보존, 반올림 필요 값 거부, 최대 금액·null·음수·DB 범위 초과를 검증했다.

## 직접 해보기

1. `./gradlew :order-service:test --tests '*OrderTest'`를 실행한다. 복원한 `allowsLargestRepresentableTotal`, `rejectsTotalPriceOverflow`에서 Long 최댓값과 곱셈 오버플로를 확인한다.
2. Docker 실행 상태에서 `./gradlew :order-service:test --tests '*OrderRepositoryIntegrationTest'`를 실행한다. `savesAndReloadsOrder`에서 정수 금액 저장·조회를 확인한다.

## 원복

정수 원화 범위에는 소수 자릿수·반올림 정책이 필요하지 않으므로 기존 long·BIGINT와 오버플로 검증을 복원했다. BigDecimal 전용 테스트는 제거하고 기반 테스트 42개를 유지했다. ADR 0004는 `대체됨 (0005)`으로 표시했다.

원복 후 `./gradlew build --console=plain`이 통과했다. 주문 테스트 42개를 다시 실행했고 전체 XML 합계 140개, 실패·오류·skip 0이다. 다른 모듈 테스트는 이전 성공 결과를 재사용했다. 문서·에러 코드·공백·로컬 링크 검사도 통과했다.

## 참고

- [구현 노트](../../../services/order/notes.md)
- [Java 17 BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [PostgreSQL 16 Numeric Types](https://www.postgresql.org/docs/16/datatype-numeric.html)
