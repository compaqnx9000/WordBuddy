"""Upload WordBuddy release APK + version.json to Aliyun."""
from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_APP = "/opt/hotwords/server/public/app"
LOCAL_ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")
APK = LOCAL_ROOT / "outputs" / "WordBuddy-release.apk"
VERSION = LOCAL_ROOT / "server" / "public" / "app" / "version.json"


def main() -> None:
    password = os.environ.get("HOTWORDS_SSH_PASSWORD")
    if not password:
        raise SystemExit("HOTWORDS_SSH_PASSWORD required")
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

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=password, timeout=30)
    sftp = c.open_sftp()
    try:
        sftp.stat(REMOTE_APP)
    except FileNotFoundError:
        stdin, stdout, stderr = c.exec_command(f"mkdir -p {REMOTE_APP}")
        stdout.channel.recv_exit_status()

    archive = f"WordBuddy-{name}-c{code}.apk"
    print(f"upload {APK.name} -> {REMOTE_APP}/WordBuddy-release.apk")
    sftp.put(str(APK), f"{REMOTE_APP}/WordBuddy-release.apk")
    print(f"archive -> {REMOTE_APP}/{archive}")
    sftp.put(str(APK), f"{REMOTE_APP}/{archive}")
    print("upload version.json")
    sftp.put(str(VERSION), f"{REMOTE_APP}/version.json")

    # Keep lightweight history if present.
    history_remote = f"{REMOTE_APP}/versions.json"
    try:
        with sftp.open(history_remote, "r") as f:
            history = json.loads(f.read().decode("utf-8"))
    except Exception:
        history = {"items": []}
    items = history.get("items") if isinstance(history, dict) else None
    if not isinstance(items, list):
        items = []
    entry = {
        **meta,
        "apkArchivePath": f"/app/{archive}",
        "current": True,
    }
    items = [i for i in items if int(i.get("versionCode") or 0) != code]
    for i in items:
        i["current"] = False
    items.insert(0, entry)
    history = {"items": items[:30]}
    with sftp.open(history_remote, "w") as f:
        f.write(json.dumps(history, ensure_ascii=False, indent=2))
    sftp.close()

    stdin, stdout, stderr = c.exec_command(
        "systemctl restart hotwords; sleep 1; curl -s http://127.0.0.1:8787/app/version"
    )
    print(stdout.read().decode("utf-8", "replace"))
    print("exit", stdout.channel.recv_exit_status())
    c.close()
    print("RELEASE DEPLOY OK", name, code)


if __name__ == "__main__":
    main()
