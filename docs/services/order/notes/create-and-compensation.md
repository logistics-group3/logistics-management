# 주문 생성과 실패 보상

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
- 테스트 작성 중 Mockito 검증을 중첩 호출한 문제는 [TS-007](../../../troubleshooting.md)에 기록했다.
- 이 단계에서는 실제 Feign 호출·HTTP API·Gateway를 검증하지 않았다. 후속 결과는 아래 Feign·Controller 기록을 따른다. Slack 발송은 아직 구현하지 않았다.
- 재고 단계와 공통 모듈, 다른 서비스 코드는 추가·변경하지 않았다. 신규 서비스 에러 코드도 없다.

### 근거

- [SA 5.6·6.3·6.7·7.1](../../../SA/sparta-logistics-sa.md)
- [application 규칙](../../../agents/rules/service.md), [Feign 규칙](../../../agents/rules/feign.md)
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

- 테스트 드라이버의 503 자동 재전송 문제는 [TS-008](../../../troubleshooting.md)에 기록했다. 호출 횟수 검증을 약하게 바꾸지 않고 드라이버 재시도를 제거했다.
- 의존 DB 컨테이너 재생성은 [TS-009](../../../troubleshooting.md)에 기록했다.
- Slack 알림, O-02~O-06, 팀 서비스 실제 데이터 연동은 이번 구현에 포함하지 않았다. 공통 모듈·다른 서비스 코드·SA 계약은 바꾸지 않았다.
- Feign·Controller 변경은 후속 요청에서 `9b48adc feat(order): 주문 생성 Feign과 Controller 연결`로 커밋했다. 푸시는 수행하지 않았다.

### 근거

- [OpenFeign 4.3 설정과 contextId](https://docs.spring.io/spring-cloud-openfeign/reference/4.3/spring-cloud-openfeign.html)
- [Feign 규칙](../../../agents/rules/feign.md), [Controller 규칙](../../../agents/rules/controller.md)
