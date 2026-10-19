// S3 읽기: 측정 항목 B(허브·경로)의 보조 기준선. 진입점: script/perf.sh run s3
// 프런트가 자주 부르는 허브 목록(H-01)과 허브 간 경로 검색(H-03)을 같은 비율로 섞어 호출한다.
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
    const res = http.get(`${BASE}/hubs?page=0&size=30`, { ...headers(token), tags: { name: 'GET /hubs' } });
    check(res, { 'H-01 200': (r) => r.status === 200 });
  } else {
    const from = pick(data.hubs);
    let to = pick(data.hubs);
    while (to.hubId === from.hubId) to = pick(data.hubs);
    const res = http.get(`${BASE}/hub-routes?departureHubId=${from.hubId}&arrivalHubId=${to.hubId}`,
      { ...headers(token), tags: { name: 'GET /hub-routes' } });
    check(res, { 'H-03 200': (r) => r.status === 200 });
  }
}

export default function () {}
