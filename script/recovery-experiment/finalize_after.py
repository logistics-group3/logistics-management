#!/usr/bin/env python3
"""구현 후 F1/F2·권한·마이그레이션 보존을 검증한 뒤 전용 스택을 종료한다."""
import json
import sys

from experiment import Experiment, LOCAL, SERVICES, now
from scenario import snapshot


def finalize():
    ex = Experiment('implementation-final-verification')
    ex.wait_ready()
    results = []
    for run_id in ['20261020-AFTER-C0-F1-02', '20261020-AFTER-C0-F2-02',
                   '20261020-AFTER-F1-02', '20261020-AFTER-F2-02']:
        result = json.loads((LOCAL / run_id / 'result.json').read_text())
        control = result['kind'].startswith('C0')
        current = snapshot(ex, result['orderId'], run_id)
        status, progress = ex.http('/orders/creations/' + result['orderId'], label=run_id + '-progress')
        assert status == 200 and progress['data']['status'] == current['creation']['status']
        assert current['creation']['status'] == ('COMPLETED' if control else 'ABORTED')
        assert current['activeOrders'] == current['activeDeliveries'] == (1 if control else 0)
        assert current['activeRoutes'] == (2 if control else 0)
        assert current['closures'] == (0 if control else 1)
        if not control:
            recovery_id = result['recoveryResult']['recoveryId']
            status, detail = ex.http('/orders/creation-recoveries/' + recovery_id, label=run_id + '-history')
            assert status == 200 and detail['data']['status'] == 'COMPLETED'
            assert detail['data']['expectedStatus'] == 'PROCESSING'
            assert detail['data']['reason'] and detail['data']['requestedBy']
            assert all(event['actor'] == ex.values['MASTER_USERNAME'] for event in detail['data']['events'])
        results.append({'runId': run_id, 'verdict': result['verdict'], 'current': current})
    master_token = ex.token
    ex.token = None
    assert ex.http('/orders/creation-recoveries/candidates', label='unauthenticated-candidates')[0] == 401
    status, login = ex.http('/auth/login', 'POST', {'username': 'recdriver', 'password': ex.values['MASTER_PASSWORD']}, label='user-login')
    assert status == 200
    ex.token = login['data']['accessToken']
    assert ex.http('/orders/creation-recoveries/candidates', label='user-candidates')[0] == 403
    ex.token = master_token
    assert ex.http('/orders/creation-recoveries/candidates', label='master-candidates')[0] == 200
    baseline = json.loads((LOCAL / 'final-verification/summary.json').read_text())['cases']
    for case in baseline:
        status, progress = ex.http('/orders/creations/' + case['orderId'], label='preserved-' + case['kind'])
        assert status == 200 and progress['data']['status'] == case['final']['creation']['status']
    ex.command(ex.compose + ['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', 'logistics', '-At', '-c',
               'SELECT version,success FROM order_schema.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1'], 'migration-v7')
    for service in SERVICES:
        ex.inspect(service)
    ex.command(ex.compose + ['logs', '--no-color', '--timestamps', 'order-service', 'delivery-service', 'gateway-service'], 'final-services-log')
    ex.save('summary.json', {'at': now(), 'cases': results, 'baselineRecordsPreserved': True,
                             'gatewayAuthorization': {'unauthenticated': 401, 'user': 403, 'master': 200}})
    ex.command(ex.compose + ['down'], 'compose-down', timeout=180)
    ex.command(['docker', 'volume', 'inspect', 'logistics-recovery-test_pgdata'], 'preserved-volume')
    original = (LOCAL / 'setup/existing-containers.stdout').read_text()
    expected = {line.split()[1]: line.split()[0] for line in original.splitlines()}
    current = ex.command(['docker', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}'], 'existing-containers-after')
    actual = {line.split()[1]: line.split()[0] for line in current.splitlines()}
    assert all(actual.get(name) == identity for name, identity in expected.items())
    def only_state(text):
        return json.dumps([{key: item[key] for key in ['Id', 'Name', 'State', 'RestartCount']}
                           for item in json.loads(text)], indent=2)
    ex.command(['docker', 'inspect', *expected.values()], 'existing-container-states', transform=only_state)
    ex.save('cleanup.json', {'at': now(), 'stackStopped': True, 'volumePreserved': True,
              'originalContainerIdsPreserved': True, 'originalContainersRunning': len(expected),
              'incident': 'OOM 실행 1건은 제외했다. 기존 일부 컨테이너 재시작도 관찰했으므로 무영향이라고 주장하지 않는다.'})
    print('복구 후 최종 상태·실제 Gateway 권한·V7 보존 검증·전용 스택 종료 완료')


if __name__ == '__main__':
    if sys.argv[1:] != ['--stop-dedicated-stack']:
        raise SystemExit('사용법: finalize_after.py --stop-dedicated-stack')
    finalize()
