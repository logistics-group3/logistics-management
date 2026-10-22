#!/usr/bin/env bash
# 성능 마스터 측정 기록 검증(delivery-service/docs/perf-master.md 10·11절).
# 칸마다 필수 파일, manifest 해시, 무효 조건(쉬는 CPU, 예열, 실제 도착률, 커밋 일치), 판정 재계산을 보고 STATUS.md를 다시 만들어 비교한다.
# 사용: load-test/perf-verify.sh [버전]   종료 코드 0 통과, 1 문제 있음(측정을 멈추고 보고한다)
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 load-test/perf/perf.py verify "$@"
