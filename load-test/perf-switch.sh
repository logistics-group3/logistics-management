#!/usr/bin/env bash
# 측정 버전으로 delivery를 바꾼다(delivery-service/docs/perf-master.md 5절 1단계).
# 사용: load-test/perf-switch.sh <V0|V1|V2|V3|V4>   (V4는 V3 코드다)
#   그 커밋으로 delivery 이미지를 빌드(커밋을 라벨로 붙인다) → delivery 스키마 삭제 → 기동(Flyway가 그 버전 스키마를 만든다)
#   → 계정·업체 시드(delivery-e2e.sql) → 측정 시드(perf-seed.sh). 기록: load-test/results/perf/<버전>/switch.txt
#   전제: 다른 서비스가 load-test/perf/lib.sh의 COMPOSE_FILE 조합으로 떠 있다.
#   COUNT: 과거 배송 수(기본 1000000). 그 커밋 이미지가 이미 있으면 다시 빌드하지 않는다(FORCE_BUILD=1이면 빌드한다).
set -euo pipefail
cd "$(dirname "$0")/.."
source load-test/perf/lib.sh

ver=${1:?버전(V0~V4)}
perf_require_version "$ver"
img_ver=$(perf_image_version "$ver")
commit=$(perf_expected_commit "$ver")
tag="$PERF_IMAGE:perf-$img_ver"
mkdir -p "$PERF_RESULTS/$ver"
log="$PERF_RESULTS/$ver/switch.txt"
started=$(date +%s)

built=0
if [ "$(docker image inspect -f '{{index .Config.Labels "perf.commit"}}' "$tag" 2>/dev/null || true)" != "$commit" ] \
  || [ "${FORCE_BUILD:-0}" = 1 ]; then
  echo "delivery 이미지 빌드: $img_ver $commit"
  # Note: 작업 트리가 아니라 그 커밋의 내용만 빌드 컨텍스트로 보낸다. 커밋하지 않은 파일이 섞이지 않고 worktree도 남지 않는다.
  git archive --format=tar "$commit" | docker build -q -t "$tag" --build-arg MODULE=delivery-service \
    --label "perf.commit=$commit" --label "perf.version=$img_ver" - >/dev/null
  built=1
fi
docker tag "$tag" "$PERF_IMAGE:perf-current"

# 계정·업체 시드는 delivery 스키마를 쓰지 않는다. 기동 확인에 dmaster 로그인이 필요해 먼저 넣는다(이미 있으면 건너뛴다)
perf_psql < infra/postgres/seed/delivery-e2e.sql > /dev/null

echo "delivery 스키마를 지우고 $ver 코드로 다시 띄운다"
docker compose stop delivery-service >/dev/null
perf_psql <<'SQL'
DROP SCHEMA IF EXISTS delivery_schema CASCADE;
CREATE SCHEMA delivery_schema AUTHORIZATION delivery_app;
SQL
docker compose up -d --no-deps --no-build --force-recreate delivery-service >/dev/null
perf_stop_zipkin
perf_wait_ready
[ "$(perf_delivery_commit)" = "$commit" ] || { echo "기동한 delivery 커밋이 ${commit}이 아니다" >&2; exit 1; }

echo "시드: 배송담당자 허브당 10명, 과거 배송 ${COUNT:-1000000}건"
seed=$(COUNT="${COUNT:-1000000}" load-test/seed/perf-seed.sh 2>&1)
echo "$seed" | tail -3

{
  echo "version=$ver"
  echo "commit=$commit"
  echo "image=$(docker image inspect -f '{{.Id}}' "$tag")"
  echo "built=$built"
  echo "delivery_started_at=$(perf_delivery_started_at)"
  echo "switched_at=$(date +%s)"
  echo "took_sec=$(( $(date +%s) - started ))"
  echo "seed:"
  echo "$seed"
} > "$log"
echo "끝: $log"
