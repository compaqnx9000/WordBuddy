"""Deploy Alipay point-purchase backend."""
from __future__ import annotations

import os
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/opt/hotwords/server"
LOCAL_ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")

FILES = [
    ("server/src/points.js", f"{REMOTE_ROOT}/src/points.js"),
    ("server/src/alipay.js", f"{REMOTE_ROOT}/src/alipay.js"),
    ("server/src/pointOrders.js", f"{REMOTE_ROOT}/src/pointOrders.js"),
    ("server/src/images.js", f"{REMOTE_ROOT}/src/images.js"),
    ("server/src/db.js", f"{REMOTE_ROOT}/src/db.js"),
    ("server/src/schema.sql", f"{REMOTE_ROOT}/src/schema.sql"),
    ("server/src/routes.js", f"{REMOTE_ROOT}/src/routes.js"),
    ("server/src/index.js", f"{REMOTE_ROOT}/src/index.js"),
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

    # Ensure sandbox env knobs exist (do not overwrite existing secrets).
    env_cmds = [
        f"grep -q '^ALIPAY_APP_ID=' {REMOTE_ROOT}/.env || echo 'ALIPAY_APP_ID=2021006198682932' >> {REMOTE_ROOT}/.env",
        f"grep -q '^ALIPAY_NOTIFY_URL=' {REMOTE_ROOT}/.env || echo 'ALIPAY_NOTIFY_URL=http://39.96.67.128:8787/alipay/notify' >> {REMOTE_ROOT}/.env",
        f"grep -q '^ALIPAY_SANDBOX=' {REMOTE_ROOT}/.env || echo 'ALIPAY_SANDBOX=true' >> {REMOTE_ROOT}/.env",
        f"grep -q '^AI_IMAGE_POINTS_COST=' {REMOTE_ROOT}/.env || echo 'AI_IMAGE_POINTS_COST=5' >> {REMOTE_ROOT}/.env",
        "systemctl restart hotwords; sleep 2; "
        "curl -s http://127.0.0.1:8787/point-packages; echo; "
        "curl -s -o /dev/null -w 'notify=%{http_code}\\n' -X POST http://127.0.0.1:8787/alipay/notify; "
        "journalctl -u hotwords -n 12 --no-pager",
    ]
    _, stdout, stderr = c.exec_command(" && ".join(env_cmds))
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace").strip()
    if err:
        print("stderr:", err[:500])
    c.close()
    print("SERVER DEPLOY OK")


if __name__ == "__main__":
    main()
