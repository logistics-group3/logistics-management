#!/usr/bin/env bash
# 구간 배정 결과 집계(delivery-service 결정 로그 27). 측정 직후 실행한다. 측정 전에 load-test/reset-delivery-data.sh로 비웠다는 전제다.
# 과거 배송 시드(created_by = 'LOAD')는 집계에서 뺀다(KEEP_LOAD=1로 남겨 둔 측정).
# 사용: load-test/assignment-stats.sh [로그를 볼 기간(예: 5m) 또는 시작 시각(RFC3339), 기본 5m]
#   - 배정 지연(생성 → 배정) 분포와 남은 대기 구간
#   - 담당자별 배정 수(편차)
#   - 배정 작업 실행 횟수와 한 번에 처리한 구간 수(배치 효과)
set -uo pipefail
cd "$(dirname "$0")/.."
since="${1:-5m}"

docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -q' <<'SQL'
\echo '== 배정 지연(ms)과 남은 대기'
SELECT count(*) AS routes,
       count(*) FILTER (WHERE manager_id IS NULL) AS pending,
       round(percentile_cont(0.5)  WITHIN GROUP (ORDER BY extract(epoch FROM assigned_at - created_at) * 1000)::numeric) AS p50_ms,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM assigned_at - created_at) * 1000)::numeric) AS p95_ms,
       round(percentile_cont(0.99) WITHIN GROUP (ORDER BY extract(epoch FROM assigned_at - created_at) * 1000)::numeric) AS p99_ms,
       round(max(extract(epoch FROM assigned_at - created_at) * 1000)::numeric) AS max_ms
FROM delivery_schema.p_delivery_routes
WHERE created_by <> 'LOAD';
\echo '== 담당자별 배정 수'
SELECT manager_id, count(*) FROM delivery_schema.p_delivery_routes WHERE created_by <> 'LOAD' GROUP BY manager_id ORDER BY 2 DESC;
SQL

echo "== 배정 작업(--since $since, 배정이 있었던 실행만)"
docker compose logs --since "$since" delivery-service 2>/dev/null | grep "구간 배정:" |
  sed -E 's/.*routes=([0-9]+).*/\1/' |
  awk '{n++; s+=$1; if ($1>m) m=$1} END {printf "실행 %d번, 평균 %.1f구간/번, 최대 %d구간\n", n, (n ? s/n : 0), m}'
