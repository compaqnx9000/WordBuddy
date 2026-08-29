# HotWords H5

手机版 HotWords 的浏览器端移植，便于在电脑上快速联调 UI / 查词流程。

## 启动

```bash
cd h5
npm install
npm run dev
```

浏览器打开终端里提示的本地地址（默认 `http://localhost:5173`）。手机同网可用局域网 IP 访问。

## 已对齐能力

- 首页查词（有道 + 百度 sug 兜底，Vite 代理）
- 生词本（遮挡释义、筛选、排序、搜索）
- 卡片模式（衬线纸横幅、音标、例句轮播、拆分发音、自动播放 / 乱序）
- 我：导入导出 JSON（与 App 备份格式 v1 兼容，不含 IPA/图片）
- 设置：主题、字体、口音、翻页朗读、循环、间隔
- 数据持久化：`localStorage`

## 说明

词典 / TTS / Datamuse / Pollinations 均走 `vite.config.ts` 代理，**开发服务器**下可用；纯静态托管需自备反代。
