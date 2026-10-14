---
paths:
  - "config-repo/**"
  - "*/src/main/resources/application*.yml"
  - "**/*Properties.java"
  - "docker-compose*.yml"
  - ".env.example"
---
# 설정 규칙

적용 경로: `config-repo/**`, 모듈 `application*.yml`, `*Properties.java`, `docker-compose*.yml`, `.env.example`. 보안과 변경 반영은 [설정 보안 가이드](../../guides/config-security.md)를 본다.

## 어디에 쓰나
| 설정 | 파일 |
|---|---|
| 모든 서비스 공통(JPA, Feign 타임아웃, Eureka, actuator) | `config-repo/application.yml` |
| 서비스 고유(포트, DB, 서비스 설정) | `config-repo/{service}.yml` |
| 모듈 `src/main/resources/application.yml` | 이름과 Config Server 연결만. 다른 설정을 넣지 않는다 |
| 컨테이너 실행 값 | `docker-compose.yml`의 `environment`, 값은 `.env` |

## 비밀값
- 저장소에 평문으로 쓰지 않는다. `config-repo`에서는 `${ENV:기본값}` 자리표시자로 받고, 실제 값은 `.env`나 환경 변수로 넣는다.
- 환경마다 다른 값(호스트, 포트, 비밀값)은 고정값이 아니라 `${ENV:기본값}` 자리표시자로 둔다. Config Server 값은 환경 변수보다 우선순위가 낮아 환경 변수로도 덮을 수 있지만, 자리표시자로 두어야 바꿀 수 있는 값이 드러난다(`config-repo/application.yml` 첫 주석).
- 새 환경 변수는 `.env.example`(로컬 전용 더미 값)과 `docker-compose.yml`의 해당 서비스 `environment`에 함께 추가한다.
- 비밀값을 로그에 남기지 않는다.

## 코드에서 받을 때
- `@Value`를 흩어 쓰지 않고 `@ConfigurationProperties(prefix = "...")` record로 받는다(`JwtProperties`, `MasterAccountProperties`). 위치는 `infrastructure/...`이고 `@EnableConfigurationProperties`로 등록한다.
- 설정은 기동할 때 한 번 읽는다. 자동 refresh는 쓰지 않으므로(팀 결정) 값을 바꾸면 서비스를 다시 시작한다.
- prefix는 서비스 고유면 `{service}.`(`order.saga.timeout`), 외부 연동이면 연동 이름(`slack.webhook-url`)이다.

## 하지 않는 것
- Gateway 라우트나 화이트리스트에 다른 서비스의 `/actuator/**` 추가
- actuator `env`, `configprops`, `heapdump` 노출
- `ddl-auto`를 `update`·`create`로 변경(스키마는 Flyway가 만든다)
