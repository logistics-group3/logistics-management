# user 구현 노트

## U-03 내 정보 조회, U-04 사용자 단건 조회 (2026-10-15)
### 처리 과정
- 마이그레이션·domain·infrastructure: 변경 없음. 기존 `User` 엔티티와 `UserRepository.findById`를 그대로 쓴다.
- application: `UserResult`(SA 6.4 UserResponse 11개 필드)를 만들고 `UserQueryService`에 `getMyInfo(UserContext)`, `getUser(UserContext, UUID)`를 추가했다. I-01과 함께 쓰는 조회·404 처리는 `findUser()`로 모았다.
- presentation: `UserResponse`와 `UserController`(`GET /api/v1/users/me`, `GET /api/v1/users/{userId}`)를 만들었다.
- 요청 흐름: Gateway가 JWT 검증과 권한 재확인 후 `X-User-*` 헤더를 넣는다 → `@CurrentUser`가 `UserContext`로 바꾼다 → `UserQueryService`가 권한 검사 후 조회한다 → `ApiResponse<UserResponse>`로 응답한다.
- 테스트: `UserQueryServiceTest` 6개, `UserControllerWebMvcTest` 6개, `UserQueryIntegrationTest.userReadApis` 1개. Gateway 경유로 200·403·404·401·400을 확인했다.

### 핵심
- U-04는 권한 검사를 조회보다 먼저 한다. 반대 순서면 USER가 남의 ID를 넣었을 때 있으면 403, 없으면 404가 나와 ID 존재 여부가 드러난다. Gateway 경유로 USER가 없는 ID를 조회해도 403인 것을 확인했다.
- 본인·MASTER 판단은 `common`의 `UserContext.validateSelfOrMaster`를 쓴다. MASTER만 허용하는 API는 컨트롤러 `@RequireRole`로, "본인 또는 MASTER"처럼 요청 값에 따라 달라지는 판단은 application에서 한다.
- U-03에도 404 처리를 둔다. Gateway 권한 재확인은 60초 캐시라서 그 사이 탈퇴한 사용자의 요청이 들어올 수 있다.
- `UserResult`(application 출력)와 `UserResponse`(외부 API 계약)를 나눈다. 지금은 필드가 같지만 내부 변경이 API 계약으로 번지지 않게 한다.
- `/me`와 `/{userId}`가 함께 있어도 Spring MVC는 변수 없는 경로를 더 구체적인 것으로 보고 먼저 고른다.

### 확인 질문
- `UserControllerWebMvcTest`는 서비스를 목으로 바꿔 403을 직접 만든다. "USER가 남의 정보를 보면 403"은 어느 테스트가 보장하나?
- 사용자 헤더 없이 user-service를 직접 호출하면 왜 401인가? 이 동작이 없으면 어떤 공격이 가능한가?
- 탈퇴한 사용자를 MASTER가 조회하면 404다. 어느 코드 한 줄이 탈퇴자를 조회에서 빼는가?

### 메모
- 응답의 `createdAt`은 `2026-10-15T15:33:46.799332`처럼 마이크로초까지 나온다. SA 예시(`2026-10-13T09:00:00`)는 초 단위다. 모든 서비스의 날짜 형식에 걸리는 문제라 user에서 따로 바꾸지 않았다. 맞추려면 common Jackson 설정에서 팀 합의가 필요하다.
- Git Bash에서 `curl -d`로 한글을 보내면 400이 난다(`docs/troubleshooting.md` TS-005). UTF-8로 저장한 파일을 `--data-binary @file`로 보내면 된다.

## I-14 허브별 배송담당자 목록 (2026-10-15)
### 처리 과정
- domain: `UserRepository.findAllByHubIdAndDeliveryManagerTrue(hubId)`. Spring Data가 메서드 이름으로 쿼리를 만들므로 infrastructure 코드는 없다. 마이그레이션도 V1의 부분 인덱스 `idx_p_users_hub_id`를 그대로 쓴다.
- application: `DeliveryManagerResult(userId, slackId)`, `UserQueryService.getDeliveryManagers`.
- presentation: `InternalUserController`에 `GET /internal/users/delivery-managers?hubId=`. 감싸지 않은 배열을 반환한다(ADR 0010).
- 테스트: `UserQueryServiceTest` 2개, `InternalUserControllerWebMvcTest` 3개, `UserQueryIntegrationTest` 2개(쿼리 필터, 내부 API).

