# 장애 격리 실험: user-service가 멈추면 배송 조회까지 멈추는가

user-service 하나가 응답하지 않을 때 delivery-service 전체가 같이 멈추는지 확인한다. 그다음 타임아웃, 벌크헤드, 서킷 브레이커를 하나씩 넣으면서 같은 조건으로 다시 재서 비교한다. 병목 분석은 [학습 노트](../../docs/learning/haneol/qna/2026-10-15-delivery-high-traffic-bottlenecks.md) 1번, 부하 테스트 공통 절차는 [load-test.md](load-test.md)에 있다. 이 문서는 load-test.md 실험 계획 3번을 자세히 풀어 쓴 것이다. 다른 실험 후보는 [성능·안정성 실험 지도](../../docs/learning/haneol/performance-scenarios.md)에 있다.

> 상태: 시나리오 작성(2026-10-16). 측정 전. 아래 "예상" 수치는 코드와 설정으로 계산한 값이고 실측이 아니다.

## 1. 지금 구조 (코드 기준, 2026-10-16)

```
k6 ──▶ Gateway ──▶ delivery-service ──▶ user-service  I-14 허브별 배송담당자 (허브마다 1번)
        (권한 캐시 60s,   (Tomcat 스레드 200)  └─▶ user-service  I-01 수령인 Slack ID
         user 조회 3s)                        └─▶ hub-service   I-03 허브 경로 (허브가 다를 때만)
```

| 항목 | 값 | 위치 |
|---|---|---|
| Feign 타임아웃 (공통) | connect 5초, read 20초 | `config-repo/application.yml` `spring.cloud.openfeign.client.config.default` |
| Feign 재시도 | `Retryer.Default(100, 1000, 3)`, 최대 3번 시도 | `common/.../feign/IdempotentRetryConfig.java`. `UserClient`, `HubClient`에 붙어 있다 |
| delivery 전용 Feign 설정 | 없음 | `config-repo/delivery-service.yml` |
| 타임아웃 시 응답 | `RetryableException` → `GlobalExceptionHandler.handleFeign` → 503 `SERVICE_UNAVAILABLE` | `common/.../exception/GlobalExceptionHandler.java` |
| I-09 트랜잭션 | 원격 호출은 트랜잭션 밖에서 하고, 저장만 `saveAndFlush`로 짧게 한다 | `DeliveryCreationService` 클래스 주석 |
| D-01·D-02 조회 | user를 부르지 않는다 | `DeliveryQueryService` |
| order → delivery I-09 | read 20초 + `IdempotentRetryConfig` (3번 시도) | `order-service/.../client/DeliveryCreateClient.java` |
| Gateway → user 권한 확인 | 캐시 60초, 조회 타임아웃 3초 | `config-repo/gateway-service.yml`, `CachedUserLookup` |
| Gateway 응답 타임아웃 | 60초 | `config-repo/gateway-service.yml` |
| Eureka (로컬) | 갱신 5초, 만료 15초, 정리 주기 5초, 자기 보호 꺼짐 | `config-repo/application.yml`, `eureka-server` |

### 계산해 본 최악의 경우
- user-service가 응답하지 않으면 user 호출 1번이 read 20초 × 3번 + 대기 시간(backoff)으로 **약 60초** 묶인다. 연결은 되는데 응답이 오지 않는 경우다.
- I-09를 초당 20건 보내면 묶이는 스레드는 20 × 60 = 1,200개가 필요하다. Tomcat 스레드는 200개라서 **장애 후 약 10초면 스레드가 바닥난다.**
- 스레드가 바닥나면 user를 부르지 않는 D-01 조회도 처리할 스레드가 없어 줄을 선다. 느린 서비스 하나가 상관없는 API까지 멈추게 하는 **연쇄 장애**다.

