# eureka-server 지침

이 모듈 코드를 고치는 사람과 AI 에이전트를 위한 지침이다. 팀 공통 규칙은 루트 [`AGENTS.md`](../AGENTS.md)가 먼저다.

## 책임과 경계
- 맡는 것: 서비스 등록·조회
- 맡지 않는 것: 라우팅(gateway), 로드밸런싱(각 클라이언트의 Spring Cloud LoadBalancer)

## 작업 전에 읽을 것
- 고치는 계층의 규칙: [`docs/agents/rules/`](../docs/agents/rules/README.md)

## 모듈 규칙
- 등록 갱신·만료·응답 캐시 값은 로컬 개발용으로 짧게 잡았다(`docs/learning/junhyun/steps/step-14-eureka-local-tuning.md`). 운영 배포를 하면 기본값으로 되돌릴지 다시 판단한다.
- 클라이언트 쪽 값(갱신·만료·목록 조회 주기)은 `config-repo/application.yml`에 있다.

## 다른 모듈이 필요할 때
- 다른 모듈의 코드는 직접 고치지 않는다. 그 서비스의 `docs/services/{service}/README.md` 협업 로그에 `OPEN`으로 요청을 남기고, 답이 필요한 작업이면 멈춘다.
- `common`, `common-core`, `config-repo/application.yml`, 루트 `build.gradle`, `docker-compose.yml` 변경은 모든 서비스에 영향이 가므로 팀 전체 리뷰를 받는다.

## 검증
- `./gradlew :eureka-server:test`
- `script/run.sh --check`로 전체를 띄워 확인한다.