### 핵심
- 담당자가 없으면 404가 아니라 빈 배열이다. 그것을 실패로 볼지는 호출하는 delivery가 정한다(`DELIVERY_MANAGER_NOT_FOUND`).
- 탈퇴자는 `@SQLRestriction`이 메서드 이름 쿼리에도 붙어 빠진다. 그 조건(`deleted_at IS NULL`)이 부분 인덱스 조건과 같아 PostgreSQL이 인덱스를 쓸 수 있다(실행 계획은 미확인).
- `/delivery-managers`와 `/{userId}`가 함께 있어도 변수 없는 경로가 먼저 매칭된다.
- 계약은 `userId`, `slackId` 두 필드만 둔다. 계약이 작을수록 user 내부 변경이 다른 서비스로 번지지 않는다.

### 확인 질문
- 메서드 이름 쿼리를 네이티브 쿼리로 바꾸면 탈퇴자가 섞이는 이유는?
- hubId로 허브 존재를 확인(I-02)하지 않는 이유는?

## U-05 사용자 검색 (2026-10-15)
### 처리 과정
- domain: `UserSearchCondition`(검색 조건), `UserRepository.search(condition, pageable)`.
- infrastructure: `UserQueryRepository` + `UserQueryRepositoryImpl`(QueryDSL). `UserJpaRepository`가 `UserQueryRepository`도 상속해 Spring Data가 `search` 호출을 Impl로 보낸다.
- application: `UserQueryService.search`가 `Page<User>`를 `Page<UserResult>`로 바꾼다.
- presentation: `GET /api/v1/users`, `@RequireRole(Role.MASTER)`, `@ModelAttribute UserSearchRequest`, 공통 `Pageable`(size 10·30·50, 기본 `createdAt,DESC`), `PageResponse`.
- 테스트: `UserControllerWebMvcTest` 3개(조건 변환, USER 403, 잘못된 role 400), `UserQueryIntegrationTest` 2개(조건·정렬·탈퇴자 제외, `sort=password` 400).

### 핵심
- 조건 메서드가 값이 없으면 null을 돌려주고, QueryDSL `where`는 null을 무시한다. 조건 조합마다 쿼리를 따로 만들지 않는다.
- 정렬은 허용 목록(`createdAt`, `username`)으로만 바꾼다(`QuerydslSupport.toOrderSpecifiers`). `password` 같은 필드로 정렬하면 순서로 값을 추측할 수 있다.
- count 쿼리는 `PageableExecutionUtils`로 필요할 때만 실행한다.
- `UserSearchCondition`은 application이 아니라 domain에 둔다. domain의 Repository 인터페이스가 받는 타입이라 domain이 application을 알게 되면 의존 방향이 거꾸로 된다.

### 확인 질문
- `UserQueryRepositoryImpl`이라는 이름에서 `Impl`을 빼면 무슨 일이 생기나?
- 검색 결과가 0건일 때 count 쿼리는 실행되나?

## U-06 수정, U-08 논리 삭제 (2026-10-15)
### 처리 과정
- domain: `User.updateProfile`(null은 유지, 배송담당자 소속 수정 거절).
- application: `UserService.update`, `UserService.delete`(자기 자신 삭제 거절 후 `softDelete(master.username())`).
- presentation: `PATCH /api/v1/users/{userId}`(`UpdateUserRequest`), `DELETE /api/v1/users/{userId}`. 둘 다 `@RequireRole(Role.MASTER)`.
- 테스트: `UserTest` 1개, `UserServiceTest` 5개, `UserControllerWebMvcTest` 5개, `UserCommandIntegrationTest` 2개(updated_by·deleted_by에 MASTER 기록, 삭제 후 I-01 404).

### 핵심
- PATCH의 "보내지 않음"은 null로 표현한다. `@NotBlank`는 null도 거절하므로 쓰지 않고, `@Pattern`으로 "보냈다면 공백만은 안 된다"를 검사한다(`@Pattern`·`@Size`는 null을 통과시킨다).
- 배송담당자 소속 고정은 Entity 메서드 안에서 검사한다. 어느 경로로 수정하든 같은 규칙이 지켜진다(user 결정 0001).
- 변경은 Entity 메서드와 변경 감지(dirty checking)로 반영한다. `save`를 다시 부르지 않는다.
- 자기 자신 삭제는 DB 조회 전에 거절한다. `updated_by`·`deleted_by`에는 Gateway가 넣은 `X-Username`이 남는다(`HeaderAuditorAware`).

