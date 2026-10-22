#!/usr/bin/env bash
# 버전마다 같은 절차로 생성 TPS 계단을 잰다(delivery-service T08 재측정, T10 성능 마스터 측정 perf-master.md 5절).
# 사용: NAME=v1-load1m RATES="40 80 160" READ_FIXED=20 STOP_ON_FAIL=1 load-test/version-compare.sh
#   환경 변수: NAME(결과 이름 앞부분, 버전·데이터·인덱스 조건을 넣는다), RATES(초당 생성·조회 각각, 기본 "25 40"),
#             REPEAT(단계마다 측정 횟수, 기본 2), REST(측정 전 쉬는 초, 기본 60), KEEP_LOAD(1이면 과거 배송 시드를 남긴다, 기본 1),
#             READ_FIXED(조회 초당 건수를 고정한다. 비우면 생성과 같다. 생성 처리량만 볼 때 쓴다),
#             SETTLE(측정이 끝나고 배정 집계까지 기다리는 초, 기본 30. 1절의 "측정 종료 30초 뒤 미배정"),
#             STOP_ON_FAIL(1이면 한 계단의 회차가 모두 실패할 때 다음 계단으로 가지 않는다. 판정 불가면 바로 멈춘다, 기본 0),
#             PRE_VUS(k6 생성 시나리오가 미리 만들 VU, 기본 400 = 최대치)
#   전제: COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml 를 export한 터미널,
#         모든 서비스가 뜬 뒤 시드(delivery-e2e.sql, KEEP_LOAD=1이면 load-test/seed/perf-seed.sh)를 넣은 DB.
#   절차: 예열 2회(초당 10+10) → [쉼 → 측정이 만든 배송 삭제 → 측정(1분) → SETTLE초 뒤 배정 집계 → 판정] × REPEAT, 단계마다 반복
#   결과: load-test/results/fault-<NAME>-rate<r>-<n>-*.json, -stats.txt, <NAME>-rate<r>-<n>-assignment.txt, -verdict.txt
# Note: batch-window-sweep.sh와 달리 delivery를 다시 띄우지 않는다. 버전 비교는 같은 JVM·같은 데이터 상태에서 부하만 올린다.
set -euo pipefail
cd "$(dirname "$0")/.."
name="${NAME:?NAME을 지정한다(예: sync-load300k)}"
rates="${RATES:-25 40}"
repeat="${REPEAT:-2}"
rest="${REST:-60}"
settle="${SETTLE:-30}"
stop_on_fail="${STOP_ON_FAIL:-0}"
export KEEP_LOAD="${KEEP_LOAD:-1}"
export PRE_VUS="${PRE_VUS:-400}"
read_fixed="${READ_FIXED:-}"

LABEL="warm-${name}" CREATE_RATE=10 READ_RATE=10 DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh
LABEL="warm-${name}b" CREATE_RATE=10 READ_RATE=10 DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh
for r in $rates; do
  failed=0
  for n in $(seq 1 "$repeat"); do
    read_rate="${read_fixed:-$r}"
    echo "--- ${name} 생성 ${r} + 조회 ${read_rate} ${n}회차: ${rest}초 쉼"
    sleep "$rest"
    load-test/reset-delivery-data.sh
    started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    json="load-test/results/fault-${name}-rate${r}-${n}-$(date +%Y%m%d-%H%M%S).json"
    LABEL="${name}-rate${r}-${n}" OUT_JSON="$json" CREATE_RATE="$r" READ_RATE="$read_rate" DURATION=1m FAULT_MODE=none \
      load-test/fault-isolation.sh || echo "측정 실패: ${name} ${r} ${n}회차" >&2
    sleep "$settle"
    out="load-test/results/${name}-rate${r}-${n}-assignment.txt"
    bash load-test/assignment-stats.sh "$started" | tee "$out" || echo "배정 집계 실패: $out" >&2
    verdict="load-test/results/${name}-rate${r}-${n}-verdict.txt"
    rc=0
    python3 load-test/perf-judge.py "$json" "$out" > "$verdict" || rc=$?
    cat "$verdict"
    if [ "$rc" = 2 ] && [ "$stop_on_fail" = 1 ]; then
      echo "멈춤: ${name} 생성 ${r} ${n}회차를 판정할 수 없다. 측정 환경을 확인한다: $verdict" >&2
      exit 2
    fi
    if [ "$rc" = 1 ]; then failed=$((failed + 1)); fi
  done
  if [ "$stop_on_fail" = 1 ] && [ "$failed" = "$repeat" ]; then
    echo "멈춤: ${name} 생성 ${r}에서 ${repeat}회 모두 실패했다. 버틴 최대 TPS는 앞 계단의 판정에서 본다(perf-master.md 1절)"
    break
  fi
done
echo "끝. 결과: load-test/results/fault-${name}-rate*-*.json, ${name}-rate*-*-verdict.txt"
