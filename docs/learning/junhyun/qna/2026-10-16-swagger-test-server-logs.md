---
title: Swagger로 전체 시나리오를 테스트할 때 서버 로그와 테스트 데이터를 어떻게 준비하나
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 중급
tags: [swagger, logging, access-log, docker-compose, test-data, gateway]
---

# Swagger로 전체 시나리오를 테스트할 때 서버 로그와 테스트 데이터를 어떻게 준비하나

## 질문
UI 대신 Swagger로 테스트하려 한다. 서버 로그를 켜 둔 채 준비된 데이터로 모든 경우의 요청을 보내려면 어떻게 세팅하나.

## 짧은 답
- 지금 서비스는 4xx 오류를 로그에 남기지 않는다. 요청마다 한 줄씩 남는 **접근 로그**를 켜야 무엇이 몇 번 실패했는지 보인다.
- 팀 설정(`docker-compose.yml`, `config-repo`, `.env`)은 고치지 않는다. `-f`로만 적용되는 **Compose 오버라이드 파일**로 환경 변수만 덧붙여 컨테이너를 다시 만든다.
- 주문을 많이 만들면 실제 Slack 채널에 알림이 쌓인다. 같은 오버라이드 파일에서 Webhook을 비워 둔다.
- 테스트 데이터는 DB에 직접 넣지 않고 **공개 API로 만드는 시드 스크립트**로 준비한다. 다시 실행해도 있는 데이터를 재사용하게 만든다.

## 자세히

### 1. 왜 기본 로그로는 부족한가
공통 `GlobalExceptionHandler`는 `BusinessException`(400·403·404·409)을 응답으로만 바꾸고 로그를 남기지 않는다. 5xx와 하위 서비스 호출 실패만 `ERROR`/`WARN`으로 찍힌다. 그래서 403이 났는지 409가 났는지는 로그만 보고 알 수 없다.
접근 로그(access log)는 웹 서버가 "어떤 요청이 들어와 어떤 상태 코드로 나갔는지"를 요청마다 한 줄씩 남기는 기록이다. 애플리케이션 코드를 바꾸지 않고 서버 설정만으로 켤 수 있다.

### 2. 서버마다 켜는 방법이 다르다
| 대상 | 웹 서버 | 켜는 방법 |
|---|---|---|
| Gateway | Netty(WebFlux) | JVM 옵션 `-Dreactor.netty.http.server.accessLogEnabled=true` |
| user·hub·company·order·delivery | Tomcat(MVC) | `server.tomcat.accesslog.*` 설정 |

Tomcat 접근 로그는 원래 파일(`logs/access_log.날짜.log`)에 쓴다. 컨테이너에서는 `docker compose logs`로 보는 편이 쉬우므로 출력 위치를 `/dev/stdout`으로 바꾼다.

```yaml
SERVER_TOMCAT_ACCESSLOG_ENABLED: "true"
SERVER_TOMCAT_ACCESSLOG_DIRECTORY: /dev
SERVER_TOMCAT_ACCESSLOG_PREFIX: stdout
SERVER_TOMCAT_ACCESSLOG_SUFFIX: ""
SERVER_TOMCAT_ACCESSLOG_FILEDATEFORMAT: ""
SERVER_TOMCAT_ACCESSLOG_PATTERN: "ACCESS %r -> %s %{ms}Tms user=%{X-Username}i"
```

Spring Boot는 `SERVER_TOMCAT_ACCESSLOG_ENABLED` 같은 환경 변수를 `server.tomcat.accesslog.enabled`로 읽는다(relaxed binding). `config-repo`에는 접근 로그 설정이 없으므로 환경 변수로 추가만 하면 되고, 팀 설정 파일을 고치지 않아도 된다(2026-10-16 실제 로그 출력으로 확인).
패턴의 `%{X-Username}i`는 요청 헤더 값이다. Gateway가 JWT를 검증한 뒤 넣는 헤더라서 누가 보낸 요청인지 바로 보인다. 서비스 사이 Feign 호출도 받는 쪽 접근 로그에 `/internal/...`로 찍힌다. 예를 들어 업체를 만들면 hub-service에 `GET /internal/hubs/{id}`가 남아 서비스 간 흐름을 따라갈 수 있다.

