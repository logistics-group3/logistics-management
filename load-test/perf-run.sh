#!/usr/bin/env bash
# 성능 마스터 측정 실행(delivery-service/docs/perf-master.md 5·10·11절). 예열 → 계단 측정 → 판정 → 기록 → STATUS.md → 캡처.
# 사용: load-test/perf-run.sh <V0|V1|V2|V3|V4>   (먼저 load-test/perf-switch.sh <버전>)
#   다음에 잴 칸은 칸 기록에서 정한다(load-test/perf/perf.py next). 끊겨도 같은 명령으로 이어 가고, 검증을 통과한 칸은 다시 재지 않는다.
#   무효 칸(쉬는 상태 아님, 예열 없음, 도착률·커밋 불일치 등)은 <칸>.invalid-<시각>으로 옮기고 다시 잰다. 3번 연속이면 멈춘다.
#   멈추기: load-test/results/perf/STOP 파일을 만들면 다음 칸 전에 멈춘다. 지우고 같은 명령을 다시 실행하면 이어 간다.
#   종료 코드: 0 버전 끝(계단 끝 또는 2회 모두 실패로 멈춤), 3 무효가 거듭됨, 4 STOP, 5 준비 안 됨
set -euo pipefail
cd "$(dirname "$0")/.."
source load-test/perf/lib.sh

ver=${1:?버전(V0~V4)}
perf_require_version "$ver"
[ "$(perf_delivery_commit)" = "$(perf_expected_commit "$ver")" ] \
  || { echo "delivery가 $ver 코드가 아니다. load-test/perf-switch.sh $ver 를 먼저 실행한다" >&2; exit 5; }

mkdir -p "$PERF_RESULTS/$ver"
lock="$PERF_RESULTS/.run.lock"
if ! mkdir "$lock" 2>/dev/null; then
  if kill -0 "$(cat "$lock/pid" 2>/dev/null)" 2>/dev/null; then echo "이미 실행 중이다(pid $(cat "$lock/pid"))" >&2; exit 5; fi
  rm -rf "$lock"; mkdir "$lock"
fi
echo $$ > "$lock/pid"
trap 'rm -rf "$lock"' EXIT
caffeinate -i -w $$ &

# 셸에 남은 측정 변수가 k6로 넘어가지 않게 지운다(7절). 칸마다 cell.sh가 필요한 값만 다시 넣는다
unset CREATE_RATE READ_RATE DURATION FAULT_MODE LABEL TARGET MAX_VUS CLIENT_TIMEOUT LEAD FAULT_SEC FAULT_SERVICE OUT_JSON CONFIRM PRE_VUS
rm -rf "$PERF_RESULTS/$ver"/*.tmp
perf_stop_zipkin

# 예열 2회(초당 10+10, 1분). 실행할 때마다 다시 한다. 칸은 마지막 예열 시각이 delivery 기동 뒤인지로 예열 여부를 검증한다
confirm=$(perf_confirm "$ver")
mkdir -p "$PERF_RESULTS/$ver/warm"
for i in 1 2; do
  stamp=$(date +%Y%m%d-%H%M%S)
  LABEL="perf-warm-$ver-$i" OUT_JSON="$PERF_RESULTS/$ver/warm/$stamp-$i.json" CREATE_RATE=10 READ_RATE=10 DURATION=1m \
    FAULT_MODE=none CONFIRM="$confirm" PRE_VUS="$PERF_PRE_VUS" load-test/fault-isolation.sh > "$PERF_RESULTS/$ver/warm/$stamp-$i-k6.txt" 2>&1
done
echo "warm_at=$(date +%s)" >> "$PERF_RESULTS/$ver/warm.txt"

invalid=0
while :; do
  python3 load-test/perf/perf.py status
  next=$(python3 load-test/perf/perf.py next "$ver")
  [ "${next%% *}" = done ] && { echo "$ver: ${next#done }"; break; }
  cell=${next#cell }
  [ -f "$PERF_RESULTS/STOP" ] && { echo "STOP 파일이 있어 $cell 전에 멈춘다"; exit 4; }
  load-test/perf/cell.sh "$ver" "$cell"
  if python3 load-test/perf/perf.py check-cell "$PERF_RESULTS/$ver/$cell"; then
    invalid=0
  else
    mv "$PERF_RESULTS/$ver/$cell" "$PERF_RESULTS/$ver/$cell.invalid-$(date +%Y%m%d-%H%M%S)"
    invalid=$((invalid + 1))
    echo "무효 칸: $cell (${invalid}번째 연속). 다시 잰다" >&2
    if [ "$invalid" -ge 3 ]; then python3 load-test/perf/perf.py status; echo "무효가 3번 연속이라 멈춘다" >&2; exit 3; fi
  fi
done
load-test/perf/capture.sh "$ver" || echo "캡처 실패. 나중에 load-test/perf/capture.sh $ver 로 다시 뽑는다" >&2
python3 load-test/perf/perf.py status
echo "끝. $PERF_RESULTS/STATUS.md"
