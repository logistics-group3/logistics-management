#!/usr/bin/env python3
"""성능 마스터 측정 한 회차를 합격선으로 판정한다(delivery-service/docs/perf-master.md 1절).

사용: python3 load-test/perf-judge.py <k6 요약 JSON> <배정 집계 -assignment.txt> > <-verdict.txt>
  종료 코드: 0 통과, 1 실패, 2 판정 불가(입력 파일이나 값이 없다. 측정 환경부터 본다)
  같은 입력이면 출력이 같다. 기록을 검증할 때는 다시 돌려 -verdict.txt와 비교한다.
"""
import json
import os
import re
import sys

# perf-master.md 1절 합격선. 바꾸면 그 문서와 함께 바꾼다
CREATE_P95_MS = 200
CREATE_P99_MS = 500
LOSS_PCT = 0.1
READ_P95_MS = 100
DEVIATION = 1
ASSIGN_P95_MS = 1000
PENDING = 0

CREATE = 'I-09 직접'
READ = 'D-01 허브별'
CONFIRM = 'I-19 직접'

PASS, FAIL, UNKNOWN, NA = '통과', '실패', '판정 불가', '해당 없음'


def duration(metrics, name, stat):
    return metrics.get(f'http_req_duration{{name:{name},phase:nofault}}', {}).get(stat)


def requests(metrics, name):
    """(보낸 수, 실패 수). k6 Rate 지표의 passes는 값이 참인 표본 수라 http_req_failed에서는 실패한 요청 수다."""
    m = metrics.get(f'http_req_failed{{name:{name},phase:nofault}}')
    if m is None:
        return None, None
    return m['passes'] + m['fails'], m['passes']


def read_assignment(path):
    """'== 판정용' 아래 key=value 줄(load-test/assignment-stats.sh)."""
    values, seen = {}, False
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if line.startswith('== 판정용'):
                seen = True
            elif seen and (m := re.fullmatch(r'(\w+)=(.*)', line)):
                values[m.group(1)] = m.group(2)
    return values


def check(label, value, limit, fmt):
    if value is None:
        return [label, '-', str(limit), UNKNOWN]
    return [label, fmt.format(value), str(limit), PASS if value <= limit else FAIL]


def to_number(text):
    return float(text) if text not in (None, '') else None


def judge(k6_path, assignment_path):
    rows, notes = [], []
    try:
        with open(k6_path, encoding='utf-8') as f:
            metrics = json.load(f)['metrics']
    except OSError as e:
        return [], [f'k6 요약을 읽지 못했다: {e.strerror}']
    except (ValueError, KeyError) as e:
        return [], [f'k6 요약 형식이 다르다: {e}']
    try:
        assignment = read_assignment(assignment_path)
    except OSError as e:
        assignment = {}
        notes.append(f'배정 집계를 읽지 못했다: {e.strerror}')

    rows.append(check('생성 p95(ms)', duration(metrics, CREATE, 'p(95)'), CREATE_P95_MS, '{:.1f}'))
    rows.append(check('생성 p99(ms)', duration(metrics, CREATE, 'p(99)'), CREATE_P99_MS, '{:.1f}'))

    sent, errors = requests(metrics, CREATE)
    dropped = metrics.get('dropped_iterations{scenario:create}', {}).get('count')
    if dropped is None:
        notes.append('dropped_iterations{scenario:create}가 없다. k6 스크립트가 T10 도구(fault-isolation.js)가 아니다')
    if not sent:
        notes.append('생성 요청이 없다. k6 출력을 확인한다')
    loss = None
    if sent and dropped is not None:
        loss = (errors + dropped) / (sent + dropped) * 100
        notes.append(f'생성 보냄 {sent}, 실패 {errors}, 못 보냄 {dropped}')
    rows.append(check('생성 실패+못 보냄(%)', loss, LOSS_PCT, '{:.3f}'))

    rows.append(check('조회 p95(ms)', duration(metrics, READ, 'p(95)'), READ_P95_MS, '{:.1f}'))
    rows.append(check('담당자 편차(건)', to_number(assignment.get('deviation')), DEVIATION, '{:.0f}'))

    basis = assignment.get('basis')
    if basis == 'none':
        rows.append(['배정 지연 p95(ms)', '-', str(ASSIGN_P95_MS), NA])
        rows.append(['미배정(건)', assignment.get('pending', '-'), str(PENDING), NA])
    elif basis is None or 'assign_p95_ms' not in assignment:
        rows.append(['배정 지연 p95(ms)', '-', str(ASSIGN_P95_MS), UNKNOWN])
        rows.append(check('미배정(건)', to_number(assignment.get('pending')), PENDING, '{:.0f}'))
    else:
        # 배정이 하나도 없으면 지연 값이 비어 있다. 입력은 있으니 판정 불가가 아니라 실패다
        p95 = to_number(assignment['assign_p95_ms'])
        rows.append(check('배정 지연 p95(ms)', p95, ASSIGN_P95_MS, '{:.0f}') if p95 is not None
                    else ['배정 지연 p95(ms)', '배정 없음', str(ASSIGN_P95_MS), FAIL])
        rows.append(check('미배정(건)', to_number(assignment.get('pending')), PENDING, '{:.0f}'))

    # 판정에 넣지 않는 참고 값. 확정(I-19) 실패는 배정되지 않은 구간으로 미배정에서 잡힌다(perf-master.md 1절)
    confirm_sent, confirm_errors = requests(metrics, CONFIRM)
    if confirm_sent:
        notes.append(f'확정 p95 {duration(metrics, CONFIRM, "p(95)") or 0:.1f}ms, 보냄 {confirm_sent}, 실패 {confirm_errors}')
    vus = metrics.get('vus', {})
    vus_max = metrics.get('vus_max', {})
    notes.append(f'VU 동시 최대 {vus.get("max", "-")}, 할당 최대 {vus_max.get("max", "-")}')
    if assignment:
        notes.append(f'배정 기준 {basis}, 측정 허브 담당자 {assignment.get("managers", "-")}명, '
                     f'허브 밖 배정 {assignment.get("outside", "-")}')
    return rows, notes


def main():
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    k6_path, assignment_path = sys.argv[1:]
    rows, notes = judge(k6_path, assignment_path)

    results = [r[3] for r in rows]
    if not rows or UNKNOWN in results:
        verdict, code = UNKNOWN, 2
    elif FAIL in results:
        verdict, code = FAIL, 1
    else:
        verdict, code = PASS, 0

    print('판정 기준: delivery-service/docs/perf-master.md 1절')
    print(f'입력: {os.path.basename(k6_path)}, {os.path.basename(assignment_path)}')
    print('\t'.join(['지표', '값', '합격선', '결과']))
    for r in rows:
        print('\t'.join(r))
    for n in notes:
        print(f'참고\t{n}')
    failed = [r[0] for r in rows if r[3] in (FAIL, UNKNOWN)]
    print('\t'.join(['판정', verdict] + ([', '.join(failed)] if failed else [])))
    sys.exit(code)


if __name__ == '__main__':
    main()
