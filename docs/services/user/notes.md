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
