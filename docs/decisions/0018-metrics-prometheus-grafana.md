# 0018. 메트릭 수집: Prometheus + Grafana 도입

- 상태: 제안 (사용자 요청으로 도입, 공통 영역 변경이라 팀원 전체 리뷰 대상)
- 날짜: 2026-10-16
- 결정자: Junhyun An 작업

## 맥락

- 분산 추적(ADR 0017)으로 요청 하나의 흐름은 볼 수 있다. 하지만 "지금 어느 서비스가 느린가", "오류가 얼마나 나는가", "커넥션 풀이 모자란가"처럼 여러 요청을 모아 본 수치는 없다.
- 사용자가 Grafana·Prometheus 연동을 요청했다(2026-10-16). 발표·시나리오 테스트 때 시나리오를 실행하면서 요청 수와 상태 코드를 한 화면에서 확인하려는 목적도 있다.
- 발제는 Redis 캐싱, Zipkin과 함께 모니터링을 필수 범위로 두지 않는다. SA에도 메트릭 수집은 없다. 기본 기능을 바꾸지 않고 관측 수단만 더한다.
- 팀 보안 규칙 S4는 actuator를 `health`, `info`만 노출하도록 정해 두었다(`docs/guides/config-security.md`).

## 결정

- 모든 `*-service` 모듈(Gateway 포함)에 `micrometer-registry-prometheus`를 `runtimeOnly`로 넣는다. 루트 `build.gradle`에서 한 번 선언하고 버전은 Boot BOM에 맡긴다(1.15.12, 2026-10-16 확인).
- `config-repo/application.yml`에서 actuator 노출에 `prometheus`를 더한다. 모든 메트릭에 `application=${spring.application.name}` 태그를 붙인다.
- 서버·클라이언트 HTTP 요청 시간은 histogram bucket으로도 내보낸다(`http.server.requests`, `http.client.requests`, `feign.client.requests`). 여러 인스턴스를 합친 p95를 Prometheus에서 계산할 수 있다. bucket에는 traceId가 예시(exemplar)로 붙는다.
- Gateway의 actuator는 별도 관리 포트 `8090`(`management.server.port`, `GATEWAY_MANAGEMENT_PORT`)으로 옮긴다. 8080은 호스트에 공개된 유일한 포트라 메트릭이 외부에 보이지 않게 하기 위해서다.
  - Security 필터는 관리 서버에도 적용된다(2026-10-16 테스트로 확인).
  - `ManagementPortMatcher`가 관리 서버가 실제로 뜬 포트를 기동 이벤트에서 받는다. 그 포트로 온 `/actuator/**`만 토큰 검증에서 뺀다.
  - 관리 포트로 `/api/**`를 보내면 여전히 401이다. Gateway가 관리 포트 요청을 라우팅하는 경우에도 인증 우회 경로가 생기지 않는다.
  - 공개 포트의 `/actuator/prometheus`는 401이다. 쓰지 않게 된 화이트리스트 `/actuator/health`는 지웠다.
- MVC 서비스는 서비스 포트(8081~8085)를 호스트에 공개하지 않으므로 같은 포트에서 `/actuator/prometheus`를 연다.
- Feign 계측 메트릭은 `common`의 `FeignMeterFilter`가 `feign.client.requests`로 이름을 바꾼다.
  - 이유: Prometheus는 같은 이름의 메트릭이 모두 같은 라벨 키를 가져야 한다. Eureka 클라이언트의 `http.client.requests`(client_name, method, uri, status)와 Feign 라벨 키(clientName, http_method, http_url, http_status_code)가 다르다.
  - 이름이 같으면 Feign 메트릭이 등록을 거부당해 서비스 간 호출 지표가 통째로 빠진다(2026-10-16 order·delivery 로그로 확인).
- `docker-compose.yml`에 수집 서버와 화면을 추가한다.
  - `prom/prometheus:v3.5.0`을 `127.0.0.1:${PROMETHEUS_PORT:-9090}`로 연다.
  - `grafana/grafana:12.1.1`을 `127.0.0.1:${GRAFANA_PORT:-3000}`로 연다. UI 테스트 스택은 19090·13000을 쓴다.
