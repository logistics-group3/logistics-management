# order 구현 노트

## O-01 준비: 주문 Entity·마이그레이션·Repository (2026-10-15)

### 처리 과정

1. `OrderTest`와 `OrderRepositoryIntegrationTest`를 먼저 작성했다. 첫 실행은 `Order`, `OrderStatus`, `OrderRepository`가 없어 `compileTestJava` 단계에서 실패했다.
2. `order-service/src/main/java/com/sparta/logistics/order/domain/`에 `Order`, `OrderStatus`, `OrderRepository`를 추가했다. 주문 생성 규칙을 구현한 뒤 단위 테스트 24개를 통과했다.
3. `order-service/src/main/resources/db/migration/V1__create_p_orders.sql`에 테이블·CHECK·부분 인덱스를 선언했다. 실행 스키마는 설정으로 정하므로 SQL 테이블명에는 스키마를 붙이지 않았다.
4. `infrastructure/persistence/OrderJpaRepository`가 `JpaRepository<Order, UUID>`와 도메인 Repository를 함께 상속하도록 연결했다.
5. 공통 `PostgresIntegrationTest`를 상속하고 `order_schema`를 지정해 PostgreSQL 통합 테스트 18개를 실행했다. Flyway V1 적용과 Hibernate `ddl-auto=validate`를 포함한다.

### 핵심

- **주문 스냅샷**: 상품명·단가는 주문 시점 값을 저장한다. `totalPrice`는 외부 입력으로 받지 않고 엔티티가 단가와 수량으로 계산한다. 이후 상품 정보 변경과 관계없이 주문 이력을 보존한다.
- **금액 규칙**: SA 5.5·6.7에 따라 Java `long`과 DB `BIGINT`를 쓴다. 같은 날 BigDecimal 전환을 검토했으나 사용자가 정수 원화 범위를 확인하고 원복을 요청했다. 현재 기준은 [ADR 0005](decisions/0005-order-money-integer-krw.md)이며 곱셈 오버플로를 `Math.multiplyExact`로 막는다.
- **선발급 ID와 서비스 경계**: `Order.create`는 application이 배송 호출 전에 발급한 ID를 받는다. 주문 안에서 다른 ID를 생성하지 않는다. 사용자·업체·상품·배송은 UUID로 참조하고 다른 서비스 테이블과 FK·JPA 연관관계를 만들지 않는다.
- **저장소와 커밋**: 도메인에는 `saveAndFlush`와 `findById`만 노출한다. SQL 실행과 커밋은 다르므로 향후 Saga는 로컬 트랜잭션 종료 시점의 실패까지 처리해야 한다. ID가 이미 있는 객체는 Spring Data의 기본 판정에서 `merge` 경로를 쓰므로 저장 메서드가 반환한 Entity를 사용한다.
- **논리 삭제**: 기존 `BaseEntity`의 Audit·`softDelete`를 재사용하고 `Order`에 `@SQLRestriction`을 붙였다. SA 5.1에 따라 조회 인덱스와 배송 유니크 인덱스는 활성 행만 포함한다. DB에는 삭제 이력이 남고 Repository 조회에서는 제외된다.

### 검증

| 실행 | 결과 |
|---|---|
| `./gradlew :order-service:test --tests 'com.sparta.logistics.order.domain.OrderTest' --console=plain` | 구현 전 타입 누락으로 컴파일 실패, 구현 후 단위 테스트 24개 통과 |
| `./gradlew :order-service:test --console=plain` | 42개 통과: 단위 24개 + PostgreSQL 통합 18개 |
| `./gradlew build --console=plain` | 성공. 전체 테스트 XML 합계 140개, 실패·오류·skip 0. 직전에 통과한 order 테스트는 UP-TO-DATE로 재사용 |
| `script/docs-check.sh`, `script/error-codes-check.sh`, `git diff --check` | 통과. 변경 문서의 로컬 링크와 신규 파일 공백도 확인 |

통합 테스트에서는 PostgreSQL 16.15 테스트 컨테이너를 사용했다. 저장·조회, SYSTEM/헤더 기반 Audit, 논리 삭제, 활성 배송 중복 거부, 삭제된 행의 유니크 제외, 필수 참조 ID, CHECK, 부분 인덱스, 주문 스키마와 FK 부재를 확인했다.

### 확인 질문

- 주문 시점 상품명·단가를 저장하지 않고 매번 company-service에서 조회하면 어떤 문제가 생기는가?
- 배송 생성과 주문 저장에서 서로 다른 orderId를 사용하면 실패 보상에 어떤 문제가 생기는가?
- `saveAndFlush`가 성공했는데도 트랜잭션 전체를 성공으로 단정할 수 없는 이유는 무엇인가?

### 메모

