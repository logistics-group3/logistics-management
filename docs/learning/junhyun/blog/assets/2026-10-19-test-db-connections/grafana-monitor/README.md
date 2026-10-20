# 테스트 DB 연결 수를 Grafana로 보는 임시 구성

통합 테스트 중 PostgreSQL 연결 수를 실제 Grafana 패널로 보기 위한 로컬 전용 구성이다(TS-025). 개발 스택의 Prometheus·Grafana(3000)는 테스트 DB를 수집하지 않으므로, 이 구성은 개발 스택과 분리한 Prometheus·Pushgateway·Grafana를 따로 띄운다. 저장소의 인프라 파일(`infra/`, `docker-compose.yml`)은 건드리지 않는다.

흐름: `script/test-db-connections.sh`(1초마다 `pg_stat_activity` 조회) → Pushgateway → Prometheus(1초 수집) → Grafana.

| 파일 | 내용 |
|---|---|
| `prometheus.yml` | 1초 간격으로 `ts025-pushgateway:9091`을 수집(`honor_labels`) |
| `provisioning/datasources/ds.yml` | Grafana 데이터소스(`ts025-prometheus:9090`, uid `prom-ts025`) |
| `provisioning/dashboards/db.yml` | 대시보드 파일 로더 |
| `dashboards/ts025.json` | 대시보드 `ts025-test-db`(시계열 + 최대 연결 수 + 읽는 법). 한도 97 점선 포함. 제 확인용 시리즈 `probe`는 쿼리에서 제외 |

## 실행 (저장소 루트에서, Docker 필요)

컨테이너 이름이 설정 파일에 들어 있으므로 아래 이름을 그대로 쓴다. 포트는 개발 스택과 겹치지 않게 29090·29091·23000이다. `prom/pushgateway` 이미지는 처음 한 번 받아야 한다.

```bash
D=docs/learning/junhyun/blog/assets/2026-10-19-test-db-connections/grafana-monitor
docker network create ts025-monitor
docker run -d --rm --name ts025-pushgateway --network ts025-monitor -p 127.0.0.1:29091:9091 prom/pushgateway:v1.11.1
docker run -d --rm --name ts025-prometheus --network ts025-monitor -p 127.0.0.1:29090:9090 \
  -v "$PWD/$D/prometheus.yml:/etc/prometheus/prometheus.yml:ro" prom/prometheus:v3.5.0
docker run -d --rm --name ts025-grafana --network ts025-monitor -p 127.0.0.1:23000:3000 \
  -e GF_AUTH_ANONYMOUS_ENABLED=true -e GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer \
  -e GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH=/var/lib/grafana/dashboards/ts025.json \
  -e GF_ANALYTICS_REPORTING_ENABLED=false -e GF_ANALYTICS_CHECK_FOR_UPDATES=false -e GF_NEWS_NEWS_FEED_ENABLED=false \
  -v "$PWD/$D/provisioning:/etc/grafana/provisioning:ro" -v "$PWD/$D/dashboards:/var/lib/grafana/dashboards:ro" \
  grafana/grafana:12.1.1
open "http://127.0.0.1:23000/d/ts025-test-db?orgId=1&from=now-6m&to=now&refresh=5s"
```

## 측정 (다른 터미널)

`SCENARIO`가 범례 이름이 된다. 대시보드의 색 지정은 `cache-limit-none`(빨강)과 `cache-limit-8`(파랑)에 맞춰져 있다. 캐시 상한 없음 시나리오는 `common/src/testFixtures/resources/spring.properties`를 잠시 치워야 재현되며, 끝나면 반드시 되돌린다.

```bash
PUSHGATEWAY_URL=http://127.0.0.1:29091 SCENARIO=cache-limit-8 \
  script/test-db-connections.sh :order-service:test --rerun-tasks
```

## 정리

```bash
docker rm -f ts025-grafana ts025-prometheus ts025-pushgateway && docker network rm ts025-monitor
```

컨테이너를 지우면 Prometheus 데이터도 사라진다. 그래서 화면은 캡처(`../grafana-test-db-connections.png`)로 남겼다.
