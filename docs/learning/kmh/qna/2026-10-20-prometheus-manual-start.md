---
title: Prometheus를 수동으로 띄우고 수집을 확인하는 방법
date: 2026-10-20
updated: 2026-10-20
asker: minhyeok.kwon
level: 기초
tags: [prometheus, docker-compose, eureka, monitoring, local-run]
---

# Prometheus를 수동으로 띄우고 수집을 확인하는 방법

## 질문
Prometheus를 수동으로 기동할 때는 어떻게 하나.

## 짧은 답
- Prometheus는 설정 파일 하나(`prometheus.yml`)를 읽고 그 안의 대상을 주기적으로 가져가는 프로그램이다. 띄우는 방법은 "어떤 이미지·바이너리를 어떤 설정 파일로 실행하느냐"가 전부다.
- 이 프로젝트에서는 팀 Compose의 `prometheus` 서비스만 따로 띄우는 것이 가장 간단하다: `docker compose up -d prometheus`
- 수집 대상은 설정에 적은 주소가 아니라 Eureka 목록에서 온다. 그래서 Prometheus를 띄우는 것만으로는 부족하고, Eureka와 서비스가 떠서 등록돼야 대상이 생긴다.
- 확인은 http://localhost:9090/targets 에서 대상이 `UP`인지 본다.

## 자세히

### 1. Prometheus가 시작할 때 하는 일
1. `--config.file`의 설정을 읽는다. `global.scrape_interval`(수집 주기)과 `scrape_configs`(무엇을 어디서 가져갈지)가 핵심이다.
2. 대상 목록을 만든다. `static_configs`면 적힌 주소를 쓰고, `eureka_sd_configs` 같은 서비스 디스커버리면 그 서버에 주기적으로 물어 목록을 갱신한다.
3. `relabel_configs`로 주소·라벨을 고친 뒤, 대상마다 `metrics_path`(기본 `/metrics`)를 수집 주기마다 GET 한다.
4. 받은 값은 `--storage.tsdb.path`(기본 `data/`)에 저장하고, 9090 포트에서 화면과 HTTP API(`/api/v1/query`)를 연다.

### 2. 자주 쓰는 실행 옵션
| 옵션 | 뜻 |
|---|---|
| `--config.file=...` | 설정 파일 위치 |
| `--storage.tsdb.retention.time=3d` | 데이터 보관 기간 |
| `--enable-feature=exemplar-storage` | 응답 시간 버킷에 붙은 traceId(exemplar) 저장 |
| `--web.enable-lifecycle` | `POST /-/reload`로 재시작 없이 설정 다시 읽기(켜야 쓸 수 있다) |

### 3. 상태 확인 주소
| 주소 | 보는 것 |
|---|---|
| `/-/ready` | 요청을 받을 준비가 됐는지 |
| `/targets` | 대상별 `UP`/`DOWN`, 마지막 수집 시각, 실패 이유 |
| `/config` | 실제로 읽은 설정 |
| `/graph` | PromQL 직접 실행. `up`을 실행하면 대상마다 1(성공) 또는 0(실패)이 나온다 |

## 이 프로젝트에서는

### 방법 A. 팀 Compose로 Prometheus만 띄우기 (권장)
```bash
docker compose up -d prometheus            # depends_on 때문에 eureka-server도 함께 뜬다
docker compose up -d --no-deps prometheus  # Eureka는 이미 떠 있고 Prometheus만 띄울 때
docker compose logs -f prometheus          # "Server is ready to receive web requests." 확인
docker compose restart prometheus          # infra/prometheus/prometheus.yml을 바꾼 뒤
docker compose stop prometheus             # 내리기
```
- 설정은 `docker-compose.yml`의 `prometheus` 서비스에 있다. 이미지 `prom/prometheus:v3.5.0`, 설정 파일은 `./infra/prometheus/prometheus.yml`을 읽기 전용으로 넣는다. 화면은 `127.0.0.1:${PROMETHEUS_PORT:-9090}`이다.
- 데이터 볼륨이 없어서 컨테이너를 다시 만들면 지난 메트릭은 사라진다. `restart`는 컨테이너를 유지하므로 남는다.
- 같은 compose 네트워크 안에서 `eureka-server:8761`에 물어 대상을 찾는다. 서비스 포트를 호스트에 공개하지 않아도 수집되는 이유다.

