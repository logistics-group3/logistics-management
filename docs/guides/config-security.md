# 설정 보안 가이드

비밀값을 다루는 규칙과 설정을 바꿨을 때 반영하는 방법을 정리한다. 원본은 haneol `doc/guide/12-config-ops.md`의 S1~S7이고, 이 저장소 구성(config-repo를 jar에 포함, 자동 refresh 없음)에 맞췄다. 코드 작성 규칙은 [설정 규칙](../agents/rules/config.md)에 있다.

## 1. 설정이 전달되는 경로
```
config-repo/ ──(config-server 빌드 때 jar에 포함)──▶ config-server :8888 (native, classpath:/config-repo)
                                                      │
                                                      ▼ 서비스가 기동할 때 한 번 조회 (fail-fast)
                                                 각 서비스
```
- `config-server/build.gradle`의 `processResources`가 루트 `config-repo/`를 jar 안 `classpath:/config-repo`로 넣는다. 로컬 실행과 컨테이너가 같은 설정을 읽는다.
- 서비스는 기동할 때만 설정을 받는다. Config Server에 접속하지 못하면 기본값으로 뜨지 않고 바로 실패한다(`spring.cloud.config.fail-fast: true`).
- `config-repo`의 `${ENV:기본값}` 자리표시자는 서비스 쪽 환경 변수로 풀린다. 환경마다 다른 값은 고정값이 아니라 자리표시자로 둔다.
- 우선순위: 환경 변수·JVM 시스템 속성 > Config Server 값 > 모듈 `application.yml`. Config Server 값은 `spring.config.import`로 들어오는 설정 데이터라 환경 변수보다 낮다. 2026-10-14에 Config Server가 `server.port: 8080`을 내려줄 때 `SERVER_PORT=18080`을 준 Gateway가 18080으로 뜨는 것을 확인했다.

## 2. 비밀값 목록
| 값 | 저장소에 적는 형태 | 실제 값 | 없을 때 |
|---|---|---|---|
| PostgreSQL 관리자 비밀번호 | compose `${POSTGRES_PASSWORD}` | `.env` | DB 컨테이너가 뜨지 않는다 |
| 서비스 DB 비밀번호 | `config-repo/{service}.yml`의 `${USER_DB_PASSWORD:user_local_pw}` 등 | `.env` → compose `environment` | 로컬 더미 값으로 접속한다. compose는 `:?`로 값이 없으면 실행을 거부한다 |
| JWT 서명 키 | `${JWT_SECRET}` (gateway, user) | `.env`의 `JWT_SECRET`(32바이트 이상) | 기본값을 두지 않는다. IDE로 띄울 때는 실행 설정에 환경 변수로 넣는다 |
| 최초 MASTER 계정 | `${MASTER_USERNAME:}`, `${MASTER_PASSWORD:}` (user) | `.env` | 비어 있으면 MASTER를 만들지 않는다 |
| Slack Webhook URL | `config-repo/order-service.yml`의 `${SLACK_WEBHOOK_URL:}` → `slack.webhook-url`, compose가 order에 전달 | `.env` | 알림을 생략한다. 주문 생성은 유지한다. URL과 HTTP 예외 메시지·응답 본문을 로그에 남기지 않는다 |
| Slack Bot Token | `${SLACK_BOT_TOKEN:}` → `slack.dm.bot-token`, compose가 order에만 전달 | `.env` 또는 격리 환경의 `.env.ui-test` | DM을 생략한다. 프런트·Git·로그에 넣지 않는다 |

`.env.example`의 포트 값(`POSTGRES_PORT`, `CONFIG_SERVER_PORT`, `EUREKA_PORT`, `GATEWAY_PORT`, `ZIPKIN_PORT`)과 분산 추적 설정(`TRACING_SAMPLING_PROBABILITY`)은 비밀값이 아니다. 기존 환경과 병행하거나 추적 비율을 바꿀 때 쓴다.

DM은 `SLACK_DM_ENABLED=false`가 기본이며 토큰과 `SLACK_DM_WORKSPACE_ID`를 함께 확인한다. `SLACK_DM_ALLOWED_EMAILS`는 선택 수신자 제한이고, UI 테스트에서는 활성화 시 필수다. 설정·회수 범위와 실행 절차는 [Slack DM 안내](slack-dm.md)에 있다.

