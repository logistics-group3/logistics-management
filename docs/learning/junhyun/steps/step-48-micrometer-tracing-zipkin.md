# 48. Micrometer Tracing + Zipkin 분산 추적 도입

- 날짜: 2026-10-16
- 브랜치: `feat/infra-api-test-tracing`
- 이전 작업 커밋: `16084db docs(docs): Swagger 테스트·로컬 분산 추적 가이드와 결정 기록`
- 이번 커밋 제목: `feat(infra): Micrometer Tracing과 Zipkin 분산 추적 도입`, `chore(infra): API 테스트 도구의 추적을 기본 기능으로 전환`, `docs(docs): 분산 추적 결정 기록과 가이드를 Zipkin 기준으로 갱신`
- 범위: SA 3.3의 "분산 추적 (선택) Micrometer Tracing + Zipkin, 로그에 traceId 포함". 사용자가 "원래 도입하기로 한 도구를 도입"하라고 요청했다. 공통 영역(`build.gradle`, `config-repo/application.yml`, `docker-compose.yml`) 변경이라 PR에서 팀원 전체 리뷰를 요청한다.

## 바뀐 동작

- 모든 서비스(Gateway 포함) 로그 줄의 스레드 이름 뒤에 `[traceId-spanId]`가 붙는다. 한 요청이 거친 서비스는 같은 traceId를 가진다.
- Gateway가 외부 요청마다 trace를 시작하고, 하위 서비스 호출(Gateway 라우팅, 권한 재확인 WebClient, 서비스 간 Feign)에 W3C `traceparent` 헤더를 싣는다.
- 구간(span)이 Zipkin으로 간다. 화면은 http://localhost:9411 이다. 로컬 기본 샘플링은 1.0(모든 요청)이다.
- `script/run.sh`가 Zipkin 주소를 출력한다. UI 테스트 스택의 Zipkin은 19411이다.
- `script/api-test.sh`는 에이전트·Jaeger를 쓰지 않는다. 추적은 기본 기능을 쓰고, 도구는 접근 로그·라우팅 로그·Slack 끄기·traceId로 줄 모으기만 맡는다.

## 순서와 계층별 역할

| 위치 | 바꾼 것 |
|---|---|
| 루트 `build.gradle` | `*-service` 전체에 `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`. Gateway를 뺀 5개 서비스에 `feign-micrometer` |
| `config-repo/application.yml` | `management.tracing.sampling.probability: ${TRACING_SAMPLING_PROBABILITY:1.0}`, `management.zipkin.tracing.endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}` |
| `config-repo/gateway-service.yml` | `spring.reactor.context-propagation: auto` |
| `docker-compose.yml` | `zipkin`(openzipkin/zipkin:3.6.1, 127.0.0.1:`${ZIPKIN_PORT:-9411}`), 공통 환경 변수 `ZIPKIN_ENDPOINT`·`TRACING_SAMPLING_PROBABILITY`, 서비스 `depends_on`에 zipkin |
| `.env.example`, `script/ui-test.sh`, `script/run.sh` | `ZIPKIN_PORT`, UI 테스트 19411 기본값, Zipkin 주소 출력 |
| `script/api-test.sh`, `script/api-test/compose.override.yml` | 에이전트·Jaeger 제거, Zipkin 링크 |

## 왜 이렇게 만들었는가

- 의존성을 루트 `configure(subprojects.findAll { it.name.endsWith('-service') })`에 둔 이유: Gateway는 `common`을 쓰지 않으므로 `common`의 `api` 의존성으로는 6개 서비스에 모두 닿지 않는다. 기존 Flyway·QueryDSL 공통 설정과 같은 방식이다.
- `feign-micrometer`를 넣은 이유: Micrometer Tracing만 넣으면 Spring MVC 서버 쪽과 Boot 빌더로 만든 HTTP 클라이언트만 계측된다. Feign 호출에 `traceparent`가 실리려면 Spring Cloud OpenFeign이 feign-micrometer를 보고 계측을 붙여야 한다.
- 샘플링을 1.0으로 둔 이유: Boot 기본값 0.1이면 로컬 테스트 10건 중 9건이 추적되지 않는다. 운영처럼 요청이 많으면 `TRACING_SAMPLING_PROBABILITY`로 낮춘다.
- Gateway에 context-propagation을 켠 이유: WebFlux는 요청 처리 중 스레드가 바뀌어 MDC(ThreadLocal)가 비기 쉽다. 켜면 Gateway 로그에도 traceId가 남는다.
- UI 테스트 스택 포트: 같은 `docker-compose.yml`을 쓰므로 그대로 두면 두 스택의 Zipkin이 9411에서 충돌한다. 다른 포트와 같은 규칙으로 19411을 쓴다. ZIPKIN_PORT가 없는 기존 `.env.ui-test`도 19411이 되게 했다.

