#!/usr/bin/env bash
# 주문 → DM 끝단 처리량 측정(delivery-service/docs/load-test.md "주문 → DM 끝단 처리량").
# 사용: NAME=dm-base RATES="5 10 20" load-test/order-dm.sh
#   환경 변수: NAME(결과 이름 앞부분), RATES(초당 주문 수, 기본 "5 10"), REPEAT(단계마다 측정 횟수, 기본 2),
#             DURATION(측정 시간, 기본 1m), REST(측정 전 쉬는 초, 기본 60), WARM_RATE(예열 초당 주문 수, 기본 5, 0이면 예열 생략),
#             DRAIN_MAX(측정 뒤 DM이 멈출 때까지 기다리는 최대 초, 기본 150. 수동 지정 전환 60초 + 주기 작업 30초보다 길게)
#   전제: COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml:docker-compose.order-dm.yml
#         를 export한 터미널, 모든 서비스가 뜬 뒤 delivery-e2e.sql과 load-test/order-dm-seed.sql을 넣은 DB.
#   절차: 예열 2회 → [쉼 → 측정 주문·배송·대역 기록 비우기 → 측정 → DM이 멈출 때까지 대기 → 집계] × REPEAT, 단계마다 반복
#   결과: load-test/results/<NAME>-rate<r>-<n>.json(k6 요약), -stats.txt(10초마다 실행기·커넥션 상태), -e2e.txt(끝단 집계)
set -euo pipefail
cd "$(dirname "$0")/.."
name="${NAME:?NAME을 지정한다(예: dm-base)}"
rates="${RATES:-5 10}"
repeat="${REPEAT:-2}"
duration="${DURATION:-1m}"
rest="${REST:-60}"
warm_rate="${WARM_RATE:-5}"
drain_max="${DRAIN_MAX:-150}"
STUB="${STUB_URL:-http://localhost:9900}"
base="${BASE_URL:-http://localhost:8080}"
export KEEP_LOAD="${KEEP_LOAD:-1}"
mkdir -p load-test/results

# 대역이 빠진 채 띄우면 order가 실제 Slack 설정(.env)으로 보낸다. 시작 전에 막는다
if ! docker compose config 2>/dev/null | grep -q 'slack-stub:9900'; then
  echo "Slack 대역 설정이 없다. 다음을 export한 터미널에서 script/run.sh로 다시 띄운다:" >&2
  echo "  export COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml:docker-compose.order-dm.yml" >&2
  exit 1
fi
curl -sf "$STUB/_stats" >/dev/null || { echo "Slack 대역($STUB)이 응답하지 않는다. docker compose up -d slack-stub" >&2; exit 1; }

