import errno
import http.client, http.server, json, os, subprocess, sys

PORT, API_PORT = 8888, 8887

here = os.path.dirname(os.path.abspath(__file__))
HTML = open(os.path.join(here, "index.html"), encoding="utf-8").read().encode()
CSS  = open(os.path.join(here, "style.css"),  encoding="utf-8").read().encode()

os.makedirs("data", exist_ok=True)
java = subprocess.Popen(["java", "-cp", "bin/peer", "Main"], stdout=sys.stdout, stderr=sys.stderr)

class Handler(http.server.BaseHTTPRequestHandler):

    def _send(self, code, ctype, body):
        self.send_response(code); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", len(body)); self.end_headers(); self.wfile.write(body)

    def _proxy(self, method, path, body=None):
        # transfère la requête au serveur Java (port 8887) et renvoie sa réponse JSON
        try:
            c = http.client.HTTPConnection("localhost", API_PORT, timeout=10)
            c.request(method, path, body, {"Content-Type": "application/json"} if body else {})
            r = c.getresponse(); self._send(r.status, "application/json", r.read()); c.close()
        except Exception as e:
            self._send(503, "application/json", json.dumps({"error": str(e)}).encode())

    def do_GET(self):
        if   self.path in ("/", "/index.html"): self._send(200, "text/html; charset=utf-8", HTML)
        elif self.path == "/style.css":         self._send(200, "text/css; charset=utf-8", CSS)
        elif self.path in ("/status", "/tracker-status"): self._proxy("GET", self.path)
        else: self.send_response(404); self.end_headers()

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.path in ("/search", "/download"): self._proxy("POST", self.path, body)
        else: self.send_response(404); self.end_headers()

    def log_message(self, *_): pass

try:
    print(f"Dashboard: http://localhost:{PORT}")
    http.server.HTTPServer(("localhost", PORT), Handler).serve_forever()
except OSError as e:
    if e.errno == errno.EADDRINUSE:
        print(f"Dashboard deja actif sur http://localhost:{PORT}")
    else:
        raise
finally:
    java.terminate()  # coupe Java quand Python s'arrête
