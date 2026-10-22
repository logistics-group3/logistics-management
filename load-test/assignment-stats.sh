#!/usr/bin/env bash
# 구간 배정 결과 집계(delivery-service 결정 로그 27, perf-master.md 1절). 측정 직후 실행한다. 측정 전에 load-test/reset-delivery-data.sh로 비웠다는 전제다.
# 과거 배송 시드(created_by = 'LOAD')는 집계에서 뺀다(KEEP_LOAD=1로 남겨 둔 측정).
# 사용: load-test/assignment-stats.sh [로그를 볼 기간(예: 5m) 또는 시작 시각(RFC3339), 기본 5m]
#   HUB_ID: 편차를 볼 측정 허브(기본 서울 허브, k6 SEED.seoulHub)
#   - 배정 지연과 남은 대기. 버전은 스키마로 가린다
#       assigned_at 없음(V0·V1): 생성 중에 배정하므로 지연을 재지 않는다
#       assigned_at만 있음(V2): 구간 생성 → 배정
#       confirmed_at도 있음(V3): 주문 확정 → 배정
#   - 측정 허브 배송담당자별 배정 수(0건 포함)와 편차(최대 - 최소)
#   - 배정 작업 실행 횟수와 한 번에 처리한 구간 수(배치 효과, V2부터)
#   - "== 판정용" 아래 key=value 줄은 load-test/perf-judge.py가 읽는다
set -uo pipefail
cd "$(dirname "$0")/.."
since="${1:-5m}"
hub="${HUB_ID:-6f39d519-4947-4a95-a454-58c25728f388}"
[[ "$hub" =~ ^[0-9a-f-]{36}$ ]] || { echo "HUB_ID가 UUID가 아니다: $hub" >&2; exit 1; }

docker compose exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d logistics -q -v hub=$hub" <<'SQL'
SELECT EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'delivery_schema' AND table_name = 'p_delivery_routes'
                 AND column_name = 'assigned_at') AS has_assigned,
       EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'delivery_schema' AND table_name = 'p_deliveries'
                 AND column_name = 'confirmed_at') AS has_confirmed
\gset

-- Note: psql \if로 버전마다 다른 쿼리를 고른다. 없는 컬럼을 쓴 쿼리는 파싱 단계에서 오류가 나므로 SQL CASE로는 나눌 수 없다.
\if :has_confirmed
\set basis confirmed_at
CREATE TEMP TABLE measured AS
SELECT r.manager_id, extract(epoch FROM r.assigned_at - d.confirmed_at)::float8 * 1000 AS delay_ms
FROM delivery_schema.p_delivery_routes r
         JOIN delivery_schema.p_deliveries d ON d.delivery_id = r.delivery_id
WHERE r.created_by <> 'LOAD';
\elif :has_assigned
\set basis created_at
CREATE TEMP TABLE measured AS
SELECT manager_id, extract(epoch FROM assigned_at - created_at)::float8 * 1000 AS delay_ms
FROM delivery_schema.p_delivery_routes
WHERE created_by <> 'LOAD';
\else
\set basis none
CREATE TEMP TABLE measured AS
SELECT manager_id, NULL::float8 AS delay_ms
FROM delivery_schema.p_delivery_routes
WHERE created_by <> 'LOAD';
\endif

-- 배정을 하나도 못 받은 담당자도 0건으로 세야 편차가 맞다
CREATE TEMP TABLE per_manager AS
SELECT u.user_id AS manager_id, u.username, count(m.manager_id) AS routes
FROM user_schema.p_users u
         LEFT JOIN measured m ON m.manager_id = u.user_id
WHERE u.is_delivery_manager AND u.deleted_at IS NULL AND u.hub_id = :'hub'
GROUP BY u.user_id, u.username;

\echo '== 배정 지연(ms)과 남은 대기, 기준:' :basis
SELECT count(*) AS routes,
       count(*) FILTER (WHERE manager_id IS NULL) AS pending,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY delay_ms)::numeric)  AS p50_ms,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY delay_ms)::numeric) AS p95_ms,
       round(percentile_cont(0.99) WITHIN GROUP (ORDER BY delay_ms)::numeric) AS p99_ms,
       round(max(delay_ms)::numeric) AS max_ms
FROM measured;
\echo '== 측정 허브 배송담당자별 배정 수'
SELECT username, manager_id, routes FROM per_manager ORDER BY routes DESC, username;

\echo '== 판정용'
\pset format unaligned
\pset tuples_only on
SELECT concat_ws(E'\n',
    'basis=' || :'basis',
    'routes=' || (SELECT count(*) FROM measured),
    'pending=' || (SELECT count(*) FROM measured WHERE manager_id IS NULL),
    'assign_p95_ms=' || coalesce((SELECT round(percentile_cont(0.95) WITHIN GROUP (ORDER BY delay_ms)::numeric)::text
                                  FROM measured), ''),
    'managers=' || (SELECT count(*) FROM per_manager),
    'deviation=' || coalesce((SELECT (max(routes) - min(routes))::text FROM per_manager), ''),
    'outside=' || (SELECT count(*) FROM measured
                   WHERE manager_id IS NOT NULL AND manager_id NOT IN (SELECT manager_id FROM per_manager)));
SQL

echo "== 배정 작업(--since $since, 배정이 있었던 실행만)"
# 배정 로그가 없는 버전(V0·V1)에서 grep이 실패로 끝나 집계 전체가 실패로 보이지 않게 한다
docker compose logs --since "$since" delivery-service 2>/dev/null | { grep "구간 배정:" || true; } |
  sed -E 's/.*routes=([0-9]+).*/\1/' |
  awk '{n++; s+=$1; if ($1>m) m=$1} END {printf "실행 %d번, 평균 %.1f구간/번, 최대 %d구간\n", n, (n ? s/n : 0), m}'
