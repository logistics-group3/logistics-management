-- =====================================================================
-- infra/postgres/init.sql (SA 3.4)
-- postgres 컨테이너가 "처음" 뜰 때(pgdata 볼륨이 비어 있을 때) 한 번만 실행된다.
-- 수정 후 다시 적용하려면: docker compose down -v  (볼륨 삭제 → 데이터도 삭제됨)
--
-- 서비스별 스키마 + 전용 계정. 각 계정은 자기 스키마에만 권한을 갖는다.
-- 비밀번호는 로컬 전용이며 .env 의 *_DB_PASSWORD 와 같아야 한다.
-- =====================================================================

-- user-service
CREATE USER user_app WITH PASSWORD 'user_local_pw';
CREATE SCHEMA IF NOT EXISTS user_schema AUTHORIZATION user_app;
ALTER ROLE user_app SET search_path = user_schema;

-- hub-service
CREATE USER hub_app WITH PASSWORD 'hub_local_pw';
CREATE SCHEMA IF NOT EXISTS hub_schema AUTHORIZATION hub_app;
ALTER ROLE hub_app SET search_path = hub_schema;

-- company-service
CREATE USER company_app WITH PASSWORD 'company_local_pw';
CREATE SCHEMA IF NOT EXISTS company_schema AUTHORIZATION company_app;
ALTER ROLE company_app SET search_path = company_schema;

-- order-service
CREATE USER order_app WITH PASSWORD 'order_local_pw';
CREATE SCHEMA IF NOT EXISTS order_schema AUTHORIZATION order_app;
ALTER ROLE order_app SET search_path = order_schema;

-- delivery-service
CREATE USER delivery_app WITH PASSWORD 'delivery_local_pw';
CREATE SCHEMA IF NOT EXISTS delivery_schema AUTHORIZATION delivery_app;
ALTER ROLE delivery_app SET search_path = delivery_schema;

