"""Deploy WeChat pay + withdraw backend files and ensure notify URL."""
from __future__ import annotations

import os
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/opt/hotwords/server"
LOCAL_ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")

FILES = [
    ("server/src/wechat.js", f"{REMOTE_ROOT}/src/wechat.js"),
    ("server/src/pointOrders.js", f"{REMOTE_ROOT}/src/pointOrders.js"),
    ("server/src/withdrawals.js", f"{REMOTE_ROOT}/src/withdrawals.js"),
    ("server/src/routes.js", f"{REMOTE_ROOT}/src/routes.js"),
    ("server/src/db.js", f"{REMOTE_ROOT}/src/db.js"),
    ("server/src/schema.sql", f"{REMOTE_ROOT}/src/schema.sql"),
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

    # Ensure notify URL exists; do not overwrite secrets.
    cmd = r"""
python3 - <<'PY'
from pathlib import Path
p = Path('/opt/hotwords/server/.env')
text = p.read_text(encoding='utf-8') if p.exists() else ''
lines = text.splitlines()
keys = {}
order = []
for line in lines:
    if not line.strip() or line.strip().startswith('#') or '=' not in line:
        order.append(('raw', line))
        continue
    k, v = line.split('=', 1)
    k = k.strip()
    keys[k] = v
    order.append(('kv', k))
changed = False
if 'WECHAT_PAY_NOTIFY_URL' not in keys or not keys['WECHAT_PAY_NOTIFY_URL'].strip():
    keys['WECHAT_PAY_NOTIFY_URL'] = 'http://39.96.67.128:8787/wechat/notify'
    if not any(t == 'kv' and k == 'WECHAT_PAY_NOTIFY_URL' for t, k in order):
        order.append(('kv', 'WECHAT_PAY_NOTIFY_URL'))
    changed = True
if changed:
    out = []
    seen = set()
    for t, v in order:
        if t == 'raw':
            out.append(v)
        else:
            if v in seen:
                continue
            seen.add(v)
            out.append(f'{v}={keys[v]}')
    for k, val in keys.items():
        if k not in seen:
            out.append(f'{k}={val}')
    p.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
    print('updated WECHAT_PAY_NOTIFY_URL')
else:
    print('WECHAT_PAY_NOTIFY_URL ok')
# report readiness
need = ['WECHAT_MCH_ID','WECHAT_APP_ID','WECHAT_MCH_SERIAL_NO','WECHAT_API_V3_KEY','WECHAT_PAY_NOTIFY_URL']
for k in need:
    v = keys.get(k, '').strip()
    if k == 'WECHAT_API_V3_KEY':
        print(f'{k}={"SET" if v else "MISSING"}')
    else:
        print(f'{k}={v or "MISSING"}')
has_key = bool(keys.get('WECHAT_MCH_PRIVATE_KEY','').strip()) or Path('/opt/hotwords/server/certs/apiclient_key.pem').exists() or Path('/opt/hotwords/server/certs/wechat_apiclient_key.pem').exists()
print(f'WECHAT_MCH_PRIVATE_KEY_OR_FILE={"SET" if has_key else "MISSING"}')
PY
systemctl restart hotwords
sleep 2
systemctl is-active hotwords
curl -s http://127.0.0.1:8787/point-packages | head -c 500
echo
"""
    _, stdout, stderr = c.exec_command(cmd)
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace")
    if err.strip():
        print(err)
    c.close()


if __name__ == "__main__":
    main()
