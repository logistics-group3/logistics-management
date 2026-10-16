#!/usr/bin/env bash
# Swagger 수동 테스트 보조 도구(로컬 전용). 기본 개발 스택(Gateway 8080)에 테스트 설정을 덧씌운다.
# 사용: script/api-test.sh up          접근 로그·분산 추적(Jaeger) 켜고 Slack 끈 상태로 서비스 재생성(이미지 재빌드 없음)
#      script/api-test.sh seed        테스트 계정·허브·업체·상품 준비, .local/api-test/data.json 저장
#      script/api-test.sh tokens      모든 테스트 계정 토큰 재발급(60분 만료), .local/api-test/tokens.txt 저장
#      script/api-test.sh logs        6개 서비스 로그 전체를 한 화면에, .local/api-test/logs/에 파일로도 저장
#      script/api-test.sh flow        요청 흐름 줄만(라우팅·접근 로그·SQL·경고·오류). 파일 저장은 logs와 같다
#      script/api-test.sh trace <ID>  저장된 로그에서 trace ID 하나의 줄만 순서대로 모아 보기
#      script/api-test.sh restore     팀 기본 설정으로 되돌림(접근 로그·추적 끔, Jaeger 제거, .env의 Slack 복구)
#      script/api-test.sh status      현재 컨테이너 상태와 Slack·접근 로그·추적·SQL 설정 확인
# 환경 변수: API_TEST_SQL=DEBUG(up 할 때 SQL 로그 켬), API_TEST_PREFIX=jh(seed 계정 접두어)
# 팀 docker-compose.yml·config-repo·.env는 바꾸지 않는다. 실행 상태는 Git에서 제외된 .local/api-test/에 둔다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOL="$ROOT/script/api-test"
STATE="$ROOT/.local/api-test"
cd "$ROOT"

SERVICES=(gateway-service user-service hub-service company-service order-service delivery-service)
JAEGER_UI=http://localhost:16686
# OpenTelemetry Java 에이전트는 저장소에 넣지 않고 Maven Central 배포본을 받아 SHA-256을 확인한다.
OTEL_VERSION=2.31.1
OTEL_SHA256=bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba
OTEL_JAR="$STATE/otel/opentelemetry-javaagent.jar"

compose_test() { docker compose -f docker-compose.yml -f "$TOOL/compose.override.yml" "$@"; }
sha256() { python3 -c 'import hashlib, sys; print(hashlib.sha256(open(sys.argv[1], "rb").read()).hexdigest())' "$1"; }

ensure_agent() {
  if [[ -f "$OTEL_JAR" && "$(sha256 "$OTEL_JAR")" == "$OTEL_SHA256" ]]; then return 0; fi
  mkdir -p "$STATE/otel"
  echo "OpenTelemetry Java 에이전트 $OTEL_VERSION 을 내려받는다."
  curl -sfL -o "$OTEL_JAR.tmp" \
    "https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/$OTEL_VERSION/opentelemetry-javaagent-$OTEL_VERSION.jar"
  if [[ "$(sha256 "$OTEL_JAR.tmp")" != "$OTEL_SHA256" ]]; then
    rm -f "$OTEL_JAR.tmp"
    echo "에이전트 SHA-256이 맞지 않아 중단한다." >&2
    exit 1
  fi
  mv "$OTEL_JAR.tmp" "$OTEL_JAR"
}

# Note: Eureka 응답은 최대 30초 캐시되고 재생성 직전 인스턴스가 잠시 남아 있어 UP 개수만 세면 너무 일찍 준비로 판단한다.
# 그래서 Gateway를 거쳐 각 서비스 API가 실제로 응답하는지 확인한다(seed.py --probe).
wait_ready() {
  echo "Gateway 경유 응답을 기다린다(최대 3분)."
  for _ in $(seq 1 36); do
    if python3 "$TOOL/seed.py" --probe >/dev/null 2>&1; then
      python3 "$TOOL/seed.py" --probe
      return 0
    fi
    sleep 5
  done
  python3 "$TOOL/seed.py" --probe || true
  echo "3분 안에 준비되지 않았다. docker compose logs {서비스}로 확인한다." >&2
  return 1
}

