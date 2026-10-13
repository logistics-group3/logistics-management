# config/ — Spring Cloud Config 설정 저장소 (native)

config-server(:8888)가 이 폴더를 읽어 각 서비스에 설정을 내려줍니다.
서비스의 `src/main/resources/application.yml`에는 이름·프로파일·Config 주소만 있습니다. (eureka-server, config-server 자신은 제외)

## 프로파일

| 프로파일 | 언제 | 켜는 법 |
|---|---|---|
| `local` | IDE·`bootRun`으로 서비스를 PC에서 직접 실행 (DB·eureka·config-server만 compose) | 기본값 |
| `docker` | `docker compose`로 전체를 컨테이너로 실행 | compose가 `SPRING_PROFILES_ACTIVE=docker` 설정 |

## 파일과 우선순위 (아래로 갈수록 우선)

```
application.yml              모든 서비스 공통 (JPA, Feign 타임아웃, actuator, eureka 인스턴스, Swagger)
application-{profile}.yml    환경별 공통 (DB 주소 규칙, eureka 주소, JWT, 로그)
{service}.yml                서비스별 (포트, DB 이름·계정, 라우팅, Slack 등)
{service}-{profile}.yml      서비스 + 환경 (필요할 때만)
```

## 규칙

- 비밀값(DB 비밀번호, JWT, Slack URL, 관리자 비밀번호)은 `${ENV}`로만 둡니다. 값은 `.env` → compose 환경 변수. local에만 로컬 개발용 기본값이 있습니다.
- DB 주소는 `{service}.yml`에 `db.local-port`·`db.docker-host`만 적고, 실제 `db.host`·`db.port`는 `application-{profile}.yml`이 정합니다.
- **설정을 바꾸고 저장하면 끝입니다.** config-server가 2~4초 안에 변경을 감지해 해당 서비스(공통 파일이면 전체)에 `/actuator/refresh`를 호출합니다. 결과는 `docker compose logs -f config-server`
- 포트·DB·JWT 키처럼 refresh로 바뀌지 않는 키는 서비스 재시작이 필요합니다 → 목록: [`doc/guide/12-config-ops.md`](../doc/guide/12-config-ops.md) §3.2
- 서비스 고유 설정을 코드에서 받을 때는 JavaBean `@ConfigurationProperties`로 (record·`@Value`는 refresh 안 됨)
- 바꾼 내용은 `doc/design/02-architecture.md` §1과 `doc/etc/07-changelog.md`에 반영합니다.
