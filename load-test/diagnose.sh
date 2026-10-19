#!/usr/bin/env bash
# 부하 테스트가 이상할 때 서버 상태를 한 파일로 모은다(delivery-service/docs/fault-isolation.md).
# 사용: load-test/diagnose.sh  → load-test/results/diag-<시각>.txt
cd "$(dirname "$0")/.."
out="load-test/results/diag-$(date +%Y%m%d-%H%M%S).txt"
mkdir -p load-test/results
{
  echo "== COMPOSE_FILE=${COMPOSE_FILE:-<없음>}"
  echo "== Docker 할당 자원: CPU $(docker info --format '{{.NCPU}}')개, 메모리 $(docker info --format '{{.MemTotal}}' | awk '{printf "%.1fGiB", $1/1024/1024/1024}')"
  echo "== docker compose ps"; docker compose ps -a --format 'table {{.Service}}\t{{.State}}\t{{.Status}}'
  echo; echo "== docker stats"; docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}'
  echo; echo "== 실험 설정 반영"; docker compose config 2>/dev/null | grep -E "DELIVERY_LOAD_TEST_ENABLED|AUTH_USERCACHETTL|MBEANREGISTRY|TRACING_SAMPLING"
  base="${BASE_URL:-http://localhost:8080}"
  token=$(curl -s -m 10 -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"dmaster","password":"Sparta!2026"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null)
  echo; echo "== 단건 요청 (부하 없음). 상태코드 / 걸린 시간(초)"
  [ -n "$token" ] || echo "로그인 실패"
  for i in 1 2 3; do
    curl -s -m 15 -o /dev/null -w "D-01 %{http_code} %{time_total}\n" -H "Authorization: Bearer $token" \
      "$base/api/v1/deliveries?departureHubId=6f39d519-4947-4a95-a454-58c25728f388&size=10"
  done
  curl -s -m 15 -o /dev/null -w "load-test(빈 본문, 400이면 켜짐) %{http_code} %{time_total}\n" -X POST \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{}' "$base/api/v1/deliveries/load-test"
  for svc in delivery-service gateway-service user-service; do
    echo; echo "== $svc 로그 (에러·경고 마지막 40줄)"
    docker compose logs --no-color --tail 2000 "$svc" 2>&1 | grep -iE "error|exception|warn|timeout|pool|refused|reject" | tail -40 | cut -c1-400
  done
  echo; echo "== delivery 메트릭"
  docker compose exec -T delivery-service sh -c 'curl -s localhost:8085/actuator/prometheus' 2>/dev/null \
    | grep -E "^(tomcat_threads_(busy|current|config_max)|hikaricp_connections_(active|idle|pending|max|timeout_total)|jvm_threads_live|process_cpu_usage|http_server_requests_seconds_count\{.*(deliveries|load-test))" | head -40
} > "$out" 2>&1
echo "결과: $out"
