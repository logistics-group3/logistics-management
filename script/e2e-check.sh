#!/usr/bin/env bash
# 공통 인프라 완료 기준 6개를 실제 컨테이너 환경에서 확인한다.
# 사용: script/e2e-check.sh                     (이미 compose가 떠 있을 때)
#      script/e2e-check.sh --up                (compose를 빌드·기동한 뒤 확인. 실패하면 바로 종료)
#      script/e2e-check.sh --with-cache-ttl    (권한 재확인 60초 캐시와 만료 후 재조회까지 확인, 약 65초 추가)
set -uo pipefail
cd "$(dirname "$0")/.."

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
CURL=(curl -s --max-time 10)
status() { "${CURL[@]}" -o /dev/null -w '%{http_code}' "$@"; }
code() { "${CURL[@]}" "$@" | json "d.get('code')"; }
signup() {
  "${CURL[@]}" -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/v1/auth/signup" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\",\"name\":\"검증\",\"slackId\":\"U0E2E\",\"affiliationType\":\"HUB\",\"affiliationName\":\"서울특별시 센터\"}"
}
login() {
  "${CURL[@]}" -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"${2:-$PASSWORD}\"}" | json "d['data']['accessToken']"
}
# .env를 셸 코드로 실행하지 않고 KEY=VALUE 한 줄만 읽는다. 환경 변수가 있으면 그 값을 쓴다.
env_value() {
  local file
  for file in .env .env.example; do
    [[ -f "$file" ]] || continue
    grep -E "^$1=" "$file" | tail -n 1 | cut -d= -f2- && return 0
  done
  return 1
}

up=false
cache_ttl=false
for arg in "$@"; do
  case "$arg" in
    --up) up=true ;;
    --with-cache-ttl) cache_ttl=true ;;
  esac
done

if [[ "$up" == true ]]; then
  [[ -f .env ]] || cp .env.example .env
  if ! docker compose up -d --build; then
    echo "docker compose 빌드·기동 실패" >&2
    exit 1
  fi
fi

