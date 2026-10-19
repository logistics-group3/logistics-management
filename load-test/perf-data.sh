#!/usr/bin/env bash
# 부하 측정용 데이터 조건을 바꾼다(전용 UI 테스트 스택). 사용: perf-data.sh managers-on|managers-off|complete-start <초>|complete-stop|plan
#   managers-on/off  서울 허브 배송담당자 20명(perfmgr1..20, 시드 2명에 더해 22명)을 켜고 끈다. 없으면 만든다.
#   complete-start N 새 구간이 만들어진 지 N초 지난 WAITING·MOVING 구간을 ARRIVED로 바꾸는 루프를 백그라운드로 돌린다(배송이 끝나는 상황을 흉내).
#   analyze-start    주문·배송 테이블의 통계를 2초마다 갱신한다(자동 통계 갱신이 따라오지 못하는 빠른 증가를 대신한다). analyze-stop으로 끝낸다.
#   plan             활성 구간 집계 쿼리의 실행 계획(요약)과 순차 스캔 누계, 활성 구간 수를 출력한다.
set -euo pipefail
PSQL() { docker exec -i logistics-ui-test-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d logistics -At -q -v ON_ERROR_STOP=1'; }
PIDFILE="${TMPDIR:-/tmp}/perf-complete.pid"
case "${1:?}" in
  managers-on)
    PSQL <<'SQL'
INSERT INTO user_schema.p_users (user_id, username, password, name, slack_id, affiliation_type, affiliation_name, role, is_delivery_manager, hub_id, created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid(), 'perfmgr' || g, u.password, '성능측정담당' || g, 'UPERF' || g, u.affiliation_type, u.affiliation_name, u.role, TRUE, u.hub_id, NOW(), 'PERF', NOW(), 'PERF'
FROM user_schema.p_users u CROSS JOIN generate_series(1, 20) g
WHERE u.username = 'dmgr1' AND NOT EXISTS (SELECT 1 FROM user_schema.p_users WHERE username = 'perfmgr' || g);
UPDATE user_schema.p_users SET is_delivery_manager = TRUE WHERE username LIKE 'perfmgr%';
SELECT '서울 허브 배송담당자 ' || count(*) FROM user_schema.p_users WHERE is_delivery_manager AND hub_id = (SELECT hub_id FROM user_schema.p_users WHERE username='dmgr1') AND deleted_at IS NULL;
SQL
    ;;
  managers-off)
    PSQL <<'SQL'
UPDATE user_schema.p_users SET is_delivery_manager = FALSE WHERE username LIKE 'perfmgr%';
SELECT '서울 허브 배송담당자 ' || count(*) FROM user_schema.p_users WHERE is_delivery_manager AND hub_id = (SELECT hub_id FROM user_schema.p_users WHERE username='dmgr1') AND deleted_at IS NULL;
SQL
    ;;
  complete-start)
    SECS="${2:?초가 필요하다}"
    # Note: psql 한 개가 \watch로 1초마다 같은 UPDATE를 반복한다. application_name으로 찾아 끝낼 수 있어 고아 프로세스가 남지 않는다.
    ( printf "UPDATE delivery_schema.p_delivery_routes SET status='ARRIVED', updated_at=NOW(), updated_by='PERF' WHERE status IN ('WAITING','MOVING') AND created_at < (SELECT max(created_at) FROM delivery_schema.p_delivery_routes) - interval '%s seconds'\\n\\\\watch 1\\n" "$SECS" \
      | docker exec -i -e PGAPPNAME=perf-complete logistics-ui-test-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d logistics -q -At >/dev/null 2>&1' ) &
    ;;
  complete-stop)
    echo "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE application_name = 'perf-complete';" | PSQL >/dev/null
    ;;
  analyze-start)
    ( printf "ANALYZE order_schema.p_order_creations, order_schema.p_orders, delivery_schema.p_deliveries, delivery_schema.p_delivery_routes\\n\\\\watch 2\\n" \
      | docker exec -i -e PGAPPNAME=perf-analyze logistics-ui-test-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d logistics -q -At >/dev/null 2>&1' ) &
    ;;
  analyze-stop)
    echo "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE application_name = 'perf-analyze';" | PSQL >/dev/null
    ;;
  plan)
    IDS=$(echo "SELECT string_agg(quote_literal(user_id::text) || '::uuid', ',') FROM user_schema.p_users WHERE is_delivery_manager AND deleted_at IS NULL AND hub_id = (SELECT hub_id FROM user_schema.p_users WHERE username='dmgr1');" | PSQL)
    PSQL <<SQL | grep -E "Seq Scan|Index|Bitmap|Execution Time|ACTIVE|SEQSCANS" | sed -E 's/^ +//' | cut -c1-150
SELECT 'ACTIVE ' || count(*) FILTER (WHERE status IN ('WAITING','MOVING')) || ' / 전체 ' || count(*) FROM delivery_schema.p_delivery_routes;
SELECT 'SEQSCANS p_delivery_routes ' || seq_scan FROM pg_stat_user_tables WHERE relname = 'p_delivery_routes';
EXPLAIN (ANALYZE, BUFFERS) SELECT manager_id, count(delivery_route_id) FROM delivery_schema.p_delivery_routes WHERE manager_id IN ($IDS) AND status IN ('WAITING','MOVING') AND deleted_at IS NULL GROUP BY manager_id;
SQL
    ;;
  *) echo "사용법: perf-data.sh managers-on|managers-off|complete-start <초>|complete-stop|analyze-start|analyze-stop|plan" >&2; exit 2 ;;
esac