### 3. 팀 설정을 건드리지 않는 방법: Compose 오버라이드
`docker compose -f docker-compose.yml -f 다른파일.yml`처럼 파일을 여러 개 주면 뒤 파일의 값이 앞 파일 위에 합쳐진다. `environment`는 키 단위로 합쳐지므로 필요한 변수만 적으면 된다.
- 오버라이드 파일은 `script/api-test/compose.override.yml`에 둔다. `script/api-test.sh up`이 `-f`로 줄 때만 적용되므로 팀원의 평소 실행에는 영향이 없다. 시드 결과·토큰·로그 같은 실행 상태는 Git에서 제외된 `.local/api-test/`에 쌓인다.
- 기본 이름 `docker-compose.override.yml`은 쓰지 않는다. 이 이름은 `docker compose` 명령마다 자동으로 읽혀 `script/run.sh`에도 끼어든다. 일부러 `-f`로 줄 때만 적용되게 다른 이름을 쓴다.
- 되돌릴 때는 오버라이드 없이 `docker compose up -d --no-build <서비스>`를 실행한다. 설정이 달라진 컨테이너만 다시 만들어진다.

### 4. 테스트 데이터는 공개 API로 만든다
DB에 INSERT하면 빠르지만, 서비스가 하는 검증(허브 존재 확인, 업체 관리자 자동 연결)을 건너뛰어 실제와 다른 데이터가 생길 수 있다. API로 만들면 시드 자체가 S1·S2 정상 경로의 테스트가 된다.
- 다시 실행해도 안전하게 만든다. 가입이 409면 이미 있는 계정으로 보고 재사용하고, 업체·상품은 이름으로 찾아 없을 때만 만든다.
- MASTER는 `script/promote-master.sh`로 올린다. 이때 순서가 중요하다. Gateway는 사용자 권한을 60초 캐시하므로, USER 토큰으로 인증 API를 먼저 부르면 MASTER로 올린 뒤에도 최대 60초 동안 `TOKEN_ROLE_MISMATCH`가 난다. 그래서 role은 로그인 응답 JWT의 payload를 직접 읽어 확인하고, Gateway를 거치는 호출은 승격 뒤에 한다.

### 5. 로그 레벨 환경 변수의 함정
SQL을 보려고 `LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG`를 넣었는데 SQL이 찍히지 않았다. Spring Boot는 환경 변수 이름을 속성으로 바꿀 때 로거 이름을 소문자로 만든다(`logging.level.org.hibernate.sql`). logback 로거 이름은 대소문자를 구분하므로 `org.hibernate.SQL`에는 적용되지 않는다. 대문자가 든 로거는 `SPRING_APPLICATION_JSON`에 원래 이름으로 넣는다.

```yaml
SPRING_APPLICATION_JSON: '{"logging.level.org.hibernate.SQL":"DEBUG"}'
```

설정을 넣은 뒤에는 로그가 실제로 바뀌었는지 확인해야 한다. 이번에도 처음에는 확인 없이 안내했다가 나중에 발견했다.

## 직접 해보기
1. `script/api-test.sh up`으로 테스트 설정을 적용하고 `script/api-test.sh status`에서 "Slack Webhook: 꺼짐, 접근 로그: 켜짐"을 확인한다.
2. 다른 터미널에서 `script/api-test.sh logs`를 켠다.
3. 토큰 없이 `curl -i http://localhost:8080/api/v1/users/me`를 보낸다. 로그에 Gateway의 `"GET /api/v1/users/me HTTP/1.1" 401` 줄만 찍히고 user-service 줄은 없다. Gateway가 인증 단계에서 막았기 때문이다.
4. Swagger에서 MASTER 토큰으로 `GET /api/v1/users/me`를 보낸다. 이번에는 Gateway 줄과 user-service의 `ACCESS ... -> 200 ... user=jhmaster` 줄이 함께 찍힌다.
5. 테스트가 끝나면 `script/api-test.sh restore`로 되돌리고 `status`에서 Slack Webhook이 다시 켜졌는지 확인한다.

## 관련 기록
- [Swagger 수동 테스트와 요청 흐름 보기](../../../guides/api-test.md), [ADR 0017](../../../decisions/0017-distributed-tracing-micrometer-zipkin.md)
- [여러 서비스를 거치는 요청 흐름을 한 번에 보려면](2026-10-16-distributed-tracing-local.md)
- 시나리오: Notion 「기능 테스트」, 실행 기록: Notion 「junhyun 테스트 일지」
- [Slack Webhook을 배송담당자 이메일에 연결할 수 있는가](2026-10-15-slack-webhook-email-and-dm.md)
