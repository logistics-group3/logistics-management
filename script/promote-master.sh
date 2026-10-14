#!/usr/bin/env bash
# 로컬 개발용: 가입한 사용자를 MASTER로 올린다. 사용: script/promote-master.sh <username>
# Gateway는 권한을 60초 캐시하므로, 이미 로그인한 토큰은 최대 60초 뒤 TOKEN_ROLE_MISMATCH가 된다. 다시 로그인한다.
set -euo pipefail
cd "$(dirname "$0")/.."

username="${1:?username을 지정한다}"
if [[ ! "$username" =~ ^[a-z0-9]{4,10}$ ]]; then
  echo "username 형식이 아니다(소문자·숫자 4~10자): $username" >&2
  exit 1
fi

# psql 변수(:'username')로 넘겨 SQL 문자열에 직접 끼워 넣지 않는다
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q -v username="$1"' _ "$username" <<'SQL'
UPDATE user_schema.p_users
SET role = 'MASTER', updated_at = now(), updated_by = 'SYSTEM'
WHERE username = :'username';
SQL
echo "MASTER로 변경: ${username}"
