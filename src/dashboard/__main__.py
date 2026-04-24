import http.server
import os

from .config import config
from .server import Handler

PORT = 8888

os.makedirs("data", exist_ok=True)
print(f"Dashboard : http://localhost:{PORT}")
print(f"Tracker   : {config['tracker_host']}:{config['tracker_port']}")
http.server.HTTPServer(("localhost", PORT), Handler).serve_forever()
