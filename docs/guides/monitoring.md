# 메트릭 모니터링 (Prometheus + Grafana)

서비스가 받은 요청 수, 오류, 응답 시간, JVM·DB 커넥션 상태를 그래프로 본다. 요청 하나의 흐름은 Zipkin(ADR 0017), 여러 요청을 모은 수치는 이 문서의 도구로 본다. 결정과 대안은 [ADR 0018](../decisions/0018-metrics-prometheus-grafana.md)에 있다.

## 1. 열기

`script/run.sh`로 띄우면 함께 뜬다. 출력 끝에 주소가 나온다.

| 화면 | 개발 스택 | UI 테스트 스택(`script/ui-test.sh`) | 쓰는 것 |
|---|---|---|---|
| Grafana | http://localhost:3000 | http://127.0.0.1:13000 | 대시보드 "스파르타 물류 서비스 개요"가 첫 화면이다. 보기는 로그인 없이 된다 |
| Prometheus | http://localhost:9090/targets | http://127.0.0.1:19090/targets | 수집 대상이 모두 `UP`인지, PromQL을 직접 실행해 본다 |

Grafana 편집은 admin 계정과 `.env`의 `GRAFANA_ADMIN_PASSWORD`로 한다. 다만 대시보드는 `infra/grafana/dashboards/*.json`이 정본이라 화면에서 고친 내용은 다시 시작하면 사라진다. 바꾸려면 화면에서 고친 뒤 JSON으로 내보내 파일을 교체한다.

## 2. 어떻게 모이나

```text
각 서비스 ──(Micrometer가 지표를 모아 둔다)──▶ /actuator/prometheus
                                                    ▲ 15초마다 가져감(pull)
Eureka ──(인스턴스 목록)──▶ Prometheus ─────────────┘
                              │
                              ▼
                           Grafana ──(trace_id)──▶ Zipkin
```

1. 각 서비스의 Micrometer가 HTTP 요청, Feign 호출, JVM, HikariCP 지표를 메모리에 모은다. `micrometer-registry-prometheus`가 이를 `/actuator/prometheus`에 텍스트로 내놓는다.
2. Prometheus가 Eureka에 등록된 인스턴스 목록을 받아 15초마다 각 주소를 호출해 가져간다. 서비스가 수집 서버 주소를 알 필요가 없다. `--scale`로 인스턴스를 늘리면 수집 대상도 저절로 늘어난다.
3. Grafana가 Prometheus에 PromQL로 물어 그래프를 그린다.

| 서비스 | 수집 주소 | 이유 |
|---|---|---|
| user·hub·company·order·delivery | `IP:서비스 포트/actuator/prometheus` | 서비스 포트를 호스트에 공개하지 않는다 |
| gateway | `IP:8090/actuator/prometheus` | 8080은 외부에 공개된 포트라 관리 포트를 분리했다. 8080의 같은 경로는 401이다 |

## 3. 대시보드 읽기

| 패널 | 보는 것 | 이럴 때 의심한다 |
|---|---|---|
| 서비스 상태 | Prometheus가 인스턴스를 수집할 수 있는지 | DOWN이면 컨테이너가 멈췄거나 Eureka 등록 전이다 |
| 초당 외부 요청, 5xx 비율 | Gateway 기준 전체 트래픽과 서버 오류 | 5xx가 늘면 아래 라우트별 그래프에서 서비스를 찾는다 |
| 주문 생성 응답 | `POST /api/v1/orders`의 상태 코드별 누적 수 | 404가 있으면 배송 생성이 실패해 주문이 남지 않은 경우다(롤백 시나리오) |
| Gateway 라우트별 요청·4xx·5xx | 외부 요청이 어느 서비스로 갔고 몇 건이 거절됐는지 | 403이 많으면 권한 검사, 404면 없는 자원 요청이다 |
| 서비스별 p95 응답 시간 | 요청 95%가 끝난 시간 | 점(exemplar)을 누르면 그 요청의 trace가 Zipkin에서 열린다 |
| Feign 호출 수·p95 | 어느 서비스가 어느 클라이언트로 몇 번, 얼마나 걸려 불렀는지 | 주문 생성이 느리면 order → CompanyClient·DeliveryCreateClient 쪽을 본다 |
| 내부 API가 받은 호출 | `/internal/**`을 받은 쪽의 누적 호출 수와 상태 | 배송담당자가 없어 주문이 실패하면 delivery `POST /internal/deliveries` 404가 남는다. 허브 간 주문은 hub `GET /internal/hub-routes`가 함께 늘어난다 |
| 엔드포인트별 요청·상태 | 외부 API별 누적 호출 수와 상태 코드 | 테스트 시나리오의 기대 상태 코드와 맞춰 본다 |
| JVM 힙, CPU, DB 커넥션 풀 | 자원 사용량 | 커넥션 대기가 계속 0보다 크면 풀이 모자라다 |

