// 배송 부하 테스트 공통 값과 함수. 시드(infra/postgres/seed/delivery-e2e.sql)의 고정 ID와 계정을 쓴다.
import http from 'k6/http';
import { fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PASSWORD = 'Sparta!2026';

export const SEED = {
  seoulHub: '6f39d519-4947-4a95-a454-58c25728f388',
  supplierCompany: 'e2e00000-0000-4000-8000-000000000101',
  receiverCompany: 'e2e00000-0000-4000-8000-000000000102',
  receiverManager: 'e2e00000-0000-4000-8000-000000000006',
  product: 'e2e00000-0000-4000-8000-000000000201',
};

/** setup()에서 한 번 로그인해 토큰을 VU 전체가 같이 쓴다. 로그인 자체는 측정 대상이 아니다. */
export function login(username) {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ username, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });
  if (res.status !== 200) {
    fail(`${username} 로그인 실패(${res.status}). 시드를 넣었는지 확인한다: delivery-service/docs/load-test.md`);
  }
  return res.json('data.accessToken');
}

/** name 태그로 API별 지표(http_req_duration{name:O-01} 등)를 나눈다. URL에 ID가 들어가도 한 묶음으로 센다. */
export function params(token, name, headers = {}) {
  return {
    headers: Object.assign({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, headers),
    tags: { name },
  };
}

/** UUID v4. 측정용 orderId라 암호학적 난수가 필요 없다. k6 버전마다 crypto 지원이 달라 직접 만든다. */
export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

/** 단계 설정을 환경 변수로 바꿀 수 있게 한다. 예: PEAK_RATE=40 */
export function numberEnv(name, fallback) {
  const value = Number(__ENV[name]);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}
