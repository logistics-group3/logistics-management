// 읽기 경로 부하: 배송 목록(D-01)과 단건(D-02). 대량 데이터(infra/postgres/seed/delivery-load.sql)를 넣은 뒤 돌린다.
// 세 시나리오가 동시에 돈다. API별 지표는 name 태그(D-01 허브별, D-01 담당자, D-02)로 나뉜다.
// 실행: k6 run load-test/k6/delivery-read.js  (RATE=시나리오별 초당 요청 수, 기본 20)
import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE_URL, SEED, login, numberEnv, params } from './lib/delivery.js';

const RATE = numberEnv('RATE', 20);
const DURATION = __ENV.DURATION || '2m';

function constantRate(exec) {
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate: RATE,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: 20,
    maxVUs: numberEnv('MAX_VUS', 200),
  };
}

export const options = {
  scenarios: {
    // MASTER가 허브별 최신순으로 본다: departure_hub_id로 거른 뒤 created_at 정렬
    list_by_hub: constantRate('listByHub'),
    // 배송담당자가 자기 배송을 최신순으로 본다: 구간 EXISTS로 거른 뒤 정렬과 count
    list_by_manager: constantRate('listByManager'),
    // 단건: 배송 1건 + 구간 쿼리 두 번
    detail: constantRate('detail'),
  },
  thresholds: {
    'http_req_failed{name:D-01 허브별}': ['rate<0.01'],
    'http_req_failed{name:D-01 담당자}': ['rate<0.01'],
    'http_req_failed{name:D-02}': ['rate<0.01'],
    'http_req_duration{name:D-01 허브별}': ['p(95)<500'],
    'http_req_duration{name:D-01 담당자}': ['p(95)<500'],
    'http_req_duration{name:D-02}': ['p(95)<300'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const master = login('dmaster');
  const manager = login('dmgr1');
  // 단건 조회 대상: 서울 허브 배송 50건
  const res = http.get(`${BASE_URL}/api/v1/deliveries?departureHubId=${SEED.seoulHub}&size=50`, params(master, 'setup'));
  const ids = res.status === 200 ? res.json('data.content').map((d) => d.deliveryId) : [];
  if (ids.length === 0) {
    fail(`단건 조회 대상이 없다(${res.status}). 대량 데이터를 넣었는지 확인한다: delivery-service/docs/load-test.md`);
  }
  return { master, manager, ids };
}

export function listByHub(data) {
  const res = http.get(`${BASE_URL}/api/v1/deliveries?departureHubId=${SEED.seoulHub}&sort=createdAt,desc&size=10`,
    params(data.master, 'D-01 허브별'));
  check(res, { 'D-01 허브별 200': (r) => r.status === 200 });
}

export function listByManager(data) {
  const res = http.get(`${BASE_URL}/api/v1/deliveries?sort=createdAt,desc&size=10`, params(data.manager, 'D-01 담당자'));
  check(res, { 'D-01 담당자 200': (r) => r.status === 200 });
}

export function detail(data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const res = http.get(`${BASE_URL}/api/v1/deliveries/${id}`, params(data.master, 'D-02'));
  check(res, { 'D-02 200': (r) => r.status === 200 });
}
