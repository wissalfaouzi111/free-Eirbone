import http.server
import os

from .config import config
from .server import Handler

PORT = 8888

os.makedirs("data", exist_ok=True)
http.server.HTTPServer(("localhost", PORT), Handler).serve_forever()
