import http.server
import json
import os

from . import peer, tracker
from .config import config

STATUS = "data/status.json"

here = os.path.dirname(__file__)
HTML = open(os.path.join(here, "index.html"), encoding="utf-8").read().encode()
CSS  = open(os.path.join(here, "style.css"),  encoding="utf-8").read().encode()


class Handler(http.server.BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self.reply(200, "text/html; charset=utf-8", HTML)

        elif self.path == "/style.css":
            self.reply(200, "text/css; charset=utf-8", CSS)

        elif self.path == "/tracker-status":
            ok, err = tracker.ping()
            addr = f"{config['tracker_host']}:{config['tracker_port']}"
            self.reply(200, "application/json",
                       json.dumps({"ok": ok, "addr": addr, "error": err}).encode())

        elif self.path == "/status":
            data = {"local": [], "downloads": peer.all_downloads()}
            try:
                with open(STATUS) as f:
                    data["local"] = json.load(f).get("downloads", [])
            except Exception:
                pass
            self.reply(200, "application/json", json.dumps(data).encode())

        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))

        if self.path == "/search":
            req = json.loads(body)
            res = tracker.search(req.get("filename", ""))
            self.reply(200, "application/json", json.dumps(res).encode())

        elif self.path == "/download":
            req = json.loads(body)
            ok, err = peer.start(req["ip"], req["port"], req["key"], req.get("filename", ""))
            code = 200 if ok else 409
            self.reply(code, "application/json",
                       json.dumps({"ok": ok, "error": err}).encode())

        else:
            self.send_response(404)
            self.end_headers()

    def reply(self, code, ctype, body):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", len(body))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass
