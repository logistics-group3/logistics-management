#!/usr/bin/env python3
"""성능 마스터 측정의 판단과 기록(delivery-service/docs/perf-master.md 1·5·7·10절).

사용: python3 load-test/perf/perf.py <명령>
  next <버전>                   다음에 잴 칸을 칸 기록에서 정한다. "cell <칸>" 또는 "done <이유>"
  status                        load-test/results/perf/STATUS.md를 칸 기록으로 다시 만든다
  verify [버전]                 칸마다 필수 파일·해시·무효 조건·판정 재계산을 보고, STATUS.md를 다시 만들어 비교한다(문제가 있으면 1)
  check-cell <칸 폴더>          칸 하나만 확인한다(perf-run.sh가 칸이 끝날 때 부른다. 문제가 있으면 1)
  prom-dump <시작> <끝> <파일>  Prometheus 시계열을 JSON으로 남긴다(epoch 초)
  judge-fault <칸 폴더>         V4 장애 격리 칸을 판정한다(5절 V4 합격선)
  table                         perf-master.md 8절 칸별 표(V0~V3)와 V4 표를 칸 기록으로 만든다
상태는 대화나 별도 파일이 아니라 칸 폴더에서만 나온다. 그래서 끊긴 뒤 같은 명령으로 이어 가고, 검증은 다시 계산해 비교한다.
"""
import csv
import difflib
import gzip
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.dont_write_bytecode = True   # perf-judge.py를 불러올 때 load-test/에 __pycache__를 남기지 않는다
ROOT = Path(__file__).resolve().parents[2]
os.chdir(ROOT)
RESULTS = Path(os.environ.get('PERF_RESULTS_DIR', 'load-test/results/perf'))
STATUS = RESULTS / 'STATUS.md'
CAPTURES = RESULTS / 'captures'
VERSIONS = ['V0', 'V1', 'V2', 'V3', 'V4']
RATES = [40, 80, 160, 240, 320, 480, 640]   # 1절 생성 TPS 계단
V4_CELLS = ['fault-160', 'fault-80']         # 5절 V4
SPIKE_SKIP_SEC = 30                          # 7절 "1회차 첫 30초만 튐"
FILES = ['k6.json', 'k6.csv.gz', 'k6.txt', 'k6-stats.txt', 'reset.txt', 'idle.txt', 'pgwait.txt', 'docker.txt',
         'assignment.txt', 'prom.json', 'verdict.txt', 'manifest.txt']
V4_FILES = ['recovery.txt', 'timeline.txt']

_spec = importlib.util.spec_from_file_location('perf_judge', 'load-test/perf-judge.py')
judge = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(judge)
K6_LABELS = {'생성 p95(ms)', '생성 p99(ms)', '생성 실패+못 보냄(%)', '조회 p95(ms)'}


# ---------- 칸 기록 읽기 ----------

def manifest(cell):
    values, files = {}, {}
    for line in (cell / 'manifest.txt').read_text(encoding='utf-8').splitlines():
        key, _, value = line.partition('=')
        if key == 'file':
            name, _, digest = value.partition(' ')
            files[name] = digest
        else:
            values[key] = value
    return values, files


def verdict_lines(cell):
    path = cell / 'verdict.txt'
    return path.read_text(encoding='utf-8').splitlines() if path.exists() else []


def verdict(cell):
    """'통과', '실패', '판정 불가'. 칸이 없으면 None."""
    for line in reversed(verdict_lines(cell)):
        if line.startswith('판정\t'):
            return line.split('\t')[1]
    return None


def failing(cell):
    return [line.split('\t')[0] for line in verdict_lines(cell)
            if line.count('\t') == 3 and line.split('\t')[3] in (judge.FAIL, judge.UNKNOWN)]


def cells(ver):
    d = RESULTS / ver
    return sorted(p for p in d.iterdir() if p.is_dir() and re.fullmatch(r'\d+-\d|fault-\d+', p.name)) if d.exists() else []


# ---------- 7절: 1회차 첫 30초만 튐 ----------

