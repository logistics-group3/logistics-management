# common-core 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/common/README.md`](../docs/services/common/README.md)에 있다.

## 책임과 경계
- 맡는 것: 웹 스택과 무관한 계약(응답·오류 형식, 에러 코드, 역할, 사용자 헤더 이름). gateway(WebFlux)와 common(MVC)이 함께 쓴다
- 맡지 않는 것: Spring MVC·WebFlux·JPA에 의존하는 코드

## 작업 전에 읽을 것
- [`docs/services/common/README.md`](../docs/services/common/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 6.1(응답·에러 형식, 공통 에러 코드)
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)

## 모듈 규칙
- 의존성은 `spring-web`과 `jackson-annotations`만 둔다(ADR 0006). MVC·WebFlux·JPA 의존성을 넣지 않는다.
- 응답 형식은 `ErrorResponseContractTest`가 고정한다. 형식을 바꾸면 SA 6.1과 gateway·서비스 테스트를 함께 확인한다.
- 여러 서비스가 같은 의미로 쓰는 에러 코드만 `CommonErrorCode`에 둔다. 추가하면 `docs/error-codes.md`를 고친다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :common-core:test`
- `common-core`를 바꿨으면 `./gradlew build`로 gateway와 서비스 전체를 확인한다.
