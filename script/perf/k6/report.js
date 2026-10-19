// 측정 결과 보고서. script/perf.sh run이 부하 직후 실행한다.
// k6 요약(사용자 체감)과 Prometheus 지표(서비스·구간별)를 본 측정 구간 하나로 맞춰 report.md와 prometheus.json에 쓴다.
// 집계 구간은 [END - DURATION, END]다. 본 측정이 테스트의 마지막 시나리오이므로 끝 시각이 같다.
import http from 'k6/http';
import { fail } from 'k6';

export const options = { iterations: 1, vus: 1 };

const LABEL = __ENV.LABEL;
const PROM = __ENV.PROMETHEUS_URL || 'http://prometheus:9090';
const END = Number(__ENV.END);
const summary = JSON.parse(open(`/out/results/${LABEL}/k6-summary.json`));

function seconds(text) {
  const m = /^(\d+)(s|m)$/.exec(text);
  return Number(m[1]) * (m[2] === 'm' ? 60 : 1);
}
const WINDOW = `${seconds(__ENV.DURATION || '5m')}s`;

// 서비스 메트릭에서 측정 도구 자신의 요청(actuator 수집)은 뺀다.
const NOT_ACTUATOR = 'uri!~"/actuator.*"';
const QUERIES = {
  serverCount: `sum by (application, method, uri, status) (increase(http_server_requests_seconds_count{${NOT_ACTUATOR}}[${WINDOW}]))`,
  serverP95: `histogram_quantile(0.95, sum by (le, application, method, uri) (rate(http_server_requests_seconds_bucket{${NOT_ACTUATOR}}[${WINDOW}])))`,
  feignCount: `sum by (application, clientName, http_method, http_url, http_status_code) (increase(feign_client_requests_seconds_count[${WINDOW}]))`,
  feignP95: `histogram_quantile(0.95, sum by (le, application, clientName, http_url) (rate(feign_client_requests_seconds_bucket[${WINDOW}])))`,
  // Gateway의 http_client_requests는 라우트 프록시 호출(Gateway → 서비스) 지표다. I-01 WebClient 호출은 따로 계측되지 않는다.
  gatewayRouteCount: `sum by (spring_cloud_gateway_route_id, http_method, http_status_code) (increase(http_client_requests_seconds_count{application="gateway-service"}[${WINDOW}]))`,
  gatewayRouteP95: `histogram_quantile(0.95, sum by (le, spring_cloud_gateway_route_id) (rate(http_client_requests_seconds_bucket{application="gateway-service"}[${WINDOW}])))`,
  hikariActiveMax: `max by (application) (max_over_time(hikaricp_connections_active[${WINDOW}]))`,
  hikariPendingMax: `max by (application) (max_over_time(hikaricp_connections_pending[${WINDOW}]))`,
  cpuAvg: `avg by (application) (avg_over_time(process_cpu_usage[${WINDOW}]))`,
};

function query(expr) {
  const res = http.get(`${PROM}/api/v1/query?query=${encodeURIComponent(expr)}&time=${END}`);
  if (res.status !== 200 || res.json('status') !== 'success') fail(`Prometheus 조회 실패: ${res.status} ${expr}`);
  return res.json('data.result').map((r) => ({ labels: r.metric, value: Number(r.value[1]) }))
    .filter((r) => Number.isFinite(r.value));
}

const fmt = (v, digits = 1) => (v === undefined || v === null || Number.isNaN(v) ? '-' : Number(v).toFixed(digits));
const ms = (secondsValue) => fmt(secondsValue * 1000);

function k6Metric(name) {
  return summary.metrics[name] || {};
}

function table(header, rows) {
  if (rows.length === 0) return '_데이터 없음_\n';
  return [`| ${header.join(' | ')} |`, `|${header.map(() => '---').join('|')}|`, ...rows.map((r) => `| ${r.join(' | ')} |`)].join('\n') + '\n';
}

