"""Deploy WeChat login backend."""
from __future__ import annotations

import os
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/opt/hotwords/server"
LOCAL_ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")

FILES = [
    ("server/src/wechatLogin.js", f"{REMOTE_ROOT}/src/wechatLogin.js"),
    ("server/src/db.js", f"{REMOTE_ROOT}/src/db.js"),
    ("server/src/schema.sql", f"{REMOTE_ROOT}/src/schema.sql"),
    ("server/src/routes.js", f"{REMOTE_ROOT}/src/routes.js"),
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
        print(f"upload {rel}")
        sftp.put(str(LOCAL_ROOT / rel), remote)
    sftp.close()

    stdin, stdout, stderr = c.exec_command(
        "systemctl restart hotwords; sleep 2; systemctl is-active hotwords; "
        "curl -s -X POST http://127.0.0.1:8787/auth/wechat "
        "-H 'Content-Type: application/json' -d '{\"code\":\"invalidcode\"}'"
    )
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace")
    if err.strip():
        print(err)
    c.close()


if __name__ == "__main__":
    main()
