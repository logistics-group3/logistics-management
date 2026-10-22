#!/usr/bin/env bash
# 성능 마스터 측정 한 칸(delivery-service/docs/perf-master.md 5·6절). load-test/perf-run.sh가 부른다.
# 사용: load-test/perf/cell.sh <버전> <칸>   예: V1 240-1, V4 fault-160, F1 f10x10-1, F1 n40x40-1
#   쉼 → 측정이 만든 배송 삭제 → 쉬는 상태 확인 → 측정(pg_stat_activity·docker stats 표본) → 집계 대기 → 배정 집계
#   → Prometheus 덤프 → 판정 → manifest. 끝까지 가야 <칸>.tmp를 <칸>으로 옮긴다. 끊긴 칸은 .tmp로 남고 다음 실행이 지운다.
set -euo pipefail
cd "$(dirname "$0")/../.."
source load-test/perf/lib.sh

ver=${1:?버전}
cell=${2:?칸 이름(예: 240-1, fault-160, f10x10-1)}
perf_require_version "$ver"
# 칸 이름: <TPS>-<회차>(계단, 1분) | fault-<TPS>(V4, 6분, 조회 20) | f<생성>x<조회>-<회차>(장애 격리, 4분, user 30초 정지)
#          | n<생성>x<조회>-<회차>(장애 없이 4분, 서킷 오탐을 본다)
read_rate=$PERF_READ
if [[ "$cell" =~ ^fault-([0-9]+)$ ]]; then
  tps=${BASH_REMATCH[1]}; duration=6m; duration_sec=360; fault_mode=pause
elif [[ "$cell" =~ ^([fn])([0-9]+)x([0-9]+)-[0-9]+$ ]]; then
  tps=${BASH_REMATCH[2]}; read_rate=${BASH_REMATCH[3]}; duration=4m; duration_sec=240
  fault_mode=none; [ "${BASH_REMATCH[1]}" = f ] && fault_mode=pause
elif [[ "$cell" =~ ^([0-9]+)-([0-9]+)$ ]]; then
  tps=${BASH_REMATCH[1]}; duration=1m; duration_sec=60; fault_mode=none
else
  echo "칸 이름을 알 수 없다: $cell" >&2; exit 2
fi
confirm=$(perf_confirm "$ver")
confirm_path=$(perf_confirm_path "$ver")
confirm_url=
[ "$confirm_path" = direct ] && confirm_url="http://localhost:$PERF_DELIVERY_PORT"
dir="$PERF_RESULTS/$ver/$cell"
tmp="$dir.tmp"
[ ! -e "$dir" ] || { echo "이미 있는 칸이다: $dir" >&2; exit 2; }
rm -rf "$tmp"; mkdir -p "$tmp"

echo "--- $ver $cell: 생성 $tps + 조회 $read_rate, $duration, 장애 $fault_mode, CONFIRM=$confirm(${confirm_path}). ${PERF_REST}초 쉼"
sleep "$PERF_REST"
KEEP_LOAD=1 load-test/reset-delivery-data.sh > "$tmp/reset.txt" 2>&1

# 쉬는 상태: 모든 컨테이너 CPU가 기준 미만이 될 때까지 기다린다. 끝내 안 되면 idle_ok=0으로 남겨 무효 칸이 된다(7절)
idle_ok=0; idle_max=; waited=0
while :; do
  sample=$(perf_cpu_sample)
  echo "$sample" >> "$tmp/idle.txt"
  read -r idle_max idle_top < <(echo "$sample" | perf_idle_max)
  if awk -v m="$idle_max" -v t="$PERF_IDLE_CPU" 'BEGIN {exit !(m < t)}'; then idle_ok=1; break; fi
  [ "$waited" -ge "$PERF_IDLE_WAIT" ] && break
  sleep 10; waited=$((waited + 10))
done
echo "쉬는 상태: 최고 CPU ${idle_max}% (${idle_top}), 기다림 ${waited}초, idle_ok=$idle_ok"

# 측정 동안 표본. pg_stat_activity는 psql 한 세션의 \watch로 5초마다 찍어 표본 비용을 줄인다
watch_count=$(( duration_sec / 5 + 3 ))
perf_psql -A -t -F $'\t' > "$tmp/pgwait.txt" 2>&1 <<SQL &
\echo 'time	active	advisory_wait	lock_wait	lwlock	io	idle_in_tx	delivery_conns	waits'
SELECT to_char(clock_timestamp(), 'HH24:MI:SS'),
       count(*) FILTER (WHERE state = 'active'),
       count(*) FILTER (WHERE wait_event_type = 'Lock' AND wait_event = 'advisory'),
       count(*) FILTER (WHERE wait_event_type = 'Lock' AND wait_event <> 'advisory'),
       count(*) FILTER (WHERE wait_event_type = 'LWLock'),
       count(*) FILTER (WHERE wait_event_type = 'IO'),
       count(*) FILTER (WHERE state = 'idle in transaction'),
       count(*) FILTER (WHERE usename = 'delivery_app'),
       coalesce(string_agg(DISTINCT wait_event_type || ':' || wait_event, ' ')
                FILTER (WHERE state = 'active' AND wait_event IS NOT NULL), '-')
FROM pg_stat_activity
WHERE datname = 'logistics' AND backend_type = 'client backend' AND pid <> pg_backend_pid()
\watch i=5 c=$watch_count
SQL
pg_pid=$!
touch "$tmp/.running"
( while [ -f "$tmp/.running" ]; do perf_cpu_sample; sleep 3; done > "$tmp/docker.txt" 2>&1 ) &
docker_pid=$!

