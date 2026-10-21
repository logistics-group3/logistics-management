#!/usr/bin/env bash
# 측정 사이에 배송 데이터만 비운다(delivery-service/docs/fault-isolation.md 4절).
# script/run.sh --reset은 컨테이너를 다시 만들어 JVM 예열이 사라진다. 예열된 서비스는 그대로 두고 배송·구간 테이블만 비워
# 매 측정을 같은 데이터 상태에서 시작한다. 계정·업체 시드(delivery-e2e.sql)는 남는다.
# KEEP_LOAD=1: 과거 배송 대량 시드(delivery-load.sql, created_by = 'LOAD')는 남기고 측정이 만든 배송만 지운다(T08 재측정).
#   지운 뒤 VACUUM ANALYZE로 죽은 행과 통계를 정리해 매 측정의 쿼리 비용을 맞춘다.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${KEEP_LOAD:-0}" = 1 ]; then
  docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q' <<'SQL'
DELETE FROM delivery_schema.p_delivery_routes WHERE created_by <> 'LOAD';
DELETE FROM delivery_schema.p_deliveries WHERE created_by <> 'LOAD';
VACUUM ANALYZE delivery_schema.p_delivery_routes;
VACUUM ANALYZE delivery_schema.p_deliveries;
SELECT 'p_deliveries ' || count(*) FROM delivery_schema.p_deliveries;
SQL
  exit 0
fi
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q' <<'SQL'
TRUNCATE delivery_schema.p_delivery_routes, delivery_schema.p_deliveries;
SELECT 'p_deliveries ' || count(*) FROM delivery_schema.p_deliveries;
SQL
