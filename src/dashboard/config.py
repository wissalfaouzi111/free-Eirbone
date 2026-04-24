config = {"tracker_host": "localhost", "tracker_port": "8080"}

# on lit juste ce qu'il faut dans le config.ini
try:
    with open("config.ini", encoding="utf-8") as f:
        for l in f:
            if "=" not in l:
                continue
            k, v = l.strip().split("=", 1)
            k = k.strip()
            v = v.strip()
            if k == "trackerHost":
                config["tracker_host"] = v
            elif k == "trackerPort":
                config["tracker_port"] = v
except FileNotFoundError:
    pass
