---
paths:
  - "config/**"
  - "backend/**/application*.yml"
  - "backend/**/*Properties.java"
  - "docker-compose*.yml"
  - ".env.example"
---
# 설정 규칙

상세(보안·무중단 변경): `doc/guide/12-config-ops.md`, 파일 구조: `config/README.md`.

## 어디에 쓰나
| 설정 | 파일 |
|---|---|
| 모든 서비스 공통 (JPA, Feign, actuator, eureka 인스턴스) | `config/application.yml` |
| 환경별 공통 (DB 주소 규칙, eureka 주소, JWT) | `config/application-{local,docker}.yml` |
| 서비스 고유 (포트, DB 이름, 비즈니스 설정) | `config/{service}-service.yml` |
| 서비스 `src/main/resources/application.yml` | 이름·프로파일·config import **만**. 다른 설정 추가 금지 |

## 비밀값
- 평문 금지. `${ENV}`로만. docker용 파일에는 기본값 없이 (`${DB_PASSWORD}`), 로컬 기본값은 `application-local.yml` 또는 `local-*` 키에만
- 새 환경 변수 → `.env.example`(더미 값) + `docker-compose.yml` 해당 서비스 `environment`
- 로그에 비밀값 출력 금지

## 설정을 코드에서 받을 때
- `@Value` 대신 `@ConfigurationProperties(prefix = "{service}.xxx")` + **JavaBean(`@Getter @Setter` class)** — `config/` 저장 시 config-server가 자동으로 `/actuator/refresh`를 호출하는데, record·생성자 바인딩·일반 빈의 `@Value`는 바뀌지 않는다
- 위치 `infrastructure/config/{X}Properties.java`, Application에 `@ConfigurationPropertiesScan`
- 값으로 객체를 만들어 두는 빈(캐시, 클라이언트)은 refresh 대상이면 `@RefreshScope`
- 기동 후 바뀌면 안 되는 값(JWT 키, DB)은 생성자에서 읽어도 되지만, 바꾸려면 롤링 재시작임을 주석에 남긴다
- prefix: 서비스 고유는 `{service}.` (`order.saga.timeout`), 외부 연동은 연동명 (`slack.webhook-url`)

## 금지
- Gateway 라우트·`public-paths`에 다른 서비스의 `/actuator/**` 추가
- actuator `env`, `configprops`, `heapdump` 노출
- `ddl-auto`를 `update`/`create`로 변경 (DDL은 `doc/sql`이 원본)

## 문서
`config/*.yml`을 바꾸면 `doc/design/02-architecture.md` §1 설정 표 + CHANGELOG `[config]`. refresh로 반영되지 않는 키면 PR에 "재시작 필요".
