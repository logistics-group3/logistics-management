// 주문 생성(O-01) 전체 경로 부하. 비동기 접수(ADR 0010) 이후라 접수(202)와 완료(O-07이 COMPLETED)를 따로 잰다.
//   접수 TPS  = 202를 받은 초당 건수(http_reqs{name:O-01 접수}, accepted 카운터)
//   완료 TPS  = O-07 폴링으로 COMPLETED를 확인한 초당 건수(completed 카운터)
//   완료 지연 = 접수 시각부터 COMPLETED를 확인할 때까지(order_complete_ms)
// 실행: RATE=초당 접수 건수 DURATION=1m k6 run load-test/k6/order-e2e.js  (load-test/order-steps.sh가 단계별로 돌린다)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, SEED, login, numberEnv, params } from './lib/delivery.js';

const RATE = numberEnv('RATE', 10);
const DURATION = __ENV.DURATION || '1m';
const POLL_INTERVAL = numberEnv('POLL_INTERVAL', 0.5);
const POLL_TIMEOUT = numberEnv('POLL_TIMEOUT', 60);

const accepted = new Counter('orders_accepted');
const completed = new Counter('orders_completed');
const failed = new Counter('orders_failed');
const rejected = new Counter('orders_rejected'); // 접수 단계의 503·4xx(실행기 포화 등)
const timedOut = new Counter('orders_poll_timeout');
const completeMs = new Trend('order_complete_ms', true);

export const options = {
  scenarios: {
    // Note: 열린 모델(arrival-rate). 서버가 느려져도 요청은 줄지 않아 한계 지점이 드러난다. VU가 모자라면 dropped_iterations로 보인다.
    order: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(Math.max(50, RATE * 2), numberEnv('MAX_VUS', 2000)),
      maxVUs: numberEnv('MAX_VUS', 2000),
    },
  },
  // Note: k6는 임계값이 있는 태그만 요약에 하위 지표로 내보낸다. 항상 통과하는 조건으로 접수·폴링 지표를 분리한다.
  thresholds: {
    'http_req_duration{name:O-01 접수}': ['max>=0'],
    'http_req_duration{name:O-07 진행 조회}': ['max>=0'],
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
    requestNote: 'k6',
  });
  const key = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/orders`, body, params(data.token, 'O-01 접수', { 'Idempotency-Key': key }));
  if (!check(res, { 'O-01 접수 202': (r) => r.status === 202 })) {
    rejected.add(1);
    return;
  }
  accepted.add(1);
  const orderId = res.json('data.orderId');

  while ((Date.now() - start) / 1000 < POLL_TIMEOUT) {
    sleep(POLL_INTERVAL);
    const progress = http.get(`${BASE_URL}/api/v1/orders/creations/${orderId}`, params(data.token, 'O-07 진행 조회'));
    const status = progress.status === 200 ? progress.json('data.status') : null;
    if (status === 'COMPLETED') {
      completed.add(1);
      completeMs.add(Date.now() - start);
      return;
    }
    if (status === 'ABORTED' || status === 'RECOVERY_REQUIRED') {
      failed.add(1);
      return;
    }
  }
  timedOut.add(1);
}