### 먼저 직접 확인할 질문 (답은 코드에서)
실험 전에 아래 질문에 직접 답해 본다. 면접에서 그대로 나오는 질문이다.
1. I-09 한 건이 user를 몇 번, 어떤 순서로 부르나? → `DeliveryCreationService.create`, `prepareAssigner`
2. user 응답을 기다리는 동안 DB 커넥션을 쥐고 있나? 왜 그렇게 만들었나? → 같은 클래스의 주석, `saveAndFlush`
3. 타임아웃이 나면 어떤 예외가 어디서 503으로 바뀌나? → `GlobalExceptionHandler`, `FeignErrorDecoder`
4. `Retryer.Default(100, 1000, 3)`에서 세 숫자는 각각 무엇이고, 최악의 대기는 몇 초인가? → Feign `Retryer.Default` 소스
5. order가 I-09를 재시도하면 delivery에서는 무슨 일이 일어나나? 데이터가 중복되지 않는 이유는? → `create`의 `findByOrderId`와 `DataIntegrityViolationException` 처리
6. `UserClientAdapter.findUser`가 404만 "없음"으로 바꾸고 나머지는 다시 던지는 이유는? → 메서드 주석

## 2. 가설

| # | 가설 | 맞는지 판단할 지표 |
|---|---|---|
| H1 | user가 멈추면 I-09가 스레드를 쥔 채 묶인다. 약 10초 안에 Tomcat 스레드 200개가 차고, **user를 부르지 않는 D-01**의 클라이언트 p95가 기준선(수십 ms)에서 10초(클라이언트 타임아웃)까지 뛴다 | k6 `D-01 허브별` phase=fault p95·실패율, `tomcat_threads_busy_threads` |
| H2 | 막히는 자원은 DB 커넥션이 아니라 스레드다. I-09는 트랜잭션 밖에서 원격 호출을 하므로 Hikari 대기는 0으로 남는다 | `hikaricp_connections_pending` |
| H3 | order의 I-09 대기(20초)가 delivery 안쪽 대기(최대 60초)보다 짧다. 그래서 order가 포기하고 다시 보낸 요청이 delivery에서 겹쳐 돈다. O-01 1건당 delivery가 받는 I-09가 최대 3건이 된다 | 증폭 비율 (아래 PromQL 5) |
| H4 | 짧은 타임아웃, 벌크헤드, 서킷 브레이커를 넣으면 장애 중에도 D-01 p95가 기준선의 2배 안에 머문다. I-09는 1초 안에 503으로 빨리 실패하고, 복구 후 몇 초 안에 정상으로 돌아온다 | H1과 같은 지표 + 서킷 상태 |

## 3. 지표

### k6 (클라이언트 쪽, 기준 값)
- `http_req_duration{name:..., phase:...}`: 장애 전(before), 장애 중(fault), 복구 직후 30초(recovery), 그 뒤(after)로 나눈 p95·p99
- `http_req_failed{name:..., phase:...}`: 구간별 실패율. 클라이언트 타임아웃 10초를 넘은 요청도 실패로 센다
- `dropped_iterations`: 0보다 크면 VU가 모자라 정한 속도로 보내지 못한 것이다. 이 경우 결과를 비교에 쓰지 않는다

클라이언트 타임아웃을 10초로 두는 이유: 사용자는 10초 넘게 기다리지 않는다는 기준(SLO 같은 역할)이다. 그리고 VU가 60초씩 묶이지 않게 한다. p95가 10초에 붙어 있으면 "거의 모두 실패"로 읽는다.

> Note: 서버 쪽 `http_server_requests` 지연은 요청이 **스레드를 받은 뒤부터** 잰다. 스레드를 기다리며 줄 서 있던 시간은 들어가지 않는다. 그래서 연쇄 장애 중에도 서버 쪽 D-01 p95는 낮게 보일 수 있다. 사용자 체감은 k6 값으로 판단하고, 서버 지표는 원인을 찾는 데 쓴다.

