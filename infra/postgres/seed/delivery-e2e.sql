-- 배송 E2E 확인용 로컬 더미 데이터. script/delivery-e2e-check.sh가 적용한다(직접 적용 방법은 delivery-service/docs/e2e.md).
-- 운영 마이그레이션이 아니다. user-service, company-service가 Flyway로 테이블을 만든 뒤에 적용한다.
-- ID를 고정해 스크립트와 수동 호출에서 같은 값을 쓴다. 이미 있으면 건너뛰므로 여러 번 적용해도 된다.
-- 비밀번호는 모두 Sparta!2026 (BCrypt cost 10). 로컬 전용 값이다.
-- 업체 두 곳을 같은 허브(서울특별시 센터)에 둔다. hub I-03(허브 간 경로)이 없으면 다른 허브 사이 배송은 만들 수 없다.

\set seoul_hub   '6f39d519-4947-4a95-a454-58c25728f388'
\set gyeonggi_north_hub 'cfa35654-5a89-49e3-a6af-1d538093a49a'
\set password    '$2a$10$NZepRVYm.M6dyN04cxx6wODCVUXfbgxpQg/7JKAxj3DwrO33xIZcy'

BEGIN;

-- 계정. 회원가입은 항상 USER라 MASTER와 배송담당자는 여기서 바로 넣는다.
INSERT INTO user_schema.p_users (user_id, username, password, name, slack_id, affiliation_type, affiliation_name,
                                 role, is_delivery_manager, hub_id, created_at, created_by, updated_at, updated_by)
VALUES ('e2e00000-0000-4000-8000-000000000001', 'dmaster', :'password', '배송E2E 마스터', 'U0E2EMASTER', 'HUB', '서울특별시 센터',
        'MASTER', FALSE, NULL, NOW(), 'SEED', NOW(), 'SEED'),
       -- 서울 허브 배송담당자 2명: 자동 배정 후보, 재배정 대상
       ('e2e00000-0000-4000-8000-000000000002', 'dmgr1', :'password', '배송E2E 서울담당1', 'U0E2EMGR1', 'HUB', '서울특별시 센터',
        'USER', TRUE, :'seoul_hub', NOW(), 'SEED', NOW(), 'SEED'),
       ('e2e00000-0000-4000-8000-000000000003', 'dmgr2', :'password', '배송E2E 서울담당2', 'U0E2EMGR2', 'HUB', '서울특별시 센터',
        'USER', TRUE, :'seoul_hub', NOW(), 'SEED', NOW(), 'SEED'),
       -- 다른 허브 배송담당자: 담당 아닌 배송 조회(403), 허브가 다른 재배정(MANAGER_HUB_MISMATCH)
       ('e2e00000-0000-4000-8000-000000000004', 'dmgr3', :'password', '배송E2E 경기북부담당', 'U0E2EMGR3', 'HUB', '경기 북부 센터',
        'USER', TRUE, :'gyeonggi_north_hub', NOW(), 'SEED', NOW(), 'SEED'),
       -- 업체 담당자: 공급업체, 수령업체(주문자)
       ('e2e00000-0000-4000-8000-000000000005', 'dsupplier', :'password', '배송E2E 공급담당', 'U0E2ESUP', 'COMPANY', '배송E2E 공급업체',
        'USER', FALSE, NULL, NOW(), 'SEED', NOW(), 'SEED'),
       ('e2e00000-0000-4000-8000-000000000006', 'dreceiver', :'password', '배송E2E 수령담당', 'U0E2ERCV', 'COMPANY', '배송E2E 수령업체',
        'USER', FALSE, NULL, NOW(), 'SEED', NOW(), 'SEED'),
       -- 배송담당자가 아닌 일반 사용자: 재배정 대상이 될 수 없음(DELIVERY_MANAGER_NOT_FOUND)
       ('e2e00000-0000-4000-8000-000000000007', 'duser', :'password', '배송E2E 일반', 'U0E2EUSER', 'HUB', '서울특별시 센터',
        'USER', FALSE, NULL, NOW(), 'SEED', NOW(), 'SEED')
ON CONFLICT DO NOTHING;

INSERT INTO company_schema.p_companies (company_id, name, address, hub_id, manager_id,
                                        created_at, created_by, updated_at, updated_by)
VALUES ('e2e00000-0000-4000-8000-000000000101', '배송E2E 공급업체', '서울특별시 송파구 올림픽로 300', :'seoul_hub',
        'e2e00000-0000-4000-8000-000000000005', NOW(), 'SEED', NOW(), 'SEED'),
       ('e2e00000-0000-4000-8000-000000000102', '배송E2E 수령업체', '서울특별시 강남구 테헤란로 152', :'seoul_hub',
        'e2e00000-0000-4000-8000-000000000006', NOW(), 'SEED', NOW(), 'SEED')
ON CONFLICT DO NOTHING;

INSERT INTO company_schema.p_products (product_id, name, description, price, company_id,
                                       created_at, created_by, updated_at, updated_by)
VALUES ('e2e00000-0000-4000-8000-000000000201', '배송E2E 상품', '배송 E2E 확인용 상품', 10000,
        'e2e00000-0000-4000-8000-000000000101', NOW(), 'SEED', NOW(), 'SEED')
ON CONFLICT DO NOTHING;

COMMIT;
