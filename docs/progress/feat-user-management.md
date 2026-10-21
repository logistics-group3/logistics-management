# feat/user-management

## 지금 하는 일
user-service 남은 API(U-03~U-08, I-14) 구현·검증·커밋 완료, PR 대기

## 진행 상태
- [x] user-service 구현 계획 확정(2026-10-15): read → I-14 → U-05 → U-06·U-08 → U-07 → 샘플 정리 순서
- [x] 보호 규칙 ADR 기록: `docs/services/user/decisions/0001-user-management-guards.md`
- [x] U-03·U-04: `UserResult`, `UserQueryService.getMyInfo`·`getUser`, `UserController`, `UserResponse`. Gateway 경유 200·403·404·401·400 확인
- [x] I-14: `UserRepository.findAllByHubIdAndDeliveryManagerTrue`, `DeliveryManagerResult`, `InternalUserController` `/delivery-managers`. 내부 호출 200 `[]` 확인
- [x] U-05: `UserSearchCondition`, `UserQueryRepositoryImpl`(QueryDSL), `GET /api/v1/users`. Gateway 경유 MASTER 200, USER 403, `sort=password` 400 확인
- [x] U-06·U-08: `User.updateProfile`, `UserService.update`·`delete`. Gateway 경유 수정 200(한글 이름), USER 403, 자기 삭제 400, 삭제 200 후 로그인 401 확인
- [x] U-07: `User.changeRole`·`assignDeliveryManager`·`releaseDeliveryManager`, `UserRoleService`(트랜잭션 밖 허브 조회), `UserService.applyRoleChange`, `HubClient`·`HubClientAdapter`. Gateway 경유 권한 변경 200, 자기 권한 변경 400, hubId 누락 400 확인. 허브 지정은 hub I-02 미구현이라 404 `NOT_FOUND`(hub 구현 후 재확인)
- [x] 샘플 API 삭제, `script/e2e-check.sh`·`docs/guides/local-run.md`를 실제 API로 교체
- [x] 테스트: `./gradlew :user-service:test` 80개 통과(Docker 기동, 통합 포함)
- [x] 문서: `docs/services/user/README.md`, `docs/services/user/notes.md`, `docs/troubleshooting.md` TS-006
- [ ] `script/run.sh --check` 자동 E2E: 이 PC에 Python이 없어 미실행(TS-006). 같은 항목을 curl로 직접 확인함
- [x] 커밋: API별 7개로 나눴다(2026-10-15). 중간 커밋마다 `:user-service:test` 통과를 확인했다
- [x] PR: https://github.com/logistics-group3/logistics-management/pull/2 (dev 대상, 2026-10-15). `script/e2e-check.sh`, `docs/guides/local-run.md`, `docs/troubleshooting.md`는 공용이라 팀 전체 리뷰
- 면접 포인트: 원격 호출을 트랜잭션 밖으로 뺀 이유와 self-invocation, 권한 검사와 조회 순서가 정보 노출에 주는 영향, QueryDSL 동적 조건과 정렬 허용 목록

## 다음 단계
1. 사용자 리뷰 의견을 반영한다.
2. PR 리뷰를 반영한다. 브랜치는 범위에 맞게 `feat/user-read`에서 `feat/user-management`로 바꿨다.
3. hub-service가 I-02를 구현하면 U-07 허브 지정을 Gateway 경유로 다시 확인한다(HUB_NOT_FOUND, 허브명 저장).

## 직접 해보기
1. `./gradlew :user-service:test`를 실행한다(Docker Desktop 실행 필요). 확인: 실패 0, `UserCommandIntegrationTest`·`UserQueryIntegrationTest`가 보인다.
   - IntelliJ: `user-service/src/test` 우클릭 → Run 'All Tests'.
2. `script/run.sh`로 전체를 띄우고 30초쯤 기다린다. 확인: http://localhost:8761 에 서비스 7개가 UP.
3. MASTER 토큰을 받는다.
   ```bash
   MT=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"master","password":"Master!2026"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
   ```
4. U-05 검색: `curl -s "localhost:8080/api/v1/users?size=10&sort=username,ASC" -H "Authorization: Bearer $MT"`. 확인: `data.content` 배열과 `totalElements`. `sort=password,ASC`로 바꾸면 400.
5. 일반 사용자를 하나 가입시키고(한글 값은 UTF-8 파일로 보낸다, TS-005) 그 userId로 U-06·U-07·U-08을 호출한다.
   ```bash
   curl -s -X PATCH localhost:8080/api/v1/users/{userId} -H "Authorization: Bearer $MT" -H 'Content-Type: application/json' -d '{"slackId":"U09"}'
   curl -s -X PATCH localhost:8080/api/v1/users/{userId}/role -H "Authorization: Bearer $MT" -H 'Content-Type: application/json' -d '{"isDeliveryManager":true}'   # 400, hubId 필요
   curl -s -X DELETE localhost:8080/api/v1/users/{userId} -H "Authorization: Bearer $MT"   # 200 "사용자가 삭제되었습니다."
   ```
6. I-14는 Gateway가 막으므로 도커 네트워크 안에서 호출한다: `docker compose exec gateway-service curl -s "http://user-service:8081/internal/users/delivery-managers?hubId={허브 UUID}"`. 확인: 배열 응답.

## 메모
- 진행 방식: U-03·U-04와 I-14 domain·application은 계층마다 멈추고 설명했고, 그 뒤는 사용자 요청으로 한 번에 구현했다(impl-service "한 번에 끝까지").
- `createdAt`이 마이크로초까지 나온다(SA 예시는 초 단위). common 범위라 팀 논의 대상이다.
- Git Bash `curl -d` 한글 400은 TS-005, Python 없는 Windows의 E2E 스크립트 실패는 TS-006.
- U-07 허브 지정 시 hub-service 응답 필드 중 `hubId`, `name`만 쓴다. hub가 다른 필드 이름으로 구현하면 `HubResponse`를 맞춘다.
