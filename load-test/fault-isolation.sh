#!/usr/bin/env bash
# 장애 격리 실험 한 번 실행(delivery-service/docs/fault-isolation.md).
# 사용: LABEL=s1-before FAULT_MODE=pause load-test/fault-isolation.sh
#   FAULT_MODE: pause(응답 없음, 기본) | stop(죽음) | none(기준선)
#   LEAD: 시작 후 장애까지 초(기본 90, 앞 60초가 before 구간). FAULT_SEC: 장애 시간(기본 30)
#   그 밖의 환경 변수(TARGET, CREATE_RATE, READ_RATE)는 k6로 그대로 넘어간다.
set -euo pipefail
cd "$(dirname "$0")/.."

LABEL=${LABEL:?LABEL이 필요하다. 예: s1-before}
FAULT_MODE=${FAULT_MODE:-pause}
LEAD=${LEAD:-90}
FAULT_SEC=${FAULT_SEC:-30}
# 멈출 서비스. 기본은 user-service(S1). 주문 경로 재시도 증폭(S2)은 FAULT_SERVICE=delivery-service TARGET=order
FAULT_SERVICE=${FAULT_SERVICE:-user-service}
FAULT_AT=$(( $(date +%s) + LEAD ))
OUT="load-test/results/fault-${LABEL}-$(date +%Y%m%d-%H%M%S).json"
mkdir -p load-test/results

# 실험용 설정(부하 테스트 API, Gateway 권한 캐시 1시간)이 빠진 채 띄웠으면 결과를 비교에 쓸 수 없다. 기동한 터미널과 같은 설정인지 먼저 확인한다.
for key in DELIVERY_LOAD_TEST_ENABLED AUTH_USERCACHETTL; do
  if ! docker compose config 2>/dev/null | grep -q "$key"; then
    echo "실험용 설정($key)이 없다. 다음을 export한 터미널에서 script/run.sh로 다시 띄우고 실행한다:" >&2
    echo "  export COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml" >&2
    exit 1
  fi
done

# 서비스를 막 다시 띄웠으면 delivery가 Eureka에 등록될 때까지 Gateway가 503을 준다. 부하 테스트 API가 400(빈 본문 검증 실패)을
# 돌려줄 때까지 최대 2분 기다린다. 기다린 만큼 장애 시각도 뒤로 민다.
base="${BASE_URL:-http://localhost:8080}"
for i in $(seq 1 24); do
  token=$(curl -s -m 5 -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"dmaster","password":"Sparta!2026"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d '{}' "$base/api/v1/deliveries/load-test")
  [ "$code" = 400 ] && break
  echo "서비스 준비 대기 중(부하 테스트 API $code)... $((i * 5))초" >&2
  sleep 5
done
[ "$code" = 400 ] || { echo "2분 안에 준비되지 않았다(마지막 응답 $code). docker compose ps와 Eureka(http://localhost:8761)를 확인한다." >&2; exit 1; }
FAULT_AT=$(( $(date +%s) + LEAD ))

if [ "$FAULT_MODE" = none ]; then K6_FAULT_AT=0; else K6_FAULT_AT=$FAULT_AT; fi
# 요청별 기록(CSV)도 남긴다. 요약은 구간 전체 값이라 "복구까지 몇 초"는 10초 단위 시계열로 계산해야 한다.
k6 run -e FAULT_AT="$K6_FAULT_AT" -e FAULT_SEC="$FAULT_SEC" --summary-export="$OUT" \
  --out csv="${OUT%.json}.csv.gz" load-test/k6/fault-isolation.js &
K6_PID=$!

