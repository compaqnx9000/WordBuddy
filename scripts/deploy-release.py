"""Upload WordBuddy release APK + version.json to 47.95.111.238."""
from __future__ import annotations

import json
import socket
from datetime import datetime, timezone
from pathlib import Path

import paramiko

HOST = "47.95.111.238"
USER = "root"
PASS = "Lenovot420"
REMOTE_APP = "/opt/hotwords/server/public/app"
LOCAL_ROOT = Path(r"D:\work\zeroG\Cursor-Prrojects\HotWords")
APK = LOCAL_ROOT / "outputs" / "WordBuddy-release.apk"
VERSION = LOCAL_ROOT / "server" / "public" / "app" / "version.json"


def connect():
    sock = None
    try:
        s = socket.socket()
        s.bind(("192.168.1.3", 0))
        s.settimeout(25)
        s.connect((HOST, 22))
        sock = s
    except Exception:
        sock = None
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(
        HOST,
        username=USER,
        password=PASS,
        sock=sock,
        timeout=45,
        allow_agent=False,
        look_for_keys=False,
    )
    return c


def main() -> None:
    if not APK.is_file():
        raise SystemExit(f"missing apk: {APK}")
    if not VERSION.is_file():
        raise SystemExit(f"missing version: {VERSION}")

    meta = json.loads(VERSION.read_text(encoding="utf-8"))
    code = int(meta.get("versionCode") or 0)
    name = str(meta.get("versionName") or "").strip()
    if not meta.get("releasedAt"):
        meta["releasedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
        VERSION.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    c = connect()
    sftp = c.open_sftp()
    try:
        sftp.stat(REMOTE_APP)
    except FileNotFoundError:
        c.exec_command(f"mkdir -p {REMOTE_APP}")[1].channel.recv_exit_status()

    archive = f"WordBuddy-{name}-c{code}.apk"
    print(f"upload {APK.name} ({APK.stat().st_size/1e6:.1f} MB)")
    sftp.put(str(APK), f"{REMOTE_APP}/WordBuddy-release.apk")
    sftp.put(str(APK), f"{REMOTE_APP}/{archive}")
    sftp.put(str(VERSION), f"{REMOTE_APP}/version.json")

    history_remote = f"{REMOTE_APP}/versions.json"
    try:
        with sftp.open(history_remote, "r") as f:
            history = json.loads(f.read().decode("utf-8"))
    except Exception:
        history = {"items": []}
    items = history.get("items") if isinstance(history, dict) else None
    if not isinstance(items, list):
        items = []
    entry = {**meta, "apkArchivePath": f"/app/{archive}", "current": True}
    items = [i for i in items if int(i.get("versionCode") or 0) != code]
    for i in items:
        i["current"] = False
    items.insert(0, entry)
    with sftp.open(history_remote, "w") as f:
        f.write(json.dumps({"items": items[:30]}, ensure_ascii=False, indent=2))
    sftp.close()

    tin, tout, terr = c.exec_command(
        "systemctl restart hotwords; sleep 1; "
        "curl -s http://127.0.0.1:8787/app/version.json; echo; "
        "ls -lh /opt/hotwords/server/public/app/WordBuddy-release.apk"
    )
    print(tout.read().decode("utf-8", "replace"))
    c.close()
    print("RELEASE DEPLOY OK", name, code)


if __name__ == "__main__":
    main()
