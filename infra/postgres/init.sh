#!/bin/bash
# PostgreSQL 최초 기동 시 1회 실행 (/docker-entrypoint-initdb.d).
# 서비스별 스키마 + 전용 계정을 만들고, 각 계정은 자기 스키마에만 권한을 갖는다.
# 비밀번호는 .env → docker-compose 환경 변수로 주입된다. (SA 문서의 init.sql을 환경 변수 사용을 위해 셸 스크립트로 작성)
set -euo pipefail

create_service_schema() {
  local schema="$1" app_user="$2" password="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER ${app_user} WITH PASSWORD '${password}';
    CREATE SCHEMA IF NOT EXISTS ${schema} AUTHORIZATION ${app_user};
    ALTER ROLE ${app_user} SET search_path = ${schema};
EOSQL
}

create_service_schema user_schema     user_app     "$USER_DB_PASSWORD"
create_service_schema hub_schema      hub_app      "$HUB_DB_PASSWORD"
create_service_schema company_schema  company_app  "$COMPANY_DB_PASSWORD"
create_service_schema order_schema    order_app    "$ORDER_DB_PASSWORD"
create_service_schema delivery_schema delivery_app "$DELIVERY_DB_PASSWORD"
