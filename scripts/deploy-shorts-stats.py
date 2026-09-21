import os
from pathlib import Path
import paramiko

HOST = "39.96.67.128"
ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")
REMOTE = "/opt/hotwords/server"
pw = os.environ["HOTWORDS_SSH_PASSWORD"]

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username="root", password=pw, timeout=30)
sftp = c.open_sftp()
for rel in ["server/src/admin.js", "server/public/admin/index.html"]:
    local = ROOT / rel
    remote = f"{REMOTE}/{rel.split('/', 1)[1]}"
    print("put", rel, "->", remote)
    sftp.put(str(local), remote)
sftp.close()
_, out, err = c.exec_command(
    "systemctl restart hotwords; sleep 2; curl -s -o /dev/null -w '%{http_code}' "
    "-H 'Authorization: Bearer x' http://127.0.0.1:8787/admin/api/shorts/stats"
)
print("stats_http", out.read().decode().strip(), err.read().decode()[:200])
c.close()
