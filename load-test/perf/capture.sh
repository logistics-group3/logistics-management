#!/usr/bin/env bash
# 버전 하나를 다 잰 뒤 칸마다 Grafana 패널을 PNG로 뽑는다(delivery-service/docs/perf-master.md 6절).
# 측정 중에는 쓰지 않는다(렌더러가 Chromium을 띄워 CPU를 쓴다). perf-run.sh가 버전 끝에 부른다.
# 사용: load-test/perf/capture.sh <버전>  → load-test/results/perf/captures/<버전>-<칸>-<항목>.png (이미 있으면 건너뛴다)
#   시간 구간: 칸의 측정 시작 10초 전 ~ 끝 30초 뒤(집계 대기까지). Prometheus 볼륨에 지표가 남아 있어 나중에 다시 뽑을 수 있다.
set -euo pipefail
cd "$(dirname "$0")/../.."
source load-test/perf/lib.sh

ver=${1:?버전}
base=${GRAFANA_URL:-http://localhost:3000}
auth="admin:$(docker compose exec -T grafana printenv GF_SECURITY_ADMIN_PASSWORD)"
python3 -c 'import json, sys; print(json.dumps({"dashboard": json.load(open(sys.argv[1])), "overwrite": True}))' \
  load-test/grafana/perf-master.json \
  | curl -sf -u "$auth" -H 'Content-Type: application/json' --data-binary @- "$base/api/dashboards/db" > /dev/null

mkdir -p "$PERF_RESULTS/captures"
count=0
for m in "$PERF_RESULTS/$ver"/*/manifest.txt; do
  [ -f "$m" ] || continue
  cell=$(basename "$(dirname "$m")")
  case "$cell" in *.invalid-*|*.tmp) continue ;; esac
  from=$(( $(awk -F= '$1 == "started_at" {print $2}' "$m") * 1000 - 10000 ))
  to=$(( $(awk -F= '$1 == "ended_at" {print $2}' "$m") * 1000 + 30000 ))
  for item in 1:p95 2:hikari 3:tomcat 4:cpu 5:rps; do
    out="$PERF_RESULTS/captures/$ver-$cell-${item#*:}.png"
    [ -s "$out" ] && continue
    curl -sf -u "$auth" -o "$out" \
      "$base/render/d-solo/perf-master/perf-master?orgId=1&panelId=${item%%:*}&from=$from&to=$to&width=1000&height=450&tz=Asia%2FSeoul"
    count=$((count + 1))
  done
done
echo "캡처 ${count}장: $PERF_RESULTS/captures/$ver-*"
