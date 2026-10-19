#!/usr/bin/env python3
"""부하 중 컨테이너별 CPU%를 1~2초 간격으로 CSV(epoch,name,cpu)에 남긴다. 사용: sample-resources.py <출력 CSV>. 종료는 SIGTERM.
CPU%는 코어 하나가 100이다(Docker VM이 12코어면 합계 상한은 1200). k6 컨테이너도 함께 찍혀 부하 도구가 병목인지 볼 수 있다."""
import re, subprocess, sys, time

# Note: Docker CLI 버전에 따라 출력에 커서 이동 같은 제어 문자가 섞이므로 모두 지운다.
ANSI = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")

out = open(sys.argv[1], "a", buffering=1)
proc = subprocess.Popen(["docker", "stats", "--format", "{{.Name}},{{.CPUPerc}}"], stdout=subprocess.PIPE, text=True)
for line in proc.stdout:
    line = ANSI.sub("", line).strip()
    if "," not in line:
        continue
    name, cpu = line.rsplit(",", 1)
    out.write(f"{time.time():.1f},{name.strip()},{cpu.strip().rstrip('%')}\n")
