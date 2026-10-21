#!/usr/bin/env bash
# 성능 측정 도구(로컬 전용). 개발 스택과 분리된 logistics-perf 프로젝트를 띄우고 k6로 부하를 준다.
# 사용: script/perf.sh up              측정 환경 빌드·기동(.env.perf가 없으면 만든다)
#      script/perf.sh seed            측정 데이터 준비(사용자·업체·상품·배송담당자), .local/perf/data.json 저장
#      script/perf.sh run <s1|s2|s3> [이름]
#                                     부하 실행 후 Prometheus 지표까지 모아 .local/perf/results/<이름>/에 저장
#      script/perf.sh clean           주문·배송 데이터만 비움(같은 시드로 다시 측정할 때)
#      script/perf.sh status | down | reset(볼륨까지 삭제)
# 측정 조건 환경 변수:
#   RATE=초당 요청 수(기본 20)  DURATION=본 측정 시간(기본 5m)  WARMUP=워밍업 시간(기본 1m)
#   PERF_AUTH_CACHE_TTL=Gateway 권한 캐시 TTL(기본 60s, 0s면 캐시 없음). 바꾸면 up을 다시 실행해야 반영된다
#   PERF_USERS=시드 수령업체 담당자 수(기본 300)
# 필요한 것: Docker만. k6·Prometheus 조회도 컨테이너로 실행한다(grafana/k6 이미지).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROJECT=logistics-perf
ENV_FILE="$ROOT/.env.perf"
TOOL="$ROOT/script/perf"
STATE="$ROOT/.local/perf"
K6_IMAGE=grafana/k6:1.3.0
NETWORK="${PROJECT}_logistics-net"
GATEWAY_INTERNAL=http://gateway-service:8080
PROMETHEUS_INTERNAL=http://prometheus:9090
K6_ARGS=()

# Git Bash에서는 docker 볼륨 경로를 Windows 형식(C:/...)으로 넘겨야 한다. macOS·Linux는 pwd 그대로 쓴다.
host_path() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }
random_hex() { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }

prepare() {
  if [[ ! -f "$ENV_FILE" ]]; then
    # 기본 개발(8080 등)·UI 테스트(18080 등)와 겹치지 않게 2로 시작하는 포트를 쓴다. 비밀값은 매번 새로 만든다.
    umask 077
    cat > "$ENV_FILE" <<EOF
POSTGRES_USER=postgres
POSTGRES_PASSWORD=$(random_hex 24)
POSTGRES_PORT=25432
CONFIG_SERVER_PORT=28888
EUREKA_PORT=28761
GATEWAY_PORT=127.0.0.1:28080
ZIPKIN_PORT=29411
PROMETHEUS_PORT=29090
GRAFANA_PORT=23000
GRAFANA_ADMIN_PASSWORD=$(random_hex 12)
JWT_SECRET=$(random_hex 48)
MASTER_USERNAME=perfmaster
MASTER_PASSWORD=PerfLocal!2026
USER_DB_PASSWORD=$(random_hex 24)
HUB_DB_PASSWORD=$(random_hex 24)
COMPANY_DB_PASSWORD=$(random_hex 24)
ORDER_DB_PASSWORD=$(random_hex 24)
DELIVERY_DB_PASSWORD=$(random_hex 24)
SLACK_WEBHOOK_URL=
SLACK_DM_ENABLED=false
TRACING_SAMPLING_PROBABILITY=0.0
EOF
    echo "만들었다: $ENV_FILE"
  fi
  mkdir -p "$STATE/results"
}

compose() {
  # Note: Compose는 셸 환경 변수를 --env-file보다 우선한다. 셸에 남은 개발용 값(포트, Slack)이 끼어들지 않게
  # 전용 파일 값을 셸에 다시 올린 뒤 실행한다. PERF_AUTH_CACHE_TTL처럼 파일에 없는 값은 셸 값을 그대로 쓴다.
  (
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
    # 앞단 네트워크 대역을 개발 스택(10.210.10)과 다르게 둔다. 대역이 겹치면 함께 띄울 때 네트워크 생성이 실패한다.
    if ! grep -q '^LOGISTICS_NET_SUBNET=' "$ENV_FILE"; then
      export LOGISTICS_NET_SUBNET=10.210.20.0/24 LOGISTICS_NET_PREFIX=10.210.20.
    fi
    docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f docker-compose.yml -f "$TOOL/compose.perf.yml" "$@"
  )
}

