import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import cors from 'cors'
import dotenv from 'dotenv'
import express from 'express'
import { ensureSuperAdmin } from './auth.js'
import { ensureSchema } from './db.js'
import { adminRouter } from './admin.js'
import { router } from './routes.js'

const root = path.dirname(fileURLToPath(import.meta.url))
dotenv.config({ path: path.resolve(root, '../.env') })

if (!process.env.JWT_SECRET) {
  console.error('JWT_SECRET is required')
  process.exit(1)
}

const app = express()
fs.mkdirSync(path.resolve(root, '../uploads/avatars'), { recursive: true })
app.use(cors())
app.use(express.json({ limit: '4mb' }))
app.use(
  '/uploads',
  express.static(path.resolve(root, '../uploads'), {
    etag: false,
    maxAge: 0,
    setHeaders(res) {
      res.setHeader('Cache-Control', 'no-store')
    },
  }),
)
app.use('/admin/api', adminRouter)
app.use('/admin', express.static(path.resolve(root, '../public/admin')))
app.get('/admin', (_req, res) => {
  res.sendFile(path.resolve(root, '../public/admin/index.html'))
})
app.use(router)

app.use((err, _req, res, _next) => {
  console.error(err)
  res.status(500).json({ error: err.message || 'server error' })
})

const port = Number(process.env.PORT) || 8787
ensureSchema()
  .then(() => ensureSuperAdmin())
  .then(() => {
    app.listen(port, '0.0.0.0', () => {
      console.log(`HotWords API listening on http://0.0.0.0:${port}`)
      console.log(`Admin console: http://127.0.0.1:${port}/admin`)
    })
  })
  .catch((error) => {
    console.error(error)
    process.exit(1)
  })
