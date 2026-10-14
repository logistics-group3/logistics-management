# 코딩 규칙

AGENTS.md의 "코드는 간결하되 수준 높게"를 구체화한 문서다. SA 문서의 계층 설명은 참고 기준이고, 실제 적용 범위는 팀 합의를 따른다.

## 1. 패키지와 계층
```
com.sparta.logistics.{서비스}
├── presentation     controller, dto(request/response)
├── application      service, dto(command/result)
├── domain           entity, enum, repository(interface), 도메인 규칙
└── infrastructure   jpa, querydsl 구현, feign client, config
```
- 의존 방향: presentation → application → domain ← infrastructure. domain은 다른 계층을 모른다.
- Controller는 요청 검증과 변환만 한다. 비즈니스 규칙은 domain(Entity 메서드), 흐름 조율은 application에 둔다.
- 상태 전이처럼 불변식이 있는 규칙은 Entity 메서드 안에서 검증한다. setter를 열지 않는다.
- request DTO를 application 계층까지 넘기지 않는다. command로 변환해서 넘긴다.
- 서비스 간 데이터는 FK 없이 UUID로 참조하고, 검증은 Feign 호출로 한다. 다른 서비스의 스키마에 직접 접근하지 않는다.
- 외부 API(`/api/v1/**`)는 `ApiResponse`로 감싸 응답한다. 내부 API(`/internal/**`)는 DTO를 감싸지 않고 그대로 반환하고, FeignClient도 DTO 타입으로 받는다. 실패는 둘 다 SA 6.1 에러 형식이다(ADR 0010).
- 공통 요소(BaseEntity, ApiResponse, ErrorCode, UserContext 등)는 `common`에 둔다. common의 공통 빈은 자동 설정(`AutoConfiguration.imports`)으로 등록되므로 서비스 메인 클래스는 기본 `@SpringBootApplication`만 쓴다. 스캔 범위를 상위 패키지로 넓히지 않는다.
- common 공통 설정은 묶음별로 끄거나 바꿀 수 있다(ADR 0009). 끄기는 `common.{web,jpa-auditing,querydsl,feign,jackson,openapi}.enabled=false`, 바꾸기는 같은 타입의 빈을 서비스에 정의한다(예: `GlobalExceptionHandler`를 상속한 빈, `ErrorDecoder` 빈).

