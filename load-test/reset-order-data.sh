#!/usr/bin/env bash
# 부하 측정 사이에 k6가 만든 주문·배송 데이터만 지운다(주문자 dreceiver의 기록). 매 단계를 같은 데이터 상태에서 시작하려는 것이다.
# 이유: 배송 구간이 모두 WAITING으로 쌓여 담당자별 활성 구간 집계가 데이터 양에 비례해 느려지므로, 이어서 재면 앞 측정이 뒤 측정을 오염시킨다.
# 대상은 전용 UI 테스트 스택(logistics-ui-test)이다. 다른 사용자(UI 시드 등)의 데이터와 계정·업체 시드는 남는다.
set -euo pipefail
PERF_USER='e2e00000-0000-4000-8000-000000000006'
docker exec -i logistics-ui-test-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q' <<SQL
BEGIN;
CREATE TEMP TABLE perf_orders AS SELECT order_id FROM order_schema.p_order_creations WHERE orderer_id = '$PERF_USER';
DELETE FROM delivery_schema.p_delivery_routes WHERE delivery_id IN (SELECT delivery_id FROM delivery_schema.p_deliveries WHERE order_id IN (SELECT order_id FROM perf_orders));
DELETE FROM delivery_schema.p_deliveries WHERE order_id IN (SELECT order_id FROM perf_orders);
DELETE FROM delivery_schema.p_delivery_creation_closures WHERE order_id IN (SELECT order_id FROM perf_orders);
DELETE FROM order_schema.p_orders WHERE orderer_id = '$PERF_USER';
DELETE FROM order_schema.p_order_creations WHERE orderer_id = '$PERF_USER';
COMMIT;
VACUUM (ANALYZE) delivery_schema.p_deliveries, delivery_schema.p_delivery_routes, order_schema.p_orders, order_schema.p_order_creations;
SELECT 'k6 데이터 정리 후 p_orders=' || (SELECT count(*) FROM order_schema.p_orders) || ' p_deliveries=' || (SELECT count(*) FROM delivery_schema.p_deliveries) || ' p_order_creations=' || (SELECT count(*) FROM order_schema.p_order_creations);
SQL
