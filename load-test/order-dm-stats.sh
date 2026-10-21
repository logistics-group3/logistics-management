#!/usr/bin/env bash
# 주문 → DM 끝단 집계. 측정 직후 실행한다(load-test/order-dm.sh가 부른다).
# 사용: load-test/order-dm-stats.sh <측정 시작 epoch 초> [로그를 볼 시작 시각(RFC3339)]
#   - 건수: 접수 → 주문 커밋 → 배송 확정 → 배정 완료 → DM, 단계마다 몇 건이 남았는지(누락 위치)
#   - 지연: 단계별·끝단(접수 → 마지막 DM) p50/p95/p99/max
#   - 처리량: 10초 구간마다 접수 수와 DM 완료 수
#   - 버림·실패 로그 건수(delivery·order 알림 실행기 포화, Slack 실패)
# Note: 앱 컨테이너는 TZ=Asia/Seoul이라 TIMESTAMP 컬럼은 서울 시각이다. 대역 기록(epoch ms)도 서울 시각으로 바꿔 비교한다.
set -uo pipefail
cd "$(dirname "$0")/.."
start_epoch="${1:?측정 시작 epoch 초가 필요하다}"
since="${2:-10m}"
STUB="${STUB_URL:-http://localhost:9900}"
orderer='e2e00000-0000-4000-8000-000000000006'

psql_q() { docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -q -v ON_ERROR_STOP=1 '"$*"; }

# 대역 기록을 DB로 옮겨 주문·배송 테이블과 조인한다. 측정 전용 스키마라 서비스 스키마를 건드리지 않는다
psql_q -c "'CREATE SCHEMA IF NOT EXISTS loadtest; DROP TABLE IF EXISTS loadtest.slack_records;
  CREATE UNLOGGED TABLE loadtest.slack_records (epoch_ms BIGINT, kind TEXT, order_id TEXT, delivery_id TEXT);'"
curl -sf "$STUB/_records" | psql_q -c "'\\copy loadtest.slack_records FROM pstdin WITH (FORMAT csv)'"

psql_q -v start="$start_epoch" -v orderer="$orderer" <<'SQL'
CREATE TEMP TABLE e2e AS
WITH c AS (
  SELECT order_id, status, created_at AS accepted_at
  FROM order_schema.p_order_creations
  WHERE orderer_id = :'orderer' AND created_at >= to_timestamp(:start) AT TIME ZONE 'Asia/Seoul'
), r AS (
  SELECT delivery_id, count(*) AS routes, count(manager_id) AS assigned_routes,
         count(DISTINCT manager_id) AS managers, max(assigned_at) AS assigned_at
  FROM delivery_schema.p_delivery_routes WHERE created_by <> 'LOAD' GROUP BY delivery_id
), dm AS (
  SELECT order_id::uuid AS order_id, count(*) AS dms,
         max(to_timestamp(epoch_ms / 1000.0) AT TIME ZONE 'Asia/Seoul') AS last_dm
  FROM loadtest.slack_records WHERE kind = 'dm' AND order_id <> '' GROUP BY 1
)
SELECT c.order_id, c.status, c.accepted_at, o.created_at AS committed_at, d.confirmed_at,
       r.routes, r.assigned_routes, r.managers, r.assigned_at, coalesce(dm.dms, 0) AS dms, dm.last_dm
FROM c
LEFT JOIN order_schema.p_orders o ON o.order_id = c.order_id
LEFT JOIN delivery_schema.p_deliveries d ON d.order_id = c.order_id
LEFT JOIN r ON r.delivery_id = d.delivery_id
LEFT JOIN dm ON dm.order_id = c.order_id;

\echo '== 단계별 건수(앞 단계보다 줄면 그 사이에서 빠졌다)'
SELECT count(*) AS accepted,
       count(*) FILTER (WHERE status = 'COMPLETED') AS creation_completed,
       count(committed_at) AS order_committed,
       count(confirmed_at) AS delivery_confirmed,
       count(*) FILTER (WHERE routes > 0 AND routes = assigned_routes) AS fully_assigned,
       count(*) FILTER (WHERE dms > 0) AS dm_any,
       count(*) FILTER (WHERE dms > 0 AND dms >= managers) AS dm_all_managers,
       sum(dms) AS dm_messages
FROM e2e;
SELECT status, count(*) FROM e2e GROUP BY 1 ORDER BY 2 DESC;

\echo '== 지연(ms). 끝단 = 접수 → 그 주문의 마지막 DM'
SELECT stage, count(*) AS n,
       round(percentile_cont(0.5)  WITHIN GROUP (ORDER BY ms)::numeric) AS p50,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY ms)::numeric) AS p95,
       round(percentile_cont(0.99) WITHIN GROUP (ORDER BY ms)::numeric) AS p99,
       round(max(ms)::numeric) AS max
