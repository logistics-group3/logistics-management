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
- 공통 요소(BaseEntity, ApiResponse, ErrorCode, UserContext 등)는 `common`에 둔다. 서비스 메인 클래스는 `scanBasePackages = "com.sparta.logistics"`로 common을 스캔한다.

## 2. 간결하고 수준 높은 코드
- 필요한 것만 만든다. 쓰이지 않는 추상화, 미래를 위한 인터페이스, 한 번만 쓰는 유틸을 미리 만들지 않는다.
- 이미 있는 것을 먼저 쓴다. Spring, JPA, Bean Validation, 표준 라이브러리가 해결하는 문제를 직접 구현하지 않는다.
- 검증, 예외 처리, 트랜잭션 경계, 보안은 간결함을 이유로 생략하지 않는다.
- 이름으로 의도를 드러낸다. 주석으로 이름을 보충해야 하면 이름을 먼저 고친다.
- 생성자 주입만 쓴다(`@RequiredArgsConstructor` + `private final`). 필드 주입은 쓰지 않는다.
- `@Transactional`은 application 계층 public 메서드에 둔다. 조회는 `readOnly = true`를 쓴다.
- 예외는 `common`의 ErrorCode 기반 커스텀 예외로 던진다. `RuntimeException`을 그대로 던지지 않는다.
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

## 5. 설정
- `application.yml`에는 로컬 기본값을 두고, 컨테이너 환경 값은 환경 변수(`SPRING_DATASOURCE_*` 등)로 덮어쓴다.
- 포트, 스키마, 계정 이름은 SA 문서 3.2와 3.4를 따른다.
