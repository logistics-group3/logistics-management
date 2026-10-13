#!/usr/bin/env bash
# 공통 인프라 완료 기준 6개를 실제 컨테이너 환경에서 확인한다.
# 사용: script/e2e-check.sh          (이미 compose가 떠 있을 때)
#      script/e2e-check.sh --up     (bootJar 빌드 후 compose를 올리고 확인)
set -uo pipefail
cd "$(dirname "$0")/.."

GATEWAY="${GATEWAY:-http://localhost:8080}"
EUREKA="${EUREKA:-http://localhost:8761}"
SERVICES=(GATEWAY-SERVICE USER-SERVICE HUB-SERVICE COMPANY-SERVICE ORDER-SERVICE DELIVERY-SERVICE)
PASSWORD='Sparta!2026'
pass=0
fail=0

check() {
  local name="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    printf '  PASS  %s\n' "$name"; pass=$((pass + 1))
  else
    printf '  FAIL  %s (기대: %s, 실제: %s)\n' "$name" "$expected" "$actual"; fail=$((fail + 1))
  fi
}

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>/dev/null; }
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
signup() {
  curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/v1/auth/signup" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\",\"name\":\"검증\",\"slackId\":\"U0E2E\",\"affiliationType\":\"HUB\",\"affiliationName\":\"서울특별시 센터\"}"
}
login() {
  curl -s -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}" | json "d['data']['accessToken']"
}

if [[ "${1:-}" == "--up" ]]; then
  [[ -f .env ]] || cp .env.example .env
  ./gradlew bootJar -q && docker compose up -d --build
fi

echo "준비 대기 (Eureka 등록과 Gateway 라우팅)"
for _ in $(seq 1 60); do
  registered=$(curl -s -H 'Accept: application/json' "$EUREKA/eureka/apps" \
    | json "' '.join(sorted(a['name'] for a in d['applications'].get('application', [])))")
  ready=$(status -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"username":"none","password":"none"}')
  [[ "$(wc -w <<<"$registered")" -ge ${#SERVICES[@]} && "$ready" == "401" ]] && break
  sleep 5
done

suffix=$(date +%s | tail -c 7)
user="e2eu$suffix"
master="e2em$suffix"

echo "[1] docker compose up 한 번으로 전체 기동, Eureka 등록"
missing=$(for s in "${SERVICES[@]}"; do grep -qw "$s" <<<"$registered" || echo "$s"; done | xargs)
check "서비스 ${#SERVICES[@]}개 등록" "" "$missing"

echo "[2] 회원가입 → 로그인 → 토큰 발급"
check "회원가입 201" "201" "$(signup "$user")"
user_token=$(login "$user")
check "로그인 후 토큰 발급" "yes" "$([[ -n "$user_token" ]] && echo yes || echo no)"

echo "[3] Gateway 경유 호출 시 서비스가 @CurrentUser로 사용자 정보를 받음"
me=$(curl -s "$GATEWAY/api/v1/users/sample/me" -H "Authorization: Bearer $user_token")
check "@CurrentUser username" "$user" "$(json "d['data']['username']" <<<"$me")"

echo "[4] 토큰 없음·위조는 401, USER가 MASTER 전용 API 호출은 403"
check "토큰 없음 401" "401" "$(status "$GATEWAY/api/v1/users/sample/me")"
check "위조 토큰 401" "401" "$(status "$GATEWAY/api/v1/users/sample/me" -H "Authorization: Bearer ${user_token%?????}AAAAA")"
check "USER → MASTER 전용 403" "403" "$(status "$GATEWAY/api/v1/users/sample/admin" -H "Authorization: Bearer $user_token")"
signup "$master" >/dev/null && script/promote-master.sh "$master" >/dev/null
check "MASTER → MASTER 전용 200" "200" "$(status "$GATEWAY/api/v1/users/sample/admin" -H "Authorization: Bearer $(login "$master")")"

echo "[5] 외부에서 /internal/** 호출 차단"
check "/internal 404" "404" "$(status "$GATEWAY/internal/users/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $user_token")"

echo "[6] 클라이언트가 보낸 X-User-Role: MASTER 무시"
check "위조 role 헤더로 MASTER 전용 403" "403" \
  "$(status "$GATEWAY/api/v1/users/sample/admin" -H "Authorization: Bearer $user_token" -H 'X-User-Role: MASTER')"
forged=$(curl -s "$GATEWAY/api/v1/users/sample/me" -H "Authorization: Bearer $user_token" -H 'X-Username: evil' -H 'X-User-Role: MASTER')
check "위조 헤더가 서비스에 전달되지 않음" "$user USER" "$(json "d['data']['username'] + ' ' + d['data']['role']" <<<"$forged")"

echo
echo "결과: PASS $pass / FAIL $fail"
[[ $fail -eq 0 ]]
