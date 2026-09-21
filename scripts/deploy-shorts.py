"""Deploy short-video backend + first compressed clip to Aliyun."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/opt/hotwords/server"
LOCAL_ROOT = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords")
LOCAL_VIDEO = Path(r"C:\Users\admin\Downloads\jimeng-2026-09-20-5965-compressed.mp4")
REMOTE_VIDEO_NAME = "jimeng-2026-09-20-5965-compressed.mp4"
PUBLIC_BASE = "http://39.96.67.128:8787"

FILES = [
    ("server/src/shorts.js", f"{REMOTE_ROOT}/src/shorts.js"),
    ("server/src/db.js", f"{REMOTE_ROOT}/src/db.js"),
    ("server/src/index.js", f"{REMOTE_ROOT}/src/index.js"),
    ("server/src/routes.js", f"{REMOTE_ROOT}/src/routes.js"),
    ("server/src/admin.js", f"{REMOTE_ROOT}/src/admin.js"),
    ("server/public/admin/index.html", f"{REMOTE_ROOT}/public/admin/index.html"),
]


def load_env() -> dict[str, str]:
    env: dict[str, str] = {}
    path = LOCAL_ROOT / "server" / ".env"
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def ensure_remote_dir(sftp: paramiko.SFTPClient, client: paramiko.SSHClient, remote: str) -> None:
    try:
        sftp.stat(remote)
    except FileNotFoundError:
        client.exec_command(f"mkdir -p {remote}")[1].channel.recv_exit_status()


def main() -> None:
    password = os.environ.get("HOTWORDS_SSH_PASSWORD")
    if not password:
        raise SystemExit("HOTWORDS_SSH_PASSWORD required")
    if not LOCAL_VIDEO.is_file():
        raise SystemExit(f"missing video: {LOCAL_VIDEO}")

    env = load_env()
    admin_user = env.get("ADMIN_USERNAME") or "admin"
    admin_pass = env.get("ADMIN_PASSWORD") or ""
    if not admin_pass:
        raise SystemExit("ADMIN_PASSWORD missing in server/.env")

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=password, timeout=30)
    sftp = c.open_sftp()

    ensure_remote_dir(sftp, c, f"{REMOTE_ROOT}/uploads/videos")
    ensure_remote_dir(sftp, c, f"{REMOTE_ROOT}/src")
    ensure_remote_dir(sftp, c, f"{REMOTE_ROOT}/public/admin")

    remote_video = f"{REMOTE_ROOT}/uploads/videos/{REMOTE_VIDEO_NAME}"
    print(f"upload video -> {remote_video}")
    sftp.put(str(LOCAL_VIDEO), remote_video)

    for rel, remote in FILES:
        local = LOCAL_ROOT / rel
        print(f"upload {rel}")
        sftp.put(str(local), remote)
    sftp.close()

    # Ensure PUBLIC_BASE_URL on server if missing
    cmd = (
        f"grep -q '^PUBLIC_BASE_URL=' {REMOTE_ROOT}/.env "
        f"|| echo 'PUBLIC_BASE_URL={PUBLIC_BASE}' >> {REMOTE_ROOT}/.env; "
        "systemctl restart hotwords; sleep 2; "
        "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8787/shorts/categories"
    )
    _, stdout, stderr = c.exec_command(cmd)
    code = stdout.read().decode("utf-8", "replace").strip()
    err = stderr.read().decode("utf-8", "replace").strip()
    print("restart categories_http=", code, err[:200] if err else "")
    c.close()

    # Admin login + seed first short if feed empty
    login_body = json.dumps({"username": admin_user, "password": admin_pass}).encode()
    req = urllib.request.Request(
        f"{PUBLIC_BASE}/admin/api/login",
        data=login_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        token = json.loads(resp.read().decode()).get("token")
    if not token:
        raise SystemExit("admin login failed")

    feed_req = urllib.request.Request(f"{PUBLIC_BASE}/shorts/feed?limit=5")
    with urllib.request.urlopen(feed_req, timeout=30) as resp:
        feed = json.loads(resp.read().decode())
    items = feed.get("items") or []
    print("feed_count=", len(items))
    if items:
        print("seed skipped; feed already has items")
        print("sample=", items[0].get("title"), items[0].get("videoUrl"))
        return

    seed = {
        "title": "Reply Messages",
        "author": "词搭子口语",
        "caption": (
            "Do you reply messages as soon as you receive them? "
            "It depends on who's messaging..."
        ),
        "category": "speaking",
        "keywords": ["reply", "messages", "gossip", "ignored", "family"],
        "videoUrl": f"/uploads/videos/{REMOTE_VIDEO_NAME}",
        "durationMs": 15000,
        "sortOrder": 0,
        "published": True,
    }
    create_req = urllib.request.Request(
        f"{PUBLIC_BASE}/admin/api/shorts",
        data=json.dumps(seed, ensure_ascii=False).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(create_req, timeout=30) as resp:
            created = json.loads(resp.read().decode())
        print("seeded", created.get("item", {}).get("id"), created.get("item", {}).get("videoUrl"))
    except urllib.error.HTTPError as e:
        print("seed_failed", e.code, e.read().decode("utf-8", "replace")[:500])
        raise

    with urllib.request.urlopen(urllib.request.Request(f"{PUBLIC_BASE}/shorts/feed?limit=3"), timeout=30) as resp:
        feed2 = json.loads(resp.read().decode())
    print("feed_after=", [(i.get("id"), i.get("title"), i.get("category")) for i in feed2.get("items") or []])


if __name__ == "__main__":
    main()
