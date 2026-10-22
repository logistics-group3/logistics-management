-- 측정 전용 시드(delivery-service/docs/perf-master.md 4절). 운영 마이그레이션이 아니다.
-- 허브마다 배송담당자를 10명으로 맞춘다. 이미 있는 배송담당자(E2E의 dmgr1·dmgr2·dmgr3 등)를 세고 모자란 만큼만 넣는다.
-- 여러 번 적용해도 10명을 넘지 않는다. 비밀번호는 delivery-e2e.sql과 같은 로컬 값(Sparta!2026)이다.
-- 사용: load-test/seed/perf-seed.sh (직접: psql ... < load-test/seed/perf-managers.sql)

\set password '$2a$10$NZepRVYm.M6dyN04cxx6wODCVUXfbgxpQg/7JKAxj3DwrO33xIZcy'

BEGIN;

WITH hubs AS (SELECT hub_id, name, row_number() OVER (ORDER BY hub_id) AS hub_no
              FROM hub_schema.p_hubs
              WHERE deleted_at IS NULL),
     have AS (SELECT hub_id, count(*) AS cnt
              FROM user_schema.p_users
              WHERE is_delivery_manager AND deleted_at IS NULL AND hub_id IS NOT NULL
              GROUP BY hub_id)
INSERT INTO user_schema.p_users (user_id, username, password, name, slack_id, affiliation_type, affiliation_name,
                                 role, is_delivery_manager, hub_id, created_at, created_by, updated_at, updated_by)
SELECT ('e2e20000-0000-4000-8000-' || lpad((h.hub_no * 100 + n)::text, 12, '0'))::uuid,
       'pm' || lpad(h.hub_no::text, 2, '0') || lpad(n::text, 2, '0'),
       :'password', '측정담당 ' || h.hub_no || '-' || n, 'UPERF' || h.hub_no || '_' || n,
       'HUB', h.name, 'USER', TRUE, h.hub_id, NOW(), 'PERF', NOW(), 'PERF'
FROM hubs h
         LEFT JOIN have v USING (hub_id)
         CROSS JOIN LATERAL generate_series(1, greatest(0, 10 - coalesce(v.cnt, 0))) AS n
ON CONFLICT DO NOTHING;

COMMIT;

SELECT h.name, count(u.user_id) AS managers
FROM hub_schema.p_hubs h
         LEFT JOIN user_schema.p_users u ON u.hub_id = h.hub_id AND u.is_delivery_manager AND u.deleted_at IS NULL
WHERE h.deleted_at IS NULL
GROUP BY h.name
ORDER BY h.name;
