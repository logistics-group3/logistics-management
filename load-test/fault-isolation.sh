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
FAULT_AT=$(( $(date +%s) + LEAD ))
OUT="load-test/results/fault-${LABEL}-$(date +%Y%m%d-%H%M%S).json"
mkdir -p load-test/results

if [ "$FAULT_MODE" = none ]; then K6_FAULT_AT=0; else K6_FAULT_AT=$FAULT_AT; fi
k6 run -e FAULT_AT="$K6_FAULT_AT" -e FAULT_SEC="$FAULT_SEC" --summary-export="$OUT" load-test/k6/fault-isolation.js &
K6_PID=$!

if [ "$FAULT_MODE" != none ]; then
  sleep $(( FAULT_AT - $(date +%s) ))
  echo "[$(date +%T)] user-service ${FAULT_MODE}"
  docker compose "$FAULT_MODE" user-service
  sleep "$FAULT_SEC"
  if [ "$FAULT_MODE" = pause ]; then docker compose unpause user-service; else docker compose start user-service; fi
  echo "[$(date +%T)] user-service 복구"
fi

wait "$K6_PID" || true
echo "결과: $OUT"
