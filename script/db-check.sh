#!/usr/bin/env bash
# 현재 Compose 프로젝트의 서비스별 DB 인증과 스키마 권한을 읽기 전용으로 검증한다.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose exec -T postgres bash -euo pipefail <<'BASH'
# Note: postgres 이미지 기본 pg_hba는 소켓과 127.0.0.1 접속을 trust로 둬 비밀번호를 보지 않는다.
# 컨테이너 자신의 IP로 접속해야 서비스와 같은 scram-sha-256 비밀번호 인증을 거친다.
host=$(hostname -i | awk '{print $1}')

check_service() {
  local schema="$1" app_user="$2" password="$3" result
  result=$(PGPASSWORD="$password" psql --host="$host" --username "$app_user" \
    --dbname "$POSTGRES_DB" --no-password -v ON_ERROR_STOP=1 -At \
    --set=schema_name="$schema" --set=app_user="$app_user" <<'SQL'
SELECT current_user = :'app_user'
   AND current_schema() = :'schema_name'
   AND has_schema_privilege(current_user, :'schema_name', 'USAGE')
   AND has_schema_privilege(current_user, :'schema_name', 'CREATE')
   AND NOT has_schema_privilege(current_user, 'public', 'USAGE')
   AND NOT has_schema_privilege(current_user, 'public', 'CREATE')
   AND NOT EXISTS (
       SELECT 1 FROM pg_namespace
       WHERE nspname IN ('user_schema', 'hub_schema', 'company_schema', 'order_schema', 'delivery_schema')
         AND nspname <> :'schema_name'
         AND (has_schema_privilege(current_user, oid, 'USAGE')
              OR has_schema_privilege(current_user, oid, 'CREATE'))
   );
SQL
  )
  if [[ "$result" != t ]]; then
    printf 'FAIL %s 인증·스키마 격리\n' "$app_user" >&2
    return 1
  fi
  printf 'PASS %s 인증·스키마 격리\n' "$app_user"
}

check_service user_schema user_app "$USER_DB_PASSWORD"
check_service hub_schema hub_app "$HUB_DB_PASSWORD"
check_service company_schema company_app "$COMPANY_DB_PASSWORD"
check_service order_schema order_app "$ORDER_DB_PASSWORD"
check_service delivery_schema delivery_app "$DELIVERY_DB_PASSWORD"
BASH