### 확인 질문
- `updateProfile`에서 null 대신 `Optional`을 받으면 무엇이 좋아지고 무엇이 불편해지나?
- 논리 삭제한 사용자의 기존 토큰은 언제 막히나?

## U-07 권한·배송담당자 지정 (2026-10-15)
### 처리 과정
- domain: `User.changeRole`, `assignDeliveryManager(hubId, hubName)`(소속을 HUB·허브명으로), `releaseDeliveryManager()`(hub_id만 지움).
- infrastructure: `HubClient`(`@FeignClient(name = "hub-service", path = "/internal", configuration = IdempotentRetryConfig.class)`), `HubClientAdapter`(application의 `HubReader` 구현), `client/dto/HubResponse(hubId, name)`.
- application: `UserRoleService.changeRole`(트랜잭션 없음: 자기 role 변경 거절 → 허브 조회) → `UserService.applyRoleChange`(트랜잭션: 사용자 조회와 변경, 비담당자에게 hubId만 보낸 경우 거절).
- presentation: `PATCH /api/v1/users/{userId}/role`, `ChangeRoleRequest`의 `@AssertTrue`로 요청만 보고 판단할 수 있는 조합을 검사한다.
- 테스트: `UserTest` 5개(배송담당자 소속 고정 2개 포함), `UserServiceTest` 4개, `UserRoleServiceTest` 5개, `UserControllerWebMvcTest` 3개, `UserCommandIntegrationTest` 2개(HubReader 목).

### 핵심
- 원격 호출(I-02)은 DB 트랜잭션 밖에서 한다. 트랜잭션 안에서 부르면 응답을 기다리는 동안 DB 커넥션을 붙잡아 커넥션 풀이 바닥날 수 있다. 같은 클래스 안에서 `@Transactional` 메서드를 부르면 프록시를 거치지 않으므로 클래스를 둘로 나눴다.
- application은 `HubReader` 인터페이스만 안다. 테스트에서 목으로 바꿔 hub-service 없이 검증하고, 호출 방식을 바꿔도 application은 그대로다.
- 검증을 세 곳에 나눴다. 요청만 보면 되는 조합은 Request(`@AssertTrue`), 현재 사용자(MASTER) 정보만 필요한 자기 role 변경은 원격 호출 전 `UserRoleService`, 대상 사용자 상태가 필요한 조합은 `UserService`.
- hub가 404 `HUB_NOT_FOUND`로 응답하면 `FeignErrorDecoder`가 같은 코드의 `BusinessException`으로 바꿔 그대로 클라이언트에 전달된다. 응답이 없으면 503이다.
- 권한이 바뀐 사용자의 기존 토큰은 Gateway 권한 재확인 캐시(60초)가 지나면 401 `TOKEN_ROLE_MISMATCH`가 된다. 확인 결과 변경 직후에는 200이었다.

### 확인 질문
- `UserRoleService`에 `@Transactional`을 붙이면 무엇이 달라지나?
- 허브 조회는 성공했는데 DB 변경 직전에 그 허브가 삭제되면 어떻게 되나? 막아야 하나?

### 메모
- 진행 방식: 사용자 요청으로 I-14 presentation부터 U-07, 샘플 정리까지 계층마다 멈추지 않고 한 번에 구현했다.
- hub-service가 I-02를 아직 구현하지 않아, 허브를 지정하면 hub의 기본 404 `NOT_FOUND`가 전달된다(Gateway 경유 확인). hub 구현 후 `HUB_NOT_FOUND`와 실제 허브명 저장을 다시 확인한다.

## 공통 인프라 샘플 API 삭제 (2026-10-15)
- `presentation/sample/AuthSampleController`를 지웠다. `script/e2e-check.sh`의 `/sample/me`는 U-03 `/api/v1/users/me`로, `/sample/admin`은 MASTER 전용인 U-05 `/api/v1/users`로 바꿨다. `docs/guides/local-run.md` 예시도 바꿨다.
- `script/e2e-check.sh`, `docs/guides/local-run.md`는 공용 파일이라 PR에서 팀 전체 리뷰를 받는다.
- 이 PC에는 Python이 없어 `script/run.sh --check`를 끝까지 돌리지 못했다(TS-006). 같은 항목을 curl로 직접 확인했다.
