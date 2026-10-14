# config-server 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다.

## 책임과 경계
- 맡는 것: `config-repo/`의 서비스 설정 제공(native)
- 맡지 않는 것: 설정 값 자체(각 서비스 담당자가 `config-repo/{service}.yml`을 고친다), 설정 자동 refresh(팀 결정으로 제외)

## 작업 전에 읽을 것
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)
- [`docs/guides/config-security.md`](../docs/guides/config-security.md): 설정 변경 반영 방법

## 모듈 규칙
- 비밀값은 `config-repo`에 쓰지 않는다. `${ENV:기본값}` 자리표시자로 받고 실제 값은 `.env`·환경 변수로 넣는다([설정 보안 가이드](../docs/guides/config-security.md)).
- 공통 설정은 `config-repo/application.yml`, 서비스 설정은 `config-repo/{service}.yml`에 둔다. 공통 설정 변경은 팀 전체 리뷰 대상이다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :config-server:test`
- `script/run.sh --check`로 전체를 띄워 확인한다.
