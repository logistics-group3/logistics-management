#!/usr/bin/env bash
# 통합 테스트 중 Testcontainers PostgreSQL의 연결 수를 1초마다 재서 최대치를 보여 주는 로컬 도구(TS-025).
# 사용: script/test-db-connections.sh :order-service:test [추가 Gradle 인자]
#   예: script/test-db-connections.sh :order-service:test --rerun-tasks
# 출력: 초 단위 총 연결 수 변화(변할 때만), 마지막에 최대 연결 수·상태별 최대·컨테이너 max_connections.
# Note: Testcontainers가 라벨(org.testcontainers=true)을 붙인 postgres 컨테이너를 찾는다. 한 번에 한 모듈만 재야 값이 섞이지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ $# -ge 1 ]] || { echo "사용법: $0 :모듈:test [Gradle 인자...]" >&2; exit 2; }

LOG="$(mktemp -t test-db-connections.XXXXXX)"
./gradlew "$@" >"$LOG" 2>&1 &
GRADLE_PID=$!
trap 'kill "$GRADLE_PID" 2>/dev/null || true' INT TERM

peak=0; last=-1; limit=""
idle_peak=0; active_peak=0
while kill -0 "$GRADLE_PID" 2>/dev/null; do
  container="$(docker ps -q --filter label=org.testcontainers=true --filter ancestor=postgres:16 | head -1)"
  if [[ -n "$container" ]]; then
    [[ -n "$limit" ]] || limit="$(docker exec "$container" psql -U test -d test -tAc 'show max_connections' 2>/dev/null || true)"
    rows="$(docker exec "$container" psql -U test -d test -tAc \
      "select coalesce(state,'none'), count(*) from pg_stat_activity where datname='test' and pid<>pg_backend_pid() group by 1" 2>/dev/null || true)"
    total=0; idle=0; active=0
    while IFS='|' read -r state count; do
      [[ -n "${count:-}" ]] || continue
      total=$((total + count))
      [[ "$state" == idle ]] && idle=$count
      [[ "$state" == active ]] && active=$count
    done <<<"$rows"
    ((idle > idle_peak)) && idle_peak=$idle
    ((active > active_peak)) && active_peak=$active
    if ((total != last)); then printf '%s 연결 %d (idle %d, active %d)\n' "$(date +%H:%M:%S)" "$total" "$idle" "$active"; last=$total; fi
    ((total > peak)) && peak=$total
  fi
  sleep 1
done
wait "$GRADLE_PID" && status=0 || status=$?
echo "---"
echo "최대 연결 수: $peak (idle 최대 $idle_peak, active 최대 $active_peak), 컨테이너 max_connections: ${limit:-미확인}"
echo "Gradle 종료 코드: $status ($(grep -E 'BUILD (SUCCESSFUL|FAILED)' "$LOG" | tail -1))"
grep -E 'too many clients' "$LOG" | head -1 | sed 's/^/연결 고갈 로그: /' || true
echo "Gradle 로그: $LOG"
exit "$status"
