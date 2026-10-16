---
title: 여러 서비스를 거치는 요청 흐름을 한 번에 보려면
date: 2026-10-16
updated: 2026-10-16
asker: Junhyun An
level: 중급
tags: [distributed-tracing, micrometer-tracing, zipkin, traceparent, mdc, msa, logging]
---

# 여러 서비스를 거치는 요청 흐름을 한 번에 보려면

## 질문
모든 서비스의 로그를 모아서, Gateway가 어떤 요청을 받아 어느 서비스로 보내고 user-service가 그걸 어떻게 받아 처리하는지 쭉 이어서 볼 수는 없나. (후속: SA에 원래 정한 도구로 도입해 달라.)

## 짧은 답
- 로그를 한 화면에 모으는 것만으로는 부족하다. 여러 요청의 줄이 섞여서 어떤 줄이 같은 요청인지 알 수 없다.
- **분산 추적**은 요청마다 **traceId**를 하나 만들어 서비스 사이 호출 헤더로 계속 넘긴다. 그러면 모든 서비스의 로그와 처리 구간을 그 ID로 묶을 수 있다.
- SA가 정한 대로 **Micrometer Tracing + Zipkin**을 넣었다. 모든 서비스 로그 줄에 `[traceId-spanId]`가 붙고, 구간은 Zipkin(http://localhost:9411)에서 본다([ADR 0017](../../../decisions/0017-distributed-tracing-micrometer-zipkin.md)).
- 처음에는 코드를 안 바꾸려고 OpenTelemetry Java 에이전트 + Jaeger로 확인했다. 결과는 같지만 SA와 달라 바꿨다.

## 자세히

### 1. 왜 로그를 모으기만 해서는 안 되나
`docker compose logs -f`는 6개 서비스 로그를 도착한 순서대로 한 화면에 보여 준다. 두 사람이 동시에 요청하면 user-service 줄 두 개, company-service 줄 두 개가 섞인다. 시각이나 사용자 이름으로 짐작할 수는 있지만, 사용자 이름이 없는 내부 호출(Gateway의 권한 재확인 같은)은 어느 요청 것인지 알 수 없다. 요청마다 붙는 공통 표식이 필요하다.

### 2. trace와 span
| 용어 | 뜻 | 이 프로젝트 예 |
|---|---|---|
| trace | 외부 요청 하나가 시작해서 끝날 때까지 전체 | Swagger에서 보낸 `POST /api/v1/orders` 하나 |
| span | trace 안에서 한 서비스가 한 일 하나. 시작 시각과 걸린 시간을 가진다 | Gateway의 권한 재확인 호출, order-service의 Feign 호출, company-service의 처리 |
| traceId | trace를 가리키는 32자 16진수. 모든 span이 같은 값을 가진다 | `6ad1d13f5e02883e4417eff24bdc0fbd` |
| spanId | span 하나를 가리키는 16자 16진수. 자식 span은 부모 spanId를 기억한다 | |

span끼리 부모·자식 관계가 있어서 "Gateway 요청 → order-service 처리 → 그 안의 Feign 호출 → company-service 처리"처럼 나무 모양이 된다.

### 3. 서비스 사이로 ID를 넘기는 방법: traceparent 헤더
W3C 표준 헤더 `traceparent`에 ID를 담아 HTTP 요청과 함께 보낸다. Spring Boot 3의 기본 전파 형식이다.

```text
traceparent: 00-6ad1d13f5e02883e4417eff24bdc0fbd-4d8bf0a4c527ad3c-01
             버전  traceId(32자)                    보낸 쪽 spanId  샘플링 여부
```

1. Gateway가 외부 요청을 받으면 새 traceId를 만든다.
2. Gateway가 order-service로 요청을 넘길 때 `traceparent`를 붙인다. 권한 재확인(I-01)에 쓰는 WebClient도 같은 헤더를 붙인다.
3. order-service는 헤더를 읽어 같은 traceId로 자기 span을 만든다. Feign으로 company-service를 부를 때 헤더를 이어 붙인다.

마지막 `01`은 "이 요청을 기록한다"는 표시다. 샘플링 비율(`management.tracing.sampling.probability`)은 Gateway가 trace를 시작할 때 한 번 정하고, 뒤 서비스는 이 표시를 따른다. 그래서 한 요청의 구간이 중간에 끊기지 않는다.

Gateway만 비율 0으로 띄워 확인해 보니, 요청을 보내도 Zipkin에는 아무것도 쌓이지 않았다. 그런데 하위 서비스 접근 로그에는 `traceparent=00-<traceId>-<spanId>-00`이 그대로 찍혔다. 비율은 "Zipkin에 보낼지"만 정하고, traceId 전달과 로그 기록은 계속된다. 운영에서 비율을 낮춰도 로그로는 요청을 따라갈 수 있다는 뜻이다.

### 4. 무엇을 넣었나
| 위치 | 내용 | 이유 |
|---|---|---|
| 루트 `build.gradle` | 모든 `*-service`에 `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`. Feign 쓰는 5개 서비스에 `feign-micrometer` | Micrometer Tracing은 추적 API, Brave는 실제 구현, reporter는 Zipkin 전송, feign-micrometer는 Feign 호출 계측 |
| `config-repo/application.yml` | 샘플링 1.0, Zipkin 주소 | 로컬에서는 모든 요청을 본다. 환경 변수로 바꿀 수 있다 |
| `config-repo/gateway-service.yml` | `spring.reactor.context-propagation: auto` | WebFlux 로그 줄에도 traceId를 남긴다(5절) |
| `docker-compose.yml` | `zipkin` 컨테이너(127.0.0.1:9411) | 구간을 모아 화면으로 보여 준다 |

버전은 적지 않았다. Boot BOM과 Spring Cloud BOM이 서로 맞는 버전을 고른다(Micrometer Tracing 1.5.12, feign-micrometer 13.6.1 = feign-core와 같은 버전).

### 5. 로그 줄에 traceId가 찍히는 원리: MDC
logback에는 MDC(Mapped Diagnostic Context)가 있다. 지금 처리 중인 스레드에 꼬리표(key-value)를 달아 두면, 그 스레드에서 찍는 모든 로그가 꼬리표를 꺼내 쓸 수 있다. Micrometer Tracing이 요청을 처리하는 동안 MDC에 `traceId`, `spanId`를 넣고, Boot의 기본 로그 형식(`logging.pattern.correlation`)이 이를 `[traceId-spanId]`로 찍는다. 로그 형식을 따로 고치지 않았다.

WebFlux(Gateway)는 한 요청이 여러 스레드를 옮겨 다닌다. MDC는 스레드에 붙는 값(ThreadLocal)이라 스레드가 바뀌면 비어 버린다. `spring.reactor.context-propagation: auto`를 켜면 Reactor가 스레드를 바꿀 때 traceId도 옮겨 준다. 실제로 Gateway의 `Route matched` 줄에도 traceId가 찍힌다.

### 6. Zipkin에서 본 실제 흐름
RCV_USER가 없는 상품으로 주문했을 때다(2026-10-16 확인, 주요 구간).

```text
  0.0ms 674.2ms gateway-service  SERVER  http post /api/v1/orders
  2.2ms   8.9ms   gateway-service  CLIENT  http get http://user-service/internal/users/{RCV_USER}   권한 재확인
  4.7ms   5.5ms     user-service     SERVER  http get /internal/users/{userid}
 15.0ms 652.8ms   gateway-service  CLIENT  http post                                              order-service로 전달
 21.2ms 643.0ms     order-service    SERVER  http post /api/v1/orders
362.7ms 209.7ms       order-service    CLIENT  http get /companies/{companyId}                     공급업체(Feign)
551.6ms  18.2ms         company-service  SERVER  http get /internal/companies/{companyid}
578.8ms   5.6ms       order-service    CLIENT  http get /companies/{companyId}                     요청업체
585.1ms  60.3ms       order-service    CLIENT  http get /products/{productId}                      상품 → 404
586.3ms  59.1ms         company-service  SERVER  http get /internal/products/{productid}
```

읽는 법:
- Gateway는 JWT를 검증한 뒤 user-service에 사용자 권한을 다시 물어본다(SA 8.3, 60초 캐시). 그래서 order-service보다 user-service가 먼저 나온다.
- order-service는 업체 두 곳과 상품을 차례로 확인한다(O-01 검증 순서). 상품이 없어 404로 끝나고, 주문 기록을 만들기 전이라 남는 데이터가 없다.
- order-service가 받은 지 340ms 뒤에야 첫 Feign 호출이 나간다. 그사이 무엇을 했는지는 이 구간만으로 알 수 없다. 같은 traceId의 SQL 로그 줄을 보거나, 같은 요청을 다시 보내 줄어드는지(첫 요청 준비 비용인지) 확인하면 범위를 좁힐 수 있다.

### 7. 에이전트 방식과 비교
| 방식 | 장점 | 단점 |
|---|---|---|
| Micrometer Tracing + Zipkin(지금) | Spring Boot 기본 방식. 의존성·설정이 코드에 보이고 팀원 모두 같은 환경을 쓴다 | 모든 서비스에 의존성이 늘고, SQL 구간은 별도 라이브러리가 필요하다 |
| OpenTelemetry Java 에이전트 + Jaeger(처음 확인) | 코드 변경 없이 실행 옵션만으로 HTTP·JDBC까지 계측한다 | SA와 다르고, jar를 따로 받아 붙여야 한다. 코드만 봐서는 추적이 있는지 모른다 |

## 직접 해보기
1. `script/run.sh`로 띄운 뒤 http://localhost:9411 이 열리는지 본다.
2. `script/api-test.sh up` 후 다른 터미널에서 `script/api-test.sh flow`를 켠다.
3. Swagger에서 RCV_USER 토큰으로 없는 `productId`(`11111111-1111-1111-1111-111111111111`)를 넣어 `POST /api/v1/orders`를 보낸다. 404 `PRODUCT_NOT_FOUND`가 와야 한다.
4. 로그 줄 `[traceId-spanId]`의 앞 32자로 `script/api-test.sh trace <traceId>`를 실행한다. Gateway → user → order → company 순서로 줄이 모인다.
5. 출력 끝의 Zipkin 링크를 열어 6절과 같은 나무를 확인한다.
6. 정상 주문(O-01)도 보내 보고 delivery(`/internal/deliveries`) → user(`/internal/users/delivery-managers`)까지 이어지는 Saga 흐름을 같은 방법으로 따라간다.

## 관련 기록
- [Swagger 수동 테스트와 요청 흐름 보기](../../../guides/api-test.md)
- [Swagger로 전체 시나리오를 테스트할 때 서버 로그와 테스트 데이터를 어떻게 준비하나](2026-10-16-swagger-test-server-logs.md)