// Note: handleSummary는 VU와 다른 실행 환경에서 돌아 전역 변수를 공유하지 않는다. 결과는 setup이 돌려준 값(setup_data)으로 넘긴다.
export function setup() {
  const prom = {};
  Object.entries(QUERIES).forEach(([key, expr]) => { prom[key] = query(expr); });

  const duration = k6Metric('http_req_duration{scenario:main}');
  const failed = k6Metric('http_req_failed{scenario:main}');
  const iterations = k6Metric('iterations{scenario:main}');
  const dropped = k6Metric('dropped_iterations{scenario:main}');
  const propagation = k6Metric('role_change_propagation_ms');
  const windowSeconds = seconds(__ENV.DURATION || '5m');

  const p95Of = (list, match) => (list.find((r) => match(r.labels)) || {}).value;
  const serverRows = prom.serverCount.filter((r) => r.value >= 0.5)
    .sort((a, b) => (a.labels.application + a.labels.uri).localeCompare(b.labels.application + b.labels.uri))
    .map((r) => [r.labels.application, r.labels.method, r.labels.uri, r.labels.status, fmt(r.value, 0),
      ms(p95Of(prom.serverP95, (l) => l.application === r.labels.application && l.uri === r.labels.uri && l.method === r.labels.method))]);
  const feignRows = prom.feignCount.filter((r) => r.value >= 0.5)
    .sort((a, b) => (a.labels.application + a.labels.http_url).localeCompare(b.labels.application + b.labels.http_url))
    .map((r) => [r.labels.application, r.labels.clientName, r.labels.http_method, r.labels.http_url, r.labels.http_status_code,
      fmt(r.value, 0), ms(p95Of(prom.feignP95, (l) => l.application === r.labels.application && l.clientName === r.labels.clientName
        && l.http_url === r.labels.http_url))]);
  const gatewayRows = prom.gatewayRouteCount.filter((r) => r.value >= 0.5)
    .map((r) => [r.labels.spring_cloud_gateway_route_id, r.labels.http_method, r.labels.http_status_code, fmt(r.value, 0),
      ms(p95Of(prom.gatewayRouteP95, (l) => l.spring_cloud_gateway_route_id === r.labels.spring_cloud_gateway_route_id))]);
  // 항목별 주 지표를 요청 1건당 값으로 맞춘다. A: Gateway I-01, B: delivery→hub I-03, C: order→company 호출과 company 내부 API 지연.
  const sumWhere = (list, match) => list.filter((r) => match(r.labels)).reduce((sum, r) => sum + r.value, 0);
  const perRequest = (value) => (iterations.count ? fmt(value / iterations.count, 2) : '-');
  // Note: Gateway의 I-01 호출은 계측되지 않아 user-service가 받은 I-01에서 delivery의 Feign I-01을 빼서 구한다.
  // Feign 경로는 FeignClient의 path를 뺀 값이라 /internal 접두어가 없다(예: /{userId}, /companies/{companyId}).
  const i01Received = sumWhere(prom.serverCount, (l) => l.application === 'user-service' && l.uri === '/internal/users/{userId}');
  const i01Others = sumWhere(prom.feignCount, (l) => ['/{userId}', '/users/{userId}'].includes(l.http_url)
    && (l.clientName || '').endsWith('UserClient'));
  const i01 = Math.max(0, i01Received - i01Others);
  const i03 = sumWhere(prom.feignCount, (l) => (l.http_url || '').includes('hub-routes'));
  const companyCalls = sumWhere(prom.feignCount, (l) => l.application === 'order-service'
    && /^\/(companies|products)\//.test(l.http_url || ''));
  const companyP95s = prom.serverP95.filter((r) => r.labels.application === 'company-service'
    && (r.labels.uri || '').startsWith('/internal/')).map((r) => r.value);
  const companyInternalP95 = companyP95s.length ? Math.max(...companyP95s) : Number.NaN;
  const hubP95s = prom.serverP95.filter((r) => r.labels.application === 'hub-service').map((r) => r.value);
  const hubP95 = hubP95s.length ? Math.max(...hubP95s) : Number.NaN;
  const row = `| ${new Date(END * 1000).toISOString().slice(0, 10)} | [${LABEL}](${LABEL}.md) | ${__ENV.SCENARIO} | `
    + `${__ENV.PERF_AUTH_CACHE_TTL} | ${__ENV.RATE} | ${fmt(iterations.count / windowSeconds, 2)} | ${fmt((failed.value || 0) * 100, 2)}% | `
    + `${fmt(duration.med)} | ${fmt(duration['p(95)'])} | ${fmt(duration['p(99)'])} | ${perRequest(i01)} | ${perRequest(i03)} | `
    + `${perRequest(companyCalls)} | ${Number.isFinite(companyInternalP95) ? ms(companyInternalP95) : '-'} | `
    + `${Number.isFinite(hubP95) ? ms(hubP95) : '-'} | `
    + `${propagation.avg !== undefined ? fmt(propagation.avg, 0) : '-'} | ${__ENV.COMMIT} |\n`;
  const byApp = (list) => Object.fromEntries(list.map((r) => [r.labels.application, r.value]));
  const active = byApp(prom.hikariActiveMax);
  const pending = byApp(prom.hikariPendingMax);
  const cpu = byApp(prom.cpuAvg);
  const apps = [...new Set([...Object.keys(cpu), ...Object.keys(active)])].sort();

  const report = `# 성능 측정 결과: ${LABEL}

## 조건
| 항목 | 값 |
|---|---|
| 시나리오 | ${__ENV.SCENARIO} |
| 커밋 | ${__ENV.COMMIT} |
| 목표 도착률 | ${__ENV.RATE}/s |
| 워밍업 / 본 측정 | ${__ENV.WARMUP} / ${__ENV.DURATION} |
| Gateway 권한 캐시 TTL | ${__ENV.PERF_AUTH_CACHE_TTL} |
| 추적 샘플링 비율 | ${__ENV.SAMPLING} |
| 집계 구간 끝(UTC) | ${new Date(END * 1000).toISOString()} |

## 사용자 체감 (k6, 본 측정 구간)
| 지표 | 값 |
|---|---|
| 완료 반복 수 | ${fmt(iterations.count, 0)} (${fmt(iterations.count / windowSeconds, 2)}/s) |
| 처리하지 못한 반복(dropped) | ${fmt(dropped.count || 0, 0)} |
| 실패율 | ${fmt((failed.value || 0) * 100, 2)}% |
| 응답 시간 p50 / p95 / p99 | ${fmt(duration.med)} / ${fmt(duration['p(95)'])} / ${fmt(duration['p(99)'])} ms |
| 응답 시간 평균 / 최대 | ${fmt(duration.avg)} / ${fmt(duration.max)} ms |
${propagation.count !== undefined || propagation.avg !== undefined ? `| 권한 변경 반영 시간 평균 / 최대 | ${fmt(propagation.avg, 0)} / ${fmt(propagation.max, 0)} ms |
| 권한 변경 반영 시간 초과 | ${fmt((k6Metric('role_change_probe_timeouts').count) || 0, 0)}회 |
` : ''}
## 항목별 주 지표 (요청 1건당)
| 항목 | 값 | 계산 |
|---|---|---|
| A. Gateway I-01 | ${perRequest(i01)} (${fmt(i01, 0)}회) | user-service가 받은 I-01 ${fmt(i01Received, 0)} − 다른 서비스의 Feign I-01 ${fmt(i01Others, 0)} |
| B. I-03 허브 경로 | ${perRequest(i03)} (${fmt(i03, 0)}회), hub-service API p95 ${Number.isFinite(hubP95) ? ms(hubP95) : '-'} ms | delivery → hub Feign, hub http_server_requests(가장 느린 URI) |
| C. order → company | ${perRequest(companyCalls)} (${fmt(companyCalls, 0)}회), company 내부 API p95 ${Number.isFinite(companyInternalP95) ? ms(companyInternalP95) : '-'} ms | order Feign, company http_server_requests |

## 서비스가 받은 요청 (Prometheus http_server_requests)
${table(['서비스', '메서드', 'URI', '상태', '횟수', 'p95(ms)'], serverRows)}
## 서비스 간 호출 (Prometheus feign_client_requests)
${table(['호출한 서비스', '클라이언트', '메서드', 'URL', '상태', '횟수', 'p95(ms)'], feignRows)}
## Gateway → 서비스 라우팅 (http_client_requests)
${table(['라우트', '메서드', '상태', '횟수', 'p95(ms)'], gatewayRows)}
## 자원
${table(['서비스', 'CPU 평균(%)', 'DB 커넥션 사용 최대', 'DB 커넥션 대기 최대'],
    apps.map((a) => [a, fmt((cpu[a] || 0) * 100), fmt(active[a], 0), fmt(pending[a], 0)]))}
Prometheus 수집 주기가 15초라 횟수는 근사값이다. 같은 조건의 회차끼리 비교한다.
`;
  return { report, prom, row };
}

export default function () {}

export function handleSummary(data) {
  const result = data.setup_data;
  if (!result) fail('보고서를 만들지 못했다.');
  return {
    stdout: result.report,
    [`/out/results/${LABEL}/report.md`]: result.report,
    [`/out/results/${LABEL}/prometheus.json`]: JSON.stringify(result.prom, null, 2),
    [`/out/results/${LABEL}/summary-row.md`]: result.row,
  };
}
