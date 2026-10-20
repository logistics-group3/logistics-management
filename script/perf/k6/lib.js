// 성능 측정 스크립트 공통 함수. script/perf.sh가 k6 컨테이너 안에서 /scripts로 마운트해 실행한다.
import http from 'k6/http';
import { fail } from 'k6';

export const BASE = `${__ENV.BASE_URL || 'http://gateway-service:8080'}/api/v1`;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function headers(token, extra = {}) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}`, ...extra } };
}

export function login(username, password) {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ username, password }), { headers: JSON_HEADERS });
  if (res.status !== 200) fail(`로그인 실패: ${username} → ${res.status} ${res.body}`);
  return res.json('data.accessToken');
}

/**
 * 여러 사용자를 동시에 로그인해 토큰을 받는다. 로그인은 BCrypt 비교라 한 번에 0.1초 안팎 걸린다.
 * Note: 로그인은 Gateway 화이트리스트라 권한 재확인(I-01)을 거치지 않는다. 그래서 토큰 준비가 권한 캐시를 미리 채우지 않는다.
 */
export function loginAll(users, password, chunk = 10) {
  const tokens = {};
  for (let i = 0; i < users.length; i += chunk) {
    const part = users.slice(i, i + chunk);
    const responses = http.batch(part.map((u) => ['POST', `${BASE}/auth/login`,
      JSON.stringify({ username: u.username, password }), { headers: JSON_HEADERS }]));
    responses.forEach((res, j) => {
      if (res.status !== 200) fail(`로그인 실패: ${part[j].username} → ${res.status}`);
      tokens[part[j].username] = res.json('data.accessToken');
    });
  }
  return tokens;
}

/** 목록 API를 끝까지 넘겨 content를 모은다. 공통 페이지 크기는 10·30·50만 허용한다. */
export function allPages(path, token) {
  const items = [];
  for (let page = 0; ; page++) {
    const sep = path.includes('?') ? '&' : '?';
    const res = http.get(`${BASE}${path}${sep}page=${page}&size=50`, headers(token));
    if (res.status !== 200) fail(`목록 조회 실패: ${path} → ${res.status} ${res.body}`);
    const content = res.json('data.content');
    items.push(...content);
    if (content.length < 50) return items;
  }
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

/**
 * 워밍업과 본 측정을 이어 붙인 고정 도착률 시나리오.
 * Note: 가상 사용자 수 고정 방식은 서버가 느려지면 요청도 덜 보내 부하가 저절로 줄어든다. 도착률을 고정해야 조건끼리 공정하게 비교된다.
 * 지표는 본 측정 구간(scenario:main)만 임계값 하위 지표로 따로 집계한다.
 */
export function arrivalOptions(exec = 'default') {
  const rate = Number(__ENV.RATE || 20);
  const warmup = __ENV.WARMUP || '1m';
  const duration = __ENV.DURATION || '5m';
  const vus = Math.max(10, rate * 2);
  return {
    setupTimeout: '10m',
    discardResponseBodies: false,
    scenarios: {
      warmup: { executor: 'constant-arrival-rate', exec, rate: Math.max(1, Math.ceil(rate / 4)), timeUnit: '1s',
        duration: warmup, preAllocatedVUs: vus, maxVUs: vus * 5, gracefulStop: '5s' },
      main: { executor: 'constant-arrival-rate', exec, rate, timeUnit: '1s', duration, startTime: warmup,
        preAllocatedVUs: vus, maxVUs: vus * 5, gracefulStop: '10s' },
    },
    thresholds: {
      // 판정용이 아니라 본 측정 구간만 따로 집계하려고 둔다. 넘어도 측정은 끝까지 진행된다.
      'http_req_duration{scenario:main}': [{ threshold: 'p(95)<1000', abortOnFail: false }],
      'http_req_failed{scenario:main}': [{ threshold: 'rate<0.01', abortOnFail: false }],
      'iterations{scenario:main}': ['count>=0'],
      'dropped_iterations{scenario:main}': ['count>=0'],
    },
  };
}

export function loadData() {
  return JSON.parse(open('/out/data.json'));
}
