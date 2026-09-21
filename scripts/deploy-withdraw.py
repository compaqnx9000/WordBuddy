"""Deploy Alipay merchant-transfer withdrawal backend."""
from __future__ import annotations

import os
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/opt/hotwords/server"
LOCAL_ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")

FILES = [
    ("server/src/alipay.js", f"{REMOTE_ROOT}/src/alipay.js"),
    ("server/src/withdrawals.js", f"{REMOTE_ROOT}/src/withdrawals.js"),
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

    remote_sh = r"""
set -e
ROOT=/opt/hotwords/server
ENV="$ROOT/.env"
mkdir -p "$ROOT/certs"
touch "$ENV"
ensure_line() {
  key="$1"
  val="$2"
  if grep -q "^${key}=" "$ENV"; then
    :
  else
    printf '%s=%s\n' "$key" "$val" >> "$ENV"
  fi
}
if grep -q '^WITHDRAW_SANDBOX=' "$ENV"; then
  sed -i 's/^WITHDRAW_SANDBOX=.*/WITHDRAW_SANDBOX=false/' "$ENV"
else
  echo 'WITHDRAW_SANDBOX=false' >> "$ENV"
fi
ensure_line ALIPAY_TRANSFER_SCENE '现金营销'
ensure_line WITHDRAW_AMOUNT_FEN 100
ensure_line WITHDRAW_POINTS_COST 10
echo '--- env flags ---'
grep -E '^(ALIPAY_APP_ID|ALIPAY_NOTIFY_URL|ALIPAY_SANDBOX|ALIPAY_GATEWAY|ALIPAY_TRANSFER_SCENE|WITHDRAW_SANDBOX|WITHDRAW_AMOUNT_FEN|WITHDRAW_POINTS_COST|ALIPAY_APP_CERT_PATH|ALIPAY_PUBLIC_CERT_PATH|ALIPAY_ROOT_CERT_PATH)=' "$ENV" || true
echo -n 'ALIPAY_PRIVATE_KEY='
if grep -q 'ALIPAY_PRIVATE_KEY' "$ENV"; then echo present; else echo NO; fi
echo -n 'ALIPAY_PUBLIC_KEY='
if grep -q 'ALIPAY_PUBLIC_KEY' "$ENV"; then echo present; else echo NO; fi
echo -n 'ALIPAY_APP_CERT='
if grep -q 'BEGIN CERTIFICATE' "$ENV" && grep -q 'ALIPAY_APP_CERT' "$ENV"; then echo present; else echo maybe_file_only; fi
echo '--- certs dir ---'
ls -la "$ROOT/certs" || true
systemctl restart hotwords
sleep 2
curl -s -o /dev/null -w 'health=%{http_code}\n' http://127.0.0.1:8787/app/version
journalctl -u hotwords -n 24 --no-pager
"""
    _, stdout, stderr = c.exec_command(remote_sh)
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace").strip()
    if err:
        print("stderr:", err[:2000])
    c.close()
    print("SERVER DEPLOY OK")


if __name__ == "__main__":
    main()
