# HotWords API

本机 Node + PostgreSQL。系统词书用 `slug`（`zhongkao` / `gaokao` / `cet4` / `cet6`），之后加 PET / 托福 / 雅思只需再插入一条 catalog 并导入词。

## 准备

1. 复制 `.env.example` 为 `.env`，改成你的 Postgres 账号密码。
2. `npm install`
3. `npm run db:init`
4. `npm run db:seed`（导入中考 / 高考 / 四级 / 六级 enrichment）
5. `npm run dev`（监听 `0.0.0.0:8787`，手机同一 Wi-Fi 可访问）
6. 管理后台：浏览器打开 `http://127.0.0.1:8787/admin`，账号见 `.env` 的 `ADMIN_USERNAME` / `ADMIN_PASSWORD`

开发登录：`SMS_PROVIDER=stub` 且 `SMS_SKIP_VERIFY=true`，任意 6 位验证码即可。

正式短信（阿里云）：
1. 在 `.env` 填写 `ALIYUN_ACCESS_KEY_ID` / `ALIYUN_ACCESS_KEY_SECRET`
2. 填写已审核通过的 `ALIYUN_SMS_SIGN_NAME`（签名）和 `ALIYUN_SMS_TEMPLATE_CODE`（模板 CODE）
3. 模板变量名默认 `code`（可用 `ALIYUN_SMS_TEMPLATE_PARAM_KEY` 改）
4. `SMS_PROVIDER=aliyun`，`SMS_SKIP_VERIFY=false`
5. 重启 `npm run dev` / `npm start`

App 默认连 `http://192.168.1.3:8787`（见 `app/build.gradle.kts` 的 `API_BASE_URL`）。IP 变了改这一处再打包。
