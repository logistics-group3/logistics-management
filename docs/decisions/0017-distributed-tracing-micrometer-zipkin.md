# 0017. 분산 추적: Micrometer Tracing + Zipkin 도입

- 상태: 제안 (SA 설계대로 도입, 공통 영역 변경이라 팀원 전체 리뷰 대상)
- 날짜: 2026-10-16
- 결정자: Junhyun An 작업

## 맥락

- Swagger로 전체 시나리오를 테스트하면서 한 요청이 Gateway → 서비스 → 서비스 간 Feign 호출로 이어지는 흐름을 보고 싶다는 요청이 있었다.
- 기본 로그에는 요청을 묶을 표식이 없다. 여러 서비스 로그를 한 화면에 모아도 어떤 줄이 같은 요청인지 알 수 없다.
- SA는 분산 추적을 Micrometer Tracing + Zipkin으로 정해 두었다(1장 범위·기술 스택 "선택", 3.2 컨테이너 구성 `zipkin :9411`, 3.3 "로그에 traceId 포함").
- 먼저 코드를 바꾸지 않는 방법으로 OpenTelemetry Java 에이전트 + Jaeger를 로컬에 붙여 확인했다. 사용자가 이를 보고 "원래 도입하기로 한 도구를 도입"하기로 정했다(2026-10-16).

## 결정

- 모든 `*-service` 모듈(Gateway 포함)에 `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`를 넣는다. Feign을 쓰는 5개 서비스에는 `feign-micrometer`도 넣는다. 루트 `build.gradle`에서 한 번 선언하고 버전은 Boot·Cloud BOM에 맡긴다(Micrometer Tracing 1.5.12, Zipkin reporter 3.5.3, feign-micrometer 13.6.1, 2026-10-16 확인).
- 전파 형식은 Boot 기본값인 W3C `traceparent`다. Gateway가 외부 요청마다 trace를 시작하고, Boot `WebClient.Builder`(권한 재확인 I-01)와 Feign이 하위 호출에 헤더를 싣는다.
- `config-repo/application.yml`에 샘플링 비율 `${TRACING_SAMPLING_PROBABILITY:1.0}`, 수집 주소 `${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}`를 둔다. Compose는 `http://zipkin:9411/api/v2/spans`와 `.env`의 `TRACING_SAMPLING_PROBABILITY`(없으면 1.0)를 넣는다.
- 샘플링은 Gateway가 trace를 시작할 때 정하고 `traceparent`의 플래그로 뒤 서비스에 알린다. 비율 0에서도 traceId는 전달·로그 기록되고 Zipkin 전송만 빠진다(2026-10-16 확인).
- Gateway는 `spring.reactor.context-propagation: auto`로 WebFlux 스레드 전환 중에도 로그에 traceId를 남긴다.
- `docker-compose.yml`에 `openzipkin/zipkin:3.6.1`을 `127.0.0.1:${ZIPKIN_PORT:-9411}`로 추가한다. 서비스는 Zipkin이 시작된 뒤 뜬다(`depends_on: service_started`). UI 테스트 스택은 19411을 쓴다.
- 로그 형식은 바꾸지 않는다. 추적이 켜지면 Boot가 스레드 이름 뒤에 `[traceId-spanId]`를 붙인다(Boot 3.5 기본 `logging.pattern.correlation`, 2026-10-16 실행으로 확인).
- `script/api-test.sh`(Swagger 테스트 보조 도구)는 접근 로그·라우팅 로그·Slack 끄기만 맡고, 추적은 이 기본 기능을 그대로 쓴다.

## 검토한 대안

- OpenTelemetry Java 에이전트 + Jaeger: 코드·의존성 없이 `-javaagent`만으로 HTTP·JDBC까지 자동 계측돼 먼저 써 봤다. 하지만 SA 설계와 다르고, 에이전트 jar를 따로 받아 붙여야 해 팀원 실행 환경마다 준비가 필요하다. 실행 옵션에 숨어 있어 코드만 보고는 추적이 있는지 알기 어렵다.
- 접근 로그만 쓰기: 서비스별 요청 줄은 보이지만 같은 요청을 묶을 ID가 없다.
- 샘플링 기본값 0.1 유지: 로컬 테스트에서 10건 중 9건이 추적되지 않아 확인이 어렵다. 로컬 기본값을 1.0으로 두고 환경 변수로 낮춘다.
- Zipkin 저장소를 DB로 두기: 로컬 확인 용도라 메모리 저장으로 충분하다. 컨테이너를 다시 만들면 기록이 사라지는 것은 감수한다.

## 결과

- 얻는 것: 로그 줄의 traceId로 한 요청이 거친 서비스의 줄을 모으고, Zipkin에서 서비스별 구간과 소요 시간을 본다. 장애 시 어느 서비스 호출에서 늦어지거나 실패했는지 좁힐 수 있다.
- 감수하는 것: 모든 서비스에 의존성이 늘고 Zipkin 컨테이너가 하나 더 뜬다. 샘플링 1.0은 요청이 많은 환경에서 부담이 되므로 운영 배포 때 낮춘다.
- 범위 밖: SQL·JPA 구간(datasource 계측 라이브러리 필요), `@Async` Slack 발송 스레드로의 trace 전달, 운영용 Zipkin 저장소. 필요해지면 따로 결정한다.
- 테스트: Spring Boot는 테스트에서 추적을 기본으로 끈다. 기존 테스트는 추적 없이 그대로 돈다.