def percentile(values, p):
    """k6와 같은 방식(정렬 뒤 선형 보간)."""
    if not values:
        return None
    values = sorted(values)
    i = p * (len(values) - 1)
    lo, hi = int(i), min(int(i) + 1, len(values) - 1)
    return values[lo] + (values[hi] - values[lo]) * (i - lo)


def k6_rows(cell):
    with gzip.open(cell / 'k6.csv.gz', 'rt', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            yield r


def spike_only(cell):
    """1회차의 실패가 k6 지표뿐이고, 첫 30초를 빼면 그 지표가 모두 합격선 안인가."""
    labels = set(failing(cell))
    if not labels or not labels <= K6_LABELS:
        return False
    samples = []
    for r in k6_rows(cell):
        m = r['metric_name']
        if m in ('http_req_duration', 'http_req_failed') and r['name'] in (judge.CREATE, judge.READ):
            samples.append((int(r['timestamp']), m, r['name'], float(r['metric_value'])))
        elif m == 'dropped_iterations' and r['scenario'] == 'create':
            samples.append((int(r['timestamp']), m, 'create', float(r['metric_value'])))
    starts = [t for t, m, n, _ in samples if m == 'http_req_duration' and n == judge.CREATE]
    if not starts:
        return False
    t0 = min(starts) + SPIKE_SKIP_SEC
    after = [s for s in samples if s[0] >= t0]
    create = [v for t, m, n, v in after if m == 'http_req_duration' and n == judge.CREATE]
    read = [v for t, m, n, v in after if m == 'http_req_duration' and n == judge.READ]
    errors = sum(v for t, m, n, v in after if m == 'http_req_failed' and n == judge.CREATE)
    dropped = sum(v for t, m, n, v in after if m == 'dropped_iterations')
    loss = (errors + dropped) / (len(create) + dropped) * 100 if create else 100
    return (percentile(create, 0.95) <= judge.CREATE_P95_MS and percentile(create, 0.99) <= judge.CREATE_P99_MS
            and loss <= judge.LOSS_PCT and (percentile(read, 0.95) or 0) <= judge.READ_P95_MS)


# ---------- 계단 판정과 다음 칸 ----------

def rate_result(ver, rate):
    """한 계단의 결과. need가 있으면 그 회차를 더 잰다. stop이면 그 버전을 멈춘다(5절)."""
    d = RESULTS / ver
    v = {n: verdict(d / f'{rate}-{n}') for n in (1, 2, 3)}
    runs = {n: x for n, x in v.items() if x is not None}
    res = {'runs': runs, 'need': None, 'state': None, 'note': '', 'stop': False}
    for n in (1, 2):
        if v[n] is None or v[n] == judge.UNKNOWN:
            res['need'] = n
            return res
    p, f = judge.PASS, judge.FAIL
    if v[1] == p and v[2] == p:
        res['state'] = p
    elif v[1] == f and v[2] == f:
        res.update(state=f, stop=True, note='2회 모두 실패 → 멈춤')
    elif v[1] == f and spike_only(d / f'{rate}-1'):
        if v[3] is None or v[3] == judge.UNKNOWN:
            res.update(need=3, note='1회차 첫 30초만 튐 → 3회차(7절)')
        else:
            res.update(state=p if v[3] == p else f, note='1회차 첫 30초만 튐, 2·3회차로 판정(7절)')
    else:
        res.update(state=f, note='1회만 실패')
    return res


def next_cell(ver):
    if ver == 'V4':
        for c in V4_CELLS:
            if verdict(RESULTS / ver / c) in (None, judge.UNKNOWN):
                return f'cell {c}'
        return 'done V4 끝'
    for rate in RATES:
        res = rate_result(ver, rate)
        if res['need']:
            return f"cell {rate}-{res['need']}"
        if res['stop']:
            return f'done 생성 {rate}에서 2회 모두 실패'
    return 'done 계단 끝'


# ---------- STATUS.md ----------

def render_status():
    out = ['# 성능 마스터 측정 진행 상태', '',
           '`load-test/perf-run.sh`가 칸마다 칸 기록으로 다시 만든다. 손으로 고치지 않는다(`load-test/perf-verify.sh`가 다시 만들어 비교한다).',
           '정본은 `delivery-service/docs/perf-master.md`다.', '']
    for ver in VERSIONS:
        d = RESULTS / ver
        out.append(f'## {ver}')
        if not d.exists() or not cells(ver):
            out += ['- 상태: 아직 재지 않았다', '']
            continue
        nxt = next_cell(ver)
        state = f'진행 중(다음 칸 {nxt[5:]})' if nxt.startswith('cell') else f'끝({nxt[5:]})'
        out.append(f'- 상태: {state}')
        invalid = sorted(p.name for p in d.iterdir() if '.invalid-' in p.name)
        shots = len(list(CAPTURES.glob(f'{ver}-*.png'))) if CAPTURES.exists() else 0
        if ver == 'V4':
            out += [f'- 무효로 버린 칸: {", ".join(invalid) or "없음"}', f'- 캡처: {shots}장', '',
                    '| 칸 | 판정 | 실패한 지표 |', '|---|---|---|']
            for c in V4_CELLS:
                out.append(f'| {c} | {verdict(d / c) or ""} | {", ".join(failing(d / c))} |')
            out.append('')
            continue
        results = {r: rate_result(ver, r) for r in RATES}
        passed = [r for r, x in results.items() if x['state'] == judge.PASS]
        failed = [r for r, x in results.items() if x['state'] == judge.FAIL]
        out += [f'- 버틴 최대 TPS: {max(passed) if passed else "없음"} · 처음 실패한 TPS: {min(failed) if failed else "없음"}',
                f'- 무효로 버린 칸: {", ".join(invalid) or "없음"}', f'- 캡처: {shots}장', '',
                '| TPS | 1회차 | 2회차 | 3회차 | 계단 | 실패한 지표 · 비고 |', '|---|---|---|---|---|---|']
        for r, x in results.items():
            if not x['runs']:
                continue
            notes = [f'{n}회차: {", ".join(failing(d / f"{r}-{n}"))}' for n in (1, 2, 3)
                     if x['runs'].get(n) in (judge.FAIL, judge.UNKNOWN)]
            if x['note']:
                notes.append(x['note'])
            runs = [x['runs'].get(n, '') for n in (1, 2, 3)]
            out.append(f'| {r} | {" | ".join(runs)} | {x["state"] or ""} | {" / ".join(notes)} |')
        out.append('')
    return '\n'.join(out)


def write_status():
    RESULTS.mkdir(parents=True, exist_ok=True)
    STATUS.write_text(render_status(), encoding='utf-8')


# ---------- 검증 ----------

def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def rejudge(cell):
    if cell.parent.name == 'V4':
        return render_fault(cell)
    out = subprocess.run([sys.executable, 'load-test/perf-judge.py', str(cell / 'k6.json'), str(cell / 'assignment.txt')],
                         capture_output=True, text=True)
    return out.stdout


def arrival(cell, m):
    """k6가 실제로 보낸(+못 보낸) 요청이 목표 도착률의 99~101%인가. 셸에 남은 환경 변수로 다른 부하를 보냈는지 잡는다."""
    if cell.parent.name == 'V4':
        return []   # 장애 칸은 phase 태그 밖(warmup) 요청이 있어 요약으로 셀 수 없다. timeline.txt로 본다
    metrics = json.loads((cell / 'k6.json').read_text(encoding='utf-8'))['metrics']
    secs = 60
    problems = []
    for name, scenario, rate in ((judge.CREATE, 'create', int(m['tps'])), (judge.READ, 'read', int(m['read_rate']))):
        sent, _ = judge.requests(metrics, name)
        dropped = metrics.get(f'dropped_iterations{{scenario:{scenario}}}', {}).get('count', 0)
        target = rate * secs
        if sent is None or not 0.99 * target <= sent + dropped <= 1.01 * target:
            problems.append(f'{name} 도착률이 목표와 다르다: 보냄+못 보냄 {sent}+{dropped}, 목표 {target}')
    return problems


def check_cell(cell):
    problems = []
    required = FILES + (V4_FILES if cell.parent.name == 'V4' else [])
    missing = [f for f in required if not (cell / f).exists()]
    if missing:
        return [f'필수 파일 없음: {", ".join(missing)}']
    m, files = manifest(cell)
    for name, digest in files.items():
        if not (cell / name).exists() or sha256(cell / name) != digest:
            problems.append(f'해시가 manifest와 다르다: {name}')
    v = verdict(cell)
    if v not in (judge.PASS, judge.FAIL):
        problems.append(f'판정이 {v}')
    if rejudge(cell) != (cell / 'verdict.txt').read_text(encoding='utf-8'):
        problems.append('판정을 다시 계산한 결과가 verdict.txt와 다르다')
    ver = cell.parent.name
    ref = subprocess.run(['bash', '-c', f'source load-test/perf/lib.sh && perf_expected_commit {ver}'],
                         capture_output=True, text=True).stdout.strip()
    warm = m.get('warm_at', '')
    warmed = warm.isdigit() and int(m.get('delivery_started_at') or 0) < int(warm) <= int(m.get('started_at') or 0)
    checks = [
        (m.get('idle_ok') == '1', f'쉬는 상태가 아니었다(최고 CPU {m.get("idle_max_cpu")}% {m.get("idle_top")})'),
        (m.get('image_commit') == m.get('expected_commit') == ref,
         f'delivery 커밋이 다르다: 이미지 {m.get("image_commit")}, 기록 {m.get("expected_commit")}, 지금 {ref}'),
        (m.get('tool_dirty') == '0', '측정 도구(load-test/)에 커밋하지 않은 변경이 있었다'),
        (m.get('zipkin_running') == '0', 'Zipkin이 켜져 있었다'),
        (m.get('k6_exit') == '0', f'측정 스크립트 종료 코드 {m.get("k6_exit")}'),
        (warmed, 'delivery를 띄운 뒤 예열하지 않고 쟀다'),
    ]
    problems += [msg for ok, msg in checks if not ok]
    problems += arrival(cell, m)
    return problems


def verify(only=None):
    problems, info = [], []
    for ver in [only] if only else VERSIONS:
        d = RESULTS / ver
        if not d.exists():
            continue
        for p in sorted(d.iterdir()):
            if p.name.endswith('.tmp'):
                info.append(f'{ver}/{p.name}: 재는 중이거나 끊긴 칸(검증에서 뺀다)')
            elif '.invalid-' in p.name:
                info.append(f'{ver}/{p.name}: 무효로 버린 칸')
        for cell in cells(ver):
            for problem in check_cell(cell):
                problems.append(f'{ver}/{cell.name}: {problem}')
    expected = render_status()
    actual = STATUS.read_text(encoding='utf-8') if STATUS.exists() else ''
    if expected != actual:
        diff = '\n'.join(difflib.unified_diff(actual.splitlines(), expected.splitlines(), 'STATUS.md', '칸 기록', lineterm=''))
        problems.append('STATUS.md가 칸 기록과 다르다\n' + diff)
    for line in info:
        print(f'참고: {line}')
    for line in problems:
        print(f'문제: {line}')
    print('검증: ' + ('통과' if not problems else f'문제 {len(problems)}건'))
    return 1 if problems else 0


# ---------- Prometheus 덤프 ----------

PROM_QUERIES = {
    'delivery_p95_by_uri': 'histogram_quantile(0.95, sum by (uri, le) (rate(http_server_requests_seconds_bucket{application="delivery-service", uri!~"/actuator.*"}[20s])))',
    'p95_by_service': 'histogram_quantile(0.95, sum by (application, le) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[20s])))',
    'delivery_rps_by_uri': 'sum by (uri, status) (rate(http_server_requests_seconds_count{application="delivery-service", uri!~"/actuator.*"}[20s]))',
    'hikari_active': 'sum by (application) (hikaricp_connections_active)',
    'hikari_pending': 'sum by (application) (hikaricp_connections_pending)',
    'tomcat_busy': 'sum by (application) (tomcat_threads_busy_threads)',
    'process_cpu': 'process_cpu_usage{job="logistics-services"}',
    'gc_pause_rate': 'sum by (application) (rate(jvm_gc_pause_seconds_sum[20s]))',
}


def prom_dump(start, end, out):
    base = os.environ.get('PROMETHEUS_URL', 'http://localhost:9090')
    result = {'start': int(start), 'end': int(end), 'step': 5, 'queries': {}}
    for name, expr in PROM_QUERIES.items():
        q = urllib.parse.urlencode({'query': expr, 'start': start, 'end': end, 'step': 5})
        try:
            with urllib.request.urlopen(f'{base}/api/v1/query_range?{q}', timeout=20) as r:
                data = json.load(r)['data']['result']
        except Exception as e:   # 덤프 실패는 칸을 깨지 않는다. 빈 결과로 남기고 이유를 적는다
            data = {'error': str(e)}
        result['queries'][name] = {'expr': expr, 'result': data}
    Path(out).write_text(json.dumps(result, ensure_ascii=False), encoding='utf-8')


# ---------- V4 판정 ----------

def render_fault(cell):
    """5절 V4 합격선: 장애 중 생성 실패+못 보냄 0.1% 이하, 장애 중 조회 p95 100ms 이하, user 복구 30초 뒤 미배정 0."""
    rows, notes = [], []
    try:
        metrics = json.loads((cell / 'k6.json').read_text(encoding='utf-8'))['metrics']
    except (OSError, ValueError, KeyError) as e:
        metrics = None
        notes.append(f'k6 요약을 읽지 못했다: {type(e).__name__}')
    if metrics is not None:
        def fault(metric, name):
            return metrics.get(f'{metric}{{name:{name},phase:fault}}')
        failed = fault('http_req_failed', judge.CREATE)
        window = [int(r['timestamp']) for r in k6_rows(cell) if 'phase=fault' in (r['extra_tags'] or '')]
        dropped = sum(1 for r in k6_rows(cell) if r['metric_name'] == 'dropped_iterations' and r['scenario'] == 'create'
                      and window and min(window) <= int(r['timestamp']) <= max(window))
        loss = None
        if failed and failed['passes'] + failed['fails']:
            sent = failed['passes'] + failed['fails']
            loss = (failed['passes'] + dropped) / (sent + dropped) * 100
            notes.append(f'장애 중 생성 보냄 {sent}, 실패 {failed["passes"]}, 못 보냄 {dropped}')
        rows.append(judge.check('장애 중 생성 실패+못 보냄(%)', loss, judge.LOSS_PCT, '{:.3f}'))
        read = fault('http_req_duration', judge.READ)
        rows.append(judge.check('장애 중 조회 p95(ms)', read.get('p(95)') if read else None, judge.READ_P95_MS, '{:.1f}'))
        for phase in ('before', 'fault', 'after'):
            d = metrics.get(f'http_req_duration{{name:{judge.CREATE},phase:{phase}}}', {})
            notes.append(f'생성 p95 {phase} {d.get("p(95)", 0):.1f}ms')
    try:
        values = dict(line.split('=', 1) for line in (cell / 'recovery.txt').read_text(encoding='utf-8').splitlines()
                      if '=' in line)
        pending = judge.to_number(values.get('pending_confirmed_before_recovery'))
    except OSError:
        pending = None
    rows.append(judge.check('복구 30초 뒤 미배정(건)', pending, judge.PENDING, '{:.0f}'))

    results = [r[3] for r in rows]
    v = judge.UNKNOWN if (not rows or judge.UNKNOWN in results) else judge.FAIL if judge.FAIL in results else judge.PASS
    # 입력 줄에 칸 폴더 이름을 쓰지 않는다. 판정은 <칸>.tmp에서 만들고 검증은 옮긴 뒤 다시 계산하므로 이름이 달라진다
    lines = ['판정 기준: delivery-service/docs/perf-master.md 5절 V4', '입력: k6.json, k6.csv.gz, recovery.txt',
             '\t'.join(['지표', '값', '합격선', '결과'])] + ['\t'.join(r) for r in rows] + [f'참고\t{n}' for n in notes]
    bad = [r[0] for r in rows if r[3] in (judge.FAIL, judge.UNKNOWN)]
    lines.append('\t'.join(['판정', v] + ([', '.join(bad)] if bad else [])))
    return '\n'.join(lines) + '\n'


# ---------- 8절 표 ----------

def verdict_values(cell):
    return {line.split('\t')[0]: line.split('\t')[1] for line in verdict_lines(cell) if line.count('\t') == 3}


def cpu_of(cell, container):
    values = []
    for line in (cell / 'docker.txt').read_text(encoding='utf-8').splitlines():
        parts = line.split('\t')
        if len(parts) >= 3 and parts[1].startswith(container):
            values.append(float(parts[2].rstrip('%') or 0))
    return f'{sum(values) / len(values):.0f}% / {max(values):.0f}%' if values else '-'


def prom_max(cell, name):
    data = json.loads((cell / 'prom.json').read_text(encoding='utf-8'))['queries'][name]['result']
    values = [float(v) for s in data if isinstance(s, dict) and s['metric'].get('application') == 'delivery-service'
              for _, v in s['values']]
    return f'{max(values):.0f}' if values else '-'


def pg_max(cell, column):
    rows = (cell / 'pgwait.txt').read_text(encoding='utf-8').splitlines()
    header = rows[0].split('\t')
    i = header.index(column)
    values = [int(r.split('\t')[i]) for r in rows[1:] if r.count('\t') >= i and r.split('\t')[i].isdigit()]
    return str(max(values)) if values else '-'


def render_table():
    out = ['| 버전 | TPS | 회차 | 생성 p95 | 생성 p99 | 실패+못 보냄 | 조회 p95 | 편차 | 배정 지연 p95 | 미배정 | Hikari 대기 | advisory 대기 '
           '| postgres CPU 평균/최대 | 판정 | 캡처 |', '|' + '---|' * 15]
    for ver in VERSIONS[:4]:
        for rate in RATES:
            for n in (1, 2, 3):
                cell = RESULTS / ver / f'{rate}-{n}'
                if not cell.exists():
                    continue
                v = verdict_values(cell)
                cols = [ver, str(rate), str(n), v.get('생성 p95(ms)'), v.get('생성 p99(ms)'), v.get('생성 실패+못 보냄(%)'),
                        v.get('조회 p95(ms)'), v.get('담당자 편차(건)'), v.get('배정 지연 p95(ms)'), v.get('미배정(건)'),
                        prom_max(cell, 'hikari_pending'), pg_max(cell, 'advisory_wait'), cpu_of(cell, 'postgres'),
                        verdict(cell), f'`{ver}-{rate}-{n}-*`']
                out.append('| ' + ' | '.join(c if c not in (None, '') else '-' for c in cols) + ' |')
    out += ['', '| 칸 | 장애 중 생성 실패+못 보냄 | 장애 중 조회 p95 | 복구 30초 뒤 미배정 | 생성 p95 장애 전 / 중 / 뒤 | 판정 |', '|---|---|---|---|---|---|']
    for c in V4_CELLS:
        cell = RESULTS / 'V4' / c
        if not cell.exists():
            continue
        v = verdict_values(cell)
        notes = {line.split('\t')[1].split(' ')[2]: line.split(' ')[-1] for line in verdict_lines(cell)
                 if line.startswith('참고\t생성 p95 ')}
        out.append(f"| {c} | {v.get('장애 중 생성 실패+못 보냄(%)')}% | {v.get('장애 중 조회 p95(ms)')}ms "
                   f"| {v.get('복구 30초 뒤 미배정(건)')} | {notes.get('before')} / {notes.get('fault')} / {notes.get('after')} | {verdict(cell)} |")
    return '\n'.join(out)


def main(argv):
    if not argv:
        sys.exit(__doc__)
    cmd, args = argv[0], argv[1:]
    if cmd == 'next' and len(args) == 1:
        print(next_cell(args[0]))
    elif cmd == 'status':
        write_status()
    elif cmd == 'verify':
        sys.exit(verify(args[0] if args else None))
    elif cmd == 'check-cell' and len(args) == 1:
        problems = check_cell(Path(args[0]))
        for p in problems:
            print(f'문제: {p}')
        sys.exit(1 if problems else 0)
    elif cmd == 'prom-dump' and len(args) == 3:
        prom_dump(*args)
    elif cmd == 'judge-fault' and len(args) == 1:
        sys.stdout.write(render_fault(Path(args[0])))
    elif cmd == 'table':
        print(render_table())
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main(sys.argv[1:])
