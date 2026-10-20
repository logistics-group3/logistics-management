// 측정용 시드 데이터를 공개 API로 만든다. 진입점: script/perf.sh seed
// 여러 번 실행해도 이미 있는 사용자·업체·상품은 재사용한다. 결과는 /out/data.json(.local/perf/data.json)에 쓴다.
//
// 만드는 데이터(허브 17개는 hub-service Flyway 시드를 그대로 쓴다)
//   배송담당자   허브마다 2명(pd{허브번호}{1,2})  - 배송 생성 시 구간 담당 허브에 담당자가 있어야 한다
//   공급업체     허브마다 1개 + 관리자(ps{허브번호}) + 상품 3개
//   수령업체     PERF_USERS개 + 관리자(pr{번호}), 허브에 고르게 나눈다 - 주문은 수령업체 관리자 본인이 한다
//   권한 변경 측정용 사용자 3명(pp{번호}) - 부하 대상과 섞지 않는다
import http from 'k6/http';
import { fail } from 'k6';
import { BASE, allPages, headers, login } from './lib.js';

export const PASSWORD = 'PerfUser!2026';
const MASTER_USERNAME = __ENV.MASTER_USERNAME;
const MASTER_PASSWORD = __ENV.MASTER_PASSWORD;
const RECEIVERS = Number(__ENV.PERF_USERS || 300);
const PROBES = 3;
const CHUNK = 10;

export const options = { setupTimeout: '20m', iterations: 1, vus: 1 };

const pad = (n, width) => String(n).padStart(width, '0');

function batchCreate(label, requests, accept) {
  for (let i = 0; i < requests.length; i += CHUNK) {
    const part = requests.slice(i, i + CHUNK);
    http.batch(part.map((r) => r.req)).forEach((res, j) => {
      if (!accept.includes(res.status)) fail(`${label} 실패: ${part[j].key} → ${res.status} ${res.body}`);
    });
  }
  console.log(`${label}: ${requests.length}건 요청`);
}

function signupRequests(accounts) {
  return accounts.map((a) => ({ key: a.username, req: ['POST', `${BASE}/auth/signup`, JSON.stringify({
    username: a.username, password: PASSWORD, name: a.name, slackEmail: `${a.username}@perf.example.com`,
    affiliationType: a.affiliationType, affiliationName: a.affiliationName,
  }), { headers: { 'Content-Type': 'application/json' } }] }));
}

