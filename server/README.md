# HotWords API

本机 Node + PostgreSQL。系统词书用 `slug`（`zhongkao` / `gaokao` / `cet4` / `cet6`），之后加 PET / 托福 / 雅思只需再插入一条 catalog 并导入词。

## 准备

1. 复制 `.env.example` 为 `.env`，改成你的 Postgres 账号密码。
2. `npm install`
3. `npm run db:init`
4. `npm run db:seed`（导入中考 / 高考 / 四级 / 六级 enrichment）
5. `npm run dev`（监听 `0.0.0.0:8787`，手机同一 Wi-Fi 可访问）

开发登录：`SMS_SKIP_VERIFY=true`，任意 6 位验证码即可。接入短信后改 `SMS_PROVIDER` 并关掉 skip。

App 默认连 `http://192.168.1.3:8787`（见 `app/build.gradle.kts` 的 `API_BASE_URL`）。IP 变了改这一处再打包。
