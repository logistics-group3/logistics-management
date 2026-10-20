#!/usr/bin/env bash
# 동기 락 버전과 비동기 배정 버전을 같은 절차로 잰다(delivery-service T08 재측정).
# 사용: NAME=sync-load300k RATES="25 40" load-test/version-compare.sh
#   환경 변수: NAME(결과 이름 앞부분, 버전·데이터·인덱스 조건을 넣는다), RATES(초당 생성·조회 각각, 기본 "25 40"),
#             REPEAT(단계마다 측정 횟수, 기본 2), REST(측정 전 쉬는 초, 기본 60), KEEP_LOAD(1이면 과거 배송 시드를 남긴다, 기본 1),
#             READ_FIXED(조회 초당 건수를 고정한다. 비우면 생성과 같다. 생성 처리량만 볼 때 쓴다)
#   전제: COMPOSE_FILE=docker-compose.yml:docker-compose.loadtest.yml:docker-compose.fault.yml 를 export한 터미널,
#         모든 서비스가 뜬 뒤 시드(delivery-e2e.sql, KEEP_LOAD=1이면 delivery-load.sql)를 넣은 DB.
#   절차: 예열 2회(초당 10+10) → [쉼 → 측정이 만든 배송 삭제 → 측정(1분) → 배정 집계] × REPEAT, 단계마다 반복
#   결과: load-test/results/fault-<NAME>-rate<r>-<n>-*.json, -stats.txt, <NAME>-rate<r>-<n>-assignment.txt
# Note: batch-window-sweep.sh와 달리 delivery를 다시 띄우지 않는다. 버전 비교는 같은 JVM·같은 데이터 상태에서 부하만 올린다.
set -euo pipefail
cd "$(dirname "$0")/.."
name="${NAME:?NAME을 지정한다(예: sync-load300k)}"
rates="${RATES:-25 40}"
repeat="${REPEAT:-2}"
rest="${REST:-60}"
export KEEP_LOAD="${KEEP_LOAD:-1}"
read_fixed="${READ_FIXED:-}"

LABEL="warm-${name}" CREATE_RATE=10 READ_RATE=10 DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh
LABEL="warm-${name}b" CREATE_RATE=10 READ_RATE=10 DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh
for r in $rates; do
  for n in $(seq 1 "$repeat"); do
    read_rate="${read_fixed:-$r}"
    echo "--- ${name} 생성 ${r} + 조회 ${read_rate} ${n}회차: ${rest}초 쉼"
    sleep "$rest"
    load-test/reset-delivery-data.sh
    started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    LABEL="${name}-rate${r}-${n}" CREATE_RATE="$r" READ_RATE="$read_rate" DURATION=1m FAULT_MODE=none load-test/fault-isolation.sh \
      || echo "측정 실패: ${name} ${r} ${n}회차" >&2
    sleep 10
    out="load-test/results/${name}-rate${r}-${n}-assignment.txt"
    # 비동기 버전에만 배정 지연 컬럼(assigned_at)과 배정 로그가 있다. 동기 버전은 담당자별 배정 수만 본다
    if [ -f load-test/assignment-stats.sh ]; then
      bash load-test/assignment-stats.sh "$started" | tee "$out" || echo "배정 집계 실패: $out" >&2
    else
      docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -q' <<'SQL' | tee "$out" || echo "배정 집계 실패: $out" >&2
SELECT manager_id, count(*) FROM delivery_schema.p_delivery_routes WHERE created_by <> 'LOAD' GROUP BY 1 ORDER BY 2 DESC;
SQL
    fi
  done
done
echo "끝. 결과: load-test/results/fault-${name}-rate*-*.json"
