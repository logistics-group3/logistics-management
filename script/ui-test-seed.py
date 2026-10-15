#!/usr/bin/env python3
"""별도 UI 테스트 프로젝트에 공개 API로 재현 가능한 테스트 데이터를 만든다."""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)
ENV = dict(line.split('=', 1) for line in (ROOT / '.env.ui-test').read_text().splitlines() if '=' in line and not line.startswith('#'))
compose = ['docker', 'compose', '-p', 'logistics-ui-test', '--env-file', '.env.ui-test', '-f', 'docker-compose.yml']
port = subprocess.check_output(compose + ['port', 'gateway-service', '8080'], text=True, env={**os.environ, **ENV, 'SLACK_WEBHOOK_URL': ''}).strip()
if port != '127.0.0.1:18080':
    raise SystemExit('UI 테스트 Gateway 바인딩이 예상과 다르다. 실행 대상을 확인한다.')
container = json.loads(subprocess.check_output(['docker', 'inspect', 'logistics-ui-test-order-service-1']))[0]
if any(item.startswith('SLACK_WEBHOOK_URL=') and item != 'SLACK_WEBHOOK_URL=' for item in container['Config']['Env']):
    raise SystemExit('UI 테스트 Slack 알림이 꺼져 있어야 한다.')
if any(item.split('=', 1)[1].strip().lower() in ('true', '1', 'yes', 'on') for item in container['Config']['Env'] if item.startswith('SLACK_DM_ENABLED=')):
    raise SystemExit('실제 DM이 활성화된 동안 시드를 실행할 수 없다. DM을 끈 뒤 데이터를 준비한다.')
BASE = f'http://{port}/api/v1'
PASSWORD = ENV['MASTER_PASSWORD']

def request(path, method='GET', body=None, token=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = f'Bearer {token}'
    if key: headers['Idempotency-Key'] = key
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            return json.load(response)['data']
    except urllib.error.HTTPError as error:
        payload = json.load(error)
        raise RuntimeError(f'{method} {path}: HTTP {error.code} {payload.get("code", "")} {payload.get("message", "")}') from None

def login(username):
    return request('/auth/login', 'POST', {'username': username, 'password': PASSWORD})['accessToken']

for attempt in range(30):
    try:
        master = login(ENV['MASTER_USERNAME'])
        request('/hubs?size=50', token=master)
        break
    except (RuntimeError, OSError):
        if attempt == 29: raise SystemExit('서비스 준비를 확인하지 못했다. UI 테스트 컨테이너 로그를 확인한다.')
        time.sleep(2)

hubs = request('/hubs?size=50', token=master)['content']
hub = next((item for item in hubs if '서울' in item['name']), hubs[0])
users = request('/users?size=50', token=master)['content']
accounts = [('uiuser', '김서연', 'COMPANY', '한빛 리테일'), ('uidriver', '이준호', 'HUB', hub['name']), ('uiviewer', '박지민', 'COMPANY', '새봄 유통')]
for username, name, affiliation, affiliation_name in accounts:
    if not any(u['username'] == username for u in users):
        request('/auth/signup', 'POST', {'username': username, 'password': PASSWORD, 'name': name, 'slackEmail': username + '@example.com', 'affiliationType': affiliation, 'affiliationName': affiliation_name})
users = request('/users?size=50', token=master)['content']
by_username = {u['username']: u for u in users}
driver = by_username['uidriver']
if not driver['isDeliveryManager'] or driver['hubId'] != hub['hubId']:
    request(f'/users/{driver["userId"]}/role', 'PATCH', {'isDeliveryManager': True, 'hubId': hub['hubId']}, master)
companies = request('/companies?size=50', token=master)['content']
for name, address, manager in [('스파르타 서플라이', '서울특별시 송파구 올림픽로 300, 물류센터 A동', 'uimaster'), ('한빛 리테일', '서울특별시 강남구 테헤란로 152, 입고장 1층', 'uiuser'), ('새봄 유통', '서울특별시 성동구 아차산로 92, 2층', 'uiviewer')]:
    if not any(c['name'] == name for c in companies):
        companies.append(request('/companies', 'POST', {'name': name, 'hubId': hub['hubId'], 'address': address, 'managerId': by_username[manager]['userId']}, master))
supplier = next(c for c in companies if c['name'] == '스파르타 서플라이')
receiver = next(c for c in companies if c['name'] == '한빛 리테일')
products = request('/products?size=50', token=master)['content']
for name, price, description in [('프리미엄 A4 복사용지', 18500, '80g · 2,500매 / 박스'), ('친환경 포장 박스', 42000, '중형 · 50개입 / 묶음'), ('스테인리스 텀블러', 28000, '500ml · 실버 / 개'), ('업무용 무선 키보드', 89000, '저소음 · 블랙 / 개')]:
    if not any(p['name'] == name and p['companyId'] == supplier['companyId'] for p in products):
        products.append(request(f'/companies/{supplier["companyId"]}/products', 'POST', {'name': name, 'price': price, 'description': description}, master))
manifest_path = ROOT / '.local/ui-test-data.json'
if manifest_path.exists():
    print('테스트 계정·참조 데이터를 확인했다. 기존 주문 상태를 유지한다.')
    raise SystemExit(0)
user_token = login('uiuser')
orders = []
for index in range(12):
    product = products[index % len(products)]
    order = request('/orders', 'POST', {'supplierCompanyId': supplier['companyId'], 'receiverCompanyId': receiver['companyId'], 'productId': product['productId'], 'quantity': [4, 12, 8, 20, 6, 10][index % 6], 'requestNote': ['입고장 담당자에게 도착 전 연락 바랍니다.', '파손에 주의해 주세요.', '오전 입고 요청드립니다.'][index % 3]}, user_token if index >= 6 else master, f'ui-seed-v1-{index}')
    orders.append(order['orderId'])
    current = request(f'/orders/{order["orderId"]}', token=master)
    if index in (0, 4) and current['status'] != 'CANCELED':
        request(f'/orders/{order["orderId"]}/cancel', 'POST', token=master)
    elif index in (1, 2, 3):
        delivery = request(f'/deliveries/{order["deliveryId"]}', token=master)
        transitions = 4 if index == 1 else 1 if index == 2 else 2
        for route in sorted(delivery['routes'], key=lambda r: r['sequence']):
            for status in ('MOVING', 'ARRIVED'):
                if transitions <= 0: break
                transitions -= 1
                if route['status'] == 'ARRIVED' or route['status'] == status: continue
                request(f'/deliveries/{delivery["deliveryId"]}/routes/{route["deliveryRouteId"]}/status', 'PATCH', {'status': status}, master)
manifest_path.parent.mkdir(exist_ok=True)
manifest_path.write_text(json.dumps({'hubId': hub['hubId'], 'supplierCompanyId': supplier['companyId'], 'receiverCompanyId': receiver['companyId'], 'products': products, 'orders': orders, 'users': {key: value['userId'] for key, value in by_username.items()}}, ensure_ascii=False, indent=2))
print(f'실제 API로 준비 완료: 사용자 {len(users)}명, 허브 {len(hubs)}개, 업체 {len(companies)}개, 상품 {len(products)}개, 주문 {len(orders)}건. Slack 비활성화 확인.')