export function setup() {
  const master = login(MASTER_USERNAME, MASTER_PASSWORD);
  const hubs = allPages('/hubs', master).sort((a, b) => a.name.localeCompare(b.name));
  if (hubs.length === 0) fail('허브가 없다. hub-service Flyway 시드를 확인한다.');
  console.log(`허브 ${hubs.length}개`);

  const drivers = [];
  const suppliers = [];
  hubs.forEach((hub, h) => {
    for (let n = 1; n <= 2; n++) {
      drivers.push({ username: `pd${pad(h, 2)}${n}`, name: `측정 배송담당 ${h}-${n}`, affiliationType: 'HUB',
        affiliationName: hub.name, hub });
    }
    suppliers.push({ username: `ps${pad(h, 2)}`, name: `측정 공급 관리자 ${h}`, affiliationType: 'COMPANY',
      affiliationName: `perf-sup-${pad(h, 2)}`, hub, company: `perf-sup-${pad(h, 2)}` });
  });
  const receivers = [];
  for (let i = 0; i < RECEIVERS; i++) {
    const hub = hubs[i % hubs.length];
    receivers.push({ username: `pr${pad(i, 4)}`, name: `측정 수령 관리자 ${i}`, affiliationType: 'COMPANY',
      affiliationName: `perf-rcv-${pad(i, 4)}`, hub, company: `perf-rcv-${pad(i, 4)}` });
  }
  const probes = [];
  for (let i = 0; i < PROBES; i++) {
    probes.push({ username: `pp${pad(i, 2)}`, name: `측정 권한변경 ${i}`, affiliationType: 'COMPANY', affiliationName: '측정' });
  }

  // 1. 가입. 이미 있으면 409라 재사용한다.
  batchCreate('U-01 가입', signupRequests([...drivers, ...suppliers, ...receivers, ...probes]), [201, 409]);

  const users = {};
  allPages('/users?username=p', master).forEach((u) => { users[u.username] = u; });
  const idOf = (username) => {
    const user = users[username];
    if (!user) fail(`가입한 사용자를 찾지 못했다: ${username}`);
    return user.userId;
  };

  // 2. 배송담당자 지정(U-07). 이미 같은 허브 담당자면 건너뛴다.
  const assign = drivers.filter((d) => !(users[d.username].isDeliveryManager && users[d.username].hubId === d.hub.hubId))
    .map((d) => ({ key: d.username, req: ['PATCH', `${BASE}/users/${idOf(d.username)}/role`,
      JSON.stringify({ isDeliveryManager: true, hubId: d.hub.hubId }), headers(master)] }));
  batchCreate('U-07 배송담당자 지정', assign, [200]);

  // 3. 업체(C-01). MASTER가 managerId를 지정해 만든다.
  const companies = {};
  allPages('/companies?name=perf-', master).forEach((c) => { companies[c.name] = c; });
  const missingCompanies = [...suppliers, ...receivers].filter((a) => !companies[a.company])
    .map((a) => ({ key: a.company, req: ['POST', `${BASE}/companies`, JSON.stringify({
      name: a.company, hubId: a.hub.hubId, address: `${a.hub.name} 측정로 1`, managerId: idOf(a.username),
    }), headers(master)] }));
  batchCreate('C-01 업체 생성', missingCompanies, [201]);
  allPages('/companies?name=perf-', master).forEach((c) => { companies[c.name] = c; });

  // 4. 상품(P-01). 공급업체마다 3개.
  const products = {};
  allPages('/products?name=perf-', master).forEach((p) => { products[p.name] = p; });
  const missingProducts = [];
  suppliers.forEach((s) => {
    for (let k = 1; k <= 3; k++) {
      const name = `perf-${s.company.slice(5)}-p${k}`;
      if (!products[name]) {
        missingProducts.push({ key: name, req: ['POST', `${BASE}/companies/${companies[s.company].companyId}/products`,
          JSON.stringify({ name, price: 1000 * k, description: '성능 측정용' }), headers(master)] });
      }
    }
  });
  batchCreate('P-01 상품 생성', missingProducts, [201]);
  allPages('/products?name=perf-', master).forEach((p) => { products[p.name] = p; });

  return {
    password: PASSWORD,
    master: { username: MASTER_USERNAME, password: MASTER_PASSWORD },
    hubs: hubs.map((h) => ({ hubId: h.hubId, name: h.name })),
    routes: allPages('/hub-routes', master).map((r) => r.hubRouteId),
    receivers: receivers.map((r) => ({ username: r.username, companyId: companies[r.company].companyId, hubId: r.hub.hubId })),
    products: Object.values(products).map((p) => ({ productId: p.productId, companyId: p.companyId, hubId: p.hubId })),
    // S1 부하에 쓰는 사용자. 수령·공급 관리자와 배송담당자를 모두 넣어 캐시 키를 넓게 분산한다.
    authUsers: [...receivers, ...suppliers, ...drivers].map((a) => ({ username: a.username })),
    probes: probes.map((p) => ({ username: p.username, userId: idOf(p.username) })),
  };
}

export default function () {}

export function handleSummary(data) {
  const seed = data.setup_data;
  const text = `시드 완료: 허브 ${seed.hubs.length}, 경로 ${seed.routes.length}, 수령업체 ${seed.receivers.length}, 상품 ${seed.products.length}, `
    + `S1 사용자 ${seed.authUsers.length}, 권한 변경 측정 사용자 ${seed.probes.length}\n`;
  return { stdout: text, '/out/data.json': JSON.stringify(seed, null, 2) };
}
