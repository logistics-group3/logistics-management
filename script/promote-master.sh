#!/usr/bin/env bash
# 로컬 개발용: 가입한 사용자를 MASTER로 올린다. 사용: script/promote-master.sh <username>
# Gateway는 권한을 60초 캐시하므로, 이미 로그인한 토큰은 최대 60초 뒤 TOKEN_ROLE_MISMATCH가 된다. 다시 로그인한다.
set -euo pipefail
cd "$(dirname "$0")/.."

username="${1:?username을 지정한다}"
docker compose exec -T postgres sh -c \
  "psql -U \"\$POSTGRES_USER\" -d logistics -v ON_ERROR_STOP=1 -qc \"UPDATE user_schema.p_users SET role = 'MASTER' WHERE username = '${username}'\""
echo "MASTER로 변경: ${username}"
