#!/usr/bin/env python3
"""sample-resources.py의 CSV와 단계 구간(markers: 라벨,시작epoch,끝epoch)으로 단계별 컨테이너 평균·최대 CPU를 표로 낸다.
사용: analyze-resources.py <resources.csv> <markers.csv>"""
import collections, csv, re, sys

ANSI = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")

samples = collections.defaultdict(list)
for t, name, cpu in csv.reader(open(sys.argv[1])):
    try:
        samples[ANSI.sub("", name)].append((float(t), float(ANSI.sub("", cpu).rstrip("%"))))
    except ValueError:
        pass
for label, start, end in csv.reader(open(sys.argv[2])):
    start, end = float(start), float(end)
    rows = []
    for name, points in samples.items():
        values = [c for t, c in points if start <= t <= end]
        if values:
            short = name.replace("logistics-ui-test-", "").replace("-1", "")
            rows.append((sum(values) / len(values), max(values), short if not short.startswith("k6") and "-" not in short[:1] else short))
    rows.sort(reverse=True)
    print(f"## {label}  " + " | ".join(f"{n} 평균{a:.0f} 최대{m:.0f}" for a, m, n in rows[:6]))
