import base64
import os
import socket
import threading

downloads = {}
lock = threading.Lock()


class Conn:
    def __init__(self, ip, port):
        self.sock = socket.socket()
        self.sock.settimeout(30)
        self.sock.connect((ip, int(port)))
        self.buf = b""

    def readline(self):
        while b"\n" not in self.buf:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("connexion perdue")
            self.buf += chunk
        line, self.buf = self.buf.split(b"\n", 1)
        return line.decode().strip()

    def read_bytes(self, n):
        while len(self.buf) < n:
            self.buf += self.sock.recv(4096)
        data, self.buf = self.buf[:n], self.buf[n:]
        return data

    def send(self, msg):
        self.sock.sendall((msg + "\n").encode())

    def close(self):
        self.sock.close()


# le buffermap Java est un BitSet base64 — on récupère les indices des pièces disponibles
def decode_bitset(b64):
    data = base64.b64decode(b64)
    return [i for i in range(len(data) * 8) if data[i // 8] & (1 << (7 - i % 8))]


def parse_header(line):
    # "data key [idx size idx size ...]" → liste de (idx, size)
    inside = line[line.index("[")+1 : line.rindex("]")].split()
    return [(int(inside[i]), int(inside[i+1])) for i in range(0, len(inside)-1, 2)]


def _download(ip, port, key, filename):
    with lock:
        downloads[key] = {"filename": filename or key, "key": key,
                          "total": 0, "done": 0, "pct": 0.0,
                          "complete": False, "error": None}
    try:
        conn = Conn(ip, port)
        conn.send(f"interested {key}")

        parts = conn.readline().split(" ", 2)
        pieces = decode_bitset(parts[2])

        with lock:
            downloads[key]["total"] = len(pieces)

        conn.send(f"getpieces {key} [{' '.join(map(str, pieces))}]")
        infos = parse_header(conn.readline())

        piece_data = {}
        for idx, size in infos:
            piece_data[idx] = conn.read_bytes(size)
            with lock:
                downloads[key]["done"] = len(piece_data)
                downloads[key]["pct"] = round(len(piece_data) * 100 / len(infos), 1)

        os.makedirs("data/complete", exist_ok=True)
        with open(f"data/complete/{filename or key}", "wb") as f:
            for idx in sorted(piece_data):
                f.write(piece_data[idx])

        with lock:
            downloads[key]["complete"] = True
            downloads[key]["pct"] = 100.0

        conn.close()

    except Exception as e:
        with lock:
            downloads[key]["error"] = str(e)


def start(ip, port, key, filename):
    with lock:
        d = downloads.get(key)
        if d and not d["complete"] and not d["error"]:
            return False, "déjà en cours"
    threading.Thread(target=_download, args=(ip, port, key, filename), daemon=True).start()
    return True, None


def all_downloads():
    with lock:
        return list(downloads.values())
