---
title: 여러 서비스를 거치는 요청 흐름을 한 번에 보려면
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 중급
tags: [distributed-tracing, opentelemetry, jaeger, traceparent, mdc, msa, logging]
---

# 여러 서비스를 거치는 요청 흐름을 한 번에 보려면

## 질문
모든 서비스의 로그를 모아서, Gateway가 어떤 요청을 받아 어느 서비스로 보내고 user-service가 그걸 어떻게 받아 처리하는지 쭉 이어서 볼 수는 없나.

## 짧은 답
- 로그를 한 화면에 모으는 것만으로는 부족하다. 여러 요청의 줄이 섞여서 어떤 줄이 같은 요청인지 알 수 없다.
- **분산 추적**은 요청마다 **trace ID**를 하나 만들어 서비스 사이 호출 헤더로 계속 넘긴다. 그러면 모든 서비스의 로그와 처리 구간을 그 ID로 묶을 수 있다.
- 이 프로젝트에서는 코드를 바꾸지 않으려고 **OpenTelemetry Java 에이전트**를 실행 옵션으로 붙였다. 모은 기록은 **Jaeger** 화면에서 본다(`script/api-test.sh up`).
- SA가 정한 운영 방식은 Micrometer Tracing + Zipkin이다. 지금 것은 테스트 보조 도구다([ADR 0017](../../../decisions/0017-local-api-test-tracing.md)).

## 자세히

### 1. 왜 로그를 모으기만 해서는 안 되나
`docker compose logs -f`는 6개 서비스 로그를 도착한 순서대로 한 화면에 보여 준다. 두 사람이 동시에 요청하면 user-service 줄 두 개, company-service 줄 두 개가 섞인다. 시각이나 사용자 이름으로 짐작할 수는 있지만, 사용자 이름이 없는 내부 호출(Gateway의 권한 재확인 같은)은 어느 요청 것인지 알 수 없다. 요청마다 붙는 공통 표식이 필요하다.

### 2. trace와 span
| 용어 | 뜻 | 이 프로젝트 예 |
|---|---|---|
| trace | 외부 요청 하나가 시작해서 끝날 때까지 전체 | Swagger에서 보낸 `GET /api/v1/companies/{id}` 하나 |
| span | trace 안에서 한 서비스가 한 일 하나. 시작 시각과 걸린 시간을 가진다 | Gateway의 라우팅, user-service의 `/internal/users/{id}` 처리, SQL 한 번 |
| trace ID | trace를 가리키는 32자 16진수. 모든 span이 같은 값을 가진다 | `518fbde192d7e48ca483b18b2caccd10` |
| span ID | span 하나를 가리키는 16자 16진수. 자식 span은 부모 span ID를 기억한다 | |

span끼리 부모·자식 관계가 있어서 "Gateway 요청 → 그 안에서 user-service 호출 → 그 안에서 SQL"처럼 나무 모양이 된다.

### 3. 서비스 사이로 ID를 넘기는 방법: traceparent 헤더
W3C 표준 헤더 `traceparent`에 ID를 담아 HTTP 요청과 함께 보낸다.

```text
traceparent: 00-518fbde192d7e48ca483b18b2caccd10-c3fb9dda9edc640c-03
             버전  trace ID(32자)                  보낸 쪽 span ID  플래그
```

1. Gateway가 외부 요청을 받으면 새 trace ID를 만든다.
2. Gateway가 company-service로 요청을 넘길 때 `traceparent`를 붙인다.
3. company-service는 헤더를 읽어 같은 trace ID로 자기 span을 만든다. 이 서비스가 또 Feign으로 다른 서비스를 부르면 같은 방식으로 헤더를 이어 붙인다.

접근 로그 끝의 `traceparent=00-...`가 바로 이 헤더 값이다.

### 4. 로그 줄에 trace ID가 찍히는 원리: MDC
logback에는 MDC(Mapped Diagnostic Context)가 있다. 지금 처리 중인 스레드에 꼬리표(key-value)를 달아 두면, 그 스레드에서 찍는 모든 로그가 꼬리표를 꺼내 쓸 수 있다. 에이전트가 MDC에 `trace_id`를 넣는다. 그래서 로그 형식에 `%X{trace_id}`만 추가하면 모든 줄에 ID가 붙는다.