- 범위는 주문 기반이다. O-01 HTTP API, 외부 호출, 소유자 검증, 취소·삭제 가능 상태 판정, Slack 알림은 다음 단계다. 실제 Gateway 호출과 운영 Compose DB 적용은 수행하지 않았다.
- 신규 서비스 에러 코드는 추가하지 않았다. 생성 불변식 위반은 기존 `CommonErrorCode.INVALID_REQUEST`를 사용한다.
- 서비스 ADR 0001·0002는 제안 상태다. 재고 처리와 `HUB_WAITING` 취소 조건은 채택하지 않았고 SA v2.0을 유지했다.
- 사용자 요청에 포함된 Entity·마이그레이션·Repository·테스트를 진행하면서 계층마다 설명했다. 커밋·푸시는 수행하지 않았다.

### 근거

- [SA 5.1·5.5·6.7](../../SA/sparta-logistics-sa.md)
- [domain 규칙](../../agents/rules/domain.md), [마이그레이션 규칙](../../agents/rules/migration.md), [Repository 규칙](../../agents/rules/repository.md), [테스트 규칙](../../agents/rules/test.md)
- [Spring Data JPA: 엔티티 저장과 신규 판정](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/entity-persistence.html)
- [Java 17 Math.multiplyExact](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#multiplyExact(long,int))
- [PostgreSQL 16 부분 인덱스](https://www.postgresql.org/docs/16/indexes-partial.html)

## 주문 금액 BigDecimal 전환 (2026-10-15)

이 절은 원복된 변경의 이력이다. 현재 구현은 아래 원복 기록과 ADR 0005를 따른다.

### 처리 과정

1. 사용자 요청으로 Entity와 팩토리의 단가·총액 타입을 `BigDecimal`로 바꿨다. 총액은 `multiply`로 계산한다.
2. 테스트를 먼저 바꾸고 기존 long 타입과 맞지 않아 컴파일이 실패하는 것을 확인한 뒤 구현했다.
3. 미커밋 V1의 두 금액 컬럼을 `NUMERIC(15,2)`로 바꾸고 JPA precision·scale을 맞췄다.
4. 소수 계산·자릿수·범위 테스트를 추가했다. 주문 테스트 57개(단위 33개, PostgreSQL 통합 24개)가 통과했다.
5. 전체 `./gradlew build --console=plain`과 문서·에러 코드·공백 검사가 통과했다. 테스트 XML 합계는 155개, 실패·오류·skip 0이며 전체 빌드에서는 기존 성공 결과를 재사용했다.

### 핵심

- `long`도 정수 금액에는 정확하다. BigDecimal 전환의 이유는 소수 단위 표현과 명시적인 금액 규칙이다.
- `0.10 × 3 = 0.30`을 정확히 계산한다. 테스트에서는 문자열로 BigDecimal을 만들고 `isEqualByComparingTo`로 값의 동등성을 검사한다.
- `setScale(2, UNNECESSARY)`는 `12.3400`을 `12.34`로 정리하지만 `12.345`는 거부한다. 데이터베이스의 암묵적 반올림에 금액 정책을 맡기지 않는다.
- 소수 둘째 자리와 최대 `9,999,999,999,999.99`를 Entity에서 검사한다. PostgreSQL 테스트로 실제 저장 타입과 소수 금액 보존도 확인했다.

### 확인 질문

- `new BigDecimal("0.1")`과 `new BigDecimal(0.1)`은 왜 다른 결과를 만들 수 있는가?
- BigDecimal의 `equals`와 `compareTo`는 `1.0`, `1.00`을 어떻게 비교하는가?

### 메모

- 선택과 SA 차이는 [ADR 0004](decisions/0004-order-money-bigdecimal.md)에 기록했다. 다른 서비스의 가격 계약과 팀 공통 SA는 수정하지 않았다.
- 테스트 DB는 새 PostgreSQL 컨테이너다. 유지되는 Compose DB의 기존 BIGINT 데이터 변환은 수행하지 않았다.

## 정수 원화 금액으로 원복 (2026-10-15)

사용자가 원화만 다룬다는 전제를 확인하고 기존 long 구현으로 복원해 달라고 요청했다.
`Order`의 금액 필드·팩토리 인자, V1의 두 금액 컬럼, 단위·통합 테스트를 BigDecimal 전환 직전 상태로 복원했다. 총액은 다시 `Math.multiplyExact`로 계산한다.
Repository·Audit·논리 삭제와 주문 기반의 나머지는 유지했다. 결정 이력은 [ADR 0005](decisions/0005-order-money-integer-krw.md)에 남겼다.

검증: `./gradlew build --console=plain` 성공. 주문 테스트 42개를 다시 실행했고(단위 24개, PostgreSQL 통합 18개), 전체 XML 합계 140개에 실패·오류·skip은 없다. 다른 모듈 테스트는 이전 성공 결과를 재사용했다. 문서·에러 코드·공백·로컬 링크 검사도 통과했다.
