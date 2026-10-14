# 스텝 10-6. Testcontainers DB 통합 테스트 (리뷰 반영)

- 날짜: 2026-10-14
- 근거: [리뷰 1](review-01-common-infra.md) 테스트 공백(DB 통합 검증 없음, `@DataJpaTest` 즉시 실패)
- 결과: 실제 PostgreSQL 16 컨테이너로 user-service 통합 테스트 4개 통과. 전체 테스트 61개(common 24, config-server 1, gateway 23, user 13) 통과

## 만든 것
| 파일 | 역할 |
|---|---|
| `common/build.gradle` | `java-test-fixtures` 플러그인. testFixtures에 Testcontainers 의존성 |
| `common/src/testFixtures/.../support/PostgresIntegrationTest` | 서비스 통합 테스트 공통 부모. PostgreSQL 16 싱글턴 컨테이너 + `@ServiceConnection`, config·Eureka 끔, `ddl-auto=create-drop` |
| 서비스 5개 `build.gradle` | `testImplementation(testFixtures(project(':common')))` |
| `user-service/.../integration/UserAuthIntegrationTest` | 아래 4개 시나리오 |

## 검증한 시나리오
1. **회원가입 저장값**: 비밀번호가 BCrypt(`$2a$`)로 저장되고 원문과 다르다. role USER, is_delivery_manager false, created_by·updated_by가 SYSTEM, created_at이 채워진다.
2. **논리 삭제된 사용자**: `softDelete` 후 로그인은 LOGIN_FAILED, I-01은 USER_NOT_FOUND, 같은 username으로 다시 가입하면 DUPLICATE_USERNAME(탈퇴자까지 확인하는 네이티브 쿼리). deleted_by가 기록된다.
3. **동시 가입**: 스레드 8개가 같은 username으로 동시에 가입하면 정확히 1개만 성공하고 7개는 DUPLICATE_USERNAME이다. 500(다른 예외)은 0개다.
4. **I-01 응답 계약**: `userId`, `username`, `role`, `isDeliveryManager`, `hubId`, `slackId` 필드명이 SA 6.3과 같다.

## 테스트가 실제로 버그를 잡는지 확인
- 10-3에서 고친 `saveAndFlush`를 잠시 예전 `save()`로 되돌리고 동시 가입 테스트를 3번 돌렸다. 3번 모두 실패했다.
- 실패 사유: `DataIntegrityViolationException: duplicate key value violates unique constraint "p_users_username_key"`가 예외 변환 밖으로 빠져나왔다. 운영에서라면 500이 된다.
- 다시 `saveAndFlush`로 되돌린 뒤 전체가 통과했다(변경 없음 확인).
- mock 기반 단위 테스트로는 이 문제를 잡을 수 없었다. INSERT 시점(쓰기 지연)과 DB 유니크 제약은 실제 DB에서만 드러난다.

## 서비스 담당자가 쓰는 법
```java
@TestPropertySource(properties = "jwt.secret=...")   // 서비스에 필요한 설정만 추가
class HubSearchIntegrationTest extends PostgresIntegrationTest {
    @Autowired HubService hubService;
    // 실제 PostgreSQL에서 QueryDSL 검색, 논리 삭제, 유니크 제약을 검증한다
}
```
- Docker가 실행 중이어야 `./gradlew build`가 통과한다(통합 테스트가 컨테이너를 띄운다).
- `@DataJpaTest`를 쓸 때는 Audit 설정이 빠지므로 `@Import({JpaAuditingConfig.class, QuerydslConfig.class})`와 `@AutoConfigureTestDatabase(replace = NONE)`를 붙이고 같은 컨테이너를 쓴다. 대부분은 위 부모 클래스를 쓰는 편이 간단하다.

## 리뷰 포인트
1. **H2를 쓰지 않은 이유**: 부분 인덱스, 네이티브 쿼리, 유니크 제약, 트랜잭션 동작이 운영 DB와 달라질 수 있다. 검증하려는 것이 바로 그 동작이다.
2. **싱글턴 컨테이너**: `@Testcontainers` + `@Container`는 테스트 클래스마다 컨테이너를 새로 띄운다. static 블록에서 한 번만 `start()`하면 모든 테스트 클래스가 공유해 빠르다. Ryuk이 테스트가 끝나면 정리한다.
3. **testFixtures**: 테스트 전용 공통 코드를 main 코드에 섞지 않고 모듈 사이에 공유하는 Gradle 기능이다.

## 면접 포인트
- 통합 테스트에서 실제 DB(Testcontainers)를 쓰는 이유와 비용
- 동시성 버그를 테스트로 재현하는 방법(CountDownLatch로 동시 시작)
- "테스트가 통과한다"와 "테스트가 버그를 잡는다"의 차이(일부러 되돌려서 실패하는지 확인)
