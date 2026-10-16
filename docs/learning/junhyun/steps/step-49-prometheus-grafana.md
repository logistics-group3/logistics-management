# 49. Prometheus + Grafana 메트릭 수집

- 날짜: 2026-10-16
- 브랜치: `feat/infra-monitoring-mentor-guide`
- 이전 작업 커밋: `9f64e3c docs(docs): Zipkin 화면 읽는 법 학습 노트 추가`
- 이번 커밋 제목: `feat(infra): Prometheus·Grafana 메트릭 수집 도입`, `fix(infra): Grafana 서비스 상태를 현재 수집 대상만 표시`
- 범위: 사용자 요청 "grafana랑 prometheus 달아주고". 발제·SA 범위 밖의 관측 기능이다. 공통 영역(`build.gradle`, `common`, `config-repo/application.yml`, `docker-compose.yml`)을 고쳐 PR에서 팀원 전체 리뷰를 요청한다. 결정은 [ADR 0018](../../../decisions/0018-metrics-prometheus-grafana.md).

## 바뀐 동작

- 모든 서비스가 `/actuator/prometheus`로 메트릭을 내보낸다. Gateway는 공개하지 않는 관리 포트 8090에서만 내보내고, 8080의 같은 경로는 401이다.
- Prometheus(127.0.0.1:9090)가 Eureka에서 인스턴스를 찾아 15초마다 수집한다. UI 테스트 스택은 19090이다.
- Grafana(127.0.0.1:3000, UI 테스트 13000)를 로그인 없이 열면 "스파르타 물류 서비스 개요" 대시보드가 첫 화면이다.
  - 서비스 상태, Gateway 라우트별 요청·4xx·5xx, p95 응답 시간(exemplar로 Zipkin 이동), Feign 호출, 내부 API·엔드포인트별 상태, JVM·CPU·HikariCP를 보여 준다.
- Feign 호출 메트릭 이름이 `feign_client_requests_seconds_*`가 된다.

## 순서와 계층별 역할

| 위치 | 바꾼 것 |
|---|---|
| 루트 `build.gradle` | `*-service`에 `micrometer-registry-prometheus`(runtimeOnly) |
| `config-repo/application.yml` | actuator `health,info,prometheus`, `application` 태그, 요청 시간 histogram |
| `config-repo/gateway-service.yml` | `management.server.port: 8090`, 화이트리스트에서 `/actuator/health` 제거 |
| `gateway-service` `SecurityConfig`, `ManagementPortMatcher` | 관리 포트로 온 `/actuator/**`만 토큰 검증에서 뺀다 |
| `common` `FeignMeterFilter`, `CommonFeignAutoConfiguration` | Feign 메트릭 이름 분리(Micrometer가 있을 때만) |
| `docker-compose.yml`, `infra/prometheus`, `infra/grafana` | prometheus v3.5.0(Eureka SD, exemplar 저장), grafana 12.1.1(데이터소스 Prometheus·Zipkin, 대시보드 JSON 프로비저닝) |
| `.env.example`, `script/run.sh`, `script/ui-test.sh` | 포트·Grafana admin 비밀번호, 주소 출력, UI 테스트 19090·13000 |

## 왜 이렇게 만들었는가

