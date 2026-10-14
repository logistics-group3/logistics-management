# gateway-service 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다. 다른 서비스와의 약속(API 계약, 구현 현황, 요청 기록)은 [`docs/services/gateway/README.md`](../docs/services/gateway/README.md)에 있다.

## 책임과 경계
- 맡는 것: 외부 요청 단일 진입점, JWT 검증, 권한 재확인(I-01, 60초 캐시), `X-User-*` 헤더 정리·주입, 라우팅, `/internal/**` 차단, Swagger 통합
- 맡지 않는 것: 비즈니스 인가(각 서비스의 `@RequireRole`, 소유자 검증)

## 작업 전에 읽을 것
- [`docs/services/gateway/README.md`](../docs/services/gateway/README.md): 계약, 구현 현황, 처리할 `OPEN` 요청
- SA(`docs/SA/sparta-logistics-sa.md`): 3.2(라우팅), 8.1~8.3(인증 흐름, JWT, 필터 순서)
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)

## 모듈 규칙
- WebFlux 기반이라 `common`을 쓰지 않고 `common-core`만 쓴다. MVC·JPA 의존성을 넣지 않는다.
- 인증은 Spring Security WebFlux로 한다(ADR 0005). 필터 순서(헤더 제거 → `/internal` 차단 → 화이트리스트 → JWT 검증 → 재확인 → 헤더 주입)를 바꾸면 `GatewaySecurityTest`를 함께 고친다.
- 라우팅과 화이트리스트는 `config-repo/gateway-service.yml`에 둔다. 새 서비스 경로를 추가하면 SA 3.2 표와 맞춘다.
- 오류도 SA 6.1 형식으로 응답한다(`GatewayErrorWebExceptionHandler`).

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :gateway-service:test`
- `script/run.sh --check`로 완료 기준 6개를 확인한다.
