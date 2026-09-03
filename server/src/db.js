import path from 'node:path'
import { fileURLToPath } from 'node:url'
import dotenv from 'dotenv'
import pg from 'pg'

dotenv.config({ path: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../.env') })

const url = process.env.DATABASE_URL
if (!url) throw new Error('DATABASE_URL is required')

export const pool = new pg.Pool({
  connectionString: url,
  max: 10,
})

export function query(text, params) {
  return pool.query(text, params)
}

export async function ensureSchema() {
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT')
}