# user 복구 30초 뒤, 복구 전에 확정된 배송의 미배정 구간 수(5절 V4 합격선). 확정 컬럼이 있는 버전(CONFIRM=1)의 장애 칸만
check_recovery=0
[ "$fault_mode" = pause ] && [ "$confirm" = 1 ] && check_recovery=1
if [ "$check_recovery" = 1 ]; then
  (
    for _ in $(seq 1 600); do grep -q "user-service 복구" "$tmp/k6.txt" 2>/dev/null && break; sleep 1; done
    recovered=$(date +%s)
    sleep 30
    since=$(TZ=Asia/Seoul date -r "$recovered" '+%Y-%m-%d %H:%M:%S')
    pending=$(perf_psql -A -t -c "SELECT count(*) FROM delivery_schema.p_delivery_routes r
        JOIN delivery_schema.p_deliveries d ON d.delivery_id = r.delivery_id
        WHERE r.created_by <> 'LOAD' AND r.manager_id IS NULL AND d.confirmed_at <= '$since'")
    printf 'recovered_at=%s\nchecked_at=%s\npending_confirmed_before_recovery=%s\n' "$recovered" "$(date +%s)" "$pending"
  ) > "$tmp/recovery.txt" 2>&1 &
  recovery_pid=$!
fi

started_iso=$(date -u +%Y-%m-%dT%H:%M:%SZ)
started_at=$(date +%s)
set +e
LABEL="perf-$ver-$cell" OUT_JSON="$tmp/k6.json" CREATE_RATE="$tps" READ_RATE="$read_rate" DURATION="$duration" \
  FAULT_MODE="$fault_mode" CONFIRM="$confirm" CONFIRM_URL="$confirm_url" PRE_VUS="$PERF_PRE_VUS" \
  load-test/fault-isolation.sh 2>&1 | tee "$tmp/k6.txt"
k6_exit=${PIPESTATUS[0]}
set -e
ended_at=$(date +%s)
rm -f "$tmp/.running"
# 집계 시각은 측정 끝 기준이다(1절 "측정 종료 30초 뒤"). 표본이 끝나기를 기다리지 않고 바로 센다
sleep "$PERF_SETTLE"
wait "$docker_pid" "$pg_pid" || true
if [ "$check_recovery" = 1 ]; then wait "$recovery_pid" || true; fi
bash load-test/assignment-stats.sh "$started_iso" > "$tmp/assignment.txt" 2>&1 || true
[ "$fault_mode" = pause ] && python3 load-test/timeline.py "$tmp/k6.csv.gz" > "$tmp/timeline.txt" 2>&1 || true
python3 load-test/perf/perf.py prom-dump "$((started_at - 15))" "$((ended_at + PERF_SETTLE))" "$tmp/prom.json" || true
if [ "$ver" = V4 ]; then
  python3 load-test/perf/perf.py judge-fault "$tmp" > "$tmp/verdict.txt" || true
elif [[ "$ver" == F* ]]; then
  python3 load-test/perf/perf.py judge-isolation "$tmp" > "$tmp/verdict.txt" || true
else
  python3 load-test/perf-judge.py "$tmp/k6.json" "$tmp/assignment.txt" > "$tmp/verdict.txt" || true
fi

metric() { docker compose exec -T delivery-service sh -c 'curl -s localhost:8085/actuator/prometheus' 2>/dev/null \
  | awk -v k="$1" 'index($0, k) == 1 {print $2; exit}'; }
warm_at=$(awk -F= '/^warm_at=/ {v = $2} END {print v}' "$PERF_RESULTS/$ver/warm.txt" 2>/dev/null)
{
  echo "version=$ver"
  echo "cell=$cell"
  echo "tps=$tps"
  echo "read_rate=$read_rate"
  echo "duration=$duration"
  echo "confirm=$confirm"
  echo "confirm_path=$confirm_path"
  echo "fault_mode=$fault_mode"
  echo "pre_vus=$PERF_PRE_VUS"
  echo "rest=$PERF_REST"
  echo "settle=$PERF_SETTLE"
  echo "compose_file=$COMPOSE_FILE"
  echo "config_ref=$(perf_config_ref "$ver")"
  echo "config_sha=$(perf_served_config_sha || true)"
  echo "image=$(docker inspect -f '{{.Image}}' "$(perf_delivery_container)")"
  echo "image_commit=$(perf_delivery_commit)"
  echo "expected_commit=$(perf_expected_commit "$ver")"
  echo "tool_commit=$(git rev-parse HEAD)"
  echo "tool_dirty=$([ -z "$(git status --porcelain -- load-test)" ] && echo 0 || echo 1)"
  echo "hikari_max=$(metric 'hikaricp_connections_max{')"
  echo "tomcat_max_threads=$(metric 'tomcat_threads_config_max_threads{')"
  echo "batch_window_ms=$(docker compose exec -T delivery-service printenv DELIVERY_ASSIGNMENT_BATCH_WINDOW_MS 2>/dev/null || true)"
  echo "zipkin_running=$([ -n "$(docker compose ps -q --status running zipkin)" ] && echo 1 || echo 0)"
  echo "delivery_started_at=$(perf_delivery_started_at)"
  echo "warm_at=$warm_at"
  echo "idle_max_cpu=$idle_max"
  echo "idle_top=$idle_top"
  echo "idle_waited=$waited"
  echo "idle_ok=$idle_ok"
  echo "started_at=$started_at"
  echo "ended_at=$ended_at"
  echo "k6_exit=$k6_exit"
  for f in "$tmp"/*; do
    [ "$(basename "$f")" = manifest.txt ] && continue
    echo "file=$(basename "$f") $(shasum -a 256 "$f" | awk '{print $1}')"
  done
} > "$tmp/manifest.txt"
mv "$tmp" "$dir"
echo "칸 기록: $dir ($(tail -1 "$dir/verdict.txt"))"
