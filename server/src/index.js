import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import cors from 'cors'
import dotenv from 'dotenv'
import express from 'express'
import { ensureSuperAdmin } from './auth.js'
import { ensureSchema } from './db.js'
import { ensureGiftSeed } from './gifts.js'
import { purgeDueDeletions } from './deletion.js'
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
fs.mkdirSync(path.resolve(root, '../uploads/videos'), { recursive: true })
fs.mkdirSync(path.resolve(root, '../uploads/gifts'), { recursive: true })
fs.mkdirSync(path.resolve(root, '../public/app'), { recursive: true })
app.use(cors())
app.use(express.json({ limit: '4mb' }))
app.use(express.urlencoded({ extended: false }))
app.use(
  '/uploads',
  express.static(path.resolve(root, '../uploads'), {
    etag: true,
    maxAge: '1h',
    setHeaders(res, filePath) {
      if (filePath.includes(`${path.sep}videos${path.sep}`) || filePath.endsWith('.mp4')) {
        res.setHeader('Cache-Control', 'public, max-age=86400')
        res.setHeader('Content-Type', 'video/mp4')
      } else if (filePath.includes(`${path.sep}avatars${path.sep}`)) {
        res.setHeader('Cache-Control', 'no-store')
      }
    },
  }),
)
// App update channel: /app/version.json + APK downloads
app.use(
  '/app',
  express.static(path.resolve(root, '../public/app'), {
    etag: true,
    maxAge: '5m',
    setHeaders(res, filePath) {
      if (filePath.endsWith('.json')) {
        res.setHeader('Cache-Control', 'no-cache')
      } else if (filePath.endsWith('.apk')) {
        res.setHeader('Content-Type', 'application/vnd.android.package-archive')
        res.setHeader('Cache-Control', 'public, max-age=300')
      }
    },
  }),
)
// WeChat iOS login checks this file over HTTPS at wordbuddy.cc.
const appleAppSiteAssociation = {
  applinks: {
    apps: [],
    details: [
      'HXN67QW4CL.com.hotgis.wordbuddy.ios',
      'Y9FT29T4RU.com.hotgis.wordbuddy.ios',
      'HXN67QW4CL.com.hotgis.wordbuddy',
      'Y9FT29T4RU.com.hotgis.wordbuddy',
    ].map((appID) => ({
      appID,
      paths: ['/wordbuddy/*', '/wordbuddy/', '*'],
    })),
  },
}
function sendAppleAppSiteAssociation(_req, res) {
  res.set('Content-Type', 'application/json')
  res.set('Cache-Control', 'no-cache')
  res.json(appleAppSiteAssociation)
}
app.get('/.well-known/apple-app-site-association', sendAppleAppSiteAssociation)
app.get('/apple-app-site-association', sendAppleAppSiteAssociation)

app.use('/admin/api', adminRouter)
app.use('/admin', express.static(path.resolve(root, '../public/admin')))
app.get('/admin', (_req, res) => {
  res.sendFile(path.resolve(root, '../public/admin/index.html'))
})
// Invite landing: /i/{buddyId}
app.get('/i/:code', (req, res) => {
  res.sendFile(path.resolve(root, '../public/invite/index.html'))
})
app.get('/invite/page/:code', (req, res) => {
  res.sendFile(path.resolve(root, '../public/invite/index.html'))
})
app.use(router)

app.use((err, _req, res, _next) => {
  console.error(err)
  res.status(500).json({ error: err.message || 'server error' })
})

const port = Number(process.env.PORT) || 8787
ensureSchema()
  .then(() => ensureGiftSeed())
  .then(() => ensureSuperAdmin())
  .then(() => purgeDueDeletions())
  .then(() => {
    app.listen(port, '0.0.0.0', () => {
      console.log(`HotWords API listening on http://0.0.0.0:${port}`)
      console.log(`Admin console: http://127.0.0.1:${port}/admin`)
    })
    setInterval(() => {
      purgeDueDeletions().catch((error) => console.error('[deletion-purge]', error))
    }, 30 * 60 * 1000)
  })
  .catch((error) => {
    console.error(error)
    process.exit(1)
  })
