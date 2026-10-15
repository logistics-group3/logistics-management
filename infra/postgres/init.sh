#!/usr/bin/env bash
# PostgreSQL의 빈 볼륨을 처음 기동할 때만 실행한다. 기존 계정 비밀번호는 바꾸지 않는다.
# 출처: kmh/base-setting의 환경 변수 초기화, feature/seungkeun의 public 스키마 격리.
set -euo pipefail

: "${POSTGRES_USER:?POSTGRES_USER가 필요하다}"
: "${POSTGRES_DB:?POSTGRES_DB가 필요하다}"
: "${USER_DB_PASSWORD:?USER_DB_PASSWORD가 필요하다}"
: "${HUB_DB_PASSWORD:?HUB_DB_PASSWORD가 필요하다}"
: "${COMPANY_DB_PASSWORD:?COMPANY_DB_PASSWORD가 필요하다}"
: "${ORDER_DB_PASSWORD:?ORDER_DB_PASSWORD가 필요하다}"
: "${DELIVERY_DB_PASSWORD:?DELIVERY_DB_PASSWORD가 필요하다}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL

create_service_schema() {
  local schema="$1" app_user="$2" password="$3"
  # Note: psql의 식별자·문자열 인용을 사용해 비밀번호의 작은따옴표도 SQL로 해석되지 않게 한다.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=schema_name="$schema" --set=app_user="$app_user" --set=password="$password" <<'SQL'
CREATE USER :"app_user" WITH PASSWORD :'password';
CREATE SCHEMA IF NOT EXISTS :"schema_name" AUTHORIZATION :"app_user";
ALTER ROLE :"app_user" SET search_path = :"schema_name";
SQL
}

create_service_schema user_schema user_app "$USER_DB_PASSWORD"
create_service_schema hub_schema hub_app "$HUB_DB_PASSWORD"
create_service_schema company_schema company_app "$COMPANY_DB_PASSWORD"
create_service_schema order_schema order_app "$ORDER_DB_PASSWORD"
create_service_schema delivery_schema delivery_app "$DELIVERY_DB_PASSWORD"
