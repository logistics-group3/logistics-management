-- 측정 전용 과거 배송 시드(delivery-service/docs/perf-master.md 4절). 운영 마이그레이션이 아니다.
-- 지난 180일 동안 끝난 배송(DELIVERED, 구간 ARRIVED)을 :count건 만든다. 기본 100만 건, 구간은 약 2.9배.
-- infra/postgres/seed/delivery-load.sql과 다른 점
--   - 구간 담당자를 그 구간 담당 허브의 배송담당자(perf-managers.sql, 허브당 10명)에게 고르게 나눈다.
--     집하는 공급업체 허브, 허브 이동은 출발 허브, 업체 배송은 요청업체 허브(RouteType.managingHubId와 같다).
--   - 측정 버전(V0~V3)마다 스키마가 달라 있는 컬럼만 채운다.
--     managing_hub_id·assigned_at은 delivery V3 마이그레이션부터(측정 V2 이후), confirmed_at은 V5부터(측정 V3).
--   - 먼저 기존 과거 배송(created_by = 'LOAD')을 지운다. 측정이 만든 배송은 reset-delivery-data.sh가 지운다.
-- 사용: COUNT=1000000 load-test/seed/perf-seed.sh (perf-managers.sql을 먼저 적용한다)

\if :{?count}
\else
\set count 1000000
\endif

BEGIN;

DELETE FROM delivery_schema.p_delivery_routes WHERE created_by = 'LOAD';
DELETE FROM delivery_schema.p_deliveries WHERE created_by = 'LOAD';

CREATE TEMP TABLE hub_mgr ON COMMIT DROP AS
SELECT hub_id, array_agg(user_id ORDER BY user_id) AS ids
FROM user_schema.p_users
WHERE is_delivery_manager AND deleted_at IS NULL AND hub_id IS NOT NULL
GROUP BY hub_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM hub_schema.p_hubs h
               WHERE h.deleted_at IS NULL AND NOT EXISTS (SELECT 1 FROM hub_mgr m WHERE m.hub_id = h.hub_id)) THEN
        RAISE EXCEPTION '배송담당자가 없는 허브가 있다. perf-managers.sql을 먼저 적용한다';
    END IF;
END $$;

CREATE TEMP TABLE load_src ON COMMIT DROP AS
SELECT s.*,
       dm.ids[1 + (s.g % cardinality(dm.ids))]       AS pickup_mgr,
       dm.ids[1 + ((s.g / 3) % cardinality(dm.ids))] AS transfer_mgr,
       am.ids[1 + (s.g % cardinality(am.ids))]       AS last_mgr
FROM (SELECT g,
             gen_random_uuid()                                                     AS delivery_id,
             h.ids[1 + (g % cardinality(h.ids))]                                   AS departure_hub_id,
             h.ids[1 + ((g * 7 + g / 17) % cardinality(h.ids))]                    AS destination_hub_id,
             NOW() - (g % 180) * INTERVAL '1 day' - (g % 1440) * INTERVAL '1 minute' AS ts
      FROM generate_series(1, :count) AS g,
           (SELECT array_agg(hub_id ORDER BY hub_id) AS ids FROM hub_schema.p_hubs WHERE deleted_at IS NULL) AS h) s
         JOIN hub_mgr dm ON dm.hub_id = s.departure_hub_id
         JOIN hub_mgr am ON am.hub_id = s.destination_hub_id;

DO $$
DECLARE
    has_mh   boolean := EXISTS (SELECT 1 FROM information_schema.columns
                                WHERE table_schema = 'delivery_schema' AND table_name = 'p_delivery_routes'
                                  AND column_name = 'managing_hub_id');
    has_conf boolean := EXISTS (SELECT 1 FROM information_schema.columns
                                WHERE table_schema = 'delivery_schema' AND table_name = 'p_deliveries'
                                  AND column_name = 'confirmed_at');
    rc       text    := CASE WHEN has_mh THEN ', managing_hub_id, assigned_at' ELSE '' END;
