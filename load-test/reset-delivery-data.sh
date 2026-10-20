#!/usr/bin/env bash
# 측정 사이에 배송 데이터만 비운다(delivery-service/docs/fault-isolation.md 4절).
# script/run.sh --reset은 컨테이너를 다시 만들어 JVM 예열이 사라진다. 예열된 서비스는 그대로 두고 배송·구간 테이블만 비워
# 매 측정을 같은 데이터 상태에서 시작한다. 계정·업체 시드(delivery-e2e.sql)는 남는다.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q' <<'SQL'
TRUNCATE delivery_schema.p_delivery_routes, delivery_schema.p_deliveries;
SELECT 'p_deliveries ' || count(*) FROM delivery_schema.p_deliveries;
SQL