# k6 컨테이너를 측정 네트워크 안에서 실행한다. Gateway·Prometheus를 컨테이너 이름으로 부른다.
# Note: k6도 같은 PC의 CPU를 쓴다. 상한(1 CPU)을 고정해 부하 도구가 서비스 자원을 빼앗는 정도를 매번 같게 한다.
k6() {
  local script="$1"; shift
  local user=()
  # Linux에서는 결과 파일을 현재 사용자 소유로 쓰게 한다. Docker Desktop(Windows·macOS)은 바인드 마운트 권한을 알아서 맞춘다.
  if [[ "$(uname -s)" == Linux ]]; then user=(-u "$(id -u):$(id -g)"); fi
  MSYS_NO_PATHCONV=1 docker run --rm -i --cpus 1 --network "$NETWORK" ${user[@]+"${user[@]}"} \
    -v "$(host_path "$TOOL/k6"):/scripts:ro" \
    -v "$(host_path "$STATE"):/out" \
    -e BASE_URL="$GATEWAY_INTERNAL" -e PROMETHEUS_URL="$PROMETHEUS_INTERNAL" \
    "$@" "$K6_IMAGE" run --quiet --summary-trend-stats 'avg,min,med,max,p(90),p(95),p(99)' \
    ${K6_ARGS[@]+"${K6_ARGS[@]}"} "/scripts/$script"
}

env_value() { grep -E "^$1=" "$ENV_FILE" | head -n 1 | cut -d= -f2-; }

container_env() {
  docker inspect "$(compose ps -q "$1")" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep "^$2=" | cut -d= -f2- || true
}

wait_ready() {
  echo "Gateway 경유 응답을 기다린다(최대 5분)."
  local port
  port="$(env_value GATEWAY_PORT)"; port="${port##*:}"
  for _ in $(seq 1 60); do
    # 로그인 실패(401)가 오면 Gateway·user-service가 응답하는 것이다. 다른 서비스는 seed가 확인한다.
    code="$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
      -d '{"username":"nobody0","password":"Wrong!2026"}' "http://127.0.0.1:$port/api/v1/auth/login" || true)"
    if [[ "$code" == "401" ]]; then echo "준비 완료: http://127.0.0.1:$port"; return 0; fi
    sleep 5
  done
  echo "5분 안에 준비되지 않았다. script/perf.sh status와 docker compose -p $PROJECT logs <서비스>로 확인한다." >&2
  return 1
}

run_scenario() {
  local scenario="$1" label="${2:-}"
  local script
  case "$scenario" in
    s1) script=s1-auth.js ;;
    s2) script=s2-order.js ;;
    s3) script=s3-read.js ;;
    *) echo "시나리오는 s1, s2, s3 중 하나다." >&2; exit 2 ;;
  esac
  [[ -f "$STATE/data.json" ]] || { echo "data.json이 없다. 먼저 script/perf.sh seed를 실행한다." >&2; exit 1; }
  local rate="${RATE:-20}" duration="${DURATION:-5m}" warmup="${WARMUP:-1m}"
  # 보고서에 남길 캐시 조건은 셸 값이 아니라 실제로 떠 있는 컨테이너의 환경 변수에서 읽는다.
  local ttl auth_cache hub_cache company_cache cache
  ttl="$(container_env gateway-service AUTH_USERCACHETTL)"
  auth_cache="$(container_env gateway-service AUTH_USER_CACHE)"
  hub_cache="$(container_env hub-service HUB_CACHE_TYPE)"
  company_cache="$(container_env company-service COMPANY_CACHE_TYPE)"
  cache="auth=${auth_cache:-local}/${ttl:-60s} hub=${hub_cache:-none} company=${company_cache:-none}"
  label="${label:-$scenario-$(date +%Y%m%d-%H%M%S)}"
  local dir="$STATE/results/$label"
  mkdir -p "$dir"
  echo "실행: $scenario label=$label rate=$rate/s duration=$duration warmup=$warmup cache=[$cache]"

  K6_ARGS=(--summary-export "/out/results/$label/k6-summary.json")
  k6 "$script" -e RATE="$rate" -e DURATION="$duration" -e WARMUP="$warmup" -e LABEL="$label" \
    -e PERF_AUTH_CACHE_TTL="$ttl" -e PROBE_ROUNDS="${PROBE_ROUNDS:-3}" || echo "k6 종료 코드 $? (임계값 초과일 수 있다. 보고서는 계속 만든다)"
  K6_ARGS=()
  local end
  end="$(date +%s)"

  # Note: Prometheus는 15초마다 수집한다. 마지막 구간까지 들어오도록 한 번 더 수집될 때를 기다린 뒤 조회한다.
  echo "지표 수집을 기다린다(20초)."
  sleep 20
  k6 report.js -e LABEL="$label" -e SCENARIO="$scenario" -e END="$end" -e DURATION="$duration" -e RATE="$rate" \
    -e WARMUP="$warmup" -e PERF_AUTH_CACHE_TTL="$ttl" -e CACHE="$cache" \
    -e COMMIT="$(git rev-parse --short HEAD)$(git diff --quiet HEAD -- . ':!docs' || echo '-dirty')" \
    -e SAMPLING="$(env_value TRACING_SAMPLING_PROBABILITY)"
  echo "결과: $dir/report.md"
  publish "$label"
}