# 부하 중 서버 상태 스냅샷(컨테이너 CPU·메모리, delivery 커넥션 풀·스레드). k6 결과만으로는 원인을 알 수 없다.
STATS="${OUT%.json}-stats.txt"
snap() {
  # Note: 멈춘 user-service에는 exec가 실패한다. set -e로 스크립트가 여기서 끝나면 unpause를 못 해 장애가 끝나지 않는다(2026-10-19 S1).
  {
    echo "== $1 $(date +%T)"
    docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' | sed 's/logistics-management-//'
    docker compose exec -T delivery-service sh -c 'curl -s localhost:8085/actuator/prometheus' 2>/dev/null \
      | grep -E "^(hikaricp_connections_(active|pending|timeout_total)|tomcat_threads_busy_threads|resilience4j_circuitbreaker_state\{.*state=\"(open|closed|half_open)\"|resilience4j_circuitbreaker_not_permitted_calls_total|resilience4j_bulkhead_available_concurrent_calls|resilience4j_circuitbreaker_calls_seconds_count)"
    # 힙이 작아 GC가 CPU를 먹는지 본다(이미지 기본 -Xmx384m). 누적 GC 시간이 실행 사이에 크게 늘면 GC 압박이다.
    for svc in delivery-service:8085 order-service:8084 user-service:8081 gateway-service:8090; do
      if docker compose ps --status paused --services 2>/dev/null | grep -qx "${svc%%:*}"; then echo "${svc%%:*} 멈춤 상태(건너뜀)"; continue; fi
      docker compose exec -T "${svc%%:*}" sh -c "curl -s localhost:${svc##*:}/actuator/prometheus" 2>/dev/null \
        | grep -E "^(jvm_gc_pause_seconds_(sum|count)|jvm_memory_used_bytes\{.*area=\"heap\"|jvm_memory_max_bytes\{.*area=\"heap\"|process_cpu_usage)" \
        | sed "s/^/${svc%%:*} /" | awk '{print $1, $2, $NF}' | cut -c1-200
    done
    echo
  } >> "$STATS" 2>&1 || true
}
echo "Docker 할당: CPU $(docker info --format '{{.NCPU}}')개, 메모리 $(docker info --format '{{.MemTotal}}' | awk '{printf "%.1fGiB", $1/1024/1024/1024}')" > "$STATS"
# 재시도 증폭(S2) 계산용 누적 요청 수. 시작과 끝의 차이로 "주문 1건당 delivery가 받은 요청 수"를 구한다.
counts() {
  {
    echo "== counts $1 $(date +%T)"
    for svc in order-service:8084 delivery-service:8085; do
      docker compose exec -T "${svc%%:*}" sh -c "curl -s localhost:${svc##*:}/actuator/prometheus" 2>/dev/null \
        | grep -E '^http_server_requests_seconds_count\{.*uri="(/api/v1/orders|/internal/deliveries|/internal/deliveries/orders/\{orderId\})"' \
        | sed "s/^/${svc%%:*} /"
    done
    echo
  } >> "$STATS" 2>&1 || true
}
counts start
if [ "$FAULT_MODE" = none ]; then SNAP_AT=40; else SNAP_AT=$(( LEAD - 10 )); fi
( sleep "$SNAP_AT"; snap "before(장애 전, 시작 ${SNAP_AT}초)" ) &

restore() {
  case "$FAULT_MODE" in
    pause) docker compose unpause "$FAULT_SERVICE" >/dev/null 2>&1 || true ;;
    stop) docker compose start "$FAULT_SERVICE" >/dev/null 2>&1 || true ;;
  esac
}
# 스크립트가 어디서 끝나든(오류, Ctrl+C) user-service를 되살린다
trap restore EXIT

if [ "$FAULT_MODE" != none ]; then
  sleep $(( FAULT_AT - $(date +%s) ))
  # k6가 setup에서 멈췄으면(로그인 실패, 부하 테스트 API 꺼짐) 장애를 넣지 않고 끝낸다
  kill -0 "$K6_PID" 2>/dev/null || { echo "k6가 먼저 끝났다. 위 k6 출력을 확인한다. 장애는 넣지 않았다." >&2; exit 1; }
  echo "[$(date +%T)] $FAULT_SERVICE ${FAULT_MODE}"
  docker compose "$FAULT_MODE" "$FAULT_SERVICE"
  sleep $(( FAULT_SEC - 10 )); snap "fault(장애 중)"; sleep 10
  if [ "$FAULT_MODE" = pause ]; then docker compose unpause "$FAULT_SERVICE"; else docker compose start "$FAULT_SERVICE"; fi
  echo "[$(date +%T)] $FAULT_SERVICE 복구"
  ( sleep 40; snap "after(복구 40초 뒤)" ) &
fi

wait "$K6_PID" || true
counts end
echo "결과: $OUT"
echo "서버 상태: $STATS"
