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
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ')
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_method TEXT')
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ')
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS login_count INTEGER NOT NULL DEFAULT 0`)
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT')
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS last_device_label TEXT')
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS last_device_platform TEXT')
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS last_ip TEXT')
  await query('ALTER TABLE users ADD COLUMN IF NOT EXISTS last_ip_location TEXT')
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS user_level INTEGER NOT NULL DEFAULT 0`)
  await query(`ALTER TABLE users ALTER COLUMN user_level SET DEFAULT 0`)
  await query(`UPDATE users SET user_level = 0 WHERE user_level IS NULL OR user_level < 0 OR user_level > 7`)
  await query(`
    CREATE TABLE IF NOT EXISTS login_events (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
      phone TEXT,
      method TEXT NOT NULL,
      success BOOLEAN NOT NULL DEFAULT TRUE,
      ip TEXT,
      user_agent TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query('ALTER TABLE login_events ADD COLUMN IF NOT EXISTS device_platform TEXT')
  await query('ALTER TABLE login_events ADD COLUMN IF NOT EXISTS device_brand TEXT')
  await query('ALTER TABLE login_events ADD COLUMN IF NOT EXISTS device_model TEXT')
  await query('ALTER TABLE login_events ADD COLUMN IF NOT EXISTS device_label TEXT')
  await query('ALTER TABLE login_events ADD COLUMN IF NOT EXISTS ip_location TEXT')
  await query('CREATE INDEX IF NOT EXISTS login_events_created ON login_events (created_at DESC)')
  await query('CREATE INDEX IF NOT EXISTS login_events_user ON login_events (user_id, created_at DESC)')
  await query(`
    CREATE TABLE IF NOT EXISTS user_devices (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
      device_key TEXT NOT NULL,
      platform TEXT,
      brand TEXT,
      model TEXT,
      label TEXT NOT NULL,
      os_version TEXT,
      app_version TEXT,
      last_ip TEXT,
      last_ip_location TEXT,
      first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      login_count INTEGER NOT NULL DEFAULT 0,
      UNIQUE (user_id, device_key)
    )
  `)
  await query('CREATE INDEX IF NOT EXISTS user_devices_user_seen ON user_devices (user_id, last_seen_at DESC)')
  await query(`
    CREATE TABLE IF NOT EXISTS password_events (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT REFERENCES users (id) ON DELETE CASCADE,
      reason TEXT NOT NULL,
      ip TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query(`
    CREATE TABLE IF NOT EXISTS admins (
      id BIGSERIAL PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      last_login_at TIMESTAMPTZ
    )
  `)
  await query(`
    CREATE TABLE IF NOT EXISTS admin_audit (
      id BIGSERIAL PRIMARY KEY,
      admin_id BIGINT REFERENCES admins (id) ON DELETE SET NULL,
      action TEXT NOT NULL,
      target_type TEXT,
      target_id TEXT,
      detail JSONB,
      ip TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query('CREATE INDEX IF NOT EXISTS admin_audit_created ON admin_audit (created_at DESC)')
}