# 주문 접수 API가 검증 오류(400)를 돌려줄 때까지 기다린다. 서비스가 Eureka에 등록되기 전에는 Gateway가 503을 준다
for i in $(seq 1 60); do
  token=$(curl -s -m 5 -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"dreceiver","password":"Sparta!2026"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p') || true
  code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $token" -H 'Idempotency-Key: ready-check' \
    -H 'Content-Type: application/json' -d '{}' "$base/api/v1/orders") || true
  [ "$code" = 400 ] && break
  echo "서비스 준비 대기 중(O-01 $code)... $((i * 5))초" >&2
  sleep 5
done
[ "$code" = 400 ] || { echo "5분 안에 준비되지 않았다(마지막 응답 $code)" >&2; exit 1; }

reset_data() {
  load-test/reset-delivery-data.sh >/dev/null
  docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q' <<'SQL'
DELETE FROM order_schema.p_order_deletions WHERE order_id IN (SELECT order_id FROM order_schema.p_orders WHERE orderer_id = 'e2e00000-0000-4000-8000-000000000006');
DELETE FROM order_schema.p_order_cancellations WHERE order_id IN (SELECT order_id FROM order_schema.p_orders WHERE orderer_id = 'e2e00000-0000-4000-8000-000000000006');
DELETE FROM order_schema.p_orders WHERE orderer_id = 'e2e00000-0000-4000-8000-000000000006';
DELETE FROM order_schema.p_order_creations WHERE orderer_id = 'e2e00000-0000-4000-8000-000000000006';
VACUUM ANALYZE order_schema.p_orders;
VACUUM ANALYZE order_schema.p_order_creations;
SQL
  curl -sf -X POST "$STUB/_reset" >/dev/null
}

# 10초마다 서버 상태를 남긴다. 끝단이 느려질 때 어느 실행기에 쌓였는지는 k6 결과만으로 알 수 없다
snap_loop() {
  local out="$1"
  echo "Docker 할당: CPU $(docker info --format '{{.NCPU}}')개" > "$out"
  while true; do
    {
      echo "== $(date +%T)"
      docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' | sed 's/logistics-management-//'
      for svc in order-service:8084 delivery-service:8085; do
        docker compose exec -T "${svc%%:*}" sh -c "curl -s localhost:${svc##*:}/actuator/prometheus" 2>/dev/null \
          | grep -E '^(executor_(active_threads|queued_tasks|pool_size_threads)|hikaricp_connections_(active|pending)|tomcat_threads_busy_threads)' \
          | sed "s/^/${svc%%:*} /"
      done
      echo
    } >> "$out" 2>&1 || true
    sleep 10
  done
}

drain() {
  local last=-1 same=0 waited=0 now
  while [ "$waited" -lt "$drain_max" ]; do
    now=$(curl -sf "$STUB/_stats" | sed -n 's/.*"dm": \([0-9]*\).*/\1/p' || true)
    now="${now:-0}"
    if [ "$now" = "$last" ]; then same=$((same + 1)); else same=0; fi
    # 15초 동안 늘지 않으면 끝난 것으로 본다. 단, 대기 구간이 남았으면 주기 작업(30초)을 기다린다
    if [ "$same" -ge 3 ]; then
      pending=$(docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -tAq' \
        <<<"SELECT count(*) FROM delivery_schema.p_delivery_routes WHERE manager_id IS NULL AND created_by <> 'LOAD' AND deleted_at IS NULL;" || echo 0)
      [ "${pending:-0}" = 0 ] && break
    fi
    last="$now"; sleep 5; waited=$((waited + 5))
  done
  echo "DM 대기 ${waited}초(대역 DM ${now}건)"
}

run_once() {
  local label="$1" rate="$2" dur="$3"
  local out="load-test/results/${label}"
  local started_epoch started_iso snap_pid
  started_epoch=$(date +%s)
  started_iso=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  snap_loop "${out}-stats.txt" &
  snap_pid=$!
  k6 run -q -e RATE="$rate" -e DURATION="$dur" --summary-export="${out}.json" load-test/k6/order-dm.js \
    || echo "k6 기준 초과 또는 실패: ${label}" >&2
  drain | tee "${out}-e2e.txt"
  kill "$snap_pid" 2>/dev/null || true
  wait "$snap_pid" 2>/dev/null || true
  bash load-test/order-dm-stats.sh "$started_epoch" "$started_iso" | tee -a "${out}-e2e.txt"
}

if [ "$warm_rate" != 0 ]; then
  for w in 1 2; do
    echo "--- 예열 ${w}회차(초당 ${warm_rate}, 1분)"
    reset_data
    run_once "warm-${name}-${w}" "$warm_rate" 1m >/dev/null
  done
fi
for r in $rates; do
  for n in $(seq 1 "$repeat"); do
    echo "--- ${name} 초당 주문 ${r} ${n}회차: ${rest}초 쉼"
    sleep "$rest"
    reset_data
    run_once "${name}-rate${r}-${n}" "$r" "$duration"
  done
done
echo "끝. 결과: load-test/results/${name}-rate*-*-e2e.txt"
