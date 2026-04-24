config = {"tracker_host": "localhost", "tracker_port": "8080"}

try:
    for line in open("config.ini"):
        if "=" not in line:
            continue
        k, v = line.strip().split("=", 1)
        if k.strip() == "trackerHost":
            config["tracker_host"] = v.strip()
        if k.strip() == "trackerPort":
            config["tracker_port"] = v.strip()
except FileNotFoundError:
    pass