### 방법 B. 측정 환경에서 띄우기
`script/perf.sh up`이 함께 띄운다. 화면은 http://127.0.0.1:29090 이다. 개발 스택(9090)과 포트가 다르다.

### 방법 C. 서비스를 IDE(bootRun)로 띄운 경우
- 서비스가 호스트에서 돌면 Eureka에는 호스트의 IP·포트가 등록된다. 컨테이너 안의 Prometheus가 그 주소에 닿아야 수집된다. Docker Desktop에서는 대개 닿지만 환경마다 다르다(미확인). `docs/guides/local-run.md` 3.4의 `EUREKA_PREFER_IP=true` 안내와 같은 문제다.
- 먼저 서비스 쪽 노출부터 확인한다. 호스트에서 돌고 있으니 직접 부를 수 있다.
  ```bash
  curl -s http://localhost:8081/actuator/prometheus | head   # user-service
  curl -s http://localhost:8090/actuator/prometheus | head   # gateway는 관리 포트 8090
  ```
- 그다음 `/targets`에서 그 인스턴스가 `DOWN`이면 오류 문구(`connection refused`, `context deadline exceeded`)로 주소 문제인지 본다.

### 방법 D. Compose 없이 설정을 직접 써서 띄우기 (학습용)
Eureka 없이 대상을 직접 적는 최소 설정이다. 저장소에 두지 않고 개인 폴더에 만든다.
```yaml
# my-prometheus.yml
global:
  scrape_interval: 15s
scrape_configs:
  - job_name: user-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['host.docker.internal:8081']   # 컨테이너에서 호스트의 bootRun 서비스로 갈 때
```
```bash
docker run --rm -p 127.0.0.1:9091:9090 \
  -v "$(pwd)/my-prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  prom/prometheus:v3.5.0
```
- 9091로 띄워 팀 스택의 9090과 겹치지 않게 했다.
- 바이너리를 직접 받아 쓰면 `prometheus --config.file=my-prometheus.yml`이고, 이때 대상은 `localhost:8081`이다.
- Windows Git Bash에서 `-v` 경로가 바뀌면 `MSYS_NO_PATHCONV=1`을 앞에 붙인다(`script/perf.sh`가 쓰는 방식).

## 면접 포인트
- 서비스 디스커버리 기반 수집의 장점: 인스턴스를 늘리거나 IP가 바뀌어도 설정을 고치지 않는다. 이 프로젝트는 `--scale`로 늘린 인스턴스도 Eureka에서 찾아 수집한다.
- 대상이 `DOWN`일 때 확인 순서: 서비스가 지표를 내놓는지(`curl /actuator/prometheus`) → Prometheus 컨테이너에서 그 주소로 닿는지(네트워크, 포트) → 설정·relabel이 주소를 맞게 만들었는지(`/targets`, `/config`).

## 참고
- Prometheus 문서: Getting started, Configuration(`scrape_config`, `eureka_sd_config`, `relabel_config`) — https://prometheus.io/docs/
- `docker-compose.yml`의 `prometheus` 서비스, `infra/prometheus/prometheus.yml`, `docs/guides/monitoring.md`, `docs/guides/local-run.md`
- 방법 C·D의 명령은 이 대화에서 실행해 확인하지 않았다.
- 관련 노트: [k6와 Prometheus는 어떻게 동작하고 성능 측정에서 어떻게 함께 쓰나](2026-10-20-k6-prometheus-how-it-works.md)
