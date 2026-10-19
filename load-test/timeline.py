#!/usr/bin/env python3
"""장애 격리 실험 결과(CSV)를 장애 시작 기준 10초 단위로 정리한다(delivery-service/docs/fault-isolation.md 8절).
사용: python3 load-test/timeline.py load-test/results/fault-<label>-<시각>.csv.gz
k6 요약(JSON)은 구간 전체 값이라 "언제부터 회복했나"를 알 수 없어 요청별 기록으로 계산한다.
"""
import collections
import csv
import gzip
import sys

NAMES = {'I-09 직접': '201', 'D-01 허브별': '200', 'O-01': '201', 'O-02 목록': '200'}


def phase(row):
    for kv in (row['extra_tags'] or '').split('&'):
        if kv.startswith('phase='):
            return kv[6:]
    return ''


def p95(values):
    values = sorted(values)
    return values[max(0, int(len(values) * 0.95) - 1)] if values else 0


def main(path):
    rows = [r for r in csv.DictReader(gzip.open(path, 'rt'))
            if r['metric_name'] == 'http_req_duration' and r['name'] in NAMES]
    present = {r['name'] for r in rows}
    names = {n: ok for n, ok in NAMES.items() if n in present}
    faults = [int(r['timestamp']) for r in rows if phase(r) == 'fault']
    start = min(faults) if faults else min(int(r['timestamp']) for r in rows)
    buckets = collections.defaultdict(list)
    statuses = collections.Counter()
    for r in rows:
        buckets[(r['name'], (int(r['timestamp']) - start) // 10 * 10)].append((float(r['metric_value']), r['status']))
        if phase(r) in ('fault', 'recovery'):
            statuses[(r['name'], phase(r), r['status'])] += 1
    print('장애 시작 기준(초) | ' + ' | '.join(f'{n} 성공률 | {n} p95(ms)' for n in names))
    for t in sorted({t for _, t in buckets}):
        cells = []
        for name, ok in names.items():
            xs = buckets.get((name, t), [])
            rate = sum(1 for _, s in xs if s == ok) / len(xs) * 100 if xs else 0
            cells += [f'{rate:5.0f}%', f'{p95([v for v, _ in xs]):7.0f}']
        print(f'{t:5d} | ' + ' | '.join(cells))
    print('\n장애·복구 구간 응답 코드')
    for key, count in sorted(statuses.items()):
        print(' ', key, count)


if __name__ == '__main__':
    main(sys.argv[1])
