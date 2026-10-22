#!/usr/bin/env bash
# 측정 전용 시드를 넣는다(delivery-service/docs/perf-master.md 4절).
# 사용: COUNT=1000000 load-test/seed/perf-seed.sh
#   전제: 모든 서비스가 Flyway로 테이블을 만든 뒤, 계정·업체 시드(delivery-e2e.sql)를 넣은 DB.
#   순서: 허브마다 배송담당자 10명(perf-managers.sql) → 과거 배송 COUNT건(perf-load.sql, 기본 100만).
#   측정 버전(V0~V3)을 바꿔 DB를 새로 만들 때마다 다시 넣는다. 스키마에 있는 컬럼만 채운다.
set -euo pipefail
cd "$(dirname "$0")/../.."
count="${COUNT:-1000000}"
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d logistics -v ON_ERROR_STOP=1 -q' \
  < load-test/seed/perf-managers.sql
docker compose exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d logistics -v ON_ERROR_STOP=1 -q -v count=${count}" \
  < load-test/seed/perf-load.sql
