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
hub_by_name = {item['name']: item for item in hubs}
# 시나리오 테스트 가이드(frontend 테스트 가이드 화면)의 시나리오용 허브. 서울 허브에는 배송담당자를 더하지 않는다.
# Note: 자동 배정은 허브의 담당 건수가 가장 적은 담당자를 고르므로, 서울에 담당자를 늘리면 기존 E2E의 uidriver 배정이 바뀐다.
gyeonggi, busan, daejeon = hub_by_name['경기 북부 센터'], hub_by_name['부산광역시 센터'], hub_by_name['대전광역시 센터']
users = request('/users?size=50', token=master)['content']
accounts = [('uiuser', '김서연', 'COMPANY', '한빛 리테일'), ('uidriver', '이준호', 'HUB', hub['name']), ('uiviewer', '박지민', 'COMPANY', '새봄 유통'),
            ('uidrvgg', '최현우', 'HUB', gyeonggi['name']), ('uidrvbs', '정다은', 'HUB', busan['name'])]
for username, name, affiliation, affiliation_name in accounts:
    if not any(u['username'] == username for u in users):
        request('/auth/signup', 'POST', {'username': username, 'password': PASSWORD, 'name': name, 'slackEmail': username + '@example.com', 'affiliationType': affiliation, 'affiliationName': affiliation_name})
users = request('/users?size=50', token=master)['content']
by_username = {u['username']: u for u in users}
for username, driver_hub in [('uidriver', hub), ('uidrvgg', gyeonggi), ('uidrvbs', busan)]:
    driver = by_username[username]
    if not driver['isDeliveryManager'] or driver['hubId'] != driver_hub['hubId']:
        request(f'/users/{driver["userId"]}/role', 'PATCH', {'isDeliveryManager': True, 'hubId': driver_hub['hubId']}, master)
companies = request('/companies?size=50', token=master)['content']
for name, address, manager in [('스파르타 서플라이', '서울특별시 송파구 올림픽로 300, 물류센터 A동', 'uimaster'), ('한빛 리테일', '서울특별시 강남구 테헤란로 152, 입고장 1층', 'uiuser'), ('새봄 유통', '서울특별시 성동구 아차산로 92, 2층', 'uiviewer')]:
    if not any(c['name'] == name for c in companies):
        companies.append(request('/companies', 'POST', {'name': name, 'hubId': hub['hubId'], 'address': address, 'managerId': by_username[manager]['userId']}, master))
# 발제 예시(경기 일산 건조식품 업체 → 부산 수산물 도매 업체, 마른오징어 가공품)와 롤백 시나리오(배송담당자가 없는 대전 허브)용 업체
scenario_companies = [('경기 건조식품 가공', gyeonggi, '경기도 고양시 일산동구 중앙로 1275, 2층'), ('부산 수산 도매', busan, '부산광역시 중구 자갈치해안로 52'),
                      ('대전 사무용품 공급', daejeon, '대전광역시 서구 둔산로 31'), ('대전 오피스 리테일', daejeon, '대전광역시 유성구 대학로 99')]
for name, company_hub, address in scenario_companies:
    if not any(c['name'] == name for c in companies):
        companies.append(request('/companies', 'POST', {'name': name, 'hubId': company_hub['hubId'], 'address': address, 'managerId': by_username['uimaster']['userId']}, master))
supplier = next(c for c in companies if c['name'] == '스파르타 서플라이')
receiver = next(c for c in companies if c['name'] == '한빛 리테일')
by_company = {c['name']: c for c in companies}
products = request('/products?size=100', token=master)['content']
seed_products = [(supplier, '프리미엄 A4 복사용지', 18500, '80g · 2,500매 / 박스'), (supplier, '친환경 포장 박스', 42000, '중형 · 50개입 / 묶음'),
                 (supplier, '스테인리스 텀블러', 28000, '500ml · 실버 / 개'), (supplier, '업무용 무선 키보드', 89000, '저소음 · 블랙 / 개'),
                 (by_company['경기 건조식품 가공'], '마른오징어 가공품', 15000, '200g · 진공 포장 / 팩'),
                 (by_company['대전 사무용품 공급'], '서류 보관함', 24000, 'A4 · 3단 / 개')]
for company, name, price, description in seed_products:
    if not any(p['name'] == name and p['companyId'] == company['companyId'] for p in products):
        products.append(request(f'/companies/{company["companyId"]}/products', 'POST', {'name': name, 'price': price, 'description': description}, master))
scenario = {name: by_company[name]['companyId'] for name, _, _ in scenario_companies}
# 롤백 시나리오는 이 허브에 배송담당자가 없어야 성립한다. E2E는 이 허브에 담당자를 지정하지 않는다.
scenario['rollbackHubId'] = daejeon['hubId']
scenario['products'] = {p['name']: p['productId'] for p in products if p['name'] in ('마른오징어 가공품', '서류 보관함')}
# E2E는 products[0]을 스파르타 서플라이 상품으로 쓴다. 공급업체 상품만 기존 순서대로 남긴다.
products = [p for p in products if p['companyId'] == supplier['companyId']]
manifest_path = ROOT / '.local/ui-test-data.json'
users = request('/users?size=50', token=master)['content']
by_username = {u['username']: u for u in users}
if manifest_path.exists():
    # 기존 주문 상태는 그대로 두고, 나중에 추가한 시나리오 데이터의 식별자만 보탠다.
    manifest = json.loads(manifest_path.read_text())
    manifest['scenario'] = scenario
    manifest['users'] = {**manifest.get('users', {}), **{key: value['userId'] for key, value in by_username.items()}}
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    print('테스트 계정·참조 데이터를 확인했다. 기존 주문 상태를 유지한다.')
    print(f'계정 {len(users)}명, 업체 {len(companies)}개. 시나리오 가이드: http://127.0.0.1:5174/guide')
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
manifest_path.write_text(json.dumps({'hubId': hub['hubId'], 'supplierCompanyId': supplier['companyId'], 'receiverCompanyId': receiver['companyId'], 'products': products, 'orders': orders, 'users': {key: value['userId'] for key, value in by_username.items()}, 'scenario': scenario}, ensure_ascii=False, indent=2))
print(f'실제 API로 준비 완료: 사용자 {len(users)}명, 허브 {len(hubs)}개, 업체 {len(companies)}개, 상품 {len(products)}개, 주문 {len(orders)}건. Slack 비활성화 확인.')
print('시나리오 가이드: http://127.0.0.1:5174/guide')
