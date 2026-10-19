#!/usr/bin/env bash
# 주문 생성 TPS 단계 측정. 접수율(RATE)을 단계별로 올리며 같은 시간씩 k6를 돌려 한계 지점을 찾는다.
# 사용: load-test/order-steps.sh <결과 접두사> [DURATION] [RATE...]   예: load-test/order-steps.sh baseline 60s 5 10 20 40
# 단계마다 reset-order-data.sh로 k6 데이터를 지우고 시작한다(앞 단계의 데이터 누적이 뒤 단계를 느리게 만들기 때문).
# 환경: GATEWAY(기본 http://host.docker.internal:18080, 전용 스택), 결과 JSON은 load-test/results/(저장소에 올리지 않는다)
set -euo pipefail
cd "$(dirname "$0")/.."
PREFIX="${1:?결과 접두사가 필요하다}"; DURATION="${2:-60s}"; shift 2 || true
RATES=("${@:-5 10 20 40}")
[[ $# -gt 0 ]] && RATES=("$@") || RATES=(5 10 20 40)
GATEWAY="${GATEWAY:-http://host.docker.internal:18080}"
mkdir -p load-test/results
for rate in "${RATES[@]}"; do
  echo "== RATE=${rate}/s DURATION=${DURATION}"
  load-test/reset-order-data.sh | tail -1
  sleep 5
  docker run --rm -i -v "$PWD/load-test:/load-test" -e BASE_URL="$GATEWAY" -e RATE="$rate" -e DURATION="$DURATION" \
    grafana/k6 run --quiet --summary-export="/load-test/results/${PREFIX}-${rate}.json" /load-test/k6/order-e2e.js \
    > "load-test/results/${PREFIX}-${rate}.txt" 2>&1 || true
  python3 load-test/summarize.py "load-test/results/${PREFIX}-${rate}.json" "$rate" "$DURATION"
  sleep 10
done