# .env의 포트 값을 셸 코드로 실행하지 않고, 이 Compose 프로젝트의 실제 공개 포트를 조회한다.
compose_port() {
  local binding port
  binding=$(docker compose port "$1" "$2" 2>/dev/null) || return 1
  binding="${binding%%$'\n'*}"
  port="${binding##*:}"
  [[ "$port" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$port"
}
resolve_url() {
  local supplied="$1" override_port="$2" service="$3" internal_port="$4" port
  if [[ -n "$supplied" ]]; then
    printf '%s' "$supplied"
    return 0
  fi
  port="$override_port"
  if [[ -z "$port" ]]; then
    port=$(compose_port "$service" "$internal_port") || {
      printf '현재 Compose 프로젝트의 %s 포트를 찾을 수 없다. 먼저 기동하거나 검증 URL을 명시한다.\n' "$service" >&2
      return 1
    }
  fi
  [[ "$port" =~ ^[0-9]+$ ]] || return 1
  printf 'http://localhost:%s' "$port"
}
GATEWAY=$(resolve_url "${GATEWAY:-}" "${GATEWAY_PORT:-}" gateway-service 8080) || exit 1
EUREKA=$(resolve_url "${EUREKA:-}" "${EUREKA_PORT:-}" eureka-server 8761) || exit 1

echo "준비 대기 (Eureka에 UP 상태로 등록, Gateway 라우팅)"
ready_ok=false
for _ in $(seq 1 60); do
  # 인스턴스가 하나라도 UP인 서비스 이름만 센다
  registered=$("${CURL[@]}" -H 'Accept: application/json' "$EUREKA/eureka/apps" \
    | json "' '.join(sorted(a['name'] for a in d['applications'].get('application', []) if any(i['status'] == 'UP' for i in a['instance'])))")
  ready=$(status -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"username":"none","password":"none"}')
  if [[ "$(wc -w <<<"$registered")" -ge ${#SERVICES[@]} && "$ready" == "401" ]]; then
    ready_ok=true
    break
  fi
  sleep 5
done
if [[ "$ready_ok" != true ]]; then
  echo "5분 안에 준비되지 않았다. 등록: [$registered], 로그인 응답: $ready" >&2
  exit 1
fi

suffix=$(date +%s | tail -c 7)
user="e2eu$suffix"
master_username="${MASTER_USERNAME:-$(env_value MASTER_USERNAME)}"
master_password="${MASTER_PASSWORD:-$(env_value MASTER_PASSWORD)}"

echo "[1] docker compose up 한 번으로 전체 기동, Eureka 등록"
missing=$(for s in "${SERVICES[@]}"; do grep -qw "$s" <<<"$registered" || echo "$s"; done | xargs)
check "서비스 ${#SERVICES[@]}개 UP 등록" "" "$missing"

echo "[2] 회원가입 → 로그인 → 토큰 발급"
check "회원가입 201" "201" "$(signup "$user")"
user_token=$(login "$user")
check "로그인 후 토큰 발급" "yes" "$([[ -n "$user_token" ]] && echo yes || echo no)"

echo "[3] Gateway 경유 호출 시 서비스가 @CurrentUser로 사용자 정보를 받음"
me=$("${CURL[@]}" "$GATEWAY/api/v1/users/me" -H "Authorization: Bearer $user_token")
check "@CurrentUser username" "$user" "$(json "d['data']['username']" <<<"$me")"

echo "[4] 토큰 없음·위조는 401, USER가 MASTER 전용 API 호출은 403"
check "토큰 없음 401" "401" "$(status "$GATEWAY/api/v1/users/me")"
check "토큰 없음 code UNAUTHORIZED" "UNAUTHORIZED" "$(code "$GATEWAY/api/v1/users/me")"
check "위조 토큰 401" "401" "$(status "$GATEWAY/api/v1/users/me" -H "Authorization: Bearer ${user_token%?????}AAAAA")"
check "USER → MASTER 전용 403" "403" "$(status "$GATEWAY/api/v1/users" -H "Authorization: Bearer $user_token")"
check "USER → MASTER 전용 code FORBIDDEN" "FORBIDDEN" "$(code "$GATEWAY/api/v1/users" -H "Authorization: Bearer $user_token")"
# MASTER는 user-service가 기동할 때 .env의 MASTER_USERNAME으로 만든다
master_token=$(login "$master_username" "$master_password")
check "기동 시 만든 MASTER 로그인" "yes" "$([[ -n "$master_token" ]] && echo yes || echo no)"
check "MASTER → MASTER 전용 200" "200" "$(status "$GATEWAY/api/v1/users" -H "Authorization: Bearer $master_token")"

echo "[5] 외부에서 /internal/** 호출 차단"
check "/internal 404" "404" "$(status "$GATEWAY/internal/users/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $user_token")"
check "/internal code NOT_FOUND" "NOT_FOUND" "$(code "$GATEWAY/internal/users/00000000-0000-0000-0000-000000000000")"

echo "[6] 클라이언트가 보낸 X-User-Role: MASTER 무시"
check "위조 role 헤더로 MASTER 전용 403" "403" \
  "$(status "$GATEWAY/api/v1/users" -H "Authorization: Bearer $user_token" -H 'X-User-Role: MASTER')"
forged=$("${CURL[@]}" "$GATEWAY/api/v1/users/me" -H "Authorization: Bearer $user_token" -H 'X-Username: evil' -H 'X-User-Role: MASTER')
check "위조 헤더가 서비스에 전달되지 않음" "$user USER" "$(json "d['data']['username'] + ' ' + d['data']['role']" <<<"$forged")"

if [[ "$cache_ttl" == true ]]; then
  echo "[추가] 권한 재확인 캐시(60초)와 만료 후 user-service 재조회"
  ttl_user="e2et$suffix"
  signup "$ttl_user" >/dev/null
  ttl_token=$(login "$ttl_user")
  check "캐시 적재 200" "200" "$(status "$GATEWAY/api/v1/users/me" -H "Authorization: Bearer $ttl_token")"
  started=$(date +%s)
  script/promote-master.sh "$ttl_user" >/dev/null
  check "DB role 변경 직후에도 캐시된 값으로 200" "200" "$(status "$GATEWAY/api/v1/users/me" -H "Authorization: Bearer $ttl_token")"
  sleep $(( 63 - ($(date +%s) - started) ))
  expired=$("${CURL[@]}" "$GATEWAY/api/v1/users/me" -H "Authorization: Bearer $ttl_token")
  check "만료 후 재조회로 TOKEN_ROLE_MISMATCH" "TOKEN_ROLE_MISMATCH" "$(json "d['code']" <<<"$expired")"
fi

echo
echo "결과: PASS $pass / FAIL $fail"
[[ $fail -eq 0 ]]
