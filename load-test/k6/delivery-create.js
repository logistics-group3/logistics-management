// 쓰기 경로 부하: 배송 생성. TARGET으로 대상을 고른다.
//   TARGET=delivery(기본): 부하 테스트 API(POST /api/v1/deliveries/load-test)로 배송 생성(I-09와 같은 처리)만 잰다.
//                          delivery → user I-14 → user I-01(허브가 다르면 hub I-03)과 담당 건수 쿼리만 남는다.
//                          delivery-service를 docker-compose.loadtest.yml과 함께 띄워야 한다.
//   TARGET=order: 주문 생성(O-01)부터 잰다. order → company 조회 → delivery I-09까지 전체 경로다.
// 실행: k6 run load-test/k6/delivery-create.js  (PEAK_RATE=초당 생성 수, 기본 20)
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SEED, login, numberEnv, params, uuid } from './lib/delivery.js';

const PEAK = numberEnv('PEAK_RATE', 20);
const TARGET = __ENV.TARGET || 'delivery';
if (TARGET !== 'delivery' && TARGET !== 'order') {
  throw new Error(`TARGET은 delivery 또는 order다: ${TARGET}`);
}
const NAME = TARGET === 'delivery' ? 'I-09 직접' : 'O-01';

export const options = {
  scenarios: {
    // Note: arrival-rate는 응답이 느려져도 초당 요청 수를 유지한다(열린 모델). 실제 사용자는 서버가 느리다고 주문을 덜 보내지 않는다.
    // VU 수로 부하를 주면(닫힌 모델) 서버가 느려질수록 요청도 줄어 병목이 가려진다. VU가 모자라면 dropped_iterations로 드러난다.
    create: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: numberEnv('MAX_VUS', 300),
      stages: [
        { duration: '30s', target: Math.ceil(PEAK / 2) },
        { duration: '1m', target: Math.ceil(PEAK / 2) },
        { duration: '30s', target: PEAK },
        { duration: '1m', target: PEAK },
        { duration: '20s', target: 0 },
      ],
    },
  },
  // 목표값이다. 넘으면 k6가 실패 코드로 끝나고 요약에 표시한다. 기준선을 잰 뒤 조정한다.
  thresholds: {
    [`http_req_failed{name:${NAME}}`]: ['rate<0.01'],
    [`http_req_duration{name:${NAME}}`]: ['p(95)<1000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return { token: login(TARGET === 'delivery' ? 'dmaster' : 'dreceiver') };
}

export default function (data) {
  const res = TARGET === 'delivery' ? createDelivery(data.token) : createOrder(data.token);
  check(res, {
    [`${NAME} 201`]: (r) => r.status === 201,
  });
}

/**
 * I-09와 같은 요청. 시드 업체 두 곳(같은 서울 허브) 사이 배송이다. orderId는 매번 새로 만든다(같으면 멱등 처리로 200).
 * 생성 뒤 order 대신 주문 확정(I-19과 같은 처리)을 보낸다. 배정은 확정 뒤에 한다(결정 로그 29).
 */
function createDelivery(token) {
  const orderId = uuid();
  const body = JSON.stringify({
    orderId,
    supplierCompanyId: SEED.supplierCompany,
    receiverCompanyId: SEED.receiverCompany,
    departureHubId: SEED.seoulHub,
    destinationHubId: SEED.seoulHub,
    pickupAddress: '서울특별시 송파구 올림픽로 300',
    deliveryAddress: '서울특별시 강남구 테헤란로 152',
    recipientName: 'k6 수령인',
    receiverManagerId: SEED.receiverManager,
  });
  const res = http.post(`${BASE_URL}/api/v1/deliveries/load-test`, body, params(token, NAME));
  if (res.status === 201) {
    http.post(`${BASE_URL}/api/v1/deliveries/load-test/orders/${orderId}/confirm`, null, params(token, 'I-19 직접'));
  }
  return res;
}

function createOrder(token) {
  const body = JSON.stringify({
    supplierCompanyId: SEED.supplierCompany,
    receiverCompanyId: SEED.receiverCompany,
    productId: SEED.product,
    quantity: 1,
    requestNote: 'k6',
  });
  // 멱등키는 요청마다 새로 만든다(같은 키는 같은 주문의 재시도로 처리된다)
  const key = `k6-${__VU}-${__ITER}-${Date.now()}`;
  return http.post(`${BASE_URL}/api/v1/orders`, body, params(token, NAME, { 'Idempotency-Key': key }));
}