- Prometheus는 수집 대상을 Eureka에서 받는다(`eureka_sd_configs`, `infra/prometheus/prometheus.yml`).
  - `--scale`로 인스턴스를 늘려도 설정을 고치지 않는다.
  - Gateway처럼 관리 포트를 따로 둔 서비스는 Spring Cloud가 Eureka 메타데이터에 올리는 `management.port`로 주소를 바꾼다.
- exemplar 저장(`--enable-feature=exemplar-storage`)을 켠다. Grafana 응답 시간 그래프의 점에서 Zipkin trace로 넘어간다.
- Grafana 데이터소스(Prometheus, Zipkin)와 대시보드 "스파르타 물류 서비스 개요"는 `infra/grafana`의 파일로 프로비저닝한다. 화면에서 고친 내용은 저장되지 않고 파일이 정본이다.
- 화면 접근 방식:
  - 보기 권한은 로그인 없이 연다(익명 Viewer). 확인하는 사람과 팀원이 바로 볼 수 있게 하기 위해서다.
  - 편집은 admin 계정과 `GRAFANA_ADMIN_PASSWORD`로 한다. 값이 없으면 `.env.example`과 같은 로컬 값을 쓴다(보안 규칙 S2).
  - 두 화면 모두 127.0.0.1에만 열린다.
- 메트릭은 컨테이너 안에만 저장하고 3일 보관한다. 컨테이너를 다시 만들면 지난 메트릭은 사라진다.

## 검토한 대안

- Gateway 메트릭을 8080 화이트리스트에 넣기: 설정은 가장 간단하다. 하지만 공개 포트에서 누구나 내부 지표(경로, 상태 코드 분포, JVM 상태)를 볼 수 있어 S5 원칙(외부 진입점은 Gateway API뿐)과 맞지 않는다.
- Prometheus `static_configs`로 서비스 이름 고정: 설정이 단순하다. 하지만 인스턴스를 늘려도 한 대만 수집되고, Eureka를 쓰는 구조와 따로 관리해야 한다.
- Docker 서비스 디스커버리(`docker_sd_configs`): Docker 소켓을 컨테이너에 넣어야 해서 로컬에서도 권한 범위가 너무 넓다.
- Feign 메트릭에 표준 라벨을 맞추는 사용자 정의 ObservationConvention: 하나의 메트릭으로 합쳐 보기 좋다. 하지만 코드가 길어지고 Spring Cloud OpenFeign 버전에 따라 빈 교체 방식이 달라진다. 이름 분리 한 줄로 충분하다.
- 서비스별 비즈니스 메트릭(주문 생성 수 Counter): 의미는 분명하다. 하지만 각 서비스 코드를 고쳐야 한다. 지금은 `POST /api/v1/orders` 상태 코드별 HTTP 메트릭으로 같은 정보를 본다.

## 결과

- 얻는 것:
  - 서비스별 요청 수·오류율·응답 시간(RED), Gateway 라우트별 요청, Feign 호출, JVM·CPU·HikariCP 커넥션을 한 대시보드에서 본다.
  - 테스트 시나리오를 실행하면 상태 코드가 기대대로 나왔는지 표로 확인할 수 있다.
  - 느린 요청은 exemplar로 Zipkin trace까지 이어진다.
- 감수하는 것:
  - 컨테이너 두 개가 더 뜬다(합계 약 250MB).
  - histogram bucket 때문에 수집 데이터가 늘어난다.
  - Gateway 헬스 체크 주소가 관리 포트 8090으로 바뀐다.
  - actuator 노출 범위가 늘어 보안 규칙 S4를 함께 고쳤다.
- 범위 밖: 알림 규칙(Alertmanager), 장기 저장소, eureka·config 서버 메트릭, 로그 수집(Loki). 필요해지면 따로 결정한다.
- 테스트: Spring Boot는 테스트에서 메트릭 내보내기를 끈다. Gateway 관리 포트 테스트만 `@AutoConfigureObservability`로 켠다.