BEGIN
    RAISE NOTICE 'managing_hub_id %, confirmed_at %', has_mh, has_conf;

    EXECUTE format($f$
        INSERT INTO delivery_schema.p_deliveries (delivery_id, status, pickup_address, delivery_address, recipient_name,
                                                  recipient_slack_id, order_id, supplier_company_id, receiver_company_id,
                                                  departure_hub_id, destination_hub_id%s,
                                                  created_at, created_by, updated_at, updated_by)
        SELECT delivery_id, 'DELIVERED', '부하 테스트 출발지 ' || g, '부하 테스트 도착지 ' || g, '부하수령인' || (g %% 1000),
               NULL, gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
               departure_hub_id, destination_hub_id%s,
               ts, 'LOAD', ts, 'LOAD'
        FROM load_src$f$,
        CASE WHEN has_conf THEN ', confirmed_at' ELSE '' END,
        CASE WHEN has_conf THEN ', ts' ELSE '' END);

    -- 집하: 공급업체 → 공급업체 허브. 담당 허브는 도착 허브(= 배송 출발 허브)
    EXECUTE format($f$
        INSERT INTO delivery_schema.p_delivery_routes (delivery_route_id, sequence, route_type, status, delivery_id,
                                                       departure_hub_id, arrival_hub_id, manager_id%s,
                                                       created_at, created_by, updated_at, updated_by)
        SELECT gen_random_uuid(), 1, 'PICKUP', 'ARRIVED', delivery_id, NULL, departure_hub_id, pickup_mgr%s,
               ts, 'LOAD', ts, 'LOAD'
        FROM load_src$f$, rc, CASE WHEN has_mh THEN ', departure_hub_id, ts' ELSE '' END);

    -- 허브 이동: 허브가 다를 때만. 담당 허브는 출발 허브
    EXECUTE format($f$
        INSERT INTO delivery_schema.p_delivery_routes (delivery_route_id, sequence, route_type,
                                                       expected_distance_km, expected_duration_minutes,
                                                       actual_distance_km, actual_duration_minutes, status, delivery_id,
                                                       departure_hub_id, arrival_hub_id, manager_id%s,
                                                       created_at, created_by, updated_at, updated_by)
        SELECT gen_random_uuid(), 2, 'HUB_TRANSFER', 120.50, 95, 123.10, 101, 'ARRIVED', delivery_id,
               departure_hub_id, destination_hub_id, transfer_mgr%s,
               ts, 'LOAD', ts, 'LOAD'
        FROM load_src
        WHERE departure_hub_id <> destination_hub_id$f$, rc, CASE WHEN has_mh THEN ', departure_hub_id, ts' ELSE '' END);

    -- 업체 배송: 요청업체 허브 → 요청업체. 담당 허브는 출발 허브(= 배송 목적지 허브)
    EXECUTE format($f$
        INSERT INTO delivery_schema.p_delivery_routes (delivery_route_id, sequence, route_type, status, delivery_id,
                                                       departure_hub_id, arrival_hub_id, manager_id%s,
                                                       created_at, created_by, updated_at, updated_by)
        SELECT gen_random_uuid(), CASE WHEN departure_hub_id = destination_hub_id THEN 2 ELSE 3 END, 'LAST_MILE', 'ARRIVED',
               delivery_id, destination_hub_id, NULL, last_mgr%s,
               ts, 'LOAD', ts, 'LOAD'
        FROM load_src$f$, rc, CASE WHEN has_mh THEN ', destination_hub_id, ts' ELSE '' END);
END $$;

COMMIT;

ANALYZE delivery_schema.p_deliveries;
ANALYZE delivery_schema.p_delivery_routes;

SELECT (SELECT count(*) FROM delivery_schema.p_deliveries WHERE created_by = 'LOAD')      AS deliveries,
       (SELECT count(*) FROM delivery_schema.p_delivery_routes WHERE created_by = 'LOAD') AS routes,
       (SELECT count(DISTINCT manager_id) FROM delivery_schema.p_delivery_routes WHERE created_by = 'LOAD') AS managers;
