"""Deploy WordBuddy landing page to nginx on port 80."""
from __future__ import annotations

import os
from pathlib import Path

import paramiko

HOST = "39.96.67.128"
USER = "root"
REMOTE_ROOT = "/var/www/wordbuddy"
LOCAL_SITE = Path(r"D:\WORK\ZeroGLab\Cursor-Projects\HotWords\site")

NGINX_LANDING = r"""
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    root /var/www/wordbuddy;
    index index.html;

    client_max_body_size 64m;

    location /assets/ {
        try_files $uri =404;
        access_log off;
        expires 7d;
    }

    location /download/ {
        alias /opt/hotwords/server/public/app/;
        types { application/vnd.android.package-archive apk; }
        default_type application/vnd.android.package-archive;
        add_header Content-Disposition 'attachment';
        add_header Cache-Control "public, max-age=300";
    }

    location /app/ {
        proxy_pass http://127.0.0.1:8787/app/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = / {
        try_files /index.html =404;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
""".strip() + "\n"

# Extra locations to inject into wordbuddy.cc HTTPS server (before catch-all proxy).
DOMAIN_SNIPPET = r"""
    # WordBuddy landing (static)
    location = / {
        root /var/www/wordbuddy;
        try_files /index.html =404;
    }

    location ~* \.(html)$ {
        root /var/www/wordbuddy;
        try_files $uri =404;
    }

    location /assets/ {
        alias /var/www/wordbuddy/assets/;
        access_log off;
        expires 7d;
    }

    location /download/ {
        alias /opt/hotwords/server/public/app/;
        types { application/vnd.android.package-archive apk; }
        default_type application/vnd.android.package-archive;
        add_header Content-Disposition 'attachment';
        add_header Cache-Control "public, max-age=300";
    }
"""


def main() -> None:
    password = os.environ.get("HOTWORDS_SSH_PASSWORD")
    if not password:
        raise SystemExit("HOTWORDS_SSH_PASSWORD required")
    if not (LOCAL_SITE / "index.html").is_file():
        raise SystemExit("missing site/index.html")

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=password, timeout=30)
    sftp = c.open_sftp()

    def mkdir(path: str) -> None:
        try:
            sftp.stat(path)
        except FileNotFoundError:
            sftp.mkdir(path)

    mkdir(REMOTE_ROOT)
    mkdir(f"{REMOTE_ROOT}/assets")

    files = [p for p in LOCAL_SITE.rglob("*") if p.is_file()]
    for local in files:
        rel = local.relative_to(LOCAL_SITE).as_posix()
        remote = f"{REMOTE_ROOT}/{rel}"
        remote_dir = remote.rsplit("/", 1)[0]
        stdin, stdout, stderr = c.exec_command(f"mkdir -p {remote_dir}")
        stdout.channel.recv_exit_status()
        print(f"upload {rel}")
        sftp.put(str(local), remote)

    with sftp.open("/etc/nginx/sites-available/wordbuddy-landing", "w") as f:
        f.write(NGINX_LANDING)
    print("wrote /etc/nginx/sites-available/wordbuddy-landing")

    conf = "/etc/nginx/sites-available/wordbuddy.cc"
    with sftp.open(conf, "r") as f:
        text = f.read().decode("utf-8")
    if "WordBuddy landing (static)" not in text:
        marker = "    location / {\n        proxy_pass http://127.0.0.1:8787;"
        if marker not in text:
            raise SystemExit("unexpected wordbuddy.cc nginx layout")
        text = text.replace(marker, DOMAIN_SNIPPET + "\n" + marker, 1)
        with sftp.open(conf, "w") as f:
            f.write(text)
        print("patched wordbuddy.cc root locations")
    elif "location ~* \\.(html)$" not in text:
        insert_after = "        try_files /index.html =404;\n    }\n"
        html_loc = """
    location ~* \\.(html)$ {
        root /var/www/wordbuddy;
        try_files $uri =404;
    }
"""
        if insert_after in text:
            text = text.replace(insert_after, insert_after + html_loc, 1)
            with sftp.open(conf, "w") as f:
                f.write(text)
            print("added html locations to wordbuddy.cc")
        else:
            print("warn: could not insert html location into wordbuddy.cc")
    else:
        print("wordbuddy.cc already has landing locations")

    sftp.close()

    cmds = [
        "ln -sfn /etc/nginx/sites-available/wordbuddy-landing /etc/nginx/sites-enabled/wordbuddy-landing",
        "nginx -t",
        "systemctl reload nginx",
        "curl -sI http://127.0.0.1/ | head -10",
        "curl -s http://127.0.0.1/ | head -c 200; echo",
        "curl -sI -H 'Host: 39.96.67.128' http://127.0.0.1/product.html | head -8",
        "curl -sI -H 'Host: 39.96.67.128' http://127.0.0.1/agreement.html | head -8",
        "curl -sI -H 'Host: 39.96.67.128' http://127.0.0.1/about.html | head -8",
        "curl -sI http://127.0.0.1/download/WordBuddy-release.apk | head -12",
        "curl -s http://127.0.0.1/app/version",
    ]
    _, stdout, stderr = c.exec_command(" && ".join(cmds))
    print(stdout.read().decode("utf-8", "replace"))
    err = stderr.read().decode("utf-8", "replace").strip()
    if err:
        print("stderr:", err[:800])
    c.close()
    print("SITE DEPLOY OK")
    print(f"Open: http://{HOST}/")


if __name__ == "__main__":
    main()
