# 0017. Swagger 수동 테스트용 로컬 분산 추적: OpenTelemetry 에이전트와 Jaeger

- 상태: 제안 (사용자가 이 브랜치에 테스트 도구로 올리기로 함, 팀 채택은 PR 리뷰 대상)
- 날짜: 2026-10-16
- 결정자: Junhyun An 작업

## 맥락

- Swagger로 전체 시나리오를 수동 테스트하면서 한 요청이 Gateway → 서비스 → 서비스 간 Feign 호출로 이어지는 흐름을 보고 싶다는 요청이 있었다.
- 기본 로그에는 요청 기록이 없다. 공통 예외 처리기는 4xx `BusinessException`을 로그에 남기지 않는다.
- SA는 분산 추적을 고도화 단계의 선택 기능(Micrometer Tracing + Zipkin, 로그에 traceId 포함)으로 정했다(SA 1장 범위·기술 스택, 배포 구성). 이것을 구현하려면 모든 서비스의 의존성, `config-repo`, `docker-compose.yml`을 바꿔야 하고 팀 전체 리뷰 대상이다.
- 사용자는 SA 방식 구현 대신 지금 만든 도구를 테스트 보조 도구로 브랜치에 올리기로 했다(2026-10-16).

## 결정

- 제품 코드와 공통 설정은 바꾸지 않는다. `script/api-test.sh up`이 `docker compose -f docker-compose.yml -f script/api-test/compose.override.yml`로만 적용하고 `restore`로 되돌린다.
- OpenTelemetry Java 에이전트 2.31.1을 `-javaagent`로 붙인다. 에이전트가 trace를 만들고 W3C `traceparent` 헤더로 서비스 간 호출에 전달한다. 구간은 OTLP로 Jaeger 2.21.0(메모리 저장, `127.0.0.1:16686`)에 보낸다.
- 함께 켜는 것: Gateway(Netty)·서비스(Tomcat) 접근 로그, 로그 줄의 trace ID(에이전트가 넣는 MDC `trace_id`), Gateway 라우팅 DEBUG, 선택 SQL 로그.
- 테스트 중 order-service의 Slack Webhook·DM을 끈다.
- 에이전트 jar는 저장소에 넣지 않는다. Maven Central에서 받아 스크립트에 고정한 SHA-256으로 확인한다.
- SA의 Micrometer Tracing + Zipkin 도입 계획을 대체하지 않는다.

## 검토한 대안

- Micrometer Tracing + Zipkin(SA 방식): 운영 기능으로는 이쪽이 맞다. 다만 모든 서비스의 `build.gradle`·`config-repo`·`docker-compose.yml` 변경과 팀 전체 리뷰가 필요해 테스트 도구 범위를 넘는다. 고도화 단계에서 따로 진행한다.
- 접근 로그만 쓰기: 서비스별 줄은 보이지만 같은 요청의 줄을 묶을 ID가 없다.
- `docker-compose.override.yml` 자동 로드: 모든 `docker compose` 명령(`script/run.sh` 포함)에 적용돼 팀 기본 실행이 바뀐다.
- `LOGGING_LEVEL_*` 환경 변수로 로그 레벨 지정: Spring Boot가 로거 이름을 소문자로 바꿔 `org.hibernate.SQL`, `RoutePredicateHandlerMapping`처럼 대문자가 든 로거에 적용되지 않는다. `SPRING_APPLICATION_JSON`으로 이름을 그대로 넘긴다.

## 결과

- 얻는 것: 코드 변경 없이 한 요청의 서비스 간 경로, SQL, 구간별 소요 시간을 로그와 Jaeger에서 본다. `restore`로 원래 상태로 돌아간다.
- 감수하는 것: 에이전트 때문에 기동이 느려지고 메모리를 더 쓴다. Jaeger는 메모리 저장이라 컨테이너를 다시 만들면 기록이 없다. 구간 이름과 로그 형식은 에이전트 자동 계측을 따르므로 SA의 Micrometer 방식과 다르다.
- 후속: SA의 Micrometer Tracing + Zipkin을 구현하면 이 도구의 추적 부분을 그 설정으로 바꾸거나 없앤다.
