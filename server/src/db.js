import path from 'node:path'
import { fileURLToPath } from 'node:url'
import dotenv from 'dotenv'
import pg from 'pg'

dotenv.config({ path: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../.env') })

const url = process.env.DATABASE_URL
if (!url) throw new Error('DATABASE_URL is required')

// Keep DATE columns as yyyy-MM-dd strings — JS Date + toISOString shifts by timezone.
pg.types.setTypeParser(1082, (value) => value)

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
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS session_version INTEGER NOT NULL DEFAULT 0`)
  await query(`UPDATE users SET session_version = 0 WHERE session_version IS NULL`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS shipping_name TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS shipping_phone TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS shipping_detail TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS gender TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS region TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS buddy_id TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS signature TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS alipay_account TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS wechat_account TEXT`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS invited_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL`)
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS invite_bound_at TIMESTAMPTZ`)
  await query(`CREATE UNIQUE INDEX IF NOT EXISTS users_buddy_id_unique ON users (buddy_id) WHERE buddy_id IS NOT NULL`)
  await query(`CREATE INDEX IF NOT EXISTS users_invited_by ON users (invited_by_user_id) WHERE invited_by_user_id IS NOT NULL`)
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
  await query(`
    CREATE TABLE IF NOT EXISTS word_homophones (
      id BIGSERIAL PRIMARY KEY,
      word_key TEXT NOT NULL,
      body TEXT NOT NULL,
      author_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
      like_count INTEGER NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE (word_key, author_user_id)
    )
  `)
  await query(`
    CREATE INDEX IF NOT EXISTS word_homophones_top
      ON word_homophones (word_key, like_count DESC, id DESC)
  `)
  await query(`
    CREATE TABLE IF NOT EXISTS word_homophone_likes (
      homophone_id BIGINT NOT NULL REFERENCES word_homophones (id) ON DELETE CASCADE,
      user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (homophone_id, user_id)
    )
  `)
  await query(`
    CREATE TABLE IF NOT EXISTS user_checkins (
      user_id BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
      total_points INTEGER NOT NULL DEFAULT 0,
      streak_days INTEGER NOT NULL DEFAULT 0,
      last_checkin_date DATE,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query(`
    CREATE TABLE IF NOT EXISTS user_checkin_logs (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
      checkin_date DATE NOT NULL,
      streak_days INTEGER NOT NULL,
      points_earned INTEGER NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE (user_id, checkin_date)
    )
  `)
  await query('CREATE INDEX IF NOT EXISTS user_checkin_logs_date ON user_checkin_logs (checkin_date DESC)')
  await query('CREATE INDEX IF NOT EXISTS user_checkin_logs_user ON user_checkin_logs (user_id, checkin_date DESC)')
  await query('CREATE INDEX IF NOT EXISTS user_checkins_points ON user_checkins (total_points DESC)')
  await query(`
    CREATE TABLE IF NOT EXISTS points_ledger (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
      delta INTEGER NOT NULL,
      balance_after INTEGER NOT NULL,
      reason TEXT NOT NULL,
      ref_type TEXT,
      ref_id TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query('CREATE INDEX IF NOT EXISTS points_ledger_user ON points_ledger (user_id, created_at DESC)')
  await query(`
    CREATE TABLE IF NOT EXISTS gifts (
      id BIGSERIAL PRIMARY KEY,
      title TEXT NOT NULL,
      subtitle TEXT,
      cover_emoji TEXT NOT NULL DEFAULT '🎁',
      cover_color TEXT NOT NULL DEFAULT '#1B6CA8',
      category TEXT NOT NULL DEFAULT 'recommend',
      points_cost INTEGER NOT NULL DEFAULT 0,
      cash_fen INTEGER NOT NULL DEFAULT 0,
      original_price_fen INTEGER,
      points_offset_fen INTEGER,
      stock INTEGER NOT NULL DEFAULT -1,
      redeemed_count INTEGER NOT NULL DEFAULT 0,
      sort_order INTEGER NOT NULL DEFAULT 0,
      published BOOLEAN NOT NULL DEFAULT TRUE,
      need_address BOOLEAN NOT NULL DEFAULT FALSE,
      description TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query('CREATE INDEX IF NOT EXISTS gifts_published_sort ON gifts (published, sort_order ASC, id DESC)')
  // stock: -1 = unlimited; never allow values below -1 (guards against bad admin input / race leftovers)
  await query(`UPDATE gifts SET stock = -1 WHERE stock < -1`)
  await query(`
    DO $$ BEGIN
      ALTER TABLE gifts ADD CONSTRAINT gifts_stock_min CHECK (stock >= -1);
    EXCEPTION WHEN duplicate_object THEN NULL;
    END $$
  `)
  await query(`
    CREATE TABLE IF NOT EXISTS gift_orders (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
      gift_id BIGINT REFERENCES gifts (id) ON DELETE SET NULL,
      gift_title TEXT NOT NULL,
      cover_emoji TEXT,
      cover_color TEXT,
      points_spent INTEGER NOT NULL DEFAULT 0,
      cash_fen INTEGER NOT NULL DEFAULT 0,
      status TEXT NOT NULL DEFAULT 'completed',
      address_name TEXT,
      address_phone TEXT,
      address_detail TEXT,
      remark TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query('CREATE INDEX IF NOT EXISTS gift_orders_user ON gift_orders (user_id, created_at DESC)')
  await query('CREATE INDEX IF NOT EXISTS gift_orders_created ON gift_orders (created_at DESC)')
  await query(`
    CREATE TABLE IF NOT EXISTS withdrawals (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
      channel TEXT NOT NULL,
      account TEXT NOT NULL,
      amount_fen INTEGER NOT NULL DEFAULT 1,
      points_spent INTEGER NOT NULL DEFAULT 0,
      status TEXT NOT NULL DEFAULT 'pending',
      provider_trade_no TEXT,
      error_message TEXT,
      remark TEXT,
      sandbox BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query('CREATE INDEX IF NOT EXISTS withdrawals_user ON withdrawals (user_id, created_at DESC)')
  await query('CREATE INDEX IF NOT EXISTS withdrawals_status ON withdrawals (status, created_at DESC)')
  await query(`
    CREATE TABLE IF NOT EXISTS mnemonic_images (
      id BIGSERIAL PRIMARY KEY,
      word_key TEXT NOT NULL,
      provider TEXT NOT NULL,
      image BYTEA NOT NULL,
      prompt TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `)
  await query(`ALTER TABLE mnemonic_images ADD COLUMN IF NOT EXISTS meaning_key TEXT NOT NULL DEFAULT ''`)
  await query('DROP INDEX IF EXISTS mnemonic_images_word_provider')
  await query(
    'CREATE UNIQUE INDEX IF NOT EXISTS mnemonic_images_word_meaning ON mnemonic_images (word_key, provider, meaning_key)',
  )
}
