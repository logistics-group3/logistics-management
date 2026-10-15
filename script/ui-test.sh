#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ENV_FILE="$ROOT/.env.ui-test"

prepare() {
  if [[ ! -f "$ENV_FILE" ]]; then
    python3 - <<'PY'
import os, secrets
values = {
    'POSTGRES_USER': 'postgres', 'POSTGRES_PASSWORD': secrets.token_hex(24),
    'POSTGRES_PORT': '15432', 'CONFIG_SERVER_PORT': '18888', 'EUREKA_PORT': '18761',
    'GATEWAY_PORT': '127.0.0.1:18080', 'JWT_SECRET': secrets.token_hex(48),
    'MASTER_USERNAME': 'uimaster', 'MASTER_PASSWORD': 'UiLocal!2026',
    'SLACK_WEBHOOK_URL': '',
}
for service in ('USER', 'HUB', 'COMPANY', 'ORDER', 'DELIVERY'):
    values[f'{service}_DB_PASSWORD'] = secrets.token_hex(24)
fd = os.open('.env.ui-test', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as file:
    file.writelines(f'{key}={value}\n' for key, value in values.items())
PY
  fi
}

compose() {
  # Note: 고정된 프로젝트·환경 파일과 빈 Webhook으로 기존 개발 DB와 실제 알림을 보호한다.
  python3 - "$@" <<'PYCOMPOSE'
import os, subprocess, sys
from pathlib import Path
values = dict(line.split('=', 1) for line in Path('.env.ui-test').read_text().splitlines() if '=' in line and not line.startswith('#'))
# 셸에 남아 있는 개발 환경 변수보다 전용 파일을 우선한다.
environment = {**os.environ, **values, 'SLACK_WEBHOOK_URL': ''}
result = subprocess.run(['docker', 'compose', '-p', 'logistics-ui-test', '--env-file', '.env.ui-test', '-f', 'docker-compose.yml', *sys.argv[1:]], env=environment)
sys.exit(result.returncode)
PYCOMPOSE
}

case "${1:-status}" in
  up) prepare; compose up -d --build ;;
  down) prepare; compose down ;;
  status) prepare; compose ps --format 'table {{.Name}}\t{{.State}}\t{{.Ports}}' ;;
  seed) prepare; python3 script/ui-test-seed.py ;;
  frontend) exec npm --prefix frontend run dev -- --host 127.0.0.1 --port 5174 --strictPort ;;
  *) echo '사용법: script/ui-test.sh {up|down|status|seed|frontend}' >&2; exit 2 ;;
esac
