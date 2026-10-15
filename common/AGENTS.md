# common 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/common/README.md`](../docs/services/common/README.md)에 있다.

## 책임과 경계
- 맡는 것: MVC 서비스 공통 기능(예외 처리, Audit, 현재 사용자·역할·소유자 검사, Feign 공통 처리, 페이지·정렬 규칙, 통합 테스트 기반)
- 맡지 않는 것: 도메인 로직, 특정 서비스만 쓰는 코드

## 작업 전에 읽을 것
- [`docs/services/common/README.md`](../docs/services/common/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 5.1(공통 규칙·Audit), 6.1(공통 규약), 8.3(서비스의 역할 검사)
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)

## 모듈 규칙
- 모든 서비스에 영향이 가므로 변경은 팀 전체 리뷰를 받고, [`docs/services/common/README.md`](../docs/services/common/README.md)의 제공 기능 표를 함께 고친다.
- 빈은 컴포넌트 스캔이 아니라 자동 설정으로 등록한다(ADR 0009). 새 공통 빈은 `autoconfigure` 패키지의 자동 설정에 `@Bean`으로 두고, `@ConditionalOn*` 조건과 `common.<묶음>.enabled` 끄기 속성을 붙인다. 클래스에 `@Component`를 붙이지 않는다.
- 새 자동 설정은 `META-INF/spring/...AutoConfiguration.imports`에 등록하고, 슬라이스 테스트에 필요하면 `AutoConfigureWebMvc.imports`·`AutoConfigureDataJpa.imports`에도 등록한다.
- 쓰이지 않는 공통 기능을 미리 만들지 않는다. 두 서비스 이상이 같은 코드를 필요로 할 때 옮긴다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew build` (모든 서비스가 영향을 받으므로 전체 빌드)
- 자동 설정을 바꿨으면 `CommonAutoConfigurationTest`와 user-service `CommonAutoConfigurationIntegrationTest`를 확인한다.