# 결과를 저장소 기록(docs/reviews/performance/)에도 남긴다. 원본(k6 요약 JSON, Prometheus 응답)은 .local에만 둔다.
# Note: 같은 이름으로 다시 측정하면 보고서를 덮어쓰고 목록의 그 줄만 바꾼다. 시험 실행은 PUBLISH=0으로 건너뛴다.
publish() {
  local label="$1" dir="$STATE/results/$1" docs="$ROOT/docs/reviews/performance"
  if [[ "${PUBLISH:-1}" == 0 ]]; then echo "PUBLISH=0: docs/reviews에 기록하지 않았다."; return 0; fi
  [[ -f "$dir/summary-row.md" ]] || { echo "summary-row.md가 없어 기록하지 않았다." >&2; return 1; }
  cp "$dir/report.md" "$docs/$label.md"
  local index="$docs/README.md" tmp
  tmp="$(mktemp)"
  grep -vF "[$label]($label.md)" "$index" > "$tmp" || true
  cat "$dir/summary-row.md" >> "$tmp"
  mv "$tmp" "$index"
  echo "기록: docs/reviews/performance/$label.md, 목록 docs/reviews/performance/README.md"
}

clean() {
  # 주문·배송 스키마의 데이터 테이블만 비운다(Flyway 이력 제외). 사용자·업체·상품 시드는 남는다.
  compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$(env_value POSTGRES_USER)" -d logistics -c "
DO \$\$
DECLARE tables text;
BEGIN
  SELECT string_agg(format('%I.%I', schemaname, tablename), ', ') INTO tables
  FROM pg_tables
  WHERE schemaname IN ('order_schema', 'delivery_schema') AND tablename <> 'flyway_schema_history';
  IF tables IS NOT NULL THEN EXECUTE 'TRUNCATE ' || tables; END IF;
  RAISE NOTICE 'truncated: %', tables;
END \$\$;"
}

case "${1:-status}" in
  up) prepare; compose up -d --build; wait_ready ;;
  down) prepare; compose down ;;
  reset) prepare; compose down -v; rm -f "$STATE/data.json" ;;
  status) prepare; compose ps --format 'table {{.Name}}\t{{.State}}\t{{.Ports}}' ;;
  seed)
    prepare
    k6 seed.js -e MASTER_USERNAME="$(env_value MASTER_USERNAME)" -e MASTER_PASSWORD="$(env_value MASTER_PASSWORD)" \
      -e PERF_USERS="${PERF_USERS:-300}"
    echo "저장: $STATE/data.json"
    ;;
  run) prepare; shift; run_scenario "$@" ;;
  clean) prepare; clean ;;
  *) echo '사용법: script/perf.sh {up|seed|run <s1|s2|s3> [이름]|clean|status|down|reset}' >&2; exit 2 ;;
esac
