---
title: k6와 Prometheus는 어떻게 동작하고 성능 측정에서 어떻게 함께 쓰나
date: 2026-10-20
updated: 2026-10-20
asker: minhyeok.kwon
level: 중급
tags: [k6, prometheus, micrometer, performance-test, load-test, observability]
---

# k6와 Prometheus는 어떻게 동작하고 성능 측정에서 어떻게 함께 쓰나

## 질문
k6와 Prometheus의 동작 방식을 알고 싶다.

## 짧은 답
- k6는 부하를 주는 쪽이다. JavaScript 시나리오를 Go 엔진이 실행하며 요청을 보내고, 요청 하나하나의 응답 시간을 직접 모아 백분위수(p95 등)를 계산한다. 사용자가 느끼는 결과를 바깥에서 잰다.
- Prometheus는 서비스 내부를 보는 쪽이다. 각 서비스가 `/actuator/prometheus`로 내놓은 누적 지표를 15초마다 가져가(pull) 시계열로 저장하고, PromQL로 구간 증가량·초당 비율·백분위수 근사값을 계산한다.
- 둘을 같은 시간 구간으로 맞추면 "응답이 몇 ms 걸렸나"(k6)와 "그 안에서 어느 서비스·구간이 몇 번, 몇 ms 걸렸나"(Prometheus)를 함께 볼 수 있다.

## 자세히

### 1. k6

**실행 구조**
- k6는 Go로 만든 부하 도구이고, 시나리오는 JavaScript로 쓴다. Node.js가 아니라 k6 안의 JS 엔진에서 돈다. 그래서 npm 패키지나 Node API는 쓸 수 없고 `k6/http` 같은 k6 모듈을 쓴다.
- VU(Virtual User)는 독립된 JS 실행 환경 하나다. VU끼리는 전역 변수를 공유하지 않는다.
- 생명 주기
  1. init: 파일 맨 위 코드. VU마다 한 번 실행된다. `open()`으로 파일을 읽는 일은 여기서만 된다.
  2. `setup()`: 테스트 전체에서 한 번. 로그인처럼 준비 작업을 하고, 돌려준 값이 모든 VU에 전달된다.
  3. 반복 함수(`default` 또는 시나리오의 `exec`): 부하 본체. 한 번 실행이 iteration 하나다.
  4. `teardown()`: 끝날 때 한 번.
  5. `handleSummary(data)`: 최종 집계를 받아 파일·표준 출력으로 내보낸다.

**부하 모델(executor)**
- 닫힌 모델(`constant-vus`): VU N명이 "요청 → 응답 대기 → 다음 요청"을 반복한다. 서버가 느려지면 요청 간격도 늘어 부하가 저절로 줄어든다. 그래서 느려진 순간의 지연이 결과에서 덜 보인다(coordinated omission).
- 열린 모델(`constant-arrival-rate`): 응답과 무관하게 초당 정해진 수만큼 iteration을 시작한다. 쉬는 VU가 없으면 `maxVUs`까지 늘리고, 그래도 모자라면 시작하지 못한 iteration을 `dropped_iterations`로 센다. 실제 서비스처럼 "사용자는 서버 사정을 기다려 주지 않는다"를 흉내 낸다.

**지표**
- 내장 지표: `http_req_duration`(Trend, 응답 시간), `http_req_failed`(Rate, 실패 비율), `iterations`(Counter), `dropped_iterations` 등.
- 사용자 정의 지표: `new Trend('이름')`처럼 만들어 직접 값을 넣는다.
- 태그와 하위 지표: 요청마다 `scenario`, `name` 같은 태그가 붙는다. `thresholds`에 `http_req_duration{scenario:main}`처럼 태그 조건을 적으면 그 조건만 따로 집계한 하위 지표가 생긴다.
- Trend는 샘플을 모두 들고 있다가 끝에 백분위수를 계산한다. 그래서 k6의 p95는 버킷 근사가 아니라 실제 샘플 기준이다.

### 2. Prometheus

**수집 구조(pull)**
1. 서비스는 Micrometer로 지표를 메모리에 누적하고, `/actuator/prometheus`에서 텍스트로 내놓는다. 예: `http_server_requests_seconds_count{uri="/orders",status="201"} 12345`
2. Prometheus가 `scrape_interval`(15초)마다 각 대상의 그 주소를 GET으로 가져간다. 서비스가 Prometheus로 보내는 것이 아니다.
3. 받은 값은 "지표 이름 + 라벨 조합" 하나를 시계열 하나로 보고 (시각, 값) 쌍으로 TSDB에 쌓는다.

**지표 타입**
- Counter: 계속 늘어나기만 하는 누적값(요청 수, 걸린 시간 합). 값 자체보다 구간 증가량이 의미 있다.
- Gauge: 오르내리는 현재값(DB 커넥션 사용 수, CPU 사용률).
- Histogram: 응답 시간을 버킷(`le="0.005"`, `le="0.01"` ...)별 누적 개수로 저장한다(`_bucket`, `_count`, `_sum`).

**PromQL 핵심 함수**
- `increase(x[5m])`: 5분 동안 Counter가 늘어난 양. 수집 시점이 구간 끝과 딱 맞지 않아 외삽하므로 정수가 아닐 수 있다.
- `rate(x[5m])`: 5분 동안의 초당 평균 증가율.
- `histogram_quantile(0.95, sum by (le, ...) (rate(x_bucket[5m])))`: 버킷 분포에서 p95를 추정한다. 버킷 안에서는 선형 보간이므로 버킷 경계 간격만큼 오차가 있다.
- `max_over_time`, `avg_over_time`: Gauge의 구간 최대·평균.