## 3. 규칙
| # | 규칙 | 이유 |
|---|---|---|
| S1 | `config-repo`, `application*.yml`, compose에 비밀값을 평문으로 쓰지 않는다. `${ENV}` 자리표시자만 쓴다 | Git 이력은 지워지지 않는다 |
| S2 | 자리표시자 기본값은 `.env.example`과 같은 로컬 더미 값만 둔다. 서명 키처럼 기본값이 있으면 위험한 값은 기본값 없이 둔다 | 기본값이 다른 환경에 섞여 들어가는 것을 막는다 |
| S3 | `.env`는 커밋하지 않는다(`.gitignore`). 새 변수는 `.env.example`에 더미 값으로, `docker-compose.yml`의 해당 서비스 `environment`에 함께 추가한다 | 팀원이 필요한 변수를 알 수 있다 |
| S4 | actuator는 `health`, `info`만 노출한다(`config-repo/application.yml`). `env`, `configprops`, `heapdump`는 열지 않는다 | `/actuator/env`는 풀린 비밀값을 보여 줄 수 있다 |
| S5 | Gateway 라우트·화이트리스트에 다른 서비스의 `/actuator/**`를 넣지 않는다. 비즈니스 서비스 포트는 호스트에 공개하지 않고, 관리 포트(postgres, config-server, eureka)는 `127.0.0.1`에만 연다 | 외부 진입점은 Gateway 하나다 |
| S6 | `config-repo` 변경은 PR 리뷰를 거친다. PR 템플릿의 "설정 변경 시 다시 시작할 서비스" 항목에 적는다 | 누가 언제 무엇을 바꿨는지 PR로 추적한다. 별도 CHANGELOG는 두지 않는다(팀 결정) |
| S7 | 로그·예외 메시지·응답에 비밀값을 출력하지 않는다 | 로그는 공유되고 오래 남는다 |

비밀값을 실수로 커밋했다면 되돌리는 것으로 끝나지 않는다. 값을 바로 바꾸고 팀에 알린다.

## 4. 설정을 바꾸고 반영하기
서비스는 기동할 때만 설정을 읽는다. 그래서 바꾼 뒤에는 다시 시작해야 한다.

| 바꾼 것 | 반영 방법 |
|---|---|
| `config-repo/*.yml` (compose) | `docker compose up -d --build config-server` → `docker compose restart {서비스}` |
| `config-repo/*.yml` (IDE로 config-server 실행) | 실행 설정에 `CONFIG_REPO_LOCATION=file:/절대경로/config-repo`를 넣으면 config-server가 파일을 바로 읽는다. 서비스만 다시 시작한다 |
| `.env` | `docker compose up -d {서비스}` (컨테이너를 다시 만든다. `restart`로는 바뀌지 않는다) |
| `JWT_SECRET` | `.env` 수정 후 gateway-service와 user-service를 함께 다시 만든다. 기존 토큰은 모두 401이 되므로 다시 로그인한다 |
| DB 비밀번호 | 이미 있는 볼륨의 계정 비밀번호는 바뀌지 않는다. 로컬은 `script/run.sh --reset` |
| config-server, eureka-server 자신의 `application.yml` | 해당 모듈 이미지를 다시 빌드한다(`docker compose up -d --build {모듈}`) |

## 5. 자동 refresh를 쓰지 않는 이유
haneol 브랜치에는 재시작 없이 설정을 바꾸는 기능이 있었다. config-server가 설정 폴더를 주기적으로 확인하고, 파일이 바뀌면 Eureka에서 해당 서비스 인스턴스를 찾아 `POST /actuator/refresh`를 보낸다. 서비스는 `@ConfigurationProperties`(JavaBean)로 받은 값과 Gateway 라우트를 다시 읽는다.

팀은 공통 인프라 범위를 정할 때 "Config Server는 쓰되 자동 polling·refresh는 도입하지 않는다"고 정했다(Notion "공통 인프라 개발 범위", 통합 검토에서 제외 유지). 그래서 이 저장소에는 다음이 없다.
- `/actuator/refresh` 노출, `@RefreshScope`, Spring Cloud Bus
- refresh를 위해 `@ConfigurationProperties`를 JavaBean(setter)으로 만드는 규칙. 설정은 record로 받는다

refresh로도 바뀌지 않는 값이 많다는 점은 알아 둔다. 서명 키, 포트, DB 커넥션 풀, JPA 설정처럼 기동 때 객체를 만들어 두는 값은 refresh 기능이 있어도 재시작해야 한다. 나중에 도입을 다시 논의하면 haneol 문서의 "동적으로 바꿀 값과 재시작할 값" 구분이 출발점이 된다.

## 6. 설정 변경 PR 체크리스트
- [ ] 비밀값 평문이 없고, 자리표시자 기본값은 로컬 더미 값뿐이다(S1, S2).
- [ ] 새 환경 변수를 `.env.example`과 `docker-compose.yml`에 함께 추가했다(S3).
- [ ] actuator 노출 범위와 Gateway 라우트에 `/actuator/**`를 추가하지 않았다(S4, S5).
- [ ] PR 본문에 다시 시작할 서비스를 적었다(S6).
- [ ] 서비스 고유 설정은 `@ConfigurationProperties` record로 받는다([설정 규칙](../agents/rules/config.md)).
