import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/backend': {
        target: 'http://39.96.67.128:8787',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/backend/, ''),
      },
      '/api/youdao': {
        target: 'https://dict.youdao.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/youdao/, ''),
      },
      '/api/baidu': {
        target: 'https://fanyi.baidu.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/baidu/, ''),
      },
      '/api/datamuse': {
        target: 'https://api.datamuse.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/datamuse/, ''),
      },
    },
  },
})
