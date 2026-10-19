// S3 읽기: 측정 항목 B(허브·경로)의 보조 기준선. 진입점: script/perf.sh run s3
// 허브 단건(H-02)과 경로 단건(H-04)을 같은 비율로 무작위 호출한다. B가 캐시하는 대상이 단건 조회(I-02·I-03·H-02·H-04)라서
// 목록 검색(H-01·H-03, 페이지 응답)은 넣지 않는다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE, arrivalOptions, headers, loginAll, loadData, pick } from './lib.js';

const data = loadData();
export const options = arrivalOptions('read');

export function setup() {
  return { tokens: Object.values(loginAll(data.authUsers.slice(0, 50), data.password)) };
}

export function read(ctx) {
  const token = pick(ctx.tokens);
  if (Math.random() < 0.5) {
    const res = http.get(`${BASE}/hubs/${pick(data.hubs).hubId}`, { ...headers(token), tags: { name: 'GET /hubs/{id}' } });
    check(res, { 'H-02 200': (r) => r.status === 200 });
  } else {
    const res = http.get(`${BASE}/hub-routes/${pick(data.routes)}`, { ...headers(token), tags: { name: 'GET /hub-routes/{id}' } });
    check(res, { 'H-04 200': (r) => r.status === 200 });
  }
}

export default function () {}
