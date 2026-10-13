#!/usr/bin/env bash
# 전체 빌드 + 실행. 처음 실행은 Gradle 의존성 다운로드 때문에 몇 분 걸린다.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "⚠️  .env 가 없어서 .env.example 을 복사합니다."
  cp .env.example .env
fi

docker compose up -d --build

echo ""
echo "기동 중입니다. 서비스가 Eureka 에 등록되기까지 30초~1분 정도 걸립니다."
echo "  Eureka 대시보드 : http://localhost:8761"
echo "  Gateway         : http://localhost:8080"
echo "  공통 인프라 검증 : ./script/smoke-test.sh"
