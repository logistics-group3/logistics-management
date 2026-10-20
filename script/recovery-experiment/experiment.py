#!/usr/bin/env python3
"""현재 서비스 코드의 장애 재현용 환경·명령·HTTP 증거를 기록한다. 복구 기능은 구현하지 않는다."""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import secrets
import shlex
import socket
import subprocess
import time
import urllib.error
import urllib.request
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local/recovery-experiments'
PROJECT = 'logistics-recovery-test'
ENV_FILE = ROOT / '.env.recovery-test'
OVERRIDE = LOCAL / 'compose.override.yml'
SERVICES = ['postgres', 'config-server', 'eureka-server', 'zipkin', 'user-service',
            'hub-service', 'company-service', 'delivery-service', 'order-service', 'gateway-service']


def now():
    return datetime.now(ZoneInfo('Asia/Seoul')).isoformat()


class Experiment:
    def __init__(self, name):
        self.folder = LOCAL / name
        self.folder.mkdir(parents=True, exist_ok=True)
        self.values = dict(line.split('=', 1) for line in ENV_FILE.read_text().splitlines()
                           if '=' in line and not line.startswith('#')) if ENV_FILE.exists() else {}
        self.token = None
        self.sequence = 0
        self.environment = {key: value for key, value in os.environ.items()
                            if not key.startswith('COMPOSE_')}
        self.environment.update(self.values)
        self.compose = ['docker', 'compose', '-p', PROJECT, '--env-file', str(ENV_FILE),
                        '-f', str(ROOT / 'docker-compose.yml'), '-f', str(OVERRIDE)]

    def clean(self, value):
        if isinstance(value, dict):
            return {k: ('[REDACTED]' if k.lower() in ('password', 'accesstoken', 'refreshtoken',
                     'authorization', 'token') else self.clean(v)) for k, v in value.items()}
        if isinstance(value, list):
            return [self.clean(item) for item in value]
        if isinstance(value, str):
            for key, secret in self.values.items():
                if secret and any(word in key for word in ('PASSWORD', 'SECRET', 'TOKEN')):
                    value = value.replace(secret, '[REDACTED]')
            if self.token:
                value = value.replace(self.token, '[REDACTED]')
        return value

    def save(self, name, value):
        base = Path(name)
        suffix = 1
        while (self.folder / name).exists():
            suffix += 1
            name = f'{base.stem}-{suffix}{base.suffix}'
        (self.folder / name).write_text(json.dumps(self.clean(value), ensure_ascii=False, indent=2) + '\n')
        return name

    def record(self, value):
        with (self.folder / 'commands.jsonl').open('a') as output:
            output.write(json.dumps(self.clean(value), ensure_ascii=False) + '\n')

    def command(self, args, name, timeout=180, transform=None, check=True):
        base = name
        suffix = 1
        while (self.folder / (name + '.stdout')).exists():
            suffix += 1
            name = f'{base}-{suffix}'
        start = now()
        result = subprocess.run(args, cwd=ROOT, env=self.environment, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        out = transform(result.stdout) if transform and result.returncode == 0 else result.stdout
        (self.folder / (name + '.stdout')).write_text(self.clean(out))
        (self.folder / (name + '.stderr')).write_text(self.clean(result.stderr))
        self.record({'start': start, 'end': now(), 'cwd': str(ROOT),
                     'argv': args, 'command': shlex.join(args), 'exitCode': result.returncode,
                     'stdout': name + '.stdout', 'stderr': name + '.stderr'})
        if check and result.returncode:
            raise RuntimeError(f'{name}: exit {result.returncode}, {result.stderr[-500:]}')
        return result.stdout

    def http(self, path, method='GET', body=None, key=None, label=None):
        self.sequence += 1
        name = label or f'http-{self.sequence:03d}'
        headers = {'Content-Type': 'application/json'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        if key:
            headers['Idempotency-Key'] = key
        url = 'http://127.0.0.1:28080/api/v1' + path
        request = urllib.request.Request(url, method=method, headers=headers,
                    data=json.dumps(body).encode() if body is not None else None)
        start = now()
        try:
            response = urllib.request.urlopen(request, timeout=25)
        except urllib.error.HTTPError as error:
            response = error
        payload = response.read().decode()
        try:
            payload = json.loads(payload)
        except json.JSONDecodeError:
            pass
        receipt = self.save(name + '.json', {'start': start, 'end': now(), 'method': method, 'url': url,
                  'requestBody': body, 'idempotencyKey': key, 'status': response.status,
                  'responseHeaders': dict(response.headers), 'responseBody': payload})
        self.record({'start': start, 'end': now(), 'transport': 'Python urllib.request',
                     'method': method, 'url': url, 'status': response.status,
                     'evidence': receipt})
        return response.status, payload

    def login(self):
        status, payload = self.http('/auth/login', 'POST', {
            'username': self.values['MASTER_USERNAME'], 'password': self.values['MASTER_PASSWORD']})
        if status != 200:
            raise RuntimeError(f'login: HTTP {status}')
        self.token = payload['data']['accessToken']

    def wait_ready(self):
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                self.login()
                ok = all(self.http(path)[0] == 200 for path in
                         ['/hubs?size=50', '/companies?size=50', '/orders?size=10', '/deliveries?size=10'])
                if ok:
                    return
            except (OSError, RuntimeError):
                pass
            time.sleep(3)
        raise RuntimeError('180초 안에 실제 Gateway 경로 준비를 확인하지 못했다')

    def inspect(self, service):
        ids = self.command(self.compose + ['ps', '-aq', service], service + '-id').strip().splitlines()
        if len(ids) != 1:
            raise RuntimeError(f'{service}: container count {len(ids)}')
        def selected(text):
            item = json.loads(text)[0]
            labels = item['Config']['Labels']
            if labels.get('com.docker.compose.project') != PROJECT or labels.get('com.docker.compose.service') != service:
                raise RuntimeError('컨테이너 실험 label 불일치')
            env = dict(pair.split('=', 1) for pair in item['Config']['Env'])
            if service == 'order-service':
                assert env.get('SLACK_WEBHOOK_URL') == ''
                assert env.get('SLACK_DM_ENABLED') == 'false'
                assert env.get('SLACK_BOT_TOKEN') == ''
                assert item['HostConfig']['RestartPolicy']['Name'] == 'no'
            self.last_inspection = {'id': item['Id'], 'image': item['Image'], 'state': item['State'],
                 'restartCount': item['RestartCount'], 'labels': labels,
                 'ports': item['NetworkSettings']['Ports'],
                 'mounts': [{k: mount.get(k) for k in ('Type', 'Name', 'Destination')} for mount in item['Mounts']],
                 'slackDisabled': service == 'order-service',
                 'memoryLimitBytes': item['HostConfig']['Memory'],
                 'jvmOptions': env.get('JAVA_OPTS'),
                 'restartPolicy': item['HostConfig']['RestartPolicy']}
            return json.dumps(self.last_inspection, indent=2)
        self.command(['docker', 'inspect', ids[0]], service + '-inspect', transform=selected)
        return ids[0]


def memory_limited_override():
    options = '-Xms32m -Xmx128m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:ActiveProcessorCount=2'
    text = 'services:\n'
    for service in ['config-server', 'eureka-server', 'gateway-service', 'user-service',
                    'hub-service', 'company-service', 'delivery-service']:
        text += f'  {service}:\n    restart: "no"\n    mem_limit: 448m\n    environment:\n      JAVA_OPTS: "{options}"\n'
    order_options = options.replace('-Xmx128m', '-Xmx192m') + ' -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005'
    return text + f'''  order-service:
    restart: "no"
    mem_limit: 512m
    environment:
      SLACK_WEBHOOK_URL: ""
      SLACK_DM_ENABLED: "false"
      SLACK_BOT_TOKEN: ""
      SLACK_DM_WORKSPACE_ID: ""
      SLACK_DM_ALLOWED_EMAILS: ""
      JAVA_OPTS: "{order_options}"
    ports:
      - "127.0.0.1:25005:5005"
'''


def setup():
    LOCAL.mkdir(parents=True, exist_ok=True)
    if ENV_FILE.exists() or OVERRIDE.exists():
        raise RuntimeError('전용 설정이 이미 있다. 덮어쓰지 않고 중단한다')
    for port in [25432, 28888, 28761, 28080, 29411, 39090, 33000, 25005]:
        with socket.socket() as test:
            test.bind(('127.0.0.1', port))
    values = {'POSTGRES_USER': 'postgres', 'POSTGRES_PASSWORD': secrets.token_hex(24),
        'POSTGRES_PORT': '25432', 'CONFIG_SERVER_PORT': '28888', 'EUREKA_PORT': '28761',
        'GATEWAY_PORT': '127.0.0.1:28080', 'ZIPKIN_PORT': '29411',
        'PROMETHEUS_PORT': '39090', 'GRAFANA_PORT': '33000',
        'GRAFANA_ADMIN_PASSWORD': secrets.token_hex(12), 'JWT_SECRET': secrets.token_hex(48),
        'MASTER_USERNAME': 'recmaster', 'MASTER_PASSWORD': 'Rc!7a' + secrets.token_hex(4) + 'Z',
        'SLACK_WEBHOOK_URL': '', 'SLACK_DM_ENABLED': 'false', 'SLACK_BOT_TOKEN': '',
        'SLACK_DM_WORKSPACE_ID': '', 'SLACK_DM_ALLOWED_EMAILS': ''}
    for service in ('USER', 'HUB', 'COMPANY', 'ORDER', 'DELIVERY'):
        values[service + '_DB_PASSWORD'] = secrets.token_hex(24)
    fd = os.open(ENV_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as output:
        output.writelines(f'{key}={value}\n' for key, value in values.items())
    OVERRIDE.write_text(memory_limited_override())
    experiment = Experiment('setup')
    experiment.save('setup.json', {'at': now(), 'project': PROJECT, 'envPermission': '0600',
        'portBindCheck': 'passed', 'services': SERVICES, 'debugger': 'loopback:25005',
        'monitoring': 'Zipkin only; Prometheus/Grafana not started',
        'businessCodeChanges': False})
    for args, name in [(['git', 'rev-parse', 'HEAD'], 'git-head'),
                       (['git', 'diff', '--', '*service/src/**', 'config-repo', 'docker-compose.yml'], 'business-diff'),
                       (['docker', 'version'], 'docker-version'), (['docker', 'compose', 'version'], 'compose-version'),
                       (['java', '-version'], 'java-version'), (['./gradlew', '--version'], 'gradle-version'),
                       (['docker', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}'], 'existing-containers')]:
        experiment.command(args, name)
    experiment.command(experiment.compose + ['config', '--quiet'], 'compose-config')
    experiment.command(experiment.compose + ['up', '-d', '--build', *SERVICES], 'compose-up', timeout=1200)
    experiment.wait_ready()
    for service in SERVICES:
        experiment.inspect(service)
    print('격리 스택 준비 완료:', experiment.folder)


def seed():
    ex = Experiment('seed')
    ex.wait_ready()
    def data(path, method='GET', body=None):
        status, response = ex.http(path, method, body)
        if status not in (200, 201):
            raise RuntimeError(f'{method} {path}: {status} {response}')
        return response['data']
    hubs = data('/hubs?size=50')['content']
    hub = next(item for item in hubs if '서울' in item['name'])
    users = data('/users?size=50')['content']
    if not any(item['username'] == 'recdriver' for item in users):
        data('/auth/signup', 'POST', {'username': 'recdriver', 'password': ex.values['MASTER_PASSWORD'],
              'name': '복구실험 담당자', 'slackEmail': 'recovery-driver@example.com',
              'affiliationType': 'HUB', 'affiliationName': hub['name']})
    users = data('/users?size=50')['content']
    driver = next(item for item in users if item['username'] == 'recdriver')
    master = next(item for item in users if item['username'] == 'recmaster')
    data('/users/' + driver['userId'] + '/role', 'PATCH', {'isDeliveryManager': True, 'hubId': hub['hubId']})
    companies = data('/companies?size=50')['content']
    for name in ('복구실험 공급업체', '복구실험 수령업체'):
        if not any(item['name'] == name for item in companies):
            companies.append(data('/companies', 'POST', {'name': name, 'hubId': hub['hubId'],
                              'address': '서울특별시 복구실험 테스트 주소', 'managerId': master['userId']}))
    supplier = next(item for item in companies if item['name'] == '복구실험 공급업체')
    receiver = next(item for item in companies if item['name'] == '복구실험 수령업체')
    products = data('/products?size=50')['content']
    product = next((item for item in products if item['name'] == '복구실험 상품'), None)
    if product is None:
        product = data('/companies/' + supplier['companyId'] + '/products', 'POST',
                       {'name': '복구실험 상품', 'price': 12000, 'description': '장애 재현 전용'})
    manifest = {'version': 1, 'hubId': hub['hubId'], 'driverId': driver['userId'],
                'masterId': master['userId'], 'command': {'supplierCompanyId': supplier['companyId'],
                'receiverCompanyId': receiver['companyId'], 'productId': product['productId'], 'quantity': 2}}
    ex.save('manifest.json', manifest)
    print('공개 API 시드 완료:', ex.folder)


def upgrade():
    ex = Experiment('implementation-stack-upgrade')
    if not ENV_FILE.exists() or not OVERRIDE.exists():
        raise RuntimeError('보존한 전용 환경 파일이 없다')
    ex.command(ex.compose + ['config', '--quiet'], 'compose-config')
    ex.command(['git', 'diff', '--', 'order-service', 'delivery-service', 'config-repo'], 'implementation-diff')
    ex.command(ex.compose + ['up', '-d', '--build', *SERVICES], 'compose-up', timeout=1200)
    ex.wait_ready()
    for service in SERVICES:
        ex.inspect(service)
    print('보존한 실험 DB에 구현 이미지를 반영했다:', ex.folder)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['setup', 'seed', 'upgrade'])
    args = parser.parse_args()
    os.chdir(ROOT)
    {'setup': setup, 'seed': seed, 'upgrade': upgrade}[args.action]()
