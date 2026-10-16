// 장애 격리 실험(delivery-service/docs/fault-isolation.md). 생성과 조회를 동시에 보내고, 요청마다 장애 전후 구간(phase) 태그를 붙인다.
// 장애 자체는 load-test/fault-isolation.sh가 FAULT_AT(epoch 초)에 넣는다. 두 쪽이 같은 시각을 기준으로 구간을 나눈다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SEED, login, numberEnv, params, uuid } from './lib/delivery.js';

const TARGET = __ENV.TARGET || 'delivery';
const CREATE_RATE = numberEnv('CREATE_RATE', TARGET === 'order' ? 5 : 20);
const READ_RATE = numberEnv('READ_RATE', 20);
const DURATION = __ENV.DURATION || '4m';
const FAULT_AT = numberEnv('FAULT_AT', 0) * 1000;
const FAULT_MS = numberEnv('FAULT_SEC', 30) * 1000;
// 사용자가 기다리는 한계. 넘으면 실패로 센다. VU가 오래 묶이지 않게 하는 역할도 한다.
const CLIENT_TIMEOUT = __ENV.CLIENT_TIMEOUT || '10s';
const CREATE_NAME = TARGET === 'delivery' ? 'I-09 직접' : 'O-01';
const READ_NAME = 'D-01 허브별';
const PHASES = ['nofault', 'before', 'fault', 'recovery', 'after'];

function phase() {
  if (!FAULT_AT) return 'nofault';
  const t = Date.now();
  if (t < FAULT_AT - 60000) return 'warmup';
  if (t < FAULT_AT) return 'before';
  if (t < FAULT_AT + FAULT_MS) return 'fault';
  if (t < FAULT_AT + FAULT_MS + 30000) return 'recovery';
  return 'after';
}

// Note: k6 요약은 임계값이 걸린 하위 지표만 따로 보여 준다. 판정용이 아니라 구간별 줄을 요약에 내기 위한 항상 참인 임계값이다.
const thresholds = {};
for (const name of [CREATE_NAME, READ_NAME]) {
  for (const p of PHASES) {
    thresholds[`http_req_duration{name:${name},phase:${p}}`] = ['max>=0'];
    thresholds[`http_req_failed{name:${name},phase:${p}}`] = ['rate>=0'];
  }
}

function constantRate(exec, rate) {
  return {
    executor: 'constant-arrival-rate', exec, rate, timeUnit: '1s', duration: DURATION,
    preAllocatedVUs: 50, maxVUs: numberEnv('MAX_VUS', 400),
  };
}

export const options = {
  scenarios: {
    create: constantRate('create', CREATE_RATE),
    read: constantRate('read', READ_RATE),
  },
  thresholds,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return {
    creator: login(TARGET === 'delivery' ? 'dmaster' : 'dreceiver'),
    reader: login('dmaster'),
  };
}

function tagged(token, name, headers) {
  const p = params(token, name, headers);
  p.tags.phase = phase();
  p.timeout = CLIENT_TIMEOUT;
  return p;
}

export function create(data) {
  let res;
  if (TARGET === 'delivery') {
    res = http.post(`${BASE_URL}/api/v1/deliveries/load-test`, JSON.stringify({
      orderId: uuid(),
      supplierCompanyId: SEED.supplierCompany,
      receiverCompanyId: SEED.receiverCompany,
      departureHubId: SEED.seoulHub,
      destinationHubId: SEED.seoulHub,
      pickupAddress: '서울특별시 송파구 올림픽로 300',
      deliveryAddress: '서울특별시 강남구 테헤란로 152',
      recipientName: 'k6 수령인',
      receiverManagerId: SEED.receiverManager,
    }), tagged(data.creator, CREATE_NAME));
  } else {
    const key = `k6-fault-${__VU}-${__ITER}-${Date.now()}`;
    res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({
      supplierCompanyId: SEED.supplierCompany,
      receiverCompanyId: SEED.receiverCompany,
      productId: SEED.product,
      quantity: 1,
      requestNote: 'k6 fault',
    }), tagged(data.creator, CREATE_NAME, { 'Idempotency-Key': key }));
  }
  check(res, { [`${CREATE_NAME} 201`]: (r) => r.status === 201 });
}

export function read(data) {
  const res = http.get(`${BASE_URL}/api/v1/deliveries?departureHubId=${SEED.seoulHub}&sort=createdAt,desc&size=10`,
    tagged(data.reader, READ_NAME));
  check(res, { [`${READ_NAME} 200`]: (r) => r.status === 200 });
}
