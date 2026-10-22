# 성능 마스터 측정 스크립트의 공통 값과 함수(delivery-service/docs/perf-master.md). 저장소 루트에서 source한다.
# shellcheck shell=bash

# 측정은 늘 같은 덧붙임 조합으로 띄운다. 셸에 남은 COMPOSE_FILE을 믿지 않고 여기서 정한다.
export COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml:load-test/docker-compose.perf.yml

PERF_RESULTS=load-test/results/perf
PERF_IMAGE=logistics-management-delivery-service
PERF_READ=20          # 조회 초당 건수 고정(perf-master.md 1절)
PERF_PRE_VUS=400      # k6 생성 VU를 최대치만큼 미리 만든다(3절)
PERF_REST=${REST:-60}
PERF_SETTLE=${SETTLE:-30}
PERF_IDLE_CPU=${IDLE_CPU:-2}      # 쉬는 상태: 모든 컨테이너 CPU가 이 값(%) 미만(5절)
PERF_IDLE_WAIT=${IDLE_WAIT:-180}  # 쉬는 상태를 기다리는 최대 초

# 버전 줄(versions.txt). V4는 V3 코드로 장애 격리를 잰다.
perf_line() {
  local v=$1
  [ "$v" = V4 ] && v=V3
  awk -v v="$v" '!/^#/ && $1 == v' load-test/perf/versions.txt
}
perf_ref() { perf_line "$1" | awk '{print $2}'; }
perf_confirm() { perf_line "$1" | awk '{print $4}'; }
perf_confirm_path() { perf_line "$1" | awk '{print ($5 == "" ? "gateway" : $5)}'; }
# 같은 코드를 쓰는 변형은 이미지를 다시 빌드하지 않는다(V4·V3i는 V3 이미지)
perf_image_version() { case "$1" in V4|V3i) echo V3 ;; *) echo "$1" ;; esac; }
PERF_DELIVERY_PORT=18085   # 확정 경로 direct일 때 k6가 delivery를 직접 부르는 포트(docker-compose.perf.yml)
perf_expected_commit() { git rev-parse "$(perf_ref "$1")^{commit}"; }
perf_require_version() {
  [ -n "$(perf_line "$1")" ] || { echo "버전은 V0~V4 중 하나다: $1" >&2; exit 2; }
}

perf_psql() {
  docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q "$@"' sh "$@"
}
perf_delivery_container() { docker compose ps -q delivery-service; }
perf_delivery_commit() {
  local c
  c=$(perf_delivery_container)
  [ -n "$c" ] && docker inspect -f '{{index .Config.Labels "perf.commit"}}' "$c" || true
}
perf_epoch() { python3 -c 'import sys, datetime; print(int(datetime.datetime.fromisoformat(sys.argv[1]).timestamp()))' "$1"; }
perf_delivery_started_at() { perf_epoch "$(docker inspect -f '{{.State.StartedAt}}' "$(perf_delivery_container)")"; }

# 쉬는 상태 표본 한 번: "시각 이름 CPU%" 줄들. 가장 높은 CPU는 perf_idle_max로 본다.
perf_cpu_sample() {
  docker stats --no-stream --format "$(date +%T)\t{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | sed 's/logistics-management-//'
}
perf_idle_max() { awk -F'\t' '{gsub(/%/, "", $3); if ($3 + 0 > m) {m = $3 + 0; n = $2}} END {printf "%.2f %s\n", m, n}'; }

# delivery가 Gateway 뒤에서 부하 테스트 API를 받을 때까지(빈 본문에 400) 최대 5분 기다린다(fault-isolation.sh와 같은 기준)
perf_wait_ready() {
  local base=${BASE_URL:-http://localhost:8080} token code=
  for i in $(seq 1 60); do
    token=$(curl -s -m 5 -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' \
      -d '{"username":"dmaster","password":"Sparta!2026"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p') || true
    code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' -d '{}' "$base/api/v1/deliveries/load-test") || true
    [ "$code" = 400 ] && return 0
    sleep 5
  done
  echo "5분 안에 delivery가 준비되지 않았다(마지막 응답 $code)" >&2
  return 1
}

# 예열·측정 전에 Zipkin을 멈춘다(3절). 서비스를 --no-deps 없이 올리면 의존 관계로 다시 켜진다.
perf_stop_zipkin() {
  if [ -n "$(docker compose ps -q --status running zipkin 2>/dev/null)" ]; then
    docker compose stop zipkin >/dev/null
  fi
}
