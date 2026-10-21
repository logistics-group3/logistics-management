# 성능 측정 가이드

캐시 도입 전후를 같은 조건에서 비교하기 위한 로컬 측정 절차다. 측정 범위·조건·결과는 [캐시 도입 성능 측정 기록](../reviews/performance/README.md)에 있다.
필요한 것은 Docker뿐이다. 부하 도구 k6(`grafana/k6:1.3.0`)와 Prometheus 조회도 컨테이너로 실행한다.

## 구성
| 파일 | 역할 |
|---|---|
| `script/perf.sh` | 진입점. 환경 기동, 시드, 실행, 보고서, 기록 |
| `script/perf/compose.perf.yml` | 측정 전용 오버라이드. 컨테이너당 CPU 1개·메모리 768MB(Postgres 2개·1GB), Slack 끔, Gateway TTL 조절 |
| `script/perf/k6/seed.js` | 시드: 허브마다 배송담당자 2명·공급업체 1개·상품 3개, 수령업체 `PERF_USERS`개(기본 300), 권한 변경 측정용 사용자 3명 |
| `script/perf/k6/s1-auth.js` | S1 인증 요청(`GET /users/me`) + 권한 변경 반영 시간 측정 |
| `script/perf/k6/s2-order.js` | S2 주문 생성(`POST /orders`), 수령업체 관리자 본인이 무작위 상품 주문 |
| `script/perf/k6/s3-read.js` | S3 허브 단건(H-02)·경로 단건(H-04). B가 캐시하는 단건 조회만 부른다 |
| `script/perf/k6/report.js` | 본 측정 구간의 k6 요약과 Prometheus 지표를 보고서로 만든다 |

- 프로젝트 이름은 `logistics-perf`, 환경 파일은 `.env.perf`(처음 실행 때 만든다. Git 제외)다. 개발 스택(8080)·UI 테스트(18080)와 포트가 겹치지 않는다(Gateway 28080, Prometheus 29090, Grafana 23000).
- 추적 샘플링은 0.0이다. 추적 비용이 측정값에 섞이지 않게 한다.
- Redis(`redis:7.4.2`, 1 CPU·256MB, 디스크 저장 없음)는 쓰지 않는 조건에서도 함께 띄운다. 조건마다 컨테이너 구성을 같게 한다.

## 캐시 조건 바꾸기
`up` 할 때 셸 환경 변수로 준다. 바꾸면 `up`을 다시 실행해야 반영된다. 기본값은 팀 기본 동작과 같다([ADR 0019](../decisions/0019-redis-cache-measured-adoption.md)).

| 변수 | 값(기본) | 항목 | 들어가는 서비스 설정 |
|---|---|---|---|
| `PERF_AUTH_CACHE_TTL` | `60s`, `0s`면 캐시 없음 | A | gateway `auth.user-cache-ttl` |
| `PERF_AUTH_CACHE` | `local`(기본), `redis` | A | gateway `auth.user-cache`, user `user.auth-cache.type` |
| `PERF_HUB_CACHE` | `none`(기본), `local`, `redis` | B | hub `hub.cache.type` |
| `PERF_COMPANY_CACHE` | `none`(기본), `local`, `redis` | C | company `company.cache.type` |

```bash
script/perf.sh down && PERF_HUB_CACHE=redis script/perf.sh up   # B1-redis
```
- 부하는 고정 도착률(`constant-arrival-rate`)로 준다. 워밍업(목표의 1/4) 뒤 본 측정을 하고, 지표는 본 측정 구간만 집계한다.

## 처음 한 번
```bash
script/perf.sh up      # 이미지 빌드·기동. 처음에는 수 분 걸린다
script/perf.sh seed    # .local/perf/data.json 저장. 다시 실행해도 있는 데이터는 재사용한다
```

## 한 회차 측정
```bash
# 조건에 맞게 띄운다. TTL을 바꾸면 up을 다시 실행해야 반영된다. JIT 상태를 맞추려면 down 후 up 한다
script/perf.sh down && PERF_AUTH_CACHE_TTL=60s script/perf.sh up

# 예열 회차(버림). 새로 띄운 직후 첫 회차는 JIT·커넥션 풀 준비 비용 때문에 크게 나빠 비교에서 뺀다
RATE=60 DURATION=3m WARMUP=30s PUBLISH=0 PROBE_ROUNDS=0 script/perf.sh run s2 warm-BC0-s2

# S2는 주문·배송이 쌓이므로 회차마다 비운다(사용자·업체·상품은 남는다)
script/perf.sh clean

RATE=10 DURATION=5m WARMUP=1m script/perf.sh run s2 BC0-s2-r1
```
- 결과: `.local/perf/results/<회차>/`(report.md, k6-summary.json, prometheus.json), `docs/reviews/performance/<회차>.md`, 회차 목록 한 줄.
- 시험 실행은 `PUBLISH=0`을 붙여 docs에 남기지 않는다.
- k6가 임계값(p95 1초, 실패율 1%)을 넘어도 보고서는 만든다. 임계값은 판정이 아니라 본 측정 구간 집계용이다.

## 목표 RPS 정하기
1. `RATE=1 DURATION=1m WARMUP=10s PUBLISH=0 script/perf.sh run s2 smoke`로 오류 없이 도는지 본다.
2. RATE를 5, 10, 20, 40처럼 올리며 `PUBLISH=0`으로 짧게(2m) 돌린다. p95가 1초를 넘거나 실패율이 1%를 넘거나 dropped가 생기는 첫 값이 한계다.
3. 한계의 50~70%를 그 시나리오의 RATE로 고정한다. 같은 시나리오의 모든 조건을 이 RATE로 잰다.

## 주의
- 측정 환경 전체가 메모리를 많이 쓴다(컨테이너 합계 약 3GB, Docker Desktop VM 8GB). 2026-10-20 16GB PC에서 다른 프로그램과 함께 돌리다 메모리 부족으로 측정이 중단됐다.
- 측정 중에는 다른 프로그램을 끈다. k6도 같은 PC의 CPU를 쓰므로 1 CPU로 제한해 실행한다.
- Prometheus 수집 주기는 15초다. 호출 횟수는 근사값이고, 본 측정은 5분 이상 돌린다.
- S1 권한 변경 측정은 측정용 사용자(`pp00~pp02`)의 role을 MASTER로 바꿨다가 USER로 되돌린다. 부하 대상 사용자와 섞이지 않는다. 본 측정 시작 1분 뒤에 시작하고 본 측정이 끝나기 전에 마친다. 그래서 `DURATION`이 3m보다 짧으면 측정하지 않는다(5m이면 TTL 60s에서 2회).
- Gateway의 I-01 호출(WebClient)은 메트릭이 따로 없다. 보고서는 user-service가 받은 I-01에서 다른 서비스의 Feign I-01을 빼서 구한다. S1에는 다른 호출이 없어 정확하고, S2에서는 Prometheus 근사값끼리의 차이다.
- Feign 메트릭의 URL에는 FeignClient의 `path`(예: `/internal`)가 빠진다. 예: `/companies/{companyId}`.
- 끝나면 `script/perf.sh down`으로 내린다. 데이터까지 지우려면 `script/perf.sh reset`.
