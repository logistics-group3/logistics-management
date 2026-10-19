#!/usr/bin/env bash
# 배송 E2E 확인. 시드 데이터를 넣고 Gateway로 호출해 주문 → 배송 생성 → 구간 진행 → 배송 완료 → 삭제까지 전체 연결과
# 배송 API(D-01~D-05)의 권한·검증 규칙, 주문 취소·삭제에 따른 내부 API(I-10, I-11, I-13) 동작을 확인한다.
# 사용: script/delivery-e2e-check.sh            (compose가 떠 있을 때. 먼저 script/run.sh)
#      script/delivery-e2e-check.sh --no-seed  (시드를 다시 넣지 않는다)
# 시드: infra/postgres/seed/delivery-e2e.sql, 설명: delivery-service/docs/e2e.md
set -uo pipefail
cd "$(dirname "$0")/.."

SERVICES=(GATEWAY-SERVICE USER-SERVICE HUB-SERVICE COMPANY-SERVICE ORDER-SERVICE DELIVERY-SERVICE)
SEED_FILE=infra/postgres/seed/delivery-e2e.sql
PASSWORD='Sparta!2026'
# 시드의 고정 ID
MGR1=e2e00000-0000-4000-8000-000000000002
MGR2=e2e00000-0000-4000-8000-000000000003
MGR3=e2e00000-0000-4000-8000-000000000004
PLAIN_USER=e2e00000-0000-4000-8000-000000000007
SUPPLIER_COMPANY=e2e00000-0000-4000-8000-000000000101
RECEIVER_COMPANY=e2e00000-0000-4000-8000-000000000102
PRODUCT=e2e00000-0000-4000-8000-000000000201

seed=true
for arg in "$@"; do
  case "$arg" in
    --no-seed) seed=false ;;
    -h|--help) sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "알 수 없는 옵션: $arg" >&2; exit 2 ;;
  esac
done

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
skip() { printf '  SKIP  %s\n' "$1"; }

# call METHOD PATH TOKEN BODY [헤더...]: 응답 코드는 http_status, 본문은 http_body에 둔다.
http_status=''
http_body=''
call() {
  local method="$1" path="$2" token="$3" body="$4" header out
  shift 4
  local args=(-s --max-time 30 -X "$method" "$GATEWAY$path" -w $'\n%{http_code}')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  for header in "$@"; do args+=(-H "$header"); done
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  out=$(curl "${args[@]}")
  http_status="${out##*$'\n'}"
  http_body="${out%$'\n'*}"
}
# field 'd[...]': 마지막 응답 본문에서 값을 꺼낸다. 없으면 빈 문자열.
field() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" <<<"$http_body" 2>/dev/null; }
new_uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }

login() {
  call POST /api/v1/auth/login '' "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}"
  field "d['data']['accessToken']"
}

