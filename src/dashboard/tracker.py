import socket
from .config import config

HOST = config["tracker_host"]
PORT = int(config["tracker_port"])


def lire(sock):
    data = b""
    while b"\n" not in data:
        chunk = sock.recv(1024)
        if not chunk:
            break
        data += chunk
    return data.decode().strip()


def ping():
    try:
        s = socket.socket()
        s.settimeout(3)
        s.connect((HOST, PORT))
        s.close()
        return True, None
    except Exception as e:
        return False, str(e)


def search(filename):
    try:
        s = socket.socket()
        s.settimeout(5)
        s.connect((HOST, PORT))

        s.sendall(f"look [ {filename} ]\n".encode())
        resp = lire(s)

        if not resp.startswith("list ["):
            s.close()
            return {"found": False}

        inner = resp[resp.index("[")+1 : resp.rindex("]")].strip()
        if not inner:
            s.close()
            return {"found": False}

        nom, taille, t_piece, cle = inner.split()

        s.sendall(f"getfile {cle}\n".encode())
        resp2 = lire(s)
        s.close()

        peers = []
        if "[" in resp2 and "]" in resp2:
            for token in resp2[resp2.index("[")+1 : resp2.rindex("]")].split():
                if ":" in token:
                    ip, port = token.rsplit(":", 1)
                    peers.append({"ip": ip, "port": port})

        return {
            "found": True,
            "filename": nom,
            "size": int(taille),
            "piece_size": int(t_piece),
            "key": cle,
            "peers": peers,
        }

    except Exception as e:
        return {"found": False, "error": str(e)}
