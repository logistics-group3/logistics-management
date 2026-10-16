-- 배송 부하 테스트용 대량 데이터. 운영 마이그레이션이 아니다. 사용법은 delivery-service/docs/load-test.md.
-- 지난 180일 동안 끝난 배송(DELIVERED, 구간 ARRIVED)을 :count건 만든다. 기본 300000건, 구간은 약 2.9배.
--   psql ... -v count=300000 < infra/postgres/seed/delivery-load.sql
-- 목적: 운영 몇 달 뒤처럼 과거 데이터가 쌓였을 때의 쿼리 비용을 보려는 것이다.
--   - 담당 건수 쿼리(countActiveRoutes)는 manager_id 인덱스로 과거 도착 구간까지 훑는다. 구간의 절반을 시드 배송담당자
--     dmgr1·dmgr2에게 몰아 주문 생성(I-09) 때마다 이 비용이 드러나게 한다.
--   - 목록 조회(D-01)는 허브 17곳에 고르게 나눠 허브별 최신순 정렬 비용이 드러나게 한다.
-- 주문 데이터는 만들지 않는다(order_id는 무작위 논리 참조). 배송 조회 성능만 본다.
-- 지우기: created_by = 'LOAD'인 구간과 배송을 지우거나 script/run.sh --reset.

\if :{?count}
\else
\set count 300000
\endif

BEGIN;

CREATE TEMP TABLE load_src ON COMMIT DROP AS
SELECT g,
       gen_random_uuid()                                              AS delivery_id,
       h.ids[1 + (g % cardinality(h.ids))]                            AS departure_hub_id,
       h.ids[1 + ((g * 7 + g / 17) % cardinality(h.ids))]             AS destination_hub_id,
       -- 시드 배송담당자 2명에게 절반, 나머지는 가상 배송담당자 500명에게 나눈다
       CASE g % 4
           WHEN 0 THEN 'e2e00000-0000-4000-8000-000000000002'::uuid
           WHEN 1 THEN 'e2e00000-0000-4000-8000-000000000003'::uuid
           ELSE ('e2e10000-0000-4000-8000-' || lpad((g % 500)::text, 12, '0'))::uuid
           END                                                        AS manager_id,
       NOW() - (g % 180) * INTERVAL '1 day' - (g % 1440) * INTERVAL '1 minute' AS ts
FROM generate_series(1, :count) AS g,
     (SELECT array_agg(hub_id ORDER BY hub_id) AS ids FROM hub_schema.p_hubs WHERE deleted_at IS NULL) AS h;

INSERT INTO delivery_schema.p_deliveries (delivery_id, status, pickup_address, delivery_address, recipient_name,
                                          recipient_slack_id, order_id, supplier_company_id, receiver_company_id,
                                          departure_hub_id, destination_hub_id,
                                          created_at, created_by, updated_at, updated_by)
SELECT delivery_id, 'DELIVERED', '부하 테스트 출발지 ' || g, '부하 테스트 도착지 ' || g, '부하수령인' || (g % 1000),
       NULL, gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
       departure_hub_id, destination_hub_id,
       ts, 'LOAD', ts, 'LOAD'
FROM load_src;

-- 집하: 업체 → 출발 허브
INSERT INTO delivery_schema.p_delivery_routes (delivery_route_id, sequence, route_type, status, delivery_id,
                                               departure_hub_id, arrival_hub_id, manager_id,
                                               created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid(), 1, 'PICKUP', 'ARRIVED', delivery_id, NULL, departure_hub_id, manager_id,
       ts, 'LOAD', ts, 'LOAD'
FROM load_src;

-- 허브 이동: 허브가 다를 때만
INSERT INTO delivery_schema.p_delivery_routes (delivery_route_id, sequence, route_type,
                                               expected_distance_km, expected_duration_minutes,
                                               actual_distance_km, actual_duration_minutes, status, delivery_id,
                                               departure_hub_id, arrival_hub_id, manager_id,
                                               created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid(), 2, 'HUB_TRANSFER', 120.50, 95, 123.10, 101, 'ARRIVED', delivery_id,
       departure_hub_id, destination_hub_id, manager_id,
       ts, 'LOAD', ts, 'LOAD'
FROM load_src
WHERE departure_hub_id <> destination_hub_id;

-- 업체 배송: 목적지 허브 → 업체
INSERT INTO delivery_schema.p_delivery_routes (delivery_route_id, sequence, route_type, status, delivery_id,
                                               departure_hub_id, arrival_hub_id, manager_id,
                                               created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid(), CASE WHEN departure_hub_id = destination_hub_id THEN 2 ELSE 3 END, 'LAST_MILE', 'ARRIVED',
       delivery_id, destination_hub_id, NULL, manager_id,
       ts, 'LOAD', ts, 'LOAD'
FROM load_src;

COMMIT;

-- 통계를 갱신해 실행계획이 실제 분포를 보게 한다
ANALYZE delivery_schema.p_deliveries;
ANALYZE delivery_schema.p_delivery_routes;

SELECT (SELECT count(*) FROM delivery_schema.p_deliveries)      AS deliveries,
       (SELECT count(*) FROM delivery_schema.p_delivery_routes) AS routes;