### Prometheus (서버 쪽, 원인 확인)
Grafana Explore(http://localhost:3000/explore)에 붙여 넣는다. 대시보드는 파일에서 다시 만들어지므로 화면에서 고쳐도 저장되지 않는다. 그래프는 캡처해서 남긴다.

```promql
# 1. delivery Tomcat 바쁜 스레드 / 최대 (H1). 준비 4절의 mbeanregistry 설정이 있어야 나온다
tomcat_threads_busy_threads{application="delivery-service"}
tomcat_threads_config_max_threads{application="delivery-service"}

# 2. Hikari 커넥션 대기 (H2)
hikaricp_connections_pending{application="delivery-service"}
hikaricp_connections_active{application="delivery-service"}

# 3. delivery → user 호출 수와 p99
sum(rate(feign_client_requests_seconds_count{application="delivery-service", clientName=~".*UserClient.*"}[30s]))
histogram_quantile(0.99, sum by (le) (rate(feign_client_requests_seconds_bucket{application="delivery-service", clientName=~".*UserClient.*"}[30s])))

# 4. 서버 쪽 응답 시간 (참고용, 위 Note 참조)
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="delivery-service", uri=~"/api/v1/deliveries.*"}[30s])))

# 5. 재시도 증폭 비율 (H3): order가 받은 O-01 1건당 delivery가 받은 I-09 수
sum(rate(http_server_requests_seconds_count{application="delivery-service", method="POST", uri="/internal/deliveries"}[1m]))
  / sum(rate(http_server_requests_seconds_count{application="order-service", method="POST", uri="/api/v1/orders"}[1m]))

# 6. 개선 후: 서킷 상태(열림=1), 벌크헤드 남은 자리, 거절된 호출
resilience4j_circuitbreaker_state{application="delivery-service", state="open"}
resilience4j_bulkhead_available_concurrent_calls{application="delivery-service"}
sum by (name) (rate(resilience4j_circuitbreaker_not_permitted_calls_total{application="delivery-service"}[30s]))
```
라벨 이름(`clientName` 값, `uri`)은 Explore에서 메트릭 이름만 먼저 쳐서 확인한다. 위 값은 코드와 대시보드 쿼리를 보고 추정한 것이다.

복구 시간은 이렇게 정의한다: user를 되살린 시점부터 **I-09 성공률이 99% 이상, D-01 p95가 기준선 2배 이하로 돌아올 때까지** 걸린 초. Grafana 그래프에서 읽는다.

## 4. 실험 통제 (결과가 흔들리지 않게)

| 통제 | 방법 | 이유 |
|---|---|---|
| Gateway 권한 캐시 | 실험 중 TTL을 10분으로 늘린다 (아래 덧붙임 파일) | Gateway도 user를 부른다. 장애 중 캐시가 만료되면 모든 요청이 Gateway에서 3초 뒤 503으로 끝난다. 그러면 delivery를 격리했는지 볼 수 없다. Gateway 쪽 격리는 7절 S4에서 따로 본다 |
| 추적 비율 | `.env`에 `TRACING_SAMPLING_PROBABILITY=0.1` | 1.0이면 구간 전송 비용이 결과에 섞인다. 비율은 Gateway가 정하므로 전체에 한 번만 둔다 |
| Tomcat 스레드 지표 | delivery에 `SERVER_TOMCAT_MBEANREGISTRY_ENABLED=true` | 이 설정이 없으면 `tomcat_threads_*` 지표가 나오지 않는다 |
| 수집 주기 | 로컬에서만 `infra/prometheus/prometheus.yml`의 `scrape_interval`을 `5s`로 바꾼다. 커밋하지 않는다 | 장애가 30초인데 15초마다 수집하면 점이 2개뿐이다. 끝나면 `git restore infra/prometheus/prometheus.yml` |
| DB 상태 | 매번 `--reset` 후 시드만 넣는다. 30만 건 대량 데이터는 넣지 않는다 | 이 실험은 조회가 원래 빠른 상태에서 "장애 때문에 느려지는 것"을 봐야 한다. 쿼리가 느리면 원인이 섞인다 |
| 반복 | 같은 조건으로 3번 재고 중앙값을 쓴다 | 맥 한 대에서 k6와 서비스가 CPU를 나눠 쓰므로 편차가 크다 |
| 환경 기록 | 맥 모델, 메모리, Docker 할당 CPU·메모리를 결과에 적는다 | 절대 수치보다 전후 비율을 내세운다 |

### 알아 둘 함정: Eureka 만료
`docker compose pause`로 멈춘 user-service는 하트비트도 멈춘다. 로컬 설정(만료 15초, 정리 5초)에서는 **약 20초 뒤 Eureka 목록에서 빠진다.** delivery의 로드밸런서 캐시(Spring Cloud LoadBalancer 기본 35초)가 비워지면 그때부터는 "응답 없음(60초 대기)"이 아니라 "인스턴스 없음(바로 503)"으로 바뀐다. 장애를 30초로 두면 대부분 응답 없음 구간이지만, 그래프 끝부분이 갑자기 빨리 실패하면 이것 때문이다. Eureka 대시보드(http://localhost:8761)를 같이 본다. 운영 기본값(만료 90초, 자기 보호 켜짐)에서는 이렇게 빨리 빠지지 않으므로, 이 효과를 "격리됐다"로 해석하지 않는다.

## 5. 준비

### 5-1. 덧붙임 Compose 파일 `docker-compose.fault.yml`
```yaml
# 장애 격리 실험용 덧붙임 설정(delivery-service/docs/fault-isolation.md). 기본 실행에는 쓰지 않는다.
services:
  gateway-service:
    environment:
      # auth.user-cache-ttl. 환경 변수는 점을 _로, 대시는 빼고 대문자로 쓴다(Spring relaxed binding)
      AUTH_USERCACHETTL: 10m
  delivery-service:
    environment:
      SERVER_TOMCAT_MBEANREGISTRY_ENABLED: "true"
```

### 5-2. 기동
```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml
script/run.sh --reset
script/delivery-e2e-check.sh     # 시드 + 기능 확인. 모두 PASS인지 본다
docker compose restart prometheus   # scrape_interval 5s를 바꿨다면
```
확인할 것:
- Explore에서 `tomcat_threads_busy_threads{application="delivery-service"}`가 나온다.
- 장애를 넣지 않은 상태에서 짧게 한 번 돌려 `dropped_iterations`가 0이다.

### 5-3. k6 스크립트와 실행 스크립트
`load-test/k6/fault-isolation.js`(부록 A)와 `load-test/fault-isolation.sh`(부록 B)를 쓴다. 부록은 두 파일의 설명용 사본이다. 고칠 때는 파일을 기준으로 하고 부록도 맞춘다.
- 생성(초당 20건)과 조회(D-01 허브별, 초당 20건)를 **동시에** 4분간 보낸다.
- 시작 90초 뒤에 user-service를 30초 동안 멈춘다.
- 요청마다 phase 태그(before, fault, recovery, after)를 붙인다.

## 6. 시나리오: 개선 전 (Before)

### S0. 기준선 (장애 없음)
```bash
LABEL=s0-baseline FAULT_MODE=none load-test/fault-isolation.sh
```
- 기록: D-01 p95, I-09 p95, **delivery → user 호출 p99**(PromQL 3). 이 p99가 7절 A에서 타임아웃 값을 정하는 근거가 된다.

### S1. user-service 응답 없음 (H1, H2)
```bash
LABEL=s1-before FAULT_MODE=pause load-test/fault-isolation.sh
```
- 장애 구간 동안 Grafana에서 PromQL 1, 2, 3을 보고 캡처한다.
- 예상(계산): busy 스레드가 약 10초 만에 200에 닿는다. D-01 fault p95가 10초(클라이언트 타임아웃)에 붙고 실패율이 크게 오른다. Hikari pending은 0으로 남는다.
- 복구 후에도 이미 묶인 요청이 풀릴 때까지 recovery 구간이 느리게 남을 수 있다. 복구 시간을 기록한다.

### S2. 주문 경로 재시도 증폭 (H3)
```bash
LABEL=s2-before TARGET=order CREATE_RATE=5 FAULT_MODE=pause load-test/fault-isolation.sh
```
- 속도를 5건으로 낮춘다. 증폭은 비율만 보면 되고, 스레드 고갈이 섞이면 원인이 흐려진다.
- 증폭 비율(PromQL 5)을 기록한다. 예상: 평소 1.0, 장애 중 최대 약 3.0.
- 주의: 주문은 재고를 쓴다. 4분 × 초당 5건 = 1,200건이므로, 시드 재고가 모자라면 409가 섞인다. 시작 전에 재고를 확인한다.

### S3. (선택) user-service 완전히 죽음
```bash
LABEL=s3-before FAULT_MODE=stop load-test/fault-isolation.sh
```
- 응답 없음(pause)과 비교용이다. 연결 자체가 거부되면 대기 없이 빨리 실패하는지 본다. "느린 의존이 죽은 의존보다 위험하다"를 숫자로 보여 준다.

## 7. 개선 (직접 구현) — 한 번에 하나씩, 단계마다 S1을 다시 잰다

각 단계는 **결정할 것**과 **근거**를 결정 로그(`docs/domain/delivery-decision-log.md`)에 남긴다. 면접에서는 값 자체보다 "왜 그 값인가"를 묻는다.

### A. delivery 전용 Feign 타임아웃
- 어디: `config-repo/delivery-service.yml`의 `spring.cloud.openfeign.client.config.user-service`, `hub-service`. order가 `order-dm-user`에 1초·2초를 둔 예가 `config-repo/order-service.yml`에 있다.
- 결정할 것: connect와 read 값. **S0에서 잰 user 호출 p99에 여유를 둔 값**으로 정한다. 예: p99가 30ms면 read 1~2초.
- 예상: 호출 하나의 최악 대기가 60초에서 약 6초(2초 × 3번)로 준다. 그래도 초당 20건 × 6초 = 120개 스레드가 필요해서 **타임아웃만으로는 부족할 수 있다.** 이게 B, C로 가는 이유다. 숫자로 확인한다.

### B. 재시도 정책
- `IdempotentRetryConfig`는 common(팀 공용)이다. 바꾸지 말고 delivery 전용 설정 클래스를 만들어 `UserClient`·`HubClient`의 `configuration`만 바꾼다.
- 결정할 것: 응답이 없는 상대에게 재시도는 대기를 늘리기만 한다. 최대 2번으로 줄일지, 연결 실패에만 재시도할지, 서킷 브레이커가 열리면 재시도하지 않을지.
- 질문: Feign 재시도는 서킷 브레이커 안쪽에서 일어나나 바깥쪽에서 일어나나? 서킷 브레이커는 3번 시도를 실패 1번으로 셀까 3번으로 셀까?

### C. 벌크헤드 (동시 호출 수 제한)
- 의존성: `io.github.resilience4j:resilience4j-spring-boot3`와 `spring-boot-starter-aop`. Boot BOM에 없으므로 버전을 직접 정하고 확인한다. delivery `build.gradle`에만 넣는다.
- 어디: `UserClientAdapter`의 public 메서드(`findIdsByHub`, `findSlackId`, `findById`)에 `@Bulkhead(name = "user-service")`
  - 주의: 같은 클래스 안에서 부르는 메서드(private `findUser`)에는 AOP가 걸리지 않는다(자기 호출).
- 결정할 것: `maxConcurrentCalls`. Little의 법칙으로 근거를 만든다. **동시 호출 수 = 초당 호출 수 × 호출 시간**이다. 평소 초당 I-09 20건 × user 호출 2번 × 0.03초 ≈ 1.2개다. 그러면 10~20개면 평소에는 거의 차지 않는다. 장애 때는 이 숫자 넘게 스레드가 묶이지 않는다(200개 중 나머지는 조회가 쓴다). `maxWaitDuration`은 0으로 둬서 기다리지 않고 바로 거절한다.
- 거절 예외(`BulkheadFullException`)는 그대로 두면 500이 된다. 503 `SERVICE_UNAVAILABLE`로 바꾸는 방법(fallback 또는 delivery 예외 처리)을 정한다.

### D. 서킷 브레이커
- 어디: C와 같은 메서드에 `@CircuitBreaker(name = "user-service", fallbackMethod = ...)`
- 결정할 것 (출발점 예시, 근거와 함께 정한다):

| 설정 | 예시 | 생각할 것 |
|---|---|---|
| `slidingWindowSize` | 20 (COUNT_BASED) | 초당 호출 수가 적으면 창이 차는 데 오래 걸린다 |
| `minimumNumberOfCalls` | 10 | 너무 작으면 잠깐 실패로도 열린다 |
| `failureRateThreshold` | 50 | |
| `slowCallDurationThreshold` / `slowCallRateThreshold` | 1초 / 50 | 응답 없음은 실패보다 **느린 호출**로 먼저 잡힌다 |
| `waitDurationInOpenState` | 10초 | 짧을수록 빨리 회복을 시도한다. 길수록 상대에게 숨 쉴 틈을 준다 |
| `permittedNumberOfCallsInHalfOpenState` | 3 | |
| `ignoreExceptions` | 404 `USER_NOT_FOUND` 같은 BusinessException 4xx | 사용자가 없는 건 장애가 아니다. 실패로 세면 정상 상황에서 서킷이 열린다 |

- 질문: Resilience4j 기본 순서는 Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead(가장 안쪽)이다. 벌크헤드 거절도 서킷 브레이커 실패로 세는 게 맞나?

### E. 부분 저하 (graceful degradation)
- I-01 수령인 Slack ID: 코드가 이미 `recipientSlackId`가 null이어도 배송을 만들도록 되어 있다(`create`의 주석). user가 장애일 때 fallback으로 null을 돌려주면 **배송 생성은 성공하고 알림만 빠진다.**
- I-14 배송담당자: 대신할 값이 없다. 담당자 없이 배정할 수 없으므로 빨리 실패(503)한다. 지금의 `findUser` 주석대로 장애를 "없음"으로 삼키면 안 된다.
- 결정할 것: I-01에 부분 저하를 넣을지. 알림 누락을 받아들일지는 팀(order 담당 Slack 알림) 확인이 필요하다. 이 선택은 ADR감이다.

### F. 타임아웃 예산 (order 담당과 협의)
- order → delivery read(20초)가 delivery 안쪽 최악보다 길어야 재시도가 겹치지 않는다. A~D 뒤 delivery 안쪽 최악을 다시 계산하고, order 담당에게 `order-delivery-create`의 read 값을 제안한다.
- `config-repo/order-service.yml`은 order 담당 파일이다. 측정과 제안은 내가 하고, 바꾸는 건 협의한다.
- S2를 다시 재서 증폭 비율이 1.0 근처인지 본다.

### 단계별 측정 순서
S0 → S1(전) → A → S1 → A+B → S1 → A+B+C → S1 → A+B+C+D → S1, S2 → (E) → S1 → (F) → S2

## 8. 결과 기록

같은 PC, 같은 속도(CREATE_RATE, READ_RATE), 같은 장애 시간끼리만 비교한다. 값은 3번 잰 중앙값이다.

환경: (맥 모델 / 메모리 / Docker CPU·메모리 할당 / 날짜)

| 단계 | D-01 p95 before | D-01 p95 fault | D-01 실패율 fault | I-09 p95 fault | I-09 실패율 fault | 최대 busy 스레드 | Hikari pending 최대 | 복구 시간(초) | 증폭 비율(S2) | 메모 |
|---|---|---|---|---|---|---|---|---|---|---|
| S0 기준선 | | - | - | - | - | | | - | 1.0 | user p99 = |
| S1 개선 전 | | | | | | | | | | |
| + A 타임아웃 | | | | | | | | | | |
| + B 재시도 | | | | | | | | | | |
| + C 벌크헤드 | | | | | | | | | | |
| + D 서킷 | | | | | | | | | | |
| + E 부분 저하 | | | | | | | | | | |
| + F 예산 | | | | | | | | | | |

함께 남길 증거: k6 결과 JSON(`load-test/results/`, 저장소에 올리지 않는다), Grafana 캡처(busy 스레드, 서킷 상태), PR 링크.

### S4. (선택) Gateway도 user에 의존한다
통제를 풀고(TTL 60초) 장애를 90초로 넣으면, 캐시가 만료되는 순간부터 모든 API가 Gateway에서 3초 뒤 503으로 끝나는지 본다. 개선 방향은 만료된 캐시 값을 장애 동안 잠시 계속 쓰는 것(stale-if-error)이다. Gateway는 공통 영역이라 팀 리뷰 대상이다.

## 9. 정리할 때

### 이력서 문장 틀
- 동기 호출 MSA에서 user-service 응답 지연이 배송 서비스 전체로 번지는 연쇄 장애를 k6 장애 주입으로 재현. Tomcat 스레드 200개가 __초 만에 고갈되어 상관없는 조회 API의 p95가 __ms → __s로 늘어남을 확인
- 벌크헤드(동시 호출 __개, Little의 법칙으로 산정)와 서킷 브레이커를 적용. 같은 장애에서 조회 API p95 __s → __ms, 실패율 __% → __%, 생성 API는 __ms 안에 503으로 빠르게 실패, 복구 __초
- 호출 사슬의 타임아웃 예산 불일치로 생기는 재시도 증폭(주문 1건당 하위 호출 __배)을 측정하고 1.0배로 줄임

### 면접에서 나올 질문
- 왜 타임아웃만으로는 부족했나? → 7-A의 스레드 계산과 실측
- 벌크헤드 크기는 어떻게 정했나? → Little의 법칙, S0의 user p99
- 서킷 브레이커가 열렸을 때 사용자는 무엇을 받나? 배송 생성과 알림 중 무엇을 포기했나? → D, E의 결정
- 비동기(메시지 큐)로 바꾸면 이 문제가 사라지나? → 스레드는 묶이지 않지만 지연과 정합성 문제가 생긴다. 이번 범위는 동기 Feign(ADR 0013)이다

## 10. 한계
- 맥 한 대에서 k6와 모든 서비스가 같이 돈다. 절대 수치는 운영과 다르고, 전후 비율만 의미가 있다.
- `docker compose pause`는 "응답 없음"을 흉내 낸 것이다. 실제로는 "느리지만 응답은 오는" 경우가 더 흔하다. 이건 Toxiproxy 같은 지연 주입 도구로 따로 볼 수 있다.
- 로컬 Eureka 설정 때문에 약 20초 뒤 인스턴스가 빠진다(4절). 장애를 길게 넣으면 다른 장애 모양이 된다.
- Gateway 권한 캐시를 늘린 상태에서 잰 결과다(4절). Gateway까지 포함한 사용자 체감은 S4로 따로 본다.

---

## 부록 A. `load-test/k6/fault-isolation.js`

```js
// 장애 격리 실험(delivery-service/docs/fault-isolation.md). 생성과 조회를 동시에 보내고, 요청마다 장애 전후 구간(phase) 태그를 붙인다.
// 장애 자체는 load-test/fault-isolation.sh가 FAULT_AT(epoch 초)에 넣는다. 두 쪽이 같은 시각을 기준으로 구간을 나눈다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SEED, login, numberEnv, params, uuid } from './lib/delivery.js';

const TARGET = __ENV.TARGET || 'delivery';
const CREATE_RATE = numberEnv('CREATE_RATE', TARGET === 'order' ? 5 : 20);
const READ_RATE = numberEnv('READ_RATE', 20);
const DURATION = __ENV.DURATION || '4m';
const FAULT_AT = numberEnv('FAULT_AT', 0) * 1000;
const FAULT_MS = numberEnv('FAULT_SEC', 30) * 1000;
// 사용자가 기다리는 한계. 넘으면 실패로 센다. VU가 오래 묶이지 않게 하는 역할도 한다.
const CLIENT_TIMEOUT = __ENV.CLIENT_TIMEOUT || '10s';
const CREATE_NAME = TARGET === 'delivery' ? 'I-09 직접' : 'O-01';
const READ_NAME = 'D-01 허브별';
const PHASES = ['nofault', 'before', 'fault', 'recovery', 'after'];

function phase() {
  if (!FAULT_AT) return 'nofault';
  const t = Date.now();
  if (t < FAULT_AT - 60000) return 'warmup';
  if (t < FAULT_AT) return 'before';
  if (t < FAULT_AT + FAULT_MS) return 'fault';
  if (t < FAULT_AT + FAULT_MS + 30000) return 'recovery';
  return 'after';
}

// Note: k6 요약은 임계값이 걸린 하위 지표만 따로 보여 준다. 판정용이 아니라 구간별 줄을 요약에 내기 위한 항상 참인 임계값이다.
const thresholds = {};
for (const name of [CREATE_NAME, READ_NAME]) {
  for (const p of PHASES) {
    thresholds[`http_req_duration{name:${name},phase:${p}}`] = ['max>=0'];
    thresholds[`http_req_failed{name:${name},phase:${p}}`] = ['rate>=0'];
  }
}

function constantRate(exec, rate) {
  return {
    executor: 'constant-arrival-rate', exec, rate, timeUnit: '1s', duration: DURATION,
    preAllocatedVUs: 50, maxVUs: numberEnv('MAX_VUS', 400),
  };
}

export const options = {
  scenarios: {
    create: constantRate('create', CREATE_RATE),
    read: constantRate('read', READ_RATE),
  },
  thresholds,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return {
    creator: login(TARGET === 'delivery' ? 'dmaster' : 'dreceiver'),
    reader: login('dmaster'),
  };
}

function tagged(token, name, headers) {
  const p = params(token, name, headers);
  p.tags.phase = phase();
  p.timeout = CLIENT_TIMEOUT;
  return p;
}

export function create(data) {
  let res;
  if (TARGET === 'delivery') {
    res = http.post(`${BASE_URL}/api/v1/deliveries/load-test`, JSON.stringify({
      orderId: uuid(),
      supplierCompanyId: SEED.supplierCompany,
      receiverCompanyId: SEED.receiverCompany,
      departureHubId: SEED.seoulHub,
      destinationHubId: SEED.seoulHub,
      pickupAddress: '서울특별시 송파구 올림픽로 300',
      deliveryAddress: '서울특별시 강남구 테헤란로 152',
      recipientName: 'k6 수령인',
      receiverManagerId: SEED.receiverManager,
    }), tagged(data.creator, CREATE_NAME));
  } else {
    const key = `k6-fault-${__VU}-${__ITER}-${Date.now()}`;
    res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({
      supplierCompanyId: SEED.supplierCompany,
      receiverCompanyId: SEED.receiverCompany,
      productId: SEED.product,
      quantity: 1,
      requestNote: 'k6 fault',
    }), tagged(data.creator, CREATE_NAME, { 'Idempotency-Key': key }));
  }
  check(res, { [`${CREATE_NAME} 201`]: (r) => r.status === 201 });
}

export function read(data) {
  const res = http.get(`${BASE_URL}/api/v1/deliveries?departureHubId=${SEED.seoulHub}&sort=createdAt,desc&size=10`,
    tagged(data.reader, READ_NAME));
  check(res, { [`${READ_NAME} 200`]: (r) => r.status === 200 });
}
```

## 부록 B. `load-test/fault-isolation.sh`

```bash
#!/usr/bin/env bash
# 장애 격리 실험 한 번 실행(delivery-service/docs/fault-isolation.md).
# 사용: LABEL=s1-before FAULT_MODE=pause load-test/fault-isolation.sh
#   FAULT_MODE: pause(응답 없음, 기본) | stop(죽음) | none(기준선)
#   LEAD: 시작 후 장애까지 초(기본 90, 앞 60초가 before 구간). FAULT_SEC: 장애 시간(기본 30)
#   그 밖의 환경 변수(TARGET, CREATE_RATE, READ_RATE)는 k6로 그대로 넘어간다.
set -euo pipefail
cd "$(dirname "$0")/.."

LABEL=${LABEL:?LABEL이 필요하다. 예: s1-before}
FAULT_MODE=${FAULT_MODE:-pause}
LEAD=${LEAD:-90}
FAULT_SEC=${FAULT_SEC:-30}
FAULT_AT=$(( $(date +%s) + LEAD ))
OUT="load-test/results/fault-${LABEL}-$(date +%Y%m%d-%H%M%S).json"
mkdir -p load-test/results

if [ "$FAULT_MODE" = none ]; then K6_FAULT_AT=0; else K6_FAULT_AT=$FAULT_AT; fi
k6 run -e FAULT_AT="$K6_FAULT_AT" -e FAULT_SEC="$FAULT_SEC" --summary-export="$OUT" load-test/k6/fault-isolation.js &
K6_PID=$!

if [ "$FAULT_MODE" != none ]; then
  sleep $(( FAULT_AT - $(date +%s) ))
  echo "[$(date +%T)] user-service ${FAULT_MODE}"
  docker compose "$FAULT_MODE" user-service
  sleep "$FAULT_SEC"
  if [ "$FAULT_MODE" = pause ]; then docker compose unpause user-service; else docker compose start user-service; fi
  echo "[$(date +%T)] user-service 복구"
fi

wait "$K6_PID" || true
echo "결과: $OUT"
```
- `FAULT_MODE=none`이면 phase 태그가 `nofault` 하나다. 기준선은 그 줄을 읽는다.
- 장애 시각과 복구 시각이 터미널에 찍힌다. Grafana 그래프의 시간과 맞춰 본다.
