"""Deploy short-video favorites backend changes."""
from __future__ import annotations

import os
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/opt/hotwords/server"
LOCAL_ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")

FILES = [
    ("server/src/shorts.js", f"{REMOTE_ROOT}/src/shorts.js"),
    ("server/src/db.js", f"{REMOTE_ROOT}/src/db.js"),
    ("server/src/routes.js", f"{REMOTE_ROOT}/src/routes.js"),
    ("server/src/admin.js", f"{REMOTE_ROOT}/src/admin.js"),
    ("server/src/schema.sql", f"{REMOTE_ROOT}/src/schema.sql"),
    ("server/public/admin/index.html", f"{REMOTE_ROOT}/public/admin/index.html"),
]


def main() -> None:
    password = os.environ.get("HOTWORDS_SSH_PASSWORD")
    if not password:
        raise SystemExit("HOTWORDS_SSH_PASSWORD required")

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=password, timeout=30)
    sftp = c.open_sftp()
    for rel, remote in FILES:
        local = LOCAL_ROOT / rel
        print(f"upload {rel}")
        sftp.put(str(local), remote)
    sftp.close()

    cmd = (
        "systemctl restart hotwords; sleep 2; "
        "curl -s -o /dev/null -w 'categories=%{http_code}\\n' http://127.0.0.1:8787/shorts/categories; "
        "curl -s -o /dev/null -w 'favorites=%{http_code}\\n' http://127.0.0.1:8787/me/short-favorites; "
        "journalctl -u hotwords -n 20 --no-pager"
    )
    _, stdout, stderr = c.exec_command(cmd)
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace").strip()
    if err:
        print("stderr:", err[:400])
    c.close()
    print("SERVER DEPLOY OK")


if __name__ == "__main__":
    main()
