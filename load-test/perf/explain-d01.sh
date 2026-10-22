#!/usr/bin/env bash
# D-01 허브별 목록의 실행 계획을 목록 인덱스 있을 때와 없을 때로 잰다(delivery-service/docs/perf-master.md 2·4절 "사전 정비").
# 사용: load-test/perf/explain-d01.sh  → load-test/results/perf/explain-d01.txt
#   측정 시드(100만 건)를 넣은 뒤, 부하가 없을 때 실행한다. 인덱스는 트랜잭션 안에서 지우고 되돌린다(ROLLBACK).
#   쿼리는 DeliveryQueryRepositoryImpl.search와 같은 모양이다: 허브 조건 + 논리 삭제 제외, 최신순 10건과 전체 건수.
set -euo pipefail
cd "$(dirname "$0")/../.."
source load-test/perf/lib.sh
out="$PERF_RESULTS/explain-d01.txt"
mkdir -p "$PERF_RESULTS"
{
  echo "== $(date '+%F %T'), delivery 커밋 $(perf_delivery_commit)"
  perf_psql <<'SQL'
\set hub '''6f39d519-4947-4a95-a454-58c25728f388'''
VACUUM ANALYZE delivery_schema.p_deliveries;
SELECT count(*) AS deliveries, count(*) FILTER (WHERE departure_hub_id = :hub) AS seoul FROM delivery_schema.p_deliveries;
\echo '== 인덱스 있음(3번째 실행만 본다. 앞 두 번은 캐시 예열)'
\o /dev/null
EXPLAIN ANALYZE SELECT count(delivery_id) FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL;
EXPLAIN ANALYZE SELECT count(delivery_id) FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT count(delivery_id) FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL;
\o /dev/null
EXPLAIN ANALYZE SELECT delivery_id, order_id, status, departure_hub_id, destination_hub_id, created_at FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 10;
EXPLAIN ANALYZE SELECT delivery_id, order_id, status, departure_hub_id, destination_hub_id, created_at FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 10;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT delivery_id, order_id, status, departure_hub_id, destination_hub_id, created_at FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 10;
\echo '== 인덱스 없음(지우고 되돌린다)'
BEGIN;
DROP INDEX delivery_schema.idx_p_deliveries_departure_hub_id_created_at;
\o /dev/null
EXPLAIN ANALYZE SELECT count(delivery_id) FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL;
EXPLAIN ANALYZE SELECT count(delivery_id) FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT count(delivery_id) FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL;
\o /dev/null
EXPLAIN ANALYZE SELECT delivery_id, order_id, status, departure_hub_id, destination_hub_id, created_at FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 10;
EXPLAIN ANALYZE SELECT delivery_id, order_id, status, departure_hub_id, destination_hub_id, created_at FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 10;
\o
EXPLAIN (ANALYZE, BUFFERS) SELECT delivery_id, order_id, status, departure_hub_id, destination_hub_id, created_at FROM delivery_schema.p_deliveries WHERE departure_hub_id = :hub AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 10;
ROLLBACK;
SQL
} > "$out" 2>&1
grep -E "^==|Execution Time|Index|Seq Scan|Sort" "$out"
echo "기록: $out"
