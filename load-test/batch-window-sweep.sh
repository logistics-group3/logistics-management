#!/usr/bin/env bash
# 구간 배정 배치 창 크기별 비교(delivery-service 결정 로그 27). 창마다 delivery를 다시 띄우므로 매번 예열부터 한다.
# 사용: load-test/batch-window-sweep.sh [창 목록(ms), 기본 "0 200 500 1000"]
#   환경 변수: RATE(측정 초당 생성·조회 각각, 기본 25), REPEAT(창마다 측정 횟수, 기본 2), REST(측정 전 쉬는 초, 기본 60)
#   전제: COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml 를 export한 터미널,
#         config-server는 이 변경(config-repo batch-window-ms)이 반영된 이미지, 시드가 들어간 DB.
#   창마다: delivery 재생성 → 예열 2회(초당 10+10) → [쉼 → 배송 데이터 비움 → 측정(1분) → 배정 집계] × REPEAT
#   결과: load-test/results/fault-win<ms>-rate<RATE>-<n>-*.json, -stats.txt, win<ms>-rate<RATE>-<n>-assignment.txt
# Note: 초당 40+40은 이 Mac에서 포화 경계라 같은 조건에서도 버틴 판(p95 2초)과 무너진 판(실패 50%)이 갈렸다(2026-10-19).
#   창 크기 효과를 보려면 무너지지 않는 부하에서 같은 창을 두 번 재고, 두 값이 비슷할 때만 쓴다.
set -euo pipefail
cd "$(dirname "$0")/.."
windows="${1:-0 200 500 1000}"
rate="${RATE:-25}"
repeat="${REPEAT:-2}"
rest="${REST:-60}"

for w in $windows; do
  echo "===== 배치 창 ${w}ms (초당 ${rate}+${rate}, ${repeat}회) ====="
  ASSIGNMENT_BATCH_WINDOW_MS="$w" docker compose up -d delivery-service
  LABEL="warm-w${w}" CREATE_RATE=10 READ_RATE=10 DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh
  LABEL="warm-w${w}b" CREATE_RATE=10 READ_RATE=10 DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh
  for n in $(seq 1 "$repeat"); do
    echo "--- ${w}ms ${n}회차: ${rest}초 쉼"
    sleep "$rest"   # 앞 실행에서 밀린 요청·배정이 끝나고 CPU가 가라앉을 시간
    load-test/reset-delivery-data.sh
    # 예열·앞 회차의 배정 로그가 집계에 섞이지 않게 측정 시작 시각부터 본다
    started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    LABEL="win${w}-rate${rate}-${n}" CREATE_RATE="$rate" READ_RATE="$rate" DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh
    sleep 10   # 마지막 배정이 끝날 시간
    # 집계가 실패해도 다음 측정은 이어 간다(k6 결과는 이미 저장됐다)
    bash load-test/assignment-stats.sh "$started" | tee "load-test/results/win${w}-rate${rate}-${n}-assignment.txt" \
      || echo "배정 집계 실패: ${w}ms ${n}회차(측정 시작 $started). 나중에 bash load-test/assignment-stats.sh $started 로 다시 뽑을 수 없다(다음 회차가 데이터를 비운다)." >&2
  done
done
echo "끝. 결과: load-test/results/fault-win*-rate${rate}-*.json, win*-rate${rate}-*-assignment.txt"
