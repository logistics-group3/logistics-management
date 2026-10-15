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

## O-01 application 생성 흐름·보상 (2026-10-15)

### 처리 과정

1. 검증한 기반을 `216a768 feat(order): 주문 엔티티와 저장소 기반 구현`으로 커밋했다. 이후 application 변경은 별도 작업으로 진행했다.
2. `CreateOrderCommandTest`, `OrderServiceTest`를 먼저 작성하고 타입 누락으로 컴파일 실패를 확인했다.
3. application에 `CompanyReader`, `DeliveryGateway`와 입출력 record를 추가했다. 회사·상품·배송 서비스의 클래스를 공유하지 않는다.
4. `OrderService`가 ID 선발급 → 업체 조회 → 수령업체 관리자 또는 MASTER 확인 → 상품 소속 확인 → 배송 생성 → 주문 저장을 수행하도록 구현했다.
5. `OrderWriter`에 로컬 저장 트랜잭션을 분리했다. 실제 PostgreSQL로 flush 실패와 커밋 실패를 각각 재현해 배송 보상을 검증했다.

### 핵심

- **요청을 신뢰하는 범위**: Command에는 업체·상품 ID, 수량, 요청사항만 있다. 주문자 ID는 `UserContext`, 상품명·단가는 I-08 결과에서 가져온다. 기본 요청 오류는 Command 생성 시점에 거부한다.
- **외부 호출과 DB 경계**: `OrderService.create`는 `Propagation.NEVER`로 기존 DB 트랜잭션 안에서의 호출을 막는다. `OrderWriter.save`는 별도 빈의 `REQUIRES_NEW` 트랜잭션으로 저장하고 프록시가 커밋까지 완료한 뒤 반환한다. 따라서 flush 이후 커밋에서 발생한 오류도 호출자가 보상할 수 있다.
- **실패별 보상**: 조회 실패와 배송담당자 없음 같은 저장 전 거절은 보상하지 않는다. 배송 5xx·타임아웃·ID 없는 응답은 생성 여부가 불명확하므로 orderId로 삭제한다. 배송 생성 후 Entity 검증·DB 저장·커밋 실패도 삭제 보상한다.
- **재시도 한 곳에서 관리**: I-13 보상은 `OrderService`가 총 3회 시도한다. 모두 실패하면 orderId와 `COMPENSATION_FAILED`를 ERROR 로그에 남기고 최초 실패 코드를 유지한다. 향후 I-13 Feign 어댑터에는 별도 재시도를 겹치지 않는다. GET·I-09의 Feign 재시도 연결은 다음 단계다.
- **이 단계의 조립 범위**: 외부 어댑터 연결 전에는 `OrderService`를 테스트에서 `@Import`로 조립했다. 이후 아래 Feign·Controller 단계에서 `@Service`로 등록하고 테스트의 `@Import`는 제거했다. 운영용 가짜 외부 구현은 없다.

### 검증

| 실행 | 결과 |
|---|---|
| application 단위 테스트 | 23개 통과: Command 입력 검증 7개, 생성·권한·실패 보상 16개 |
| `OrderCreationIntegrationTest` | 4개 통과: 정상 커밋, flush 실패 보상, 지연 제약의 커밋 실패 보상, 기존 트랜잭션 호출 차단 |
| `./gradlew :order-service:test --console=plain` | 69개 통과, 실패·오류·skip 0 |
| `./gradlew build --console=plain` | 성공. 전체 XML 합계 167개, 실패·오류·skip 0. 전체 빌드에서는 기존 성공 테스트 결과를 재사용 |
| `script/docs-check.sh`, `script/error-codes-check.sh`, `git diff --check` | 통과. 변경 파일 공백과 문서 로컬 링크도 확인 |

커밋 실패 테스트는 테스트 컨테이너의 테이블에만 `DEFERRABLE INITIALLY DEFERRED` 제약을 잠시 추가한다. 실제 JDBC commit에서 중복 오류가 발생했고, 배송 삭제 호출 전에 실패한 주문 행이 롤백됐으며 DB 트랜잭션이 없음을 확인했다. 테스트가 끝나면 제약을 제거한다.