### 3. 둘의 차이
| | k6 | Prometheus |
|---|---|---|
| 위치 | 클라이언트(바깥) | 서비스 내부 |
| 보는 것 | 사용자가 느끼는 응답 시간·실패율 | 서비스·API·서비스 간 호출별 횟수와 지연, CPU, DB 커넥션 |
| 수집 | 요청마다 직접 기록 | 15초마다 누적값을 가져감 |
| 백분위수 | 전체 샘플 기준 | 버킷 기반 근사 |
| 시간 해상도 | 요청 단위 | 수집 주기(15초) 단위 |

k6만 보면 "느려졌다"는 알지만 어디서 느려졌는지 모른다. Prometheus만 보면 내부 구간은 보이지만 사용자 기준 체감과 실패율은 정확히 모른다.

## 이 프로젝트에서는

```
script/perf.sh run s2 BC0-s2-r1
  ├─ 1) k6 컨테이너(1 CPU) ── HTTP ──> Gateway(28080) ──> 각 서비스
  │      scenarios: warmup(목표/4, 1분) → main(목표 RPS, 5분)
  │      결과: k6-summary.json (main 구간 하위 지표 포함)
  │                                       각 서비스 /actuator/prometheus
  │                                                │ 15초마다 pull (대상은 Eureka에서 찾음)
  │                                                ▼
  │                                           Prometheus
  ├─ 2) 끝난 시각 END 기록                            ▲
  └─ 3) k6 report.js ── /api/v1/query?time=END ──────┘
         [END-5m, END] 구간 PromQL → k6 요약과 합쳐 report.md, 회차 목록 한 줄
```

- 부하 모델: `script/perf/k6/lib.js`의 `arrivalOptions`가 `constant-arrival-rate` 시나리오 두 개(warmup, main)를 만든다. main은 `startTime: warmup`으로 워밍업 뒤에 시작한다.
- 본 측정 구간만 집계: `thresholds`에 `{scenario:main}` 조건을 둔 이유는 판정이 아니라 main 구간만의 하위 지표를 요약 JSON에 남기기 위해서다. 그래서 `abortOnFail: false`다.
- 사용자 정의 지표: S1(`s1-auth.js`)은 권한 변경이 반영되기까지 걸린 시간을 `role_change_propagation_ms`에 넣는다.
- 보고서: `script/perf/k6/report.js`는 부하를 주지 않는 k6 스크립트다(`iterations: 1`). `setup()`에서 Prometheus HTTP API를 부르고, `handleSummary`에서 파일로 쓴다. `handleSummary`는 VU와 실행 환경이 달라 전역 변수를 볼 수 없으므로 결과를 `setup()`의 반환값(`data.setup_data`)으로 넘긴다. 이 PC에 Python이 없어 Docker로 돌릴 수 있는 k6를 보고서 도구로도 썼다.
- 수집 설정: `infra/prometheus/prometheus.yml`. 대상 주소를 직접 적지 않고 `eureka_sd_configs`로 Eureka에서 받아 오며, 관리 포트를 따로 둔 Gateway는 Eureka 메타데이터의 `management.port`로 주소를 바꾼다.
- 쓰는 지표: `http_server_requests_seconds`(서비스가 받은 요청), `feign_client_requests_seconds`(서비스 간 호출), `http_client_requests_seconds`(Gateway → 서비스 라우팅), `hikaricp_connections_active/pending`, `process_cpu_usage`. `uri!~"/actuator.*"`로 Prometheus 자신의 수집 요청은 뺀다.
- 한계: Gateway가 WebClient로 부르는 I-01은 지표가 없어서 "user-service가 받은 I-01 − 다른 서비스의 Feign I-01"로 계산한다. 15초 수집 주기 때문에 횟수는 근사값이고, 그래서 본 측정을 5분 이상 돌린다.

## 면접 포인트
- 부하 테스트에서 고정 VU 대신 고정 도착률을 쓴 이유: 닫힌 모델은 서버가 느려질수록 부하가 줄어 지연이 가려진다(coordinated omission). 열린 모델은 실제 트래픽처럼 일정하게 요청이 들어오고, 처리하지 못한 양이 `dropped_iterations`로 드러난다.
- Prometheus가 pull 방식인 이유와 장단점: 수집 대상과 주기를 Prometheus 한 곳에서 관리하고, 대상이 죽으면 `up` 지표로 바로 알 수 있다. 대신 수집 주기보다 짧은 변화는 보이지 않고, 짧게 살다 사라지는 작업은 Pushgateway 같은 별도 수단이 필요하다.
- k6의 p95와 Prometheus의 p95가 다른 이유: k6는 바깥에서 전체 샘플로 계산하고 네트워크·Gateway 시간까지 포함한다. Prometheus는 서비스 안에서 버킷으로 추정한다.
- Counter를 그대로 쓰지 않고 `rate`/`increase`를 쓰는 이유: 누적값이라 구간 비교에는 증가량이 필요하고, 서비스가 재시작해 0으로 돌아가는 경우도 두 함수가 보정한다.

## 참고
- k6 문서: Test lifecycle, Scenarios(executors: constant-arrival-rate), Thresholds, Metrics — https://grafana.com/docs/k6/latest/
- Prometheus 문서: Metric types, Querying functions(`rate`, `increase`, `histogram_quantile`) — https://prometheus.io/docs/
- 사용 버전: k6 `grafana/k6:1.3.0`(`script/perf.sh`), 수집 주기 15초(`infra/prometheus/prometheus.yml`)
- 측정 절차와 결과: `docs/guides/performance-test.md`, `docs/reviews/performance/README.md`
- 관련 노트: [캐시 도입 전 성능 기준선 측정 계획](2026-10-19-performance-baseline-plan.md)