## 검증 결과

- `./gradlew build`: BUILD SUCCESSFUL. 테스트 XML 92개, 729개, 실패·오류·skip 0. 의존성이 바뀐 서비스 모듈의 테스트가 다시 실행됐다(common·common-core는 변경 없음으로 재사용)
- 의존성 해석: Micrometer Tracing 1.5.12, zipkin-reporter-brave 3.5.3, feign-micrometer 13.6.1(feign-core 13.6.1과 같음)
- 실제 실행: `docker compose build` 후 config-server·zipkin 재생성, `API_TEST_SQL=DEBUG script/api-test.sh up`으로 6개 서비스 재생성. Gateway 경유 5개 서비스 200, zipkin healthy
- 추적(데이터를 바꾸지 않는 요청 3개):
  - SUP_USER 업체 조회 200: Gateway → user `/internal/users/{id}`(WebClient) → company. 같은 traceId
  - MASTER 배송담당자 지정(없는 허브) 404 `HUB_NOT_FOUND`: user → hub `/internal/hubs/{id}`(Feign). 같은 traceId
  - RCV_USER 주문(없는 상품) 404 `PRODUCT_NOT_FOUND`: Gateway → user(권한 재확인) → order → company `/internal/companies` 2회·`/internal/products`(Feign). Zipkin에 부모·자식 구간 15개
- 로그 줄: user·order·company의 SQL 줄과 Gateway `Route matched` 줄에 같은 `[traceId-spanId]`
- Zipkin 서비스 목록: company·delivery·gateway·hub·order·user 6개
- 샘플링: Gateway만 `TRACING_SAMPLING_PROBABILITY=0`으로 다시 띄우고 업체 조회 3건 → Zipkin의 gateway trace 수 23 → 23(늘지 않음). company 접근 로그의 `traceparent`는 남고 끝 플래그가 `00`(기록 안 함)이었다. 확인 뒤 전체를 1.0으로 되돌렸다
- UI 테스트 스택: compose 설정에서 zipkin 공개 포트가 127.0.0.1:19411로 해석되는 것만 확인했다. 실제로 띄우지는 않았다
- 미확인: 정상 주문 생성(O-01)의 delivery·user까지 이어지는 trace, `@Async` Slack 발송 스레드로의 전달(범위 밖), 프런트 검증(API 계약 변경 없음)

## 직접 해보기

1. `script/run.sh`로 이미지를 다시 빌드해 띄운다. 출력에 `Zipkin http://localhost:9411`이 나온다.
2. 아무 API나 Gateway로 호출하고 `docker compose logs order-service`처럼 로그를 본다. 스레드 이름 뒤 `[traceId-spanId]`를 확인한다.
3. Zipkin에서 Run Query → 방금 요청을 연다. Gateway → 서비스 → Feign 호출 구간이 나무로 보인다.
4. `.env`에 `TRACING_SAMPLING_PROBABILITY=0`을 넣고 `docker compose up -d gateway-service`로 Gateway를 다시 만든다. 요청을 보내도 Zipkin에 새 trace가 없고, 접근 로그의 `traceparent` 끝이 `-00`인지 본다. 확인 뒤 값을 지우고 다시 만든다.

## 확인 질문

- traceId와 spanId는 어떻게 다르고, 서비스 사이에서는 무엇으로 전달되나(`traceparent`)
- Feign 호출에 trace가 이어지려면 무엇이 더 필요했나(feign-micrometer)
- 샘플링 결정은 누가 하고, 뒤 서비스는 어떻게 따르나(sampled 플래그)
- WebFlux에서 로그에 traceId가 빠지는 이유와 해결 방법은 무엇인가(MDC와 context-propagation)
- 에이전트 방식과 Micrometer 방식은 무엇이 다른가
