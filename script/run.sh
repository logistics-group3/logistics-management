#!/usr/bin/env bash
# docker compose up --build 단축 명령. 추가 인자는 compose up에 전달된다.
#   script/run.sh                              # 전체 실행
#   script/run.sh --scale order-service=2      # 수평 확장
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo ".env 파일이 없습니다. 먼저 'cp .env.example .env' 후 값을 채워 주세요." >&2
  exit 1
fi

docker compose up -d --build "$@"
docker compose ps
