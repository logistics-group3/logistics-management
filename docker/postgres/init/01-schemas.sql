-- ADR-03: DB 1개 + 서비스별 스키마·전용 계정. 각 계정은 자기 스키마에만 권한이 있다.
-- 비밀번호는 로컬 개발용이다. 운영에서는 시크릿으로 주입한다.
REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE ROLE user_app     LOGIN PASSWORD 'user_app_pw';
CREATE ROLE hub_app      LOGIN PASSWORD 'hub_app_pw';
CREATE ROLE company_app  LOGIN PASSWORD 'company_app_pw';
CREATE ROLE order_app    LOGIN PASSWORD 'order_app_pw';
CREATE ROLE delivery_app LOGIN PASSWORD 'delivery_app_pw';

CREATE SCHEMA user_schema     AUTHORIZATION user_app;
CREATE SCHEMA hub_schema      AUTHORIZATION hub_app;
CREATE SCHEMA company_schema  AUTHORIZATION company_app;
CREATE SCHEMA order_schema    AUTHORIZATION order_app;
CREATE SCHEMA delivery_schema AUTHORIZATION delivery_app;

ALTER ROLE user_app     SET search_path = user_schema;
ALTER ROLE hub_app      SET search_path = hub_schema;
ALTER ROLE company_app  SET search_path = company_schema;
ALTER ROLE order_app    SET search_path = order_schema;
ALTER ROLE delivery_app SET search_path = delivery_schema;
