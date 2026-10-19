#!/usr/bin/env python3
"""k6 --summary-export JSON에서 접수·완료 TPS와 지연을 한 줄로 뽑는다. 사용: summarize.py <json> <rate> <duration>"""
import json, sys

path, rate, duration = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(path))["metrics"]
seconds = float(duration.rstrip("s")) if duration.endswith("s") else float(duration.rstrip("m")) * 60


def get(name, key, default=0):
    return m.get(name, {}).get(key, default)


acc, comp = get("orders_accepted", "count"), get("orders_completed", "count")
print(
    f"rate={rate}/s 접수 {acc}건({acc / seconds:.1f}/s) 완료 {comp}건({comp / seconds:.1f}/s) "
    f"거절 {get('orders_rejected', 'count')} 실패 {get('orders_failed', 'count')} 폴링초과 {get('orders_poll_timeout', 'count')} "
    f"드롭 {get('dropped_iterations', 'count')} | 접수 p95 {m.get('http_req_duration{name:O-01 접수}', {}).get('p(95)', 0):.0f}ms "
    f"p99 {m.get('http_req_duration{name:O-01 접수}', {}).get('p(99)', 0):.0f}ms | 완료 p95 {get('order_complete_ms', 'p(95)'):.0f}ms"
)
