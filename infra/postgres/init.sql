-- 로컬 개발 전용. 비밀번호는 .env.example 값과 맞춘다.
-- Note: 인스턴스는 하나지만 서비스마다 스키마와 계정을 나눠, 각 서비스가 자기 스키마에만 접근하게 한다(DB per service의 논리적 분리). 나중에 인스턴스를 나눠도 접속 정보만 바뀐다.

CREATE SCHEMA IF NOT EXISTS user_schema;
CREATE SCHEMA IF NOT EXISTS hub_schema;
CREATE SCHEMA IF NOT EXISTS company_schema;
CREATE SCHEMA IF NOT EXISTS order_schema;
CREATE SCHEMA IF NOT EXISTS delivery_schema;

CREATE USER user_app WITH PASSWORD 'user_local_pw';
CREATE USER hub_app WITH PASSWORD 'hub_local_pw';
CREATE USER company_app WITH PASSWORD 'company_local_pw';
CREATE USER order_app WITH PASSWORD 'order_local_pw';
CREATE USER delivery_app WITH PASSWORD 'delivery_local_pw';

GRANT USAGE, CREATE ON SCHEMA user_schema TO user_app;
GRANT USAGE, CREATE ON SCHEMA hub_schema TO hub_app;
GRANT USAGE, CREATE ON SCHEMA company_schema TO company_app;
GRANT USAGE, CREATE ON SCHEMA order_schema TO order_app;
GRANT USAGE, CREATE ON SCHEMA delivery_schema TO delivery_app;

ALTER ROLE user_app SET search_path = user_schema;
ALTER ROLE hub_app SET search_path = hub_schema;
ALTER ROLE company_app SET search_path = company_schema;
ALTER ROLE order_app SET search_path = order_schema;
ALTER ROLE delivery_app SET search_path = delivery_schema;