# 화면에는 서비스별 색을 유지하고, 파일에는 색 코드를 지워 grep하기 쉽게 저장한다.
follow_logs() {
  local filter="$1"
  mkdir -p "$STATE/logs"
  local file="$STATE/logs/$(date +%Y%m%d-%H%M%S).log"
  echo "로그 파일: $file  (중지: Ctrl+C)"
  echo "요청 하나만 보기: script/api-test.sh trace <로그 줄의 [32자 trace ID]>  또는  $JAEGER_UI"
  docker compose --ansi always logs -f --tail=0 "${SERVICES[@]}" 2>&1 \
    | grep --line-buffered -v -E 'ConfigClusterResolver|Resolving eureka endpoints' \
    | grep --line-buffered -E "$filter" \
    | tee >(perl -pe 'BEGIN { $| = 1 } s/\e\[[0-9;]*m//g' >> "$file")
}

container_env() {
  local id
  id="$(docker compose ps -q "$1")"
  [[ -n "$id" ]] && docker inspect "$id" --format '{{range .Config.Env}}{{println .}}{{end}}'
}

case "${1:-status}" in
  up)
    ensure_agent
    compose_test up -d --no-build jaeger "${SERVICES[@]}"
    wait_ready
    echo "Jaeger(요청 흐름 화면): $JAEGER_UI"
    echo "켜 둔 logs·flow는 보통 새 컨테이너로 이어진다. 화면이 멈췄으면 다시 실행한다."
    ;;
  restore)
    compose_test rm -sf jaeger
    docker compose up -d --no-build "${SERVICES[@]}"
    wait_ready
    echo "켜 둔 logs·flow는 보통 새 컨테이너로 이어진다. 화면이 멈췄으면 다시 실행한다."
    ;;
  seed) python3 "$TOOL/seed.py" ;;
  tokens) python3 "$TOOL/seed.py" --tokens ;;
  logs) follow_logs '.' ;;
  flow) follow_logs 'Route matched|AccessLog|ACCESS |org\.hibernate\.SQL|WARN|ERROR|Exception' ;;
  trace)
    id="${2:?trace ID를 넣는다. 로그 줄의 [32자 ID]나 접근 로그 traceparent의 가운데 값이다}"
    # logs·flow를 동시에 켜 두면 같은 줄이 여러 파일에 저장되므로, 이 ID가 있는 가장 최근 파일 하나에서만 찾는다.
    found=""
    for file in $(ls -t "$STATE"/logs/*.log 2>/dev/null); do
      if grep -q -- "$id" "$file"; then found="$file"; break; fi
    done
    if [[ -z "$found" ]]; then
      echo "저장된 로그에 이 trace ID가 없다. logs·flow를 켠 뒤 보낸 요청인지 확인한다." >&2
    else
      echo "($found)"
      grep -- "$id" "$found"
    fi
    echo
    echo "Jaeger에서 구간별로 보기: $JAEGER_UI/trace/$id"
    ;;
  status)
    compose_test ps --format 'table {{.Service}}\t{{.State}}\t{{.Status}}'
    env="$(container_env order-service || true)"
    if grep -q '^SLACK_WEBHOOK_URL=.\+' <<<"$env"; then echo "Slack Webhook: 켜짐"; else echo "Slack Webhook: 꺼짐"; fi
    if grep -q '^SERVER_TOMCAT_ACCESSLOG_ENABLED=true' <<<"$env"; then echo "접근 로그: 켜짐(테스트 설정)"; else echo "접근 로그: 꺼짐(기본 설정)"; fi
    if grep -q 'javaagent:/otel' <<<"$env"; then echo "분산 추적: 켜짐 ($JAEGER_UI)"; else echo "분산 추적: 꺼짐"; fi
    if grep -q 'org.hibernate.SQL":"DEBUG' <<<"$env"; then echo "SQL 로그: 켜짐"; else echo "SQL 로그: 꺼짐"; fi
    ;;
  -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//' ;;
  *) echo "알 수 없는 명령: $1 (--help)" >&2; exit 2 ;;
esac