compose_port() {
  local binding port
  binding=$(docker compose port "$1" "$2" 2>/dev/null) || return 1
  binding="${binding%%$'\n'*}"
  port="${binding##*:}"
  [[ "$port" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$port"
}
service_url() {
  local supplied="$1" service="$2" internal_port="$3" port
  if [[ -n "$supplied" ]]; then printf '%s' "$supplied"; return 0; fi
  port=$(compose_port "$service" "$internal_port") || {
    printf '%s 포트를 찾을 수 없다. script/run.sh로 먼저 기동한다.\n' "$service" >&2
    return 1
  }
  printf 'http://localhost:%s' "$port"
}
GATEWAY=$(service_url "${GATEWAY:-}" gateway-service 8080) || exit 1
EUREKA=$(service_url "${EUREKA:-}" eureka-server 8761) || exit 1

echo "준비 대기 (서비스 ${#SERVICES[@]}개 Eureka UP, Gateway 로그인 라우팅)"
ready_ok=false
for _ in $(seq 1 60); do
  registered=$(curl -s --max-time 10 -H 'Accept: application/json' "$EUREKA/eureka/apps" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(' '.join(a['name'] for a in d['applications'].get('application', []) if any(i['status'] == 'UP' for i in a['instance'])))" 2>/dev/null)
  call POST /api/v1/auth/login '' '{"username":"none","password":"none"}'
  if [[ "$(wc -w <<<"$registered")" -ge ${#SERVICES[@]} && "$http_status" == "401" ]]; then
    ready_ok=true
    break
  fi
  sleep 5
done
if [[ "$ready_ok" != true ]]; then
  echo "5분 안에 준비되지 않았다. 등록: [$registered], 로그인 응답: $http_status" >&2
  exit 1
fi

if [[ "$seed" == true ]]; then
  echo "[0] 시드 데이터 적용 ($SEED_FILE)"
  # Note: 서비스 계정은 자기 스키마만 쓸 수 있어 user·company 두 스키마에 넣으려면 관리자 계정(POSTGRES_USER)으로 실행한다.
  if docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q' <"$SEED_FILE" >/dev/null; then
    check "시드 적용" "ok" "ok"
  else
    check "시드 적용" "ok" "실패"
    exit 1
  fi
fi

echo "[0] 시드 계정 로그인"
master_token=$(login dmaster)
mgr1_token=$(login dmgr1)
mgr2_token=$(login dmgr2)
mgr3_token=$(login dmgr3)
receiver_token=$(login dreceiver)
for name in master_token mgr1_token mgr2_token mgr3_token receiver_token; do
  check "$name 발급" "yes" "$([[ -n "${!name}" ]] && echo yes || echo no)"
done
if [[ $fail -gt 0 ]]; then
  echo "로그인에 실패했다. 시드가 들어갔는지 확인한다(--no-seed로 실행했다면 빼고 다시 실행)." >&2
  exit 1
fi

# 구간 담당자 userId → 토큰. 시드 담당자가 아니면 MASTER로 진행한다(서울 허브에 다른 배송담당자가 있는 경우).
token_of() {
  case "$1" in
    "$MGR1") echo "$mgr1_token" ;;
    "$MGR2") echo "$mgr2_token" ;;
    "$MGR3") echo "$mgr3_token" ;;
    *) echo "$master_token" ;;
  esac
}
is_seoul_mgr() { [[ "$1" == "$MGR1" || "$1" == "$MGR2" ]]; }

# 수령업체 담당자로 주문한다. 결과는 order_id, delivery_id에 둔다.
order_id=''
delivery_id=''
create_order() {
  call POST /api/v1/orders "$receiver_token" \
    "{\"supplierCompanyId\":\"$SUPPLIER_COMPANY\",\"receiverCompanyId\":\"$RECEIVER_COMPANY\",\"productId\":\"$PRODUCT\",\"quantity\":3,\"requestNote\":\"배송 E2E\"}" \
    "Idempotency-Key: delivery-e2e-$(new_uuid)"
  order_id=$(field "d['data']['orderId']")
  delivery_id=$(field "d['data']['deliveryId']")
}
# MASTER로 배송을 읽어 구간 ID와 담당자를 pickup_*, last_mile_*에 둔다.
pickup_route=''; pickup_mgr=''; last_mile_route=''; last_mile_mgr=''
load_delivery() {
  call GET "/api/v1/deliveries/$1" "$master_token" ''
  pickup_route=$(field "d['data']['routes'][0]['deliveryRouteId']")
  pickup_mgr=$(field "d['data']['routes'][0]['managerId']")
  last_mile_route=$(field "d['data']['routes'][-1]['deliveryRouteId']")
  last_mile_mgr=$(field "d['data']['routes'][-1]['managerId']")
}
# move DELIVERY ROUTE TOKEN STATUS: D-05 구간 상태 변경
move() { call PATCH "/api/v1/deliveries/$1/routes/$2/status" "$3" "{\"status\":\"$4\"}"; }

echo "[1] 전체 연결: 주문(O-01) → 배송 생성(I-09, 자동 배정) → 구간 진행(D-05) → 배송 완료 → 주문 삭제(O-06 → I-10, I-13)"
create_order
check "O-01 주문 생성 201" "201" "$http_status"
check "주문 응답에 deliveryId" "yes" "$([[ -n "$delivery_id" && "$delivery_id" != None ]] && echo yes || echo no)"
order1="$order_id"; delivery1="$delivery_id"
load_delivery "$delivery1"
check "D-02 새 배송 PICKUP_WAITING" "200 PICKUP_WAITING" "$http_status $(field "d['data']['status']")"
check "D-02 배송의 orderId가 주문과 같음" "$order1" "$(field "d['data']['orderId']")"
check "같은 허브라 집하·업체 배송 2구간" "PICKUP LAST_MILE" "$(field "' '.join(r['routeType'] for r in d['data']['routes'])")"
check "구간 담당자는 서울 허브 배송담당자(dmgr1·dmgr2)" "yes" \
  "$(is_seoul_mgr "$pickup_mgr" && is_seoul_mgr "$last_mile_mgr" && echo yes || echo no)"
move "$delivery1" "$pickup_route" "$(token_of "$pickup_mgr")" MOVING
check "집하 담당자 출발 → PICKING_UP" "200 PICKING_UP" "$http_status $(field "d['data']['status']")"
move "$delivery1" "$pickup_route" "$(token_of "$pickup_mgr")" ARRIVED
check "집하 도착 → DEST_HUB_ARRIVED(허브 이동 없음)" "200 DEST_HUB_ARRIVED" "$http_status $(field "d['data']['status']")"
move "$delivery1" "$last_mile_route" "$(token_of "$last_mile_mgr")" MOVING
check "업체 배송 담당자 출발 → OUT_FOR_DELIVERY" "200 OUT_FOR_DELIVERY" "$http_status $(field "d['data']['status']")"
move "$delivery1" "$last_mile_route" "$(token_of "$last_mile_mgr")" ARRIVED
check "업체 배송 도착 → DELIVERED" "200 DELIVERED" "$http_status $(field "d['data']['status']")"
call DELETE "/api/v1/orders/$order1" "$master_token" ''
check "O-06 배송 완료 주문 삭제 200" "200" "$http_status"
call GET "/api/v1/deliveries/$delivery1" "$master_token" ''
check "삭제된 배송 D-02 404 DELIVERY_NOT_FOUND" "404 DELIVERY_NOT_FOUND" "$http_status $(field "d['code']")"

echo "[2] 조회 권한(D-01, D-02)"
create_order
order2="$order_id"; delivery2="$delivery_id"
load_delivery "$delivery2"
call GET "/api/v1/deliveries/$delivery2" "$(token_of "$pickup_mgr")" ''
check "구간 담당자 D-02 200" "200" "$http_status"
call GET "/api/v1/deliveries/$delivery2" "$mgr3_token" ''
check "담당 구간이 없는 배송담당자 D-02 403" "403 FORBIDDEN" "$http_status $(field "d['code']")"
call GET "/api/v1/deliveries?orderId=$order2" "$mgr3_token" ''
check "담당 구간이 없는 배송담당자 D-01 0건" "200 0" "$http_status $(field "d['data']['totalElements']")"
call GET "/api/v1/deliveries?orderId=$order2" "$(token_of "$pickup_mgr")" ''
check "구간 담당자 D-01 1건" "200 1" "$http_status $(field "d['data']['totalElements']")"
call GET "/api/v1/deliveries?orderId=$order2" "$master_token" ''
check "MASTER D-01 1건" "200 1" "$http_status $(field "d['data']['totalElements']")"
call GET "/api/v1/deliveries?sort=recipientName,asc" "$master_token" ''
check "허용하지 않는 정렬 D-01 400" "400" "$http_status"

echo "[3] 배송지·수령인 수정(D-03)"
call PATCH "/api/v1/deliveries/$delivery2" "$master_token" '{}'
check "두 필드 모두 없으면 400 VALIDATION_ERROR" "400 VALIDATION_ERROR" "$http_status $(field "d['code']")"
if [[ "$pickup_mgr" != "$last_mile_mgr" ]]; then
  call PATCH "/api/v1/deliveries/$delivery2" "$(token_of "$pickup_mgr")" '{"recipientName":"집하담당 수정"}'
  check "집하 담당자는 D-03 403" "403" "$http_status"
else
  skip "집하 담당자 D-03 403 (두 구간 담당자가 같은 사람이라 확인할 수 없음)"
fi
call PATCH "/api/v1/deliveries/$delivery2" "$(token_of "$last_mile_mgr")" '{"recipientName":"E2E 수령인 변경"}'
check "업체 배송 담당자 D-03 200, 수령인 변경" "200 E2E 수령인 변경" "$http_status $(field "d['data']['recipientName']")"

echo "[4] 담당자 재배정(D-04)"
other_seoul_mgr=$([[ "$pickup_mgr" == "$MGR1" ]] && echo "$MGR2" || echo "$MGR1")
call POST "/api/v1/deliveries/$delivery2/routes/$pickup_route/manager" "$mgr3_token" "{\"managerId\":\"$other_seoul_mgr\"}"
check "MASTER가 아니면 403" "403" "$http_status"
call POST "/api/v1/deliveries/$delivery2/routes/$pickup_route/manager" "$master_token" "{\"managerId\":\"$MGR3\"}"
check "다른 허브 배송담당자로 재배정 400 MANAGER_HUB_MISMATCH" "400 MANAGER_HUB_MISMATCH" "$http_status $(field "d['code']")"
call POST "/api/v1/deliveries/$delivery2/routes/$pickup_route/manager" "$master_token" "{\"managerId\":\"$PLAIN_USER\"}"
check "배송담당자가 아닌 사용자로 재배정 404 DELIVERY_MANAGER_NOT_FOUND" "404 DELIVERY_MANAGER_NOT_FOUND" "$http_status $(field "d['code']")"
call POST "/api/v1/deliveries/$delivery2/routes/$pickup_route/manager" "$master_token" "{\"managerId\":\"$other_seoul_mgr\"}"
check "같은 허브 배송담당자로 재배정 200" "200 $other_seoul_mgr" "$http_status $(field "d['data']['routes'][0]['managerId']")"
pickup_mgr="$other_seoul_mgr"

echo "[5] 구간 상태 규칙(D-05)"
move "$delivery2" "$last_mile_route" "$master_token" MOVING
check "앞 구간 도착 전 다음 구간 출발 409" "409 INVALID_STATUS_TRANSITION" "$http_status $(field "d['code']")"
move "$delivery2" "$pickup_route" "$master_token" WAITING
check "WAITING으로 변경 요청 409" "409 INVALID_STATUS_TRANSITION" "$http_status $(field "d['code']")"
move "$delivery2" "$pickup_route" "$mgr3_token" MOVING
check "그 구간 담당자가 아니면 403" "403 FORBIDDEN" "$http_status $(field "d['code']")"

echo "[6] 주문 취소(O-05 → I-11)와 삭제(O-06 → I-13)"
call POST "/api/v1/orders/$order2/cancel" "$receiver_token" ''
check "집하 전 주문 취소 200" "200" "$http_status"
load_delivery "$delivery2"
check "배송·구간 모두 CANCELED" "CANCELED CANCELED" \
  "$(field "d['data']['status']") $(field "' '.join(sorted(set(r['status'] for r in d['data']['routes'])))")"
move "$delivery2" "$pickup_route" "$master_token" MOVING
check "취소된 배송 구간 출발 409" "409 INVALID_STATUS_TRANSITION" "$http_status $(field "d['code']")"
call DELETE "/api/v1/orders/$order2" "$master_token" ''
check "취소된 주문 삭제 200" "200" "$http_status"
call GET "/api/v1/deliveries/$delivery2" "$master_token" ''
check "삭제된 배송 D-02 404" "404" "$http_status"

create_order
order3="$order_id"; delivery3="$delivery_id"
load_delivery "$delivery3"
move "$delivery3" "$pickup_route" "$(token_of "$pickup_mgr")" MOVING
call POST "/api/v1/orders/$order3/cancel" "$receiver_token" ''
check "집하 시작 후 주문 취소 409" "409 INVALID_STATUS_TRANSITION" "$http_status $(field "d['code']")"
call GET "/api/v1/deliveries/$delivery3" "$master_token" ''
check "취소 거절 후 배송은 PICKING_UP 그대로" "PICKING_UP" "$(field "d['data']['status']")"
# 정리: 끝까지 진행하고 삭제해 다음 실행의 담당 건수에 남지 않게 한다.
move "$delivery3" "$pickup_route" "$(token_of "$pickup_mgr")" ARRIVED
move "$delivery3" "$last_mile_route" "$(token_of "$last_mile_mgr")" MOVING
move "$delivery3" "$last_mile_route" "$(token_of "$last_mile_mgr")" ARRIVED
call DELETE "/api/v1/orders/$order3" "$master_token" ''
check "정리: 완료 후 주문 삭제 200" "200" "$http_status"

echo
echo "결과: PASS $pass / FAIL $fail"
[[ $fail -eq 0 ]]
