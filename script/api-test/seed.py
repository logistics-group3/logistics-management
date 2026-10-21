#!/usr/bin/env python3
"""Swagger 수동 테스트용 기준 데이터를 공개 API로 준비한다(로컬 전용). 진입점은 script/api-test.sh다.

「기능 테스트」 시나리오의 테스트 데이터(S1·S2 정상 경로)를 만든다. 여러 번 실행해도 있는 데이터는 재사용하고
삭제된 업체·상품만 다시 만든다. 계정 접두어는 API_TEST_PREFIX(기본 jh)로 바꿀 수 있다.
사용: script/api-test.sh seed     데이터 준비 후 .local/api-test/data.json 저장
     script/api-test.sh tokens   data.json의 모든 계정 토큰 재발급
     python3 script/api-test/seed.py --probe   Gateway 경유 준비 확인(up·restore가 사용)
실행 결과(data.json, tokens.txt)는 Git에서 제외된 .local/api-test/에 둔다. 계정은 로컬 테스트 전용이다.
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIR = ROOT / '.local/api-test'
DIR.mkdir(parents=True, exist_ok=True)
DATA = DIR / 'data.json'
PREFIX = os.environ.get('API_TEST_PREFIX', 'jh')
PASSWORD = 'ApiTest!2026'  # 로컬 테스트 계정 공통 비밀번호. 규칙: 8~15자, 대·소문자·숫자·특수문자

binding = subprocess.check_output(['docker', 'compose', 'port', 'gateway-service', '8080'], cwd=ROOT, text=True).strip()
GATEWAY = f'http://localhost:{binding.rsplit(":", 1)[1]}'
BASE = GATEWAY + '/api/v1'

HUB_NAMES = {'HUB_A': '서울특별시 센터', 'HUB_B': '경기 남부 센터', 'HUB_C': '부산광역시 센터'}
P = PREFIX.upper()
ACCOUNTS = [  # key, username 접미어, 이름, 소속 유형, 소속명
    ('MASTER', 'master', '테스트 관리자', 'HUB', HUB_NAMES['HUB_A']),
    ('SUP_USER', 'sup01', '공급업체 담당', 'COMPANY', f'{P} 테스트 공급'),
    ('RCV_USER', 'rcv01', '요청업체 담당', 'COMPANY', f'{P} 테스트 수령'),
    ('OTHER_USER', 'oth01', '무소속 사용자', 'COMPANY', '소속 없음'),
    ('DRV_A1', 'drva1', '서울 배송담당1', 'HUB', HUB_NAMES['HUB_A']),
    ('DRV_A2', 'drva2', '서울 배송담당2', 'HUB', HUB_NAMES['HUB_A']),
    ('DRV_B1', 'drvb1', '경기남부 배송담당', 'HUB', HUB_NAMES['HUB_B']),
]
DRIVER_HUBS = {'DRV_A1': 'HUB_A', 'DRV_A2': 'HUB_A', 'DRV_B1': 'HUB_B'}


def call(path, method='GET', body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                 headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.load(error)
        except ValueError:
            return error.code, {}


def ok(label, path, method='GET', body=None, token=None, expect=(200, 201)):
    status, payload = call(path, method, body, token)
    if status not in expect:
        raise SystemExit(f'실패: {label} {method} {path} → {status} {payload.get("code", "")} {payload.get("message", "")}')
    print(f'  {label} → {status}')
    return payload.get('data')


def login(username):
    status, payload = call('/auth/login', 'POST', {'username': username, 'password': PASSWORD})
    if status != 200:
        raise SystemExit(f'로그인 실패: {username} → {status} {payload.get("code", "")}. '
                         f'다른 비밀번호로 이미 있는 계정이면 API_TEST_PREFIX를 바꿔 실행한다.')
    return payload['data']['accessToken']


def token_role(token):
    # Note: 로그인 응답 JWT의 payload만 읽어 role을 확인한다. Gateway를 거치지 않으므로 권한 재확인 캐시에 USER가 남지 않는다.
    part = token.split('.')[1]
    return json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))['role']


def all_pages(path, token):
    items, page = [], 0
    while True:
        data = ok(f'목록 {path} page={page}', f'{path}{"&" if "?" in path else "?"}page={page}&size=50', token=token)
        items += data['content']
        if len(data['content']) < 50:
            return items
        page += 1


def print_tokens(data):
    lines = []
    for key, user in data['users'].items():
        token = login(user['username'])
        lines.append(f'{key:<11} {user["username"]:<10} {token}')
    (DIR / 'tokens.txt').write_text('\n'.join(lines) + '\n')
    print('토큰(60분 유효). Swagger Authorize에는 토큰만 넣는다(Bearer 없이).')
    print('\n'.join(lines))
    print(f'파일: {DIR / "tokens.txt"}')


def seed():
    print(f'대상 Gateway: {GATEWAY}, 접두어: {PREFIX}')
    for attempt in range(30):
        try:
            if call('/auth/login', 'POST', {'username': 'nobody0', 'password': 'Wrong!2026'})[0] == 401:
                break
        except OSError:
            pass
        if attempt == 29:
            raise SystemExit('Gateway·user-service 응답을 확인하지 못했다. script/api-test.sh status로 상태를 본다.')
        time.sleep(2)

    print('[S1] 계정 가입')
    users = {}
    for key, suffix, name, affiliation_type, affiliation_name in ACCOUNTS:
        username = PREFIX + suffix
        status, payload = call('/auth/signup', 'POST', {
            'username': username, 'password': PASSWORD, 'name': name, 'slackEmail': f'{username}@example.com',
            'affiliationType': affiliation_type, 'affiliationName': affiliation_name})
        if status == 201:
            print(f'  U-01 가입 {username} → 201')
        elif status == 409:
            print(f'  U-01 가입 {username} → 409 이미 있음, 재사용')
        else:
            raise SystemExit(f'실패: U-01 가입 {username} → {status} {payload.get("code", "")} {payload.get("message", "")}')
        users[key] = {'username': username}

    master_name = users['MASTER']['username']
    master = login(master_name)
    if token_role(master) != 'MASTER':
        subprocess.run([str(ROOT / 'script/promote-master.sh'), master_name], cwd=ROOT, check=True)
        master = login(master_name)
    print(f'  MASTER 토큰 role={token_role(master)}')

    by_username = {u['username']: u for u in all_pages('/users', master)}
    for key, user in users.items():
        user['userId'] = by_username[user['username']]['userId']

    print('[S2] 허브 선택과 배송담당자 지정')
    hubs_by_name = {h['name']: h for h in all_pages('/hubs', master)}
    hubs = {key: {'hubId': hubs_by_name[name]['hubId'], 'name': name} for key, name in HUB_NAMES.items()}
    ours = {u['userId'] for u in users.values()}
    for key, hub in hubs.items():
        others = [u['username'] for u in by_username.values()
                  if u.get('isDeliveryManager') and u.get('hubId') == hub['hubId'] and u['userId'] not in ours]
        if others:
            print(f'  주의: {key}({hub["name"]})에 다른 배송담당자 {others}가 있어 자동 배정이 그쪽으로 갈 수 있다')
    for key, hub_key in DRIVER_HUBS.items():
        current = by_username[users[key]['username']]
        if current.get('isDeliveryManager') and current.get('hubId') == hubs[hub_key]['hubId']:
            print(f'  U-07 {users[key]["username"]} → 이미 {hubs[hub_key]["name"]} 배송담당자')
        else:
            ok(f'U-07 {users[key]["username"]} → {hubs[hub_key]["name"]}', f'/users/{users[key]["userId"]}/role', 'PATCH',
               {'isDeliveryManager': True, 'hubId': hubs[hub_key]['hubId']}, master)

    print('[S2] 업체·상품 준비')
    companies = {c['name']: c for c in all_pages('/companies', master)}
    created = {}
    for key, owner, name, address in [('SUP_CO', 'SUP_USER', f'{P} 테스트 공급', '서울특별시 송파구 테스트로 1'),
                                      ('RCV_CO', 'RCV_USER', f'{P} 테스트 수령', '서울특별시 강남구 테스트로 2')]:
        company = companies.get(name)
        if company is None:
            company = ok(f'C-01 {name} ({users[owner]["username"]})', '/companies', 'POST',
                         {'name': name, 'hubId': hubs['HUB_A']['hubId'], 'address': address}, login(users[owner]['username']))
        else:
            print(f'  C-01 {name} → 이미 있음, 재사용')
        if company.get('managerId') != users[owner]['userId']:
            raise SystemExit(f'{name}의 관리자가 {users[owner]["username"]}가 아니다. 데이터를 확인한다.')
        created[key] = {'companyId': company['companyId'], 'name': name, 'hubId': company['hubId'], 'managerId': company['managerId']}

    product_name = f'{P} 테스트 상품'
    product = next((p for p in all_pages('/products', master)
                    if p['name'] == product_name and p['companyId'] == created['SUP_CO']['companyId']), None)
    if product is None:
        product = ok(f'P-01 {product_name}', f'/companies/{created["SUP_CO"]["companyId"]}/products', 'POST',
                     {'name': product_name, 'price': 12000, 'description': 'Swagger 테스트용'}, login(users['SUP_USER']['username']))
    else:
        print(f'  P-01 {product_name} → 이미 있음, 재사용')

    data = {
        'gateway': GATEWAY, 'swagger': GATEWAY + '/swagger-ui.html', 'prefix': PREFIX, 'password': PASSWORD,
        'users': users, 'hubs': hubs, 'companies': created,
        'products': {'PRODUCT': {'productId': product['productId'], 'name': product_name, 'price': product['price']}},
    }
    DATA.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print(f'\n준비 완료: {DATA}')
    print(f'공통 비밀번호: {PASSWORD}')
    for group in ('users', 'hubs', 'companies', 'products'):
        for key, value in data[group].items():
            ident = value.get('userId') or value.get('companyId') or value.get('productId') or value.get('hubId')
            print(f'  {key:<11} {value.get("username") or value.get("name"):<16} {ident}')


def probe():
    """재생성 뒤 준비 확인. data.json이 있으면 MASTER로 5개 서비스 조회가 모두 200인지, 없으면 user-service 로그인 응답만 본다."""
    try:
        if not DATA.exists():
            ready = call('/auth/login', 'POST', {'username': 'nobody0', 'password': 'Wrong!2026'})[0] == 401
            print('user-service 응답 확인(시드 전이라 나머지는 seed에서 확인)' if ready else '아직 준비 중')
            return ready
        status, payload = call('/auth/login', 'POST', {'username': json.loads(DATA.read_text())['users']['MASTER']['username'], 'password': PASSWORD})
        if status != 200:
            print(f'아직 준비 중: 로그인 {status}')
            return False
        token = payload['data']['accessToken']
        codes = {name: call(f'/{name}?page=0&size=1', token=token)[0] for name in ('users', 'hubs', 'companies', 'orders', 'deliveries')}
        ready = all(code == 200 for code in codes.values())
        print(('준비 완료: ' if ready else '아직 준비 중: ') + ', '.join(f'{k} {v}' for k, v in codes.items()))
        return ready
    except OSError as error:
        print(f'아직 준비 중: {error}')
        return False


if __name__ == '__main__':
    if '--probe' in sys.argv:
        sys.exit(0 if probe() else 1)
    if '--tokens' in sys.argv:
        if not DATA.exists():
            raise SystemExit('data.json이 없다. 먼저 seed를 실행한다.')
        print_tokens(json.loads(DATA.read_text()))
    else:
        seed()
