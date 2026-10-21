#!/usr/bin/env python3
"""재현 증거를 마지막으로 검증하고 전용 스택만 종료한다. DB 볼륨은 보존한다."""
import json
import sys

from experiment import Experiment, LOCAL, SERVICES, now
from scenario import snapshot


def finalize():
    ex = Experiment('final-verification')
    ex.wait_ready()
    summary = []
    for run_id in ['20261020-C0-F1-02', '20261020-C0-F2-01', '20261020-F1-01', '20261020-F2-01']:
        result = json.loads((LOCAL / run_id / 'result.json').read_text())
        order_id = result['orderId']
        status, progress = ex.http('/orders/creations/' + order_id, label=run_id + '-progress')
        assert status == 200
        current = snapshot(ex, order_id, run_id)
        control = result['kind'].startswith('C0')
        assert current['creation']['status'] == progress['data']['status'] == ('COMPLETED' if control else 'PROCESSING')
        assert current['allOrders'] == current['activeOrders'] == (1 if control else 0)
        deliveries = 0 if result['kind'] == 'F1' else 1
        assert current['allDeliveries'] == current['activeDeliveries'] == deliveries
        assert current['activeRoutes'] == deliveries * 2 and current['closures'] == 0
        summary.append({'runId': run_id, 'kind': result['kind'], 'verdict': result['verdict'],
                        'orderId': order_id, 'attemptId': current['creation']['attempt_id'],
                        'final': current, 'replayStatus': result['replayStatus']})
    ex.command(ex.compose + ['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'logistics',
                 '-At', '-v', 'ON_ERROR_STOP=1', '-c',
                 "SELECT count(*) FROM order_schema.p_order_creations WHERE idempotency_key='20261020-C0-F1-01'"],
               'excluded-attempt-count')
    ex.command(ex.compose + ['logs', '--no-color', '--timestamps', 'order-service', 'delivery-service', 'gateway-service'],
               'final-services-log')
    ex.command(['git', 'diff', '--', '*service/src/**', 'common/src/**', 'common-core/src/**',
                'config-repo', 'docker-compose.yml'], 'business-diff')
    for service in SERVICES:
        ex.inspect(service)
    ex.save('summary.json', {'at': now(), 'cases': summary, 'businessCodeChanges': False})
    ex.command(ex.compose + ['down'], 'compose-down', timeout=180)
    ex.command(['docker', 'volume', 'inspect', 'logistics-recovery-test_pgdata'], 'preserved-volume')
    current = ex.command(['docker', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}'], 'existing-containers-after')
    original = (LOCAL / 'setup/existing-containers.stdout').read_text()
    original_ids = {line.split()[1]: line.split()[0] for line in original.splitlines()}
    current_ids = {line.split()[1]: line.split()[0] for line in current.splitlines()}
    assert all(current_ids.get(name) == identity for name, identity in original_ids.items())
    ex.command(ex.compose + ['ps', '-a'], 'recovery-stack-after-down')
    ex.save('cleanup.json', {'at': now(), 'stackStopped': True, 'volumePreserved': True,
                             'originalContainerIdsPreserved': original_ids})
    print('최종 상태 확인·전용 스택 종료·DB 볼륨 및 기존 컨테이너 ID 보존 완료')


if __name__ == '__main__':
    if sys.argv[1:] != ['--stop-dedicated-stack']:
        raise SystemExit('사용법: finalize.py --stop-dedicated-stack')
    finalize()
