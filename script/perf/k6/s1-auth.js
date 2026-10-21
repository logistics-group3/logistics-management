// S1 인증 요청: 측정 항목 A(Gateway 권한 재확인 I-01)의 기준선. 진입점: script/perf.sh run s1
// 여러 사용자의 토큰을 돌려 가며 GET /users/me를 호출한다. 요청마다 Gateway가 JWT를 검증하고 I-01(권한 캐시)을 거친다.
// 같은 시간에 role_probe 시나리오가 권한 변경 반영 시간을 잰다: 측정용 사용자의 role을 바꾸고,
// 그 사용자의 기존 토큰이 401 TOKEN_ROLE_MISMATCH로 바뀌기까지 걸린 시간을 role_change_propagation_ms에 남긴다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE, arrivalOptions, headers, login, loginAll, loadData, pick } from './lib.js';

const data = loadData();
const propagation = new Trend('role_change_propagation_ms', true);
const probeTimeouts = new Counter('role_change_probe_timeouts');
const PROBE_ROUNDS = Number(__ENV.PROBE_ROUNDS || 3);
const PROBE_LIMIT_SECONDS = 120;

const base = arrivalOptions('auth');
export const options = {
  ...base,
  scenarios: {
    ...base.scenarios,
    // Note: 본 측정이 시작된 뒤 1분부터 측정한다. 부하 중의 반영 시간이 실제 상황과 같고, 워밍업 값이 섞이지 않는다.
    role_probe: { executor: 'per-vu-iterations', exec: 'roleProbe', vus: 1, iterations: 1, maxDuration: '30m',
      startTime: addDurations(__ENV.WARMUP || '1m', '1m') },
  },
};

function seconds(text) {
  const m = /^(\d+)(s|m)$/.exec(text);
  if (!m) throw new Error(`시간 형식은 30s, 5m처럼 쓴다: ${text}`);
  return Number(m[1]) * (m[2] === 'm' ? 60 : 1);
}

function addDurations(a, b) {
  return `${seconds(a) + seconds(b)}s`;
}

export function setup() {
  const tokens = loginAll(data.authUsers, data.password);
  const probeTokens = loginAll(data.probes, data.password);
  return { tokens: Object.values(tokens), probeTokens, master: login(data.master.username, data.master.password) };
}

export function auth(ctx) {
  const res = http.get(`${BASE}/users/me`, { ...headers(pick(ctx.tokens)), tags: { name: 'GET /users/me' } });
  check(res, { 'U-03 200': (r) => r.status === 200 });
}

function changeRole(master, userId, role) {
  const res = http.patch(`${BASE}/users/${userId}/role`, JSON.stringify({ role }), { ...headers(master),
    tags: { name: 'PATCH /users/{id}/role (probe)' } });
  if (res.status !== 200) throw new Error(`권한 변경 실패: ${res.status} ${res.body}`);
}

function me(token) {
  return http.get(`${BASE}/users/me`, { ...headers(token), tags: { name: 'GET /users/me (probe)' } });
}

export function roleProbe(ctx) {
  // Note: 본 측정이 끝나기 전에 끝내야 보고서의 집계 구간(측정 종료 시각 - DURATION)과 어긋나지 않는다.
  const deadline = Date.now() + (seconds(__ENV.DURATION || '5m') - 70) * 1000;
  for (let round = 0; round < PROBE_ROUNDS && round < data.probes.length; round++) {
    if (Date.now() + PROBE_LIMIT_SECONDS * 1000 > deadline + 60 * 1000) break;
    const probe = data.probes[round];
    const token = ctx.probeTokens[probe.username];
    // 1. 측정용 사용자의 캐시를 채운다(USER로 통과).
    if (me(token).status !== 200) throw new Error(`측정용 사용자 ${probe.username}가 USER 토큰으로 통과하지 못한다`);
    // 2. role을 바꾸고 기존 토큰이 거절될 때까지 0.5초 간격으로 확인한다.
    changeRole(ctx.master, probe.userId, 'MASTER');
    const started = Date.now();
    let elapsed = null;
    while (Date.now() - started < PROBE_LIMIT_SECONDS * 1000 && Date.now() < deadline) {
      const res = me(token);
      if (res.status === 401 && res.json('code') === 'TOKEN_ROLE_MISMATCH') { elapsed = Date.now() - started; break; }
      sleep(0.5);
    }
    if (elapsed === null) probeTimeouts.add(1); else propagation.add(elapsed);
    // 3. 다음 측정을 위해 USER로 되돌린다.
    changeRole(ctx.master, probe.userId, 'USER');
    console.log(`권한 변경 반영 ${round + 1}회: ${elapsed === null ? '시간 초과' : `${elapsed}ms`}`);
    sleep(5);
  }
}

export default function () {}
