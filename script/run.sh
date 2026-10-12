#!/usr/bin/env bash
# 빌드 후 컨테이너 실행
#   script/run.sh                  # 공통 인프라: user-db + eureka + config-server + gateway + user
#   script/run.sh --profile domain # 도메인 서비스까지 전부
#   script/run.sh --reset          # DB 볼륨 삭제 후 다시 (DDL 변경 시)
set -euo pipefail
# macOS 기본 bash 3.2 호환: 빈 배열은 ${arr[@]+"${arr[@]}"} 형태로 펼친다
cd "$(dirname "$0")/.."

[ -f .env ] || { echo ".env가 없습니다 → cp .env.example .env"; exit 1; }

PROFILE_ARGS=()
RESET=0
for arg in "$@"; do
  case "$arg" in
    --reset) RESET=1 ;;
    *) PROFILE_ARGS+=("$arg") ;;
  esac
done

./gradlew clean bootJar -x test

if [ "$RESET" -eq 1 ]; then
  docker compose ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} down -v
fi
docker compose ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} up -d --build
docker compose ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} ps