FROM (
  SELECT 1 AS ord, '1 접수 → 주문 커밋' AS stage, extract(epoch FROM committed_at - accepted_at) * 1000 AS ms FROM e2e
  UNION ALL SELECT 2, '2 주문 커밋 → 배송 확정', extract(epoch FROM confirmed_at - committed_at) * 1000 FROM e2e
  UNION ALL SELECT 3, '3 배송 확정 → 배정 완료', extract(epoch FROM assigned_at - confirmed_at) * 1000 FROM e2e WHERE routes = assigned_routes
  UNION ALL SELECT 4, '4 배정 완료 → DM', extract(epoch FROM last_dm - assigned_at) * 1000 FROM e2e WHERE routes = assigned_routes
  UNION ALL SELECT 5, '5 끝단: 접수 → DM', extract(epoch FROM last_dm - accepted_at) * 1000 FROM e2e
) t WHERE ms IS NOT NULL GROUP BY ord, stage ORDER BY ord;

\echo '== 10초 구간별 처리량(초당). accepted는 접수 시각, dm_done은 마지막 DM 시각 기준'
WITH t0 AS (SELECT min(accepted_at) AS t FROM e2e),
b AS (
  SELECT floor(extract(epoch FROM accepted_at - t0.t) / 10)::int AS bucket, 'a' AS k FROM e2e, t0
  UNION ALL SELECT floor(extract(epoch FROM last_dm - t0.t) / 10)::int, 'd' FROM e2e, t0 WHERE last_dm IS NOT NULL
)
SELECT bucket * 10 AS from_s,
       round(count(*) FILTER (WHERE k = 'a') / 10.0, 1) AS accepted_per_s,
       round(count(*) FILTER (WHERE k = 'd') / 10.0, 1) AS dm_done_per_s
FROM b GROUP BY bucket ORDER BY bucket;

\echo '== 끝단 처리량: 첫 접수부터 마지막 DM까지 평균'
SELECT count(last_dm) AS dm_orders,
       round(extract(epoch FROM max(last_dm) - min(accepted_at))::numeric, 1) AS span_s,
       round(count(last_dm) / nullif(extract(epoch FROM max(last_dm) - min(accepted_at)), 0)::numeric, 1) AS dm_orders_per_s
FROM e2e;
SQL

echo "== Slack 대역 호출 수"
curl -sf "$STUB/_stats"; echo
echo "== 버림·실패 로그(--since $since)"
dlog=$(docker compose logs --no-log-prefix --since "$since" delivery-service 2>/dev/null)
olog=$(docker compose logs --no-log-prefix --since "$since" order-service 2>/dev/null)
printf 'delivery 배정 결과 알림 버림(EXECUTOR_FULL): %s\n' "$(grep -c '배정 결과 알림 버림' <<<"$dlog")"
printf 'delivery 배정 결과 알림 실패(I-20 호출): %s\n' "$(grep -c '배정 결과 알림 실패' <<<"$dlog")"
printf 'order 배정 결과 알림 제출 실패(실행기 포화): %s\n' "$(grep -c '배정 결과 알림 제출 실패' <<<"$olog")"
printf 'order 배정 결과 알림 실패(처리 중 예외): %s\n' "$(grep -c '배정 결과 알림 실패' <<<"$olog")"
printf 'order 배송 DM 실패: %s\n' "$(grep -c '배송 DM 실패' <<<"$olog")"
grep '배송 DM 실패' <<<"$olog" | sed -E 's/.*reason=([A-Z_a-z]+).*/  reason=\1/' | sort | uniq -c
printf 'order 주문 Slack 알림 실패(Webhook): %s\n' "$(grep -c '주문 Slack 알림 실패' <<<"$olog")"
printf 'order 알림 생략(ORDER_NOT_ACTIVE): %s\n' "$(grep -c 'reason=ORDER_NOT_ACTIVE' <<<"$olog")"
