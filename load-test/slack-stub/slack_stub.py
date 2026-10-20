"""Slack API 대역(주문 → DM 끝단 측정용). 실제 Slack으로 보내지 않고 받은 시각만 기록한다.

order-service가 부르는 세 API(users.lookupByEmail, conversations.open, chat.postMessage)와 채널 Webhook에 정상 응답한다.
기록은 메모리에 두고 관리 경로로 꺼낸다.
  GET  /_records   CSV(epoch_ms,kind,order_id,delivery_id)
  GET  /_stats     종류별 건수
  POST /_reset     기록 비우기
환경 변수: STUB_LATENCY_MS(Slack API 호출마다 기다리는 ms, 기본 0), STUB_TEAM_ID(기본 TLOADTEST), PORT(기본 9900)

Note: 실제 Slack은 메서드별 호출 한도가 있어(chat.postMessage는 채널당 초당 1건 안팎) 끝단 처리량을 재면 Slack 한도를 재게 된다.
  대역은 한도 없이 응답해 우리 서비스의 처리 능력만 보이게 한다. 실제 응답 시간 영향은 STUB_LATENCY_MS로 따로 본다.
"""
import json
import os
import re
import threading
import zlib
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

LATENCY = int(os.environ.get("STUB_LATENCY_MS", "0")) / 1000
TEAM_ID = os.environ.get("STUB_TEAM_ID", "TLOADTEST")
ORDER_RE = re.compile(r"주문: ([0-9a-f-]{36})")
DELIVERY_RE = re.compile(r"배송: ([0-9a-f-]{36})")
# Webhook 주문 알림은 "[신규 주문] <orderId>" 형식이다
WEBHOOK_ORDER_RE = re.compile(r"\[신규 주문\] ([0-9a-f-]{36})")

lock = threading.Lock()
records = []


def record(kind, text=""):
    order = ORDER_RE.search(text) or WEBHOOK_ORDER_RE.search(text)
    delivery = DELIVERY_RE.search(text)
    row = (int(time.time() * 1000), kind, order.group(1) if order else "", delivery.group(1) if delivery else "")
    with lock:
        records.append(row)


def user_id(email):
    # 이메일마다 고정된 Slack 사용자 ID. 형식만 order의 검증([UW][A-Z0-9]+)에 맞춘다
    return "U" + format(zlib.crc32(email.encode()), "08X")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    # Note: 헤더와 본문을 따로 쓰면 Nagle과 지연 ACK가 겹쳐 호출마다 약 40ms가 붙는다. 대역 때문에 느려지지 않게 끈다
    disable_nagle_algorithm = True

    def log_message(self, *args):
        pass

    def reply(self, status, body, content_type="application/json"):
        data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        # Note: order의 JDK HttpClient는 본문을 chunked로 보낸다(Content-Length 없음). 길이만 보면 빈 본문이 된다
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            raw = b""
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    break
                raw += self.rfile.read(size)
                self.rfile.readline()
        else:
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw)
        except ValueError:
            return {}

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/_records":
            with lock:
                rows = list(records)
            return self.reply(200, "".join(f"{t},{k},{o},{d}\n" for t, k, o, d in rows), "text/csv")
        if url.path == "/_stats":
            with lock:
                counts = {}
                for _, kind, _, _ in records:
                    counts[kind] = counts.get(kind, 0) + 1
            return self.reply(200, counts)
        if url.path.endswith("/users.lookupByEmail"):
            time.sleep(LATENCY)
            email = parse_qs(url.query).get("email", [""])[0]
            record("lookup")
            return self.reply(200, {"ok": True, "user": {"id": user_id(email), "team_id": TEAM_ID, "deleted": False, "is_bot": False}})
        self.reply(404, {"ok": False, "error": "unknown_method"})

    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/_reset":
            with lock:
                records.clear()
            return self.reply(200, {"ok": True})
        body = self.body()
        if path.endswith("/conversations.open"):
            time.sleep(LATENCY)
            record("open")
            return self.reply(200, {"ok": True, "channel": {"id": "D" + str(body.get("users", "X"))[1:]}})
        if path.endswith("/chat.postMessage"):
            time.sleep(LATENCY)
            text = body.get("text", "")
            # 수동 지정 요청은 Webhook으로 가므로 여기 오는 메시지는 담당자 DM이다
            record("dm", text)
            return self.reply(200, {"ok": True, "channel": body.get("channel"), "ts": f"{time.time():.6f}"})
        if path.startswith("/webhook"):
            text = body.get("text", "")
            record("manual" if "수동 지정 필요" in text else "webhook", text)
            return self.reply(200, "ok", "text/plain")
        self.reply(404, {"ok": False, "error": "unknown_method"})


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "9900"))), Handler)
    server.daemon_threads = True
    print(f"slack-stub listening latency={LATENCY}s team={TEAM_ID}", flush=True)
    server.serve_forever()