- **Gateway만 관리 포트를 나눈 이유**: 8080은 호스트에 공개된 유일한 포트다. 메트릭에는 경로·상태 코드 분포·JVM 정보가 있어 외부에 보이면 안 된다. MVC 서비스는 서비스 포트 자체를 공개하지 않아 같은 포트에 둬도 된다.
- **포트를 설정값이 아니라 기동 이벤트로 받는 이유**: 처음에는 `management.server.port` 값을 비교했다. 테스트에서 포트를 0(무작위)으로 두자 실제 포트와 달라 매칭이 안 됐다. 관리 서버(자식 컨텍스트)의 `WebServerInitializedEvent`가 부모 리스너에도 오므로, 실제로 뜬 포트를 받는다.
- **경로까지 묶은 이유**: 관리 포트로 보낸 `/api/v1/hubs`가 Gateway 라우팅을 탔다(테스트에서 503). 포트만으로 인증을 빼면 우회 경로가 생긴다. `/actuator/**`로 좁혀 관리 포트의 API 요청도 401로 막았다.
- **Security가 관리 서버에도 걸린다는 것**: 매처가 없을 때 관리 포트 `/actuator/prometheus`가 401이었다(테스트로 확인). WebFilter가 자식 컨텍스트의 서버에도 적용된다.
- **Feign 메트릭 이름을 바꾼 이유**: order·delivery 로그에 "Prometheus requires that all meters with the same name have the same set of tag keys" 경고가 나왔다. Eureka 클라이언트가 먼저 만든 `http.client.requests`(client_name, method, uri…)와 Feign 계측 라벨 키(clientName, http.method…)가 달라, Feign 메트릭이 통째로 등록되지 않았다. MeterFilter로 이름만 나눴다.
- **Eureka 서비스 디스커버리로 수집하는 이유**: 서비스 이름을 고정하면 `--scale`로 늘린 인스턴스를 놓친다. Spring Cloud가 Eureka 메타데이터에 `management.port`를 올려 주므로, Prometheus relabel 한 줄로 Gateway 관리 포트를 찾는다.
- **표를 누적값으로 둔 이유**: `increase()`는 새로 생긴 시계열의 첫 증가를 세지 못해 요청이 적은 로컬 테스트에서 방금 보낸 요청이 빠졌다.
- **서비스 상태를 instant 쿼리로 바꾼 이유**: 컨테이너를 다시 만들면 이전 인스턴스가 조회 기간의 마지막 값(DOWN)으로 남아 8칸 중 2칸이 DOWN으로 보였다.

## 검증 결과

- `./gradlew build`: 성공. 테스트 734개(새 테스트 5개 포함), 실패·오류·skip 0
  - 새 테스트: `GatewayManagementPortTest` 3개, `FeignMeterFilterTest` 2개
  - `GatewayManagementPortTest` 내용: 관리 포트 메트릭 200, 공개 포트 401, 관리 포트 API 401
- 개발 스택(Slack 꺼진 테스트 모드)에서 이미지 재빌드 후 확인:
  - Eureka GATEWAY-SERVICE 메타데이터 `management.port=8090`
  - Prometheus 수집 대상 6개 `up`(gateway는 `:8090`)
  - Gateway `8090/actuator/prometheus` 200, `8080/actuator/prometheus` 401, `8090/api/v1/hubs` 401
- 주문·조회 트래픽 뒤 확인:
  - 대시보드 쿼리 16개가 모두 결과를 돌려준다
  - Feign 시계열 6개(order → CompanyClient 6회 등)
  - exemplar `trace_id`가 붙는다
- Grafana: Prometheus·Zipkin 데이터소스 health OK, 홈 대시보드 패널 21개
- UI 테스트 스택(19090·13000)을 띄워 브라우저로 대시보드를 확인했다. 서비스 6개 UP, Feign 그래프·내부 API 표에 실제 값이 있다
- 문서 동기화 `script/docs-check.sh` OK
- 미확인: 알림 규칙(범위 밖), 여러 인스턴스(`--scale`)에서 수집 대상이 늘어나는지

## 직접 해보기

1. `script/run.sh`로 다시 빌드해 띄운다. 출력에 Prometheus·Grafana 주소가 나온다.
2. http://localhost:9090/targets 에서 `logistics-services` 6개가 `UP`인지, gateway 주소가 `:8090`인지 본다.
3. `curl -i http://localhost:8080/actuator/prometheus`가 401인지, `docker compose exec gateway-service curl -s localhost:8090/actuator/prometheus | head`가 메트릭을 내는지 본다.
4. http://localhost:3000 에서 주문을 하나 만든 뒤 30초 기다려 "주문 생성 응답"과 "Feign 호출 수"가 늘어나는지 본다.
5. `docker compose logs order-service | grep PrometheusMeterRegistry`가 비어 있는지 본다. 비어 있지 않으면 같은 이름·다른 라벨 키 메트릭이 생긴 것이다.

## 확인 질문

- Prometheus는 왜 pull 방식이고, 서비스가 늘어나면 어떻게 대상을 찾나(Eureka SD)
- 같은 메트릭 이름에 라벨 키가 다르면 Prometheus 레지스트리에서 어떤 일이 생기나
- p95를 인스턴스별 값의 평균으로 구하면 왜 틀리나(bucket 합산 후 histogram_quantile)
- 메트릭·로그·추적은 각각 무엇을 답하나(RED 지표, 요청 하나의 흐름)
- 관리 포트를 따로 둬도 인증 우회가 생길 수 있는 이유는 무엇인가
