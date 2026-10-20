// 주문 → DM 끝단 부하. O-01 주문 접수를 일정한 초당 건수로 보낸다.
// k6가 재는 것은 접수(202)까지다. 접수 뒤 배송 생성·확정·배정·DM은 비동기라 load-test/order-dm-stats.sh가 DB와 Slack 대역 기록으로 잰다.
// 실행: k6 run -e RATE=20 -e DURATION=1m load-test/k6/order-dm.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SEED, login, numberEnv, params, uuid } from './lib/delivery.js';

const RATE = numberEnv('RATE', 10);

export const options = {
  scenarios: {
    // Note: 열린 모델(arrival-rate). 서버가 느려져도 초당 주문 수를 유지해야 끝단 처리량이 들어온 양을 따라가는지 보인다.
    orders: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: Math.min(RATE * 2, 200),
      maxVUs: numberEnv('MAX_VUS', 400),
    },
  },
  thresholds: {
    'http_req_failed{name:O-01}': ['rate<0.01'],
    'http_req_duration{name:O-01}': ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return { token: login('dreceiver') };
}

export default function (data) {
  const body = JSON.stringify({
    supplierCompanyId: SEED.supplierCompany,
    receiverCompanyId: SEED.receiverCompany,
    productId: SEED.product,
    quantity: 1,
    requestNote: 'k6 order-dm',
  });
  // 멱등키는 요청마다 새로 만든다(같은 키는 같은 주문의 재시도로 처리된다)
  const res = http.post(`${BASE_URL}/api/v1/orders`, body, params(data.token, 'O-01', { 'Idempotency-Key': `dm-${uuid()}` }));
  check(res, { 'O-01 202': (r) => r.status === 202 });
}
