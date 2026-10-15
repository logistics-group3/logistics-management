# 35. 주문 Entity·마이그레이션·Repository 기반

- 날짜: 2026-10-15
- 브랜치: `feat/order-create`
- 상태: 기반 구현과 테스트 완료. O-01 API는 미구현, 커밋·푸시는 미수행
- 후속 검토: [스텝 36](step-36-order-money-bigdecimal.md)에서 BigDecimal로 전환했다가 사용자 요청으로 원복했다. 현재 금액 타입은 이 단계의 long·BIGINT이며 [ADR 0005](../../../services/order/decisions/0005-order-money-integer-krw.md)를 따른다.

## 만든 것

| 위치 | 역할 |
|---|---|
| `order-service/.../domain/Order.java` | 주문 스냅샷, 수량·가격·필수 값 검증, 금액 계산 |
| `order-service/.../domain/OrderStatus.java` | `ORDERED`, `CANCELED` |
| `order-service/.../domain/OrderRepository.java` | 저장·단건 조회 계약 |
| `order-service/.../infrastructure/persistence/OrderJpaRepository.java` | Spring Data JPA 연결 |
| `order-service/src/main/resources/db/migration/V1__create_p_orders.sql` | 테이블, CHECK, 부분 인덱스 |
| `order-service/src/test/.../OrderTest.java`, `OrderRepositoryIntegrationTest.java` | 도메인 규칙과 실제 PostgreSQL 동작 검증 |

## 설명과 리뷰 포인트

1. Entity에는 setter를 열지 않고 `create`를 통해 유효한 주문만 만든다. 다른 서비스의 존재·권한 확인은 다음 application 단계가 맡는다.
2. 상품명·단가는 스냅샷이며 총액은 직접 계산한다. 정수 원화 요구에 맞춰 SA의 long·BIGINT 계약과 `Math.multiplyExact`의 오버플로 방어를 유지한다.
3. 주문 ID는 배송 호출 전에 선발급한 값을 받는다. 생성과 보상에서 같은 주문을 식별하기 위해서다.
4. Repository 인터페이스와 Spring Data 연결을 분리했다. `saveAndFlush`는 SQL 실행이며 커밋 완료를 뜻하지 않는다.
5. 테스트는 Java 규칙과 DB 제약을 나눴다. 후자는 H2 대신 PostgreSQL 16 컨테이너로 확인했다.

상세 흐름과 선택 이유, 공식 근거는 [order 구현 노트](../../../services/order/notes.md)에 있다.

## 검증 결과

- 테스트를 먼저 작성하고 타입 누락으로 컴파일 실패를 확인한 뒤 구현했다.
- 단위 테스트 24개와 PostgreSQL 통합 테스트 18개: 42개 모두 통과했다.
- 전체 `./gradlew build --console=plain` 성공. 테스트 XML 합계 140개, 실패·오류·skip 0이다. 주문 테스트는 직전 실행 결과를 재사용했다.
- `script/docs-check.sh`, `script/error-codes-check.sh`, `git diff --check`와 문서 로컬 링크 검사를 통과했다.
- 실제 Gateway 요청, 다른 서비스 연동, 운영 Compose DB 마이그레이션은 미검증이다.

## 직접 해보기

1. `./gradlew :order-service:test --tests '*OrderTest'`를 실행한다. IntelliJ에서는 `OrderTest` 클래스 옆 실행 버튼을 누른다. 수량·금액·ID·문자열 경계값 테스트 24개가 통과하는지 본다.
2. Docker가 실행 중인 상태에서 `./gradlew :order-service:test --tests '*OrderRepositoryIntegrationTest'`를 실행한다. PostgreSQL에서 논리 삭제·유니크·CHECK·스키마 테스트 18개가 통과하는지 본다.
3. `order-service/build/reports/tests/test/index.html`을 브라우저에서 열어 실행한 테스트의 결과를 확인한다. 전체 주문 결과는 필터 없이 `./gradlew :order-service:test`를 실행한 뒤 본다.

## 다음 단계

업체·상품 검증과 배송 생성·보상을 담당하는 application 흐름을 테스트 대역으로 구현한다. 실제 연동 전에 company·delivery의 내부 API 계약을 맞춘다.