## 2. 간결하고 수준 높은 코드
- 필요한 것만 만든다. 쓰이지 않는 추상화, 미래를 위한 인터페이스, 한 번만 쓰는 유틸을 미리 만들지 않는다.
- 이미 있는 것을 먼저 쓴다. Spring, JPA, Bean Validation, 표준 라이브러리가 해결하는 문제를 직접 구현하지 않는다.
- 검증, 예외 처리, 트랜잭션 경계, 보안은 간결함을 이유로 생략하지 않는다.
- 이름으로 의도를 드러낸다. 주석으로 이름을 보충해야 하면 이름을 먼저 고친다.
- 생성자 주입만 쓴다(`@RequiredArgsConstructor` + `private final`). 필드 주입은 쓰지 않는다.
- `@Transactional`은 application 계층 public 메서드에 둔다. 조회는 `readOnly = true`를 쓴다.
- 예외는 `common`의 ErrorCode 기반 커스텀 예외로 던진다. `RuntimeException`을 그대로 던지지 않는다.
- 에러 코드 이름 규칙(전체 목록은 `docs/error-codes.md`):
    - 서비스 코드는 `{Service}ErrorCode implements ErrorCode` enum(`domain` 패키지)에 둔다. 응답 `code`는 상수 이름이다.
    - 이름은 `{리소스}_{상황}` 대문자 스네이크다. 예: `HUB_NOT_FOUND`, `PRODUCT_NOT_FOUND`. 하위 서비스의 코드는 Feign을 거쳐 그대로 전달되므로, 리소스 접두어로 어느 서비스의 실패인지 드러낸다.
    - SA 6.1 표에 있는 코드는 이름을 바꾸지 않는다(`LOGIN_FAILED`, `DUPLICATE_USERNAME`, `MANAGER_HUB_MISMATCH` 등).
    - 여러 서비스가 같은 의미로 쓰는 코드는 `CommonErrorCode`에만 둔다. 같은 이름을 두 서비스 enum에 정의하지 않는다.
    - 다른 서비스가 분기에 쓰는 코드는 이름을 바꾸지 않는다. 바꿔야 하면 호출하는 서비스 담당자와 먼저 합의한다.
    - 코드를 추가·변경하면 `docs/error-codes.md`를 함께 고치고 `script/error-codes-check.sh`로 확인한다.
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder`(생성 전용) 정도로 제한한다. Entity에 `@Data`, `@Setter`를 쓰지 않는다.
- N+1, 불필요한 조회, 동시성 문제를 의식하고 쓴다. 해결했다면 그 지점은 `Note:` 대상이다.

## 3. `Note:` 주석
사용자가 공부할 가치가 있는 코드에 남기는 주석이다. 팀원도 읽으므로 남발하지 않는다.

**다는 곳**
- 동시성, 트랜잭션 경계, 락, 멱등성 처리
- N+1 해결, 페치 전략, 인덱스를 의식한 쿼리
- 분산 환경 처리(Feign 실패, 보상 트랜잭션, 서킷 브레이커, 헤더 전파)
- 보안(JWT 검증, 인가, 비밀번호 해시)
- 설정 하나가 동작을 크게 바꾸는 지점(toolchain, 스캔 범위, 라우팅)
- 처음 보면 놓치기 쉬운 Spring 동작(프록시, 영속성 컨텍스트, 지연 로딩)

**달지 않는 곳**
- 코드만 읽어도 알 수 있는 내용, getter와 단순 CRUD, 매 파일 관성적으로 붙이는 경우

**형식**
- `Note: ` 로 시작한다. 한두 문장으로 무엇을 하는지와 왜 볼 만한지를 쓴다.
- 평서체로 쓴다. 과장, 감탄, 이모지, AI 말투를 쓰지 않는다.
- 더 깊은 설명이 필요하면 학습 노트 경로를 덧붙인다.

```java
// Note: 재고 차감 경쟁 조건을 막기 위한 비관적 락. 트랜잭션 격리 수준만으로 해결되지 않는 이유를 함께 보면 좋다.
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Product> findWithLockById(UUID id);
```
```groovy
// Note: 셸 JAVA_HOME과 무관하게 JDK 17로 컴파일·실행하게 고정하는 설정. 팀원 간 JDK 차이를 없앤다. (docs/learning/qna/2026-10-13-gradle-toolchain-and-initializr.md)
```

## 4. 테스트
- 도메인 규칙(상태 전이, 계산)은 순수 단위 테스트로 먼저 작성한다. 실패하는 테스트 → 구현 → 리팩터 순서를 기본으로 한다.
- application 서비스는 의존을 목으로 바꿔 흐름을 검증한다.
- DB가 필요한 테스트는 common testFixtures의 `PostgresIntegrationTest`를 상속한다(실제 PostgreSQL 16, Testcontainers). H2는 쓰지 않는다. Docker가 실행 중이어야 한다.
- 부분 인덱스, 네이티브 쿼리, 유니크 제약, 동시성처럼 DB 동작에 기대는 코드는 반드시 통합 테스트로 검증한다.
- 테스트 이름은 한국어 `@DisplayName`으로 시나리오를 쓴다.
- 테스트를 통과시키려고 검증 로직을 약화하지 않는다.

## 5. DB 스키마 (Flyway)
- 스키마는 Flyway가 만든다. Hibernate는 `ddl-auto: validate`로 검사만 한다.
- 테이블을 추가·변경하면 Entity와 함께 `src/main/resources/db/migration/V{번호}__{설명}.sql`을 추가한다. SQL에 스키마 이름을 붙이지 않는다.
- 이미 커밋한 마이그레이션 파일은 고치지 않는다. 새 버전 파일로 고친다.
- SA의 CHECK, DEFAULT, 부분 인덱스(`WHERE deleted_at IS NULL`)는 마이그레이션에 명시하고, 통합 테스트로 확인한다.

## 6. 설정
- 각 서비스의 `application.yml`은 이름·포트·Config Server 연결을 설정한다. 서비스 설정 정본은 `config-repo/`이며, 환경별 값은 `${ENV:기본값}` 자리표시자로 받는다.
- 포트, 스키마, 계정 이름은 SA 문서 3.2와 3.4를 따른다.
