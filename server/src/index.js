import path from 'node:path'
import { fileURLToPath } from 'node:url'
import cors from 'cors'
import dotenv from 'dotenv'
import express from 'express'
import { ensureSchema } from './db.js'
import { router } from './routes.js'

dotenv.config({ path: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../.env') })

if (!process.env.JWT_SECRET) {
  console.error('JWT_SECRET is required')
  process.exit(1)
}

const app = express()
app.use(cors())
app.use(express.json({ limit: '1mb' }))
app.use(router)

app.use((err, _req, res, _next) => {
  console.error(err)
  res.status(500).json({ error: err.message || 'server error' })
})

const port = Number(process.env.PORT) || 8787
ensureSchema()
  .then(() => {
    app.listen(port, '0.0.0.0', () => {
      console.log(`HotWords API listening on http://0.0.0.0:${port}`)
    })
  })
  .catch((error) => {
    console.error(error)
    process.exit(1)
  })
