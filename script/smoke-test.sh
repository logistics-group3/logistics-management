#!/usr/bin/env bash
# =====================================================================
# 공통 인프라 완료 기준 6가지 자동 확인 (Notion "공통 인프라 개발 범위" 4장)
# 사용: docker compose up 후  ./script/smoke-test.sh
# 필요: curl
# =====================================================================
set -uo pipefail

GW=${GATEWAY_URL:-http://localhost:8080}
EUREKA=${EUREKA_URL:-http://localhost:8761}
MASTER_PASSWORD=${MASTER_PASSWORD:-Master!2026}
USERNAME="smoke$(( RANDOM % 9000 + 1000 ))"
PASSWORD='Sparta!2026'

PASS=0; FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
fail() { echo "  ❌ $1"; FAIL=$((FAIL+1)); }

# check <설명> <기대 HTTP 코드> <curl 인자...>
check() {
  local desc=$1 expected=$2; shift 2
  local code
  code=$(curl -s -o /tmp/smoke_body -w '%{http_code}' "$@")
  if [ "$code" = "$expected" ]; then ok "$desc ($code)"; else fail "$desc (기대 $expected, 실제 $code) $(cat /tmp/smoke_body)"; fi
}

token_of() {
  curl -s -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

echo "1) Eureka 등록 확인"
APPS=$(curl -s -H 'Accept: application/json' "$EUREKA/eureka/apps")
for svc in GATEWAY-SERVICE USER-SERVICE HUB-SERVICE COMPANY-SERVICE ORDER-SERVICE DELIVERY-SERVICE; do
  if echo "$APPS" | grep -q "\"$svc\""; then ok "$svc 등록됨"; else fail "$svc 미등록"; fi
done

echo "2) 회원가입 → 로그인 → 토큰 발급"
check "회원가입 ($USERNAME)" 201 -X POST "$GW/api/v1/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"name\":\"smoke\",\"slackId\":\"U0SMOKE\",\"affiliationType\":\"COMPANY\",\"affiliationName\":\"testcompany\"}"
check "중복 가입 → 409" 409 -X POST "$GW/api/v1/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"name\":\"smoke\",\"slackId\":\"U0SMOKE\",\"affiliationType\":\"COMPANY\",\"affiliationName\":\"testcompany\"}"
check "검증 실패 → 400" 400 -X POST "$GW/api/v1/auth/signup" -H 'Content-Type: application/json' -d '{"username":"AB"}'
check "로그인 실패 → 401" 401 -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"Wrong!2026\"}"
USER_TOKEN=$(token_of "$USERNAME" "$PASSWORD")
MASTER_TOKEN=$(token_of master "$MASTER_PASSWORD")
[ -n "$USER_TOKEN" ]   && ok "USER 토큰 발급"   || fail "USER 토큰 발급 실패"
[ -n "$MASTER_TOKEN" ] && ok "MASTER 토큰 발급" || fail "MASTER 토큰 발급 실패 (MASTER_PASSWORD 확인)"

echo "3) 토큰으로 Gateway 경유 → 서비스에서 @CurrentUser 수신"
check "GET /api/v1/hubs/sample/me" 200 "$GW/api/v1/hubs/sample/me" -H "Authorization: Bearer $USER_TOKEN"
grep -q "\"username\":\"$USERNAME\"" /tmp/smoke_body && ok "응답에 username=$USERNAME 포함" || fail "응답에 사용자 정보 없음: $(cat /tmp/smoke_body)"

echo "4) 401 / 403"
check "토큰 없음 → 401" 401 "$GW/api/v1/hubs/sample/me"
check "위조 토큰 → 401" 401 "$GW/api/v1/hubs/sample/me" -H "Authorization: Bearer ${USER_TOKEN}x"
check "USER 가 MASTER 전용 API → 403" 403 "$GW/api/v1/hubs/sample/master" -H "Authorization: Bearer $USER_TOKEN"
check "MASTER 가 MASTER 전용 API → 200" 200 "$GW/api/v1/hubs/sample/master" -H "Authorization: Bearer $MASTER_TOKEN"

echo "5) /internal/** 외부 차단"
check "GET /internal/users/{id} → 404" 404 "$GW/internal/users/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $MASTER_TOKEN"

echo "6) 클라이언트가 보낸 X-User-Role 무시"
check "X-User-Role: MASTER 위조 → 403" 403 "$GW/api/v1/hubs/sample/master" \
  -H "Authorization: Bearer $USER_TOKEN" -H 'X-User-Role: MASTER'
check "토큰 없이 X-User-* 헤더만 → 401" 401 "$GW/api/v1/hubs/sample/me" \
  -H 'X-User-Id: 3fa85f64-5717-4562-b3fc-2c963f66afa6' -H 'X-Username: hacker' -H 'X-User-Role: MASTER'

echo ""
echo "결과: 통과 $PASS / 실패 $FAIL"
[ "$FAIL" -eq 0 ]
