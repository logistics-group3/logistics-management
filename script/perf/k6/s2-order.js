// S2 주문 생성: 측정 항목 B(허브 경로 I-03), C(업체·상품 조회)의 기준선. 진입점: script/perf.sh run s2
// 수령업체 관리자 본인이 무작위 공급업체 상품을 1개 주문한다. 요청 하나 안에서 다음 호출이 모두 일어난다.
//   Gateway I-01 → order → company(공급업체, 수령업체, 상품) → delivery 생성 → hub I-03(다른 허브일 때), user I-14(허브마다), user I-01
// 구간별 횟수·지연은 report.js가 Prometheus의 feign_client_requests에서 나눠 모은다.
// 주문·배송 데이터가 쌓이므로 회차마다 script/perf.sh clean으로 비운 뒤 실행한다.
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { BASE, arrivalOptions, headers, loginAll, loadData, pick } from './lib.js';

const data = loadData();
export const options = arrivalOptions('order');

export function setup() {
  const tokens = loginAll(data.receivers, data.password);
  // 멱등키 앞부분. setup은 한 번만 실행되므로 모든 VU가 같은 값을 받는다.
  const run = `${__ENV.LABEL || 'run'}-${Date.now()}`.replace(/[^A-Za-z0-9._:-]/g, '-').slice(-80);
  return { run, receivers: data.receivers.map((r) => ({ ...r, token: tokens[r.username] })) };
}

export function order(ctx) {
  const receiver = pick(ctx.receivers);
  const product = pick(data.products);
  // Note: 새 주문마다 새 멱등키를 쓴다. __ITER는 시나리오(워밍업·본 측정)마다 0부터 다시 세므로 시나리오 이름과
  // 시나리오 전체 반복 번호로 만든다. 같은 키에 다른 본문을 보내면 409 ORDER_IDEMPOTENCY_KEY_CONFLICT가 된다.
  const key = `${ctx.run}-${exec.scenario.name}-${exec.scenario.iterationInTest}`;
  const body = JSON.stringify({ supplierCompanyId: product.companyId, receiverCompanyId: receiver.companyId,
    productId: product.productId, quantity: 1, requestNote: null });
  const res = http.post(`${BASE}/orders`, body, { ...headers(receiver.token, { 'Idempotency-Key': key }),
    tags: { name: 'POST /orders', sameHub: String(product.hubId === receiver.hubId) } });
  check(res, { 'O-01 201': (r) => r.status === 201 });
}

export default function () {}
