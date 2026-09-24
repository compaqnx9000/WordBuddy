# 你的词搭子 · WordBuddy Web

Vite + React + TypeScript 桌面端 Web 应用，与 Android App 共用后端 API。

## 环境要求

- Node.js 20+
- npm 10+

## 本地开发

```bash
cd web
npm i
npm run dev
```

浏览器打开终端提示的地址（默认 `http://localhost:5173`）。

开发服务器会将：

- `/api/backend/*` 代理到 `http://39.96.67.128:8787`
- `/api/youdao/*` 代理到有道词典
- `/api/baidu/*` 代理到百度翻译建议
- `/api/datamuse/*` 代理到 Datamuse（近义/反义）

## 构建

```bash
npm run build
npm run preview
```

## 功能说明

| 路由 | 说明 |
|------|------|
| `/` | 查词（可不登录） |
| `/login` | 手机号验证码 / 密码 / 注册 |
| `/notebook` | 生词本 |
| `/notebook/card` | 卡片复习 |
| `/shorts` | 短视频 |
| `/podcast` | NPR / LBC / China Plus 直播 |
| `/me` | 个人中心、签到、积分展示 |

登录态保存在 `localStorage` 键 `wordbuddy_token`。

Web 版 intentionally 不包含：开屏广告、激励视频、生物识别、微信/支付宝原生登录与支付（请使用 App）。
