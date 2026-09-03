import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import dotenv from 'dotenv'
import pg from 'pg'

const root = path.dirname(fileURLToPath(import.meta.url))
dotenv.config({ path: path.resolve(root, '../.env') })

const url = process.env.DATABASE_URL
if (!url) {
  console.error('Missing DATABASE_URL')
  process.exit(1)
}

const parsed = new URL(url)
const dbName = parsed.pathname.replace(/^\//, '') || 'hotwords'
const adminUrl = new URL(url)
adminUrl.pathname = '/postgres'

async function main() {
  const admin = new pg.Client({ connectionString: adminUrl.toString() })
  await admin.connect()
  const exists = await admin.query('SELECT 1 FROM pg_database WHERE datname = $1', [dbName])
  if (exists.rowCount === 0) {
    if (!/^[a-zA-Z0-9_]+$/.test(dbName)) {
      throw new Error(`unsafe database name: ${dbName}`)
    }
    await admin.query(`CREATE DATABASE ${dbName}`)
    console.log(`Created database ${dbName}`)
  } else {
    console.log(`Database ${dbName} already exists`)
  }
  await admin.end()

  const client = new pg.Client({ connectionString: url })
  await client.connect()
  const sql = fs.readFileSync(path.join(root, 'schema.sql'), 'utf8')
  await client.query(sql)
  await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT')
  console.log('Schema applied')
  await client.end()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