### 확인 질문

- `OrderService` 전체에 기본 `@Transactional`을 붙이면 원격 호출과 실패 보상에 어떤 문제가 생기는가?
- 배송 타임아웃과 `DELIVERY_MANAGER_NOT_FOUND`를 똑같이 처리하면 안 되는 이유는 무엇인가?
- 보상 API에도 Feign 3회 재시도를 붙이면 실제 호출 횟수는 어떻게 달라지는가?

### 메모

- 수령인 기본값은 SA 5.6의 요청업체명이며, 수령업체 관리자 ID를 배송에 전달한다. 수령인의 Slack ID 조회는 delivery 책임이다.
- 테스트 작성 중 Mockito 검증을 중첩 호출한 문제는 [TS-006](../../troubleshooting.md)에 기록했다.
- 이 단계에서는 실제 Feign 호출·HTTP API·Gateway를 검증하지 않았다. 후속 결과는 아래 Feign·Controller 기록을 따른다. Slack 발송은 아직 구현하지 않았다.
- 재고 단계와 공통 모듈, 다른 서비스 코드는 추가·변경하지 않았다. 신규 서비스 에러 코드도 없다.

### 근거

- [SA 5.6·6.3·6.7·7.1](../../SA/sparta-logistics-sa.md)
- [application 규칙](../../agents/rules/service.md), [Feign 규칙](../../agents/rules/feign.md)
- [Spring Framework 6.2 트랜잭션 전파](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html)
- [PostgreSQL 16 지연 제약](https://www.postgresql.org/docs/16/sql-set-constraints.html)

## O-01 실제 Feign·Controller 연결 (2026-10-15)

### 처리 과정

1. 기존 application 작업을 `3b5a39c feat(order): 주문 생성 흐름과 실패 보상 구현`으로 커밋했다.
2. Controller HTTP 계약 테스트를 먼저 작성해 타입 누락 실패를 확인한 뒤 `OrderController`, `CreateOrderRequest`, `OrderResponse`를 추가했다.
3. company 조회와 delivery 생성·삭제 Feign 클라이언트, 전송 DTO, application 인터페이스 어댑터를 연결했다. `OrderService`를 실제 Spring 빈으로 등록했다.
4. 실제 Tomcat·Feign·PostgreSQL을 연결한 테스트에서 JSON·헤더 전파·오류 코드·재시도·보상을 검증했다. 외부 서버 응답만 로컬 HTTP 대역으로 제공했다.
5. 로컬 주문 서비스를 기동하고, 별도 격리 환경에서는 실제 Gateway와 JWT까지 포함한 정상·오류 요청을 검증했다.

### 핵심

- **Controller 경계**: Request의 Bean Validation → Command 변환 → 서비스 호출 → Response 변환만 한다. 주문자와 가격은 요청으로 받지 않고 인증 사용자와 상품 조회 결과를 사용한다.
- **Feign 경계**: 전송 DTO와 application DTO를 분리한다. `name`은 Eureka 서비스 이름이며 같은 delivery 서버에도 `contextId`를 나눠 생성과 삭제의 재시도 설정을 분리한다.
- **재시도 횟수**: GET·I-09 통신 실패는 공통 설정으로 총 3회 시도한다. I-13은 서비스 보상 루프에서 재시도하므로 Feign은 `NEVER_RETRY`다. 5xx 응답은 공통 디코더가 바로 503으로 변환한다.
- **잘못된 성공 응답**: 전송 단가를 `Long`으로 받아 필드 누락과 0원을 구분한다. 누락된 가격·필수 업체 정보·배송 ID, 요청과 다른 업체·상품 ID를 정상 데이터로 사용하지 않는다.
- **정수 계약**: 주문 전용 `OrderJacksonConfig`가 `ACCEPT_FLOAT_AS_INT`를 끈다. 수량 1.5나 소수 단가를 조용히 정수로 자르지 않는다. Entity·application 금액은 계속 long이다.

### 검증

| 범위 | 결과 |
|---|---|
| Controller 슬라이스 | 11개 통과. 201 응답·사용자/Command 전달·입력 검증·401·403·503 |
| 실제 HTTP/Feign 통합 | 13개 통과. 전송 필드·헤더·Audit·오류 전파·형식 위반·재시도·보상 |
| 전체 빌드 | `./gradlew build --console=plain` 성공. 주문 93개를 실행했고 전체 XML 합계 191개, 실패·오류·skip 0. 다른 모듈 테스트는 이전 결과를 재사용 |
| 문서 검사 | `script/docs-check.sh`, `script/error-codes-check.sh`, `git diff --check` 통과. 변경 파일의 공백·문서 로컬 링크도 확인 |
| 현재 Compose | `script/run.sh order-service` 성공. 실제 order_schema의 Flyway V1 적용, Gateway OpenAPI 200/O-01 노출, 인증 없는 주문 요청 401 |
| 격리 Gateway 스모크 | 실제 Gateway·JWT 검증·Order·Feign·PostgreSQL을 연결해 401·201·400·503 확인. user·company·delivery HTTP 응답은 대역 |

격리 정상 요청은 원화 총액 600,000을 저장했고 Audit을 `smoke-user`로 기록했다. 클라이언트가 보낸 위조 `X-User-*` 헤더가 JWT 검증 결과로 교체됐으며, 배송 500 응답 때는 503과 배송 삭제 보상·미저장을 확인했다. 임시 Java 프로세스와 PostgreSQL 컨테이너는 종료·삭제했다. 이 수동 검증은 Gradle 테스트 개수에 포함하지 않는다.

현재 Compose에서는 MASTER 환경변수가 설정돼 있지 않아 로그인 계정을 새로 만들지 않았다. company·delivery의 공개 OpenAPI 업무 경로는 비어 있었고, 팀 서비스 실제 데이터와 정상 주문을 만드는 검증은 남겨 두었다.

### 실행 중 확인한 영향

`script/run.sh order-service`가 의존 PostgreSQL 컨테이너도 재생성했다. 정확한 재생성 판정 원인은 확인하지 못했으며 스크립트가 의존 서비스를 함께 조정하는 것은 확인했다.
PostgreSQL은 2026-10-14 생성된 기존 `logistics-management_pgdata`를 재사용했고 초기화를 건너뛰었다. `.env` 해시는 같고, 주문·PostgreSQL을 제외한 7개 컨테이너의 ID·시작 시각은 유지됐다. 비즈니스 서비스 5개와 Gateway의 health는 모두 UP이었다.
의존 서비스가 이미 정상인 환경에서 주문만 교체하려면 빌드 후 `docker compose up -d --no-deps --no-build order-service`처럼 범위를 명시한다. 공동 실행 스크립트 자체는 수정하지 않았다.

### 확인 질문

- 같은 배송 서버를 부르는 두 Feign 클라이언트의 contextId를 왜 나누는가?
- 내부 응답의 price를 primitive long으로 받으면 필드 누락을 어떻게 구분할 수 있는가?
- Controller에서 application Result를 그대로 반환하면 내부 변경이 외부 API에 어떤 영향을 주는가?

### 메모

- 테스트 드라이버의 503 자동 재전송 문제는 [TS-007](../../troubleshooting.md)에 기록했다. 호출 횟수 검증을 약하게 바꾸지 않고 드라이버 재시도를 제거했다.
- 의존 DB 컨테이너 재생성은 [TS-008](../../troubleshooting.md)에 기록했다.
- Slack 알림, O-02~O-06, 팀 서비스 실제 데이터 연동은 이번 구현에 포함하지 않았다. 공통 모듈·다른 서비스 코드·SA 계약은 바꾸지 않았다.
- 현재 Feign·Controller 변경은 미커밋이며 푸시는 수행하지 않았다.

### 근거

- [OpenFeign 4.3 설정과 contextId](https://docs.spring.io/spring-cloud-openfeign/reference/4.3/spring-cloud-openfeign.html)
- [Feign 규칙](../../agents/rules/feign.md), [Controller 규칙](../../agents/rules/controller.md)