- 수집 주기가 15초라서 방금 보낸 요청은 15~30초 뒤에 그래프에 보인다.
- 표와 "주문 생성 응답"은 서비스가 시작된 뒤의 누적값이다. 서비스를 다시 시작하면 0부터 센다. `increase()`는 새로 생긴 시계열의 첫 증가를 세지 못해, 요청이 적은 로컬 테스트에서는 누적값이 더 정확하다.
- Gateway가 받은 요청은 `http_server_requests`에서 경로가 `UNKNOWN`으로 나온다. 라우트로 넘기는 요청이라 컨트롤러 경로가 없기 때문이다. 그래서 Gateway는 라우트별 지표(`spring_cloud_gateway_requests_seconds`)로 본다.

## 4. 직접 PromQL 해 보기

Prometheus 화면(Graph 탭)이나 Grafana Explore에서 실행한다.

```promql
# 서비스별 초당 요청 수(1분 평균)
sum by (application) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))

# Gateway 라우트별 4xx·5xx
sum by (routeId, outcome) (rate(spring_cloud_gateway_requests_seconds_count{outcome=~"CLIENT_ERROR|SERVER_ERROR"}[1m]))

# 서비스별 p95 응답 시간(초). bucket을 합친 뒤 계산해야 인스턴스가 여러 개여도 맞다
histogram_quantile(0.95, sum by (application, le) (rate(http_server_requests_seconds_bucket[5m])))

# 주문 생성 결과(상태 코드별 누적)
sum by (status) (http_server_requests_seconds_count{application="order-service", method="POST", uri="/api/v1/orders"})
```

| 메트릭 | 만드는 곳 | 주요 라벨 |
|---|---|---|
| `http_server_requests_seconds_*` | 서비스가 받은 HTTP 요청 | `application`, `method`, `uri`(경로 템플릿), `status`, `outcome` |
| `spring_cloud_gateway_requests_seconds_*` | Gateway 라우트 | `routeId`, `httpStatusCode`, `outcome` |
| `feign_client_requests_seconds_*` | Feign 호출(호출하는 쪽) | `application`, `clientName`, `http_url`, `http_status_code` |
| `http_client_requests_seconds_*` | 그 밖의 HTTP 클라이언트(Eureka, Gateway 프록시) | `client_name` 등 |
| `hikaricp_connections_*`, `jvm_*`, `process_cpu_usage` | 커넥션 풀, JVM | `application` |

Feign 메트릭만 이름이 다른 이유: Prometheus는 같은 이름이면 라벨 키도 같아야 한다. Eureka 클라이언트와 Feign이 같은 `http.client.requests`를 다른 라벨 키로 만들어서, 이름을 나누지 않으면 Feign 메트릭이 등록을 거부당한다(`common`의 `FeignMeterFilter`).

## 5. 바꿀 때

| 바꾼 것 | 반영 |
|---|---|
| `infra/prometheus/prometheus.yml` | `docker compose restart prometheus` |
| `infra/grafana/**` | `docker compose restart grafana` |
| 노출 범위·histogram 설정(`config-repo/application.yml`) | `docker compose up -d --build config-server` 후 서비스 재시작 |

새 서비스를 추가하면 Eureka에 등록되는 순간 수집 대상이 된다. 설정을 고치지 않는다.

## 6. 직접 해보기
1. `script/run.sh`로 띄우고 http://localhost:9090/targets 에서 `logistics-services` 6개가 `UP`인지 본다.
2. http://localhost:3000 을 연다. "서비스 상태"가 6칸 모두 UP이면 성공이다.
3. Swagger나 운영 UI에서 주문을 하나 만들고 30초 기다린다. "주문 생성 응답"에 `201`이, "Feign 호출 수"에 `order-service → CompanyClient`가 생긴다.
4. "서비스별 p95 응답 시간"의 점 하나를 눌러 `trace_id` 링크로 Zipkin trace를 연다.
5. 공개 포트에서 `curl -i http://localhost:8080/actuator/prometheus`가 401인지 확인한다. 메트릭은 관리 포트에서만 나온다.
