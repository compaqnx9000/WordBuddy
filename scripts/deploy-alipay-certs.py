"""Upload Alipay certs + private key and enable live transfer."""
from __future__ import annotations

import os
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/opt/hotwords/server"
LOCAL_CERTS = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords\server\certs")
LOCAL_ALIPAY_JS = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords\server\src\alipay.js")


def wrap_pkcs8(raw: str) -> str:
    text = raw.strip().replace("\\n", "\n")
    if "BEGIN" in text:
        return text if text.endswith("\n") else text + "\n"
    body = "".join(text.split())
    chunks = [body[i : i + 64] for i in range(0, len(body), 64)]
    kind = "PRIVATE KEY" if body.startswith("MIIEv") else "RSA PRIVATE KEY"
    return "-----BEGIN " + kind + "-----\n" + "\n".join(chunks) + "\n-----END " + kind + "-----\n"


def main() -> None:
    password = os.environ.get("HOTWORDS_SSH_PASSWORD")
    if not password:
        raise SystemExit("HOTWORDS_SSH_PASSWORD required")

    key_src = next(LOCAL_CERTS.glob("*应用私钥*"))
    pem = wrap_pkcs8(key_src.read_text(encoding="utf-8"))
    if "BEGIN PRIVATE KEY" not in pem and "BEGIN RSA PRIVATE KEY" not in pem:
        raise SystemExit("private key wrap failed")

    files = [
        (LOCAL_ALIPAY_JS, f"{REMOTE_ROOT}/src/alipay.js"),
        (LOCAL_CERTS / "appCertPublicKey_2021006198682932.crt", f"{REMOTE_ROOT}/certs/appCertPublicKey_2021006198682932.crt"),
        (LOCAL_CERTS / "alipayCertPublicKey_RSA2.crt", f"{REMOTE_ROOT}/certs/alipayCertPublicKey_RSA2.crt"),
        (LOCAL_CERTS / "alipayRootCert.crt", f"{REMOTE_ROOT}/certs/alipayRootCert.crt"),
    ]

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=password, timeout=30)
    sftp = c.open_sftp()
    try:
        sftp.stat(f"{REMOTE_ROOT}/certs")
    except FileNotFoundError:
        stdin, stdout, stderr = c.exec_command(f"mkdir -p {REMOTE_ROOT}/certs")
        stdout.channel.recv_exit_status()

    for src, dest in files:
        print("upload", src.name)
        sftp.put(str(src), dest)

    print("upload app_private_key.pem")
    with sftp.open(f"{REMOTE_ROOT}/certs/app_private_key.pem", "w") as f:
        f.write(pem)
    sftp.close()

    remote_sh = r"""
set -e
ROOT=/opt/hotwords/server
chmod 700 "$ROOT/certs"
chmod 600 "$ROOT/certs/app_private_key.pem"
chmod 644 "$ROOT/certs/"*.crt
ENV="$ROOT/.env"
ensure_line() {
  key="$1"
  val="$2"
  if grep -q "^${key}=" "$ENV"; then
    sed -i "s|^${key}=.*|${key}=${val}|" "$ENV"
  else
    printf '%s=%s\n' "$key" "$val" >> "$ENV"
  fi
}
ensure_line ALIPAY_SANDBOX false
ensure_line WITHDRAW_SANDBOX false
ensure_line ALIPAY_PRIVATE_KEY_PATH certs/app_private_key.pem
ensure_line ALIPAY_APP_CERT_PATH certs/appCertPublicKey_2021006198682932.crt
ensure_line ALIPAY_PUBLIC_CERT_PATH certs/alipayCertPublicKey_RSA2.crt
ensure_line ALIPAY_ROOT_CERT_PATH certs/alipayRootCert.crt
ls -l "$ROOT/certs"
cd "$ROOT"
node --input-type=module <<'EOF'
import dotenv from 'dotenv'
import { signParams, alipayConfig } from './src/alipay.js'
dotenv.config()
const c = alipayConfig()
const out = {
  appId: c.appId,
  hasPrivateKey: Boolean(c.privateKey),
  privateKeyHeader: (c.privateKey.split('\n')[0] || ''),
  certMode: c.certMode,
  transferReady: c.transferReady,
  configured: c.configured,
  hasAlipayPublicKey: Boolean(c.alipayPublicKey),
  appCertSn: c.appCertSn,
  rootCertSnPrefix: String(c.rootCertSn || '').slice(0, 12),
  sandboxPay: c.sandbox,
  notifyUrl: c.notifyUrl,
}
try {
  signParams({ app_id: c.appId, foo: 'bar' }, c.privateKey)
  out.signOk = true
} catch (e) {
  out.signOk = false
  out.signError = String(e && e.message ? e.message : e)
}
console.log(JSON.stringify(out, null, 2))
EOF
systemctl restart hotwords
sleep 2
curl -s -o /dev/null -w 'health=%{http_code}\n' http://127.0.0.1:8787/app/version
journalctl -u hotwords -n 8 --no-pager
"""
    _, stdout, stderr = c.exec_command(remote_sh)
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace").strip()
    if err:
        print("stderr:", err[:1500])
    c.close()
    print("ALIPAY CERT DEPLOY OK")


if __name__ == "__main__":
    main()
