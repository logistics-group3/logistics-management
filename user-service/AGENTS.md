# user-service 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/user/README.md`](../docs/services/user/README.md)에 있다.

## 책임과 경계
- 맡는 것: 회원가입, 로그인(JWT 발급), 사용자 관리(U-01~U-08), 배송담당자 지정, 내부 사용자 조회(I-01, I-14), 초기 MASTER 생성
- 맡지 않는 것: 토큰 검증과 권한 재확인(gateway), 허브 존재 확인(hub의 I-02를 호출)

## 작업 전에 읽을 것
- [`docs/services/user/README.md`](../docs/services/user/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 4.2·5.2(user_schema), 6.4(API 상세), 8장(보안·권한)
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)

## 모듈 규칙
- 패키지 `com.sparta.logistics.user`, 스키마 `user_schema`, DB 계정 `user_app`. 테이블 변경은 `src/main/resources/db/migration/V{n}__*.sql`로 한다.
- username은 `^[a-z0-9]{4,10}$`이고 탈퇴 후에도 재사용할 수 없다. 중복 확인은 탈퇴자까지 보는 `existsByUsernameIncludingDeleted`를 쓴다.
- 비밀번호는 SA 8.5 규칙으로 검증하고 BCrypt로 저장한다. 로그인 실패는 이유와 관계없이 `LOGIN_FAILED` 하나로 응답한다(계정 존재를 노출하지 않는다).
- 회원가입은 항상 USER로 만든다. MASTER는 기동 시 `MasterAccountInitializer`나 `script/promote-master.sh`로만 만든다.
- 배송담당자로 지정하면 소속 허브(`hub_id`)가 반드시 있어야 한다(DB CHECK 제약).
- 내부 API는 DTO를 그대로 반환한다(ADR 0010). I-01 응답 필드를 바꾸면 gateway `UserSnapshot`과 호출하는 서비스에 협업 로그로 알린다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :user-service:test` (DB 통합 테스트는 Docker가 켜져 있어야 한다)
- API를 바꿨으면 `script/run.sh user-service`로 다시 띄워 Gateway(8080)로 호출해 본다.
- 에러 코드를 바꿨으면 `script/error-codes-check.sh`
