#!/usr/bin/env python3
"""C0 대조군과 F1/F2 강제 종료를 실행하고 증거·판정을 남긴다."""
import argparse
import hashlib
import json
import subprocess
import time
import uuid

from experiment import Experiment, LOCAL, ROOT, now


def wait_file(path, process, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if path.exists():
            return json.loads(path.read_text())
        if process.poll() is not None:
            raise RuntimeError(f'JDI 종료 {process.returncode}: {path.parent}')
        time.sleep(0.1)
    raise RuntimeError(f'JDI 신호 시간 초과: {path}')


def snapshot(ex, order_id, name):
    order_id = str(uuid.UUID(order_id))
    sql = f"""SELECT json_build_object(
      'creation', (SELECT row_to_json(c) FROM
        (SELECT order_creation_id AS attempt_id, order_id, status, delivery_id, failure_reason,
                failure_code, created_at, updated_at, deleted_at
         FROM order_schema.p_order_creations WHERE order_id='{order_id}') c),
      'allOrders', (SELECT count(*) FROM order_schema.p_orders WHERE order_id='{order_id}'),
      'activeOrders', (SELECT count(*) FROM order_schema.p_orders WHERE order_id='{order_id}' AND deleted_at IS NULL),
      'allDeliveries', (SELECT count(*) FROM delivery_schema.p_deliveries WHERE order_id='{order_id}'),
      'activeDeliveries', (SELECT count(*) FROM delivery_schema.p_deliveries WHERE order_id='{order_id}' AND deleted_at IS NULL),
      'deliveries', (SELECT coalesce(json_agg(d), '[]') FROM
        (SELECT delivery_id, order_id, status, deleted_at FROM delivery_schema.p_deliveries WHERE order_id='{order_id}') d),
      'activeRoutes', (SELECT count(*) FROM delivery_schema.p_delivery_routes r JOIN delivery_schema.p_deliveries d
        ON r.delivery_id=d.delivery_id WHERE d.order_id='{order_id}' AND d.deleted_at IS NULL AND r.deleted_at IS NULL),
      'closures', (SELECT count(*) FROM delivery_schema.p_delivery_creation_closures WHERE order_id='{order_id}'))"""
    text = ex.command(ex.compose + ['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d',
                      'logistics', '-At', '-v', 'ON_ERROR_STOP=1', '-c', sql], name + '-db')
    result = json.loads(text)
    ex.save(name + '-db.json', result)
    delivery = ex.command(ex.compose + ['exec', '-T', 'order-service', 'curl', '-fsS',
                          f'http://delivery-service:8085/internal/deliveries/orders/{order_id}'], name + '-i17')
    result['i17'] = json.loads(delivery)
    ex.save(name + '.json', result)
    return result


def run(kind, name, recover=False):
    control = kind.startswith('C0')
    fault = kind[-2:]
    ex = Experiment(name)
    tools = ex.folder / 'tools'
    tools.mkdir()
    for tool in ['experiment.py', 'scenario.py', 'JdiGate.java']:
        original = ROOT / 'script/recovery-experiment' / tool
        (tools / tool).write_bytes(original.read_bytes())
    ex.wait_ready()
    order_container = ex.inspect('order-service')
    manifest = json.loads((LOCAL / 'seed/manifest.json').read_text())
    body = {**manifest['command'], 'requestNote': name}
    source = ROOT / 'order-service/src/main/java/com/sparta/logistics/order/application/OrderService.java'
    lines = source.read_text().splitlines()
    anchor = '            process(creation);' if fault == 'F1' else '            CreateOrderCommand command = creation.command();'
    positions = [index + 1 for index, line in enumerate(lines) if line == anchor]
    assert len(positions) == 1, '중단 소스 위치가 모호하다'
    line = positions[0]
    ex.save('scenario.json', {'runId': name, 'kind': kind, 'startedAt': now(),
            'source': str(source.relative_to(ROOT)), 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'line': line, 'anchor': anchor.strip(), 'orderContainer': order_container,
            'toolSha256': {tool.name: hashlib.sha256(tool.read_bytes()).hexdigest() for tool in tools.iterdir()},
            'expected': 'COMPLETED' if control else 'PROCESSING after restart; no recovery'})
    java = ['java', '--add-modules', 'jdk.jdi', '-cp', str(LOCAL / 'jdi'),
            'JdiGate', '25005', str(line), str(ex.folder)]
    output = (ex.folder / 'jdi.stdout').open('w')
    error = (ex.folder / 'jdi.stderr').open('w')
    started = now()
    gate = subprocess.Popen(java, cwd=ROOT, stdin=subprocess.PIPE, stdout=output, stderr=error, text=True)
    result = {'kind': kind, 'runId': name, 'startedAt': started}
    try:
        wait_file(ex.folder / 'armed.json', gate)
        status, accepted = ex.http('/orders', 'POST', body, name, 'accept')
        assert status == 202, f'접수 실패: {status}, {accepted}'
        order_id = accepted['data']['orderId']
        result['orderId'] = order_id
        hit = wait_file(ex.folder / 'hit.json', gate)
        assert hit['thread'].startswith('order-creation-'), hit
        assert hit['deliveryCreateEntries'] == (0 if fault == 'F1' else 1), hit
        assert hit['orderSaveEntries'] == 0, hit
        before = snapshot(ex, order_id, 'before')
        assert before['creation']['status'] == 'PROCESSING', before
        assert before['allOrders'] == 0 and before['closures'] == 0, before
        assert before['activeDeliveries'] == (0 if fault == 'F1' else 1), before
        assert before['i17']['state'] == ('NONE' if fault == 'F1' else 'ACTIVE'), before
        if fault == 'F2':
            assert before['i17']['status'] == 'PICKUP_WAITING' and before['activeRoutes'] == 2, before
        status, _ = ex.http('/orders/creations/' + order_id, label='before-progress')
        assert status == 200
        if control:
            gate.stdin.write('resume\n')
            gate.stdin.flush()
            gate.wait(timeout=15)
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                status, progress = ex.http('/orders/creations/' + order_id)
                if status == 200 and progress['data']['status'] == 'COMPLETED':
                    break
                time.sleep(1)
            else:
                raise RuntimeError('대조군이 60초 안에 완료되지 않았다')
            after = snapshot(ex, order_id, 'after-release')
            assert after['creation']['status'] == 'COMPLETED'
            assert after['activeOrders'] == after['activeDeliveries'] == 1
            assert after['activeRoutes'] == 2 and after['closures'] == 0
            replay_status, replay = ex.http('/orders', 'POST', body, name, 'replay')
            assert replay_status == 201 and replay['data']['orderId'] == order_id
        else:
            # inspect에서 project/service label·알림 비활성·restart=no를 확인한 바로 그 컨테이너만 종료한다.
            ex.command(['docker', 'kill', '--signal=KILL', order_container], 'kill-order')
            gate.stdin.close()
            gate.wait(timeout=15)
            ex.inspect('order-service')
            state = ex.last_inspection
            ex.save('killed-container.json', state)
            assert state['state']['Status'] == 'exited' and state['state']['ExitCode'] == 137, state
            # 별도 JVM을 같은 컨테이너에서 재시작한다. debugger에는 더 이상 연결하지 않는다.
            ex.command(ex.compose + ['start', 'order-service'], 'restart-order')
            ex.wait_ready()
            ex.inspect('order-service')
            after = snapshot(ex, order_id, 'after-restart')
            assert after['creation']['status'] == 'PROCESSING'
            assert after['activeOrders'] == 0 and after['closures'] == 0
            assert after['activeDeliveries'] == (0 if fault == 'F1' else 1)
            assert after['i17']['state'] == ('NONE' if fault == 'F1' else 'ACTIVE')
            replay_status, replay = ex.http('/orders', 'POST', body, name, 'replay')
            assert replay_status == 202 and replay['data']['orderId'] == order_id
            # 같은 키 재전송의 부작용도 재확인한다. 상태 전이 대기와 구분한 10초 관측 창이다.
            time.sleep(10)
            stable = snapshot(ex, order_id, 'after-replay-10s')
            assert stable['creation']['status'] == 'PROCESSING'
            assert stable['activeOrders'] == 0
            assert stable['activeDeliveries'] == (0 if fault == 'F1' else 1)
            result['observationWindowSeconds'] = 10
            if recover:
                recovery_body = {'recoveryId': str(uuid.uuid5(uuid.NAMESPACE_URL, name)),
                    'attemptId': before['creation']['attempt_id'], 'expectedStatus': 'PROCESSING',
                    'reason': '격리 order의 SIGKILL exit 137·재기동과 원래 실행 중단을 확인했다.',
                    'originalExecutionStopped': True}
                recovery_path = '/orders/' + order_id + '/creation-recoveries'
                code, recovered = ex.http(recovery_path, 'POST', recovery_body, label='recover')
                assert code == 200 and recovered['data']['status'] == 'COMPLETED', recovered
                completed = snapshot(ex, order_id, 'after-recovery')
                assert completed['creation']['status'] == 'ABORTED' and completed['creation']['failure_reason'] is None
                assert completed['activeOrders'] == completed['activeDeliveries'] == completed['activeRoutes'] == 0
                assert completed['closures'] == 1 and completed['i17']['state'] == 'CLOSED'
                code, progress = ex.http('/orders/creations/' + order_id, label='recovered-progress')
                assert code == 200 and progress['data']['status'] == 'ABORTED'
                code, replay = ex.http('/orders', 'POST', body, name, 'recovered-replay')
                assert code == 409 and replay['code'] == 'ORDER_CREATION_ABORTED', replay
                code, repeated = ex.http(recovery_path, 'POST', recovery_body, label='repeat-recovery')
                assert code == 200 and repeated['data'] == recovered['data'], repeated
                code, candidates = ex.http('/orders/creation-recoveries/candidates?size=50', label='candidates-after-recovery')
                assert code == 200 and all(item['orderId'] != order_id for item in candidates['data']['content'])
                late_command = {'orderId': order_id, **{k: body[k] for k in ['supplierCompanyId', 'receiverCompanyId']},
                    'departureHubId': manifest['hubId'], 'destinationHubId': manifest['hubId'],
                    'pickupAddress': '서울특별시 복구실험 테스트 주소', 'deliveryAddress': '서울특별시 복구실험 테스트 주소',
                    'recipientName': '복구실험 수령업체', 'receiverManagerId': manifest['masterId']}
                late = ex.command(ex.compose + ['exec', '-T', 'order-service', 'curl', '-sS', '-w', '\n%{http_code}',
                    '-H', 'Content-Type: application/json', '-d', json.dumps(late_command, ensure_ascii=False),
                    'http://delivery-service:8085/internal/deliveries'], 'late-create-after-recovery')
                assert late.strip().endswith('409') and 'DELIVERY_CREATION_CLOSED' in late, late
                result.update({'afterRecovery': completed, 'recoveryResult': recovered['data'],
                               'recoveredReplayStatus': 409, 'recoveryReplayIdempotent': True})
        result.update({'verdict': 'PASS_CONTROL' if control else ('RECOVERED' if recover else 'REPRODUCED'),
                       'before': before, 'after': after, 'replayStatus': replay_status, 'endedAt': now()})
    except Exception as exception:
        result.update({'verdict': 'FAILED_OR_INCONCLUSIVE', 'error': str(exception), 'endedAt': now()})
        raise
    finally:
        if gate.poll() is None:
            gate.terminate()
            gate.wait(timeout=10)
        output.close()
        error.close()
        ex.record({'start': started, 'end': now(), 'argv': java, 'exitCode': gate.returncode,
                   'stdout': 'jdi.stdout', 'stderr': 'jdi.stderr', 'note': '별도 디버거; 제어 명령은 resume 또는 JVM kill'})
        ex.command(ex.compose + ['logs', '--no-color', '--timestamps', 'order-service', 'delivery-service'],
                   'services-log', check=False)
        ex.save('result.json', result)
    print(json.dumps({'runId': name, 'verdict': result['verdict'], 'orderId': result['orderId'],
                      'replayStatus': result['replayStatus']}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('kind', choices=['C0-F1', 'C0-F2', 'F1', 'F2'])
    parser.add_argument('run_id')
    parser.add_argument('--recover', action='store_true')
    args = parser.parse_args()
    if not all(char.isalnum() or char in '-_' for char in args.run_id):
        raise SystemExit('run_id는 영문·숫자·하이픈·밑줄만 허용한다')
    if (LOCAL / args.run_id).exists():
        raise SystemExit('같은 run_id가 이미 있다. 새 ID를 사용한다')
    run(args.kind, args.run_id, args.recover)