```yaml
LOGGING_PATTERN_LEVEL: "%5p [%X{trace_id:-no-trace}]"   # 레벨 뒤에 [trace ID], 없으면 no-trace
```

### 5. 코드를 안 바꾸고 붙이는 방법: Java 에이전트
`-javaagent:opentelemetry-javaagent.jar`를 JVM 옵션에 넣으면, 에이전트가 클래스를 불러오는 시점에 Spring MVC, WebFlux·Netty, HTTP 클라이언트, JDBC 같은 라이브러리 코드를 고쳐 끼운다(바이트코드 계측). 그래서 컨트롤러 처리, Feign 호출, SQL이 자동으로 span이 되고 `traceparent`도 자동으로 붙는다.

| 방식 | 장점 | 단점 |
|---|---|---|
| OpenTelemetry 에이전트(지금) | 코드·의존성 변경 없음. 실행 옵션만으로 켜고 끈다 | 기동이 느려지고 메모리를 더 쓴다. span 이름을 세밀하게 고르기 어렵다 |
| Micrometer Tracing + Zipkin(SA) | Spring Boot 기본 방식. 설정으로 범위를 조절하고 운영에 맞다 | 모든 서비스에 의존성·설정을 넣어야 한다 |

### 6. Jaeger에서 본 실제 흐름
SUP_USER가 `GET /api/v1/companies/{SUP_CO}`를 보냈을 때다(2026-10-16 확인).

```text
  0.0ms 110.6ms gateway-service  GET company-service                 요청 전체
  4.2ms   8.8ms gateway-service  GET                                 권한 재확인 호출
  6.8ms   5.7ms user-service     GET /internal/users/{userId}
 10.1ms   0.6ms user-service     SELECT user_schema.p_users
 18.6ms  92.5ms gateway-service  GET                                 company-service로 전달
 24.4ms  84.5ms company-service  GET /api/v1/companies/{companyId}
 43.6ms  60.0ms company-service  CompanyJpaRepository.findById
100.6ms   1.0ms company-service  SELECT company_schema.p_companies
```

읽는 법:
- Gateway는 JWT를 검증한 뒤 user-service에 사용자 권한을 다시 물어본다(SA 8.3, 60초 캐시). 그래서 company-service보다 user-service가 먼저 나온다.
- 전체 110ms 중 company-service가 84.5ms, 그 안에서 Repository 조회가 60ms다. 실제 SQL은 1ms라 나머지 시간은 SQL 밖(JPA 처리 등)에서 썼다. 원인은 이 한 번으로 단정할 수 없다. 같은 요청을 다시 보내 줄어드는지(첫 요청 준비 비용인지) 확인해 보면 범위를 좁힐 수 있다.

## 직접 해보기
1. `script/api-test.sh up` 후 `script/api-test.sh status`에서 "분산 추적: 켜짐"을 확인한다.
2. 다른 터미널에서 `script/api-test.sh flow`를 켠다.
3. Swagger에서 SUP_USER 토큰으로 `GET /api/v1/companies/{SUP_CO}`를 보낸다.
4. 로그 줄의 `[trace ID]`를 복사해 `script/api-test.sh trace <ID>`를 실행한다. user-service 권한 재확인 → Gateway 라우팅 → company-service 처리 순서로 줄이 모인다.
5. 출력 끝의 Jaeger 링크를 열어 위 표와 같은 막대 그래프를 확인한다.
6. 주문 생성(O-01)을 보내 보고 order → company(`/internal/companies`, `/internal/products`) → delivery(`/internal/deliveries`) → user(`/internal/users/delivery-managers`)로 이어지는 Saga 흐름을 같은 방법으로 따라간다.

## 관련 기록
- [Swagger 수동 테스트와 요청 흐름 보기](../../../guides/api-test.md)
- [Swagger로 전체 시나리오를 테스트할 때 서버 로그와 테스트 데이터를 어떻게 준비하나](2026-10-16-swagger-test-server-logs.md)
