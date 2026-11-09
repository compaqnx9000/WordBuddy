import { pool, query } from './db.js'

const SETTING_KEY = 'level_rules'
const MIGRATION_KEY = 'user_level_one_based'
const MAKEUP_SYNC_KEY = 'makeup_per_day_equals_level'

/** Display / storage levels are 1–8 (was historically 0–7). */
export const MIN_LEVEL = 1
export const MAX_LEVEL = 8

/** One calendar day counts only after this much foreground use. */
const DEFAULT_MINUTES = 30

const DEFAULT_LEVELS = [
  { level: 1, minDays: 0, videoDailyLimit: 20, checkInBonusPercent: 0, makeupPerDay: 1, mallDiscountPercent: 0 },
  { level: 2, minDays: 30, videoDailyLimit: 22, checkInBonusPercent: 0, makeupPerDay: 2, mallDiscountPercent: 0 },
  { level: 3, minDays: 100, videoDailyLimit: 24, checkInBonusPercent: 0, makeupPerDay: 3, mallDiscountPercent: 0 },
  { level: 4, minDays: 365, videoDailyLimit: 26, checkInBonusPercent: 20, makeupPerDay: 4, mallDiscountPercent: 0 },
  { level: 5, minDays: 730, videoDailyLimit: 28, checkInBonusPercent: 50, makeupPerDay: 5, mallDiscountPercent: 0 },
  { level: 6, minDays: 1095, videoDailyLimit: 30, checkInBonusPercent: 100, makeupPerDay: 6, mallDiscountPercent: 0 },
  { level: 7, minDays: 1460, videoDailyLimit: 32, checkInBonusPercent: 150, makeupPerDay: 7, mallDiscountPercent: 10 },
  { level: 8, minDays: 1825, videoDailyLimit: 34, checkInBonusPercent: 200, makeupPerDay: 8, mallDiscountPercent: 10 },
]

export function clampUserLevel(value, fallback = MIN_LEVEL) {
  const n = Math.trunc(Number(value))
  if (!Number.isFinite(n)) return fallback
  return Math.min(MAX_LEVEL, Math.max(MIN_LEVEL, n))
}

function todayShanghai(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now)
}

function clampInt(value, min, max, fallback) {
  const n = Math.trunc(Number(value))
  if (!Number.isFinite(n)) return fallback
  return Math.min(max, Math.max(min, n))
}

export function defaultLevelRules() {
  return {
    minutesPerValidDay: DEFAULT_MINUTES,
    levels: DEFAULT_LEVELS.map((row) => ({ ...row })),
  }
}

/** Old installs stored Lv0–Lv7; remap rows to Lv1–Lv8 once. */
function remapLegacyLevels(incoming) {
  if (!Array.isArray(incoming) || !incoming.length) return incoming
  const nums = incoming.map((row) => Math.trunc(Number(row?.level)))
  const hasZero = nums.includes(0)
  const hasEight = nums.includes(8)
  if (!hasZero || hasEight) return incoming
  return incoming.map((row) => ({
    ...row,
    level: Math.trunc(Number(row?.level)) + 1,
  }))
}

export function normalizeLevelRules(raw) {
  const base = defaultLevelRules()
  const minutesPerValidDay = clampInt(raw?.minutesPerValidDay, 5, 180, base.minutesPerValidDay)
  const incoming = remapLegacyLevels(Array.isArray(raw?.levels) ? raw.levels : [])
  const byLevel = new Map(incoming.map((row) => [Math.trunc(Number(row?.level)), row]))
  const levels = base.levels.map((fallback) => {
    const row = byLevel.get(fallback.level) || {}
    return {
      level: fallback.level,
      minDays: clampInt(row.minDays, 0, 20000, fallback.minDays),
      videoDailyLimit: clampInt(row.videoDailyLimit, 1, 100, fallback.videoDailyLimit),
      checkInBonusPercent: clampInt(row.checkInBonusPercent, 0, 500, fallback.checkInBonusPercent),
      makeupPerDay: clampInt(row.makeupPerDay, 0, 10, fallback.makeupPerDay),
      mallDiscountPercent: clampInt(row.mallDiscountPercent, 0, 90, fallback.mallDiscountPercent),
    }
  })
  for (let i = 1; i < levels.length; i += 1) {
    if (levels[i].minDays < levels[i - 1].minDays) {
      return { ok: false, error: `Lv${levels[i].level} 的有效天数不能小于 Lv${levels[i - 1].level}` }
    }
    if (levels[i].videoDailyLimit < levels[i - 1].videoDailyLimit) {
      return { ok: false, error: `Lv${levels[i].level} 的看视频次数不能少于 Lv${levels[i - 1].level}` }
    }
    if (levels[i].checkInBonusPercent < levels[i - 1].checkInBonusPercent) {
      return { ok: false, error: `Lv${levels[i].level} 的签到加成不能低于 Lv${levels[i - 1].level}` }
    }
  }
  if (levels[0].minDays !== 0) {
    return { ok: false, error: 'Lv1 的有效天数必须是 0' }
  }
  return { ok: true, rules: { minutesPerValidDay, levels } }
}

export async function getLevelRules() {
  const row = (
    await query('SELECT value FROM app_settings WHERE key = $1', [SETTING_KEY])
  ).rows[0]
  if (!row?.value) return defaultLevelRules()
  try {
    const parsed = normalizeLevelRules(JSON.parse(row.value))
    return parsed.ok ? parsed.rules : defaultLevelRules()
  } catch {
    return defaultLevelRules()
  }
}

export async function setLevelRules(raw) {
  const parsed = normalizeLevelRules(raw)
  if (!parsed.ok) return parsed
  await query(
    `INSERT INTO app_settings (key, value, updated_at)
     VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()`,
    [SETTING_KEY, JSON.stringify(parsed.rules)],
  )
  return { ok: true, ...parsed.rules }
}

/**
 * One-time: bump stored user_level 0–7 → 1–8 and rewrite saved level_rules.
 */
export async function migrateUserLevelsToOneBased() {
  const flag = (
    await query('SELECT value FROM app_settings WHERE key = $1', [MIGRATION_KEY])
  ).rows[0]
  if (String(flag?.value || '') === '1') return { ok: true, skipped: true }

  // Historical storage was 0–7; bump once to 1–8.
  await query(
    `UPDATE users
     SET user_level = LEAST($2::int, GREATEST($1::int, COALESCE(user_level, 0) + 1))`,
    [MIN_LEVEL, MAX_LEVEL],
  )

  const rulesRow = (
    await query('SELECT value FROM app_settings WHERE key = $1', [SETTING_KEY])
  ).rows[0]
  if (rulesRow?.value) {
    try {
      const parsed = normalizeLevelRules(JSON.parse(rulesRow.value))
      if (parsed.ok) {
        await query(
          `UPDATE app_settings SET value = $2, updated_at = now() WHERE key = $1`,
          [SETTING_KEY, JSON.stringify(parsed.rules)],
        )
      }
    } catch {
      /* ignore bad JSON; defaults apply on read */
    }
  }

  await query(
    `INSERT INTO app_settings (key, value, updated_at)
     VALUES ($1, '1', now())
     ON CONFLICT (key) DO UPDATE SET value = '1', updated_at = now()`,
    [MIGRATION_KEY],
  )
  return { ok: true, migrated: true }
}

/** One-time: daily makeup allowance is the level number (Lv1 → 1 … Lv8 → 8). */
export async function syncMakeupPerDayToLevel() {
  const flag = (
    await query('SELECT value FROM app_settings WHERE key = $1', [MAKEUP_SYNC_KEY])
  ).rows[0]
  if (String(flag?.value || '') === '1') return { ok: true, skipped: true }

  const rules = await getLevelRules()
  const levels = rules.levels.map((row) => ({ ...row, makeupPerDay: row.level }))
  const saved = await setLevelRules({ ...rules, levels })
  if (!saved.ok) return saved

  await query(
    `INSERT INTO app_settings (key, value, updated_at)
     VALUES ($1, '1', now())
     ON CONFLICT (key) DO UPDATE SET value = '1', updated_at = now()`,
    [MAKEUP_SYNC_KEY],
  )
  return { ok: true, migrated: true }
}

export function perkForDays(validDays, rules) {
  const days = Math.max(0, Math.trunc(Number(validDays) || 0))
  const levels = [...(rules?.levels || defaultLevelRules().levels)].sort((a, b) => b.minDays - a.minDays)
  const current = levels.find((row) => days >= row.minDays) || levels[levels.length - 1]
  const ordered = [...(rules?.levels || [])].sort((a, b) => a.level - b.level)
  const next = ordered.find((row) => row.minDays > days) || null
  return {
    level: clampUserLevel(current.level),
    minDays: current.minDays,
    videoDailyLimit: current.videoDailyLimit,
    checkInBonusPercent: current.checkInBonusPercent,
    makeupPerDay: current.makeupPerDay,
    mallDiscountPercent: current.mallDiscountPercent,
    validDays: days,
    nextLevel: next ? next.level : null,
    nextMinDays: next ? next.minDays : null,
    minutesPerValidDay: rules?.minutesPerValidDay || DEFAULT_MINUTES,
  }
}

export function applyCheckInBonus(basePoints, percent) {
  const base = Math.max(0, Math.trunc(Number(basePoints) || 0))
  const pct = Math.max(0, Math.trunc(Number(percent) || 0))
  if (pct <= 0) return base
  return Math.max(base, Math.round((base * (100 + pct)) / 100))
}

export function discountedPoints(pointsCost, percent) {
  const cost = Math.max(0, Math.trunc(Number(pointsCost) || 0))
  const pct = clampInt(percent, 0, 90, 0)
  if (pct <= 0) return cost
  return Math.max(0, Math.round((cost * (100 - pct)) / 100))
}

export async function countValidDays(userId) {
  const row = (
    await query(
      `SELECT count(*)::int AS n
       FROM user_activity_days
       WHERE user_id = $1 AND counted`,
      [userId],
    )
  ).rows[0]
  return Math.max(0, Number(row?.n || 0))
}

export async function syncUserLevel(userId) {
  const rules = await getLevelRules()
  const validDays = await countValidDays(userId)
  const perk = perkForDays(validDays, rules)
  await query('UPDATE users SET user_level = $1 WHERE id = $2', [perk.level, userId])
  return perk
}

export async function getUserPerks(userId) {
  return syncUserLevel(userId)
}

/**
 * Credit foreground time for today. A single report cannot exceed the real gap
 * since the previous report, and cannot exceed 90 seconds.
 */
export async function recordActiveMs(userId, activeMs) {
  const uid = Number(userId)
  const requested = Math.trunc(Number(activeMs) || 0)
  if (!Number.isFinite(uid) || uid <= 0) return { ok: false, error: '无效用户' }
  const rules = await getLevelRules()
  const threshold = rules.minutesPerValidDay * 60 * 1000
  const today = todayShanghai()
  const client = await pool.connect()
  let activeMsToday = 0
  let countedToday = false
  let creditedMs = 0
  try {
    await client.query('BEGIN')
    const row = (
      await client.query(
        `SELECT active_ms, updated_at, counted
         FROM user_activity_days
         WHERE user_id = $1 AND activity_date = $2::date
         FOR UPDATE`,
        [uid, today],
      )
    ).rows[0]
    let credit = Math.min(Math.max(0, requested), 90_000)
    if (row?.updated_at) {
      const elapsed = Math.max(0, Date.now() - new Date(row.updated_at).getTime())
      credit = Math.min(credit, elapsed)
    }
    creditedMs = credit
    activeMsToday = Math.min(24 * 60 * 60 * 1000, Number(row?.active_ms || 0) + credit)
    countedToday = Boolean(row?.counted) || activeMsToday >= threshold
    await client.query(
      `INSERT INTO user_activity_days (user_id, activity_date, active_ms, counted, updated_at)
       VALUES ($1, $2::date, $3, $4, now())
       ON CONFLICT (user_id, activity_date) DO UPDATE SET
         active_ms = EXCLUDED.active_ms,
         counted = EXCLUDED.counted,
         updated_at = now()`,
      [uid, today, activeMsToday, countedToday],
    )
    await client.query('COMMIT')
  } catch (error) {
    try {
      await client.query('ROLLBACK')
    } catch {
      /* ignore */
    }
    throw error
  } finally {
    client.release()
  }
  const perk = await syncUserLevel(uid)
  return {
    ok: true,
    ...perk,
    activeMsToday,
    countedToday,
    creditedMs,
  }
}

function presencePlatform(value) {
  const platform = String(value || '').trim().toLowerCase()
  if (platform === 'ios' || platform === 'android') return platform
  if (['web', 'windows', 'macos', 'mac os', 'linux', 'pc'].includes(platform)) return 'pc'
  return 'unknown'
}

/** One row per user, day, and platform. A row means they opened the app that day. */
export async function recordAppPresence(userId, platform, activeMs) {
  const uid = Number(userId)
  if (!Number.isFinite(uid) || uid <= 0) return
  const bucket = presencePlatform(platform)
  const credit = Math.min(24 * 60 * 60 * 1000, Math.max(0, Math.trunc(Number(activeMs) || 0)))
  await query(
    `INSERT INTO app_presence_days (user_id, activity_date, platform, active_ms, updated_at)
     VALUES ($1, $2::date, $3, $4, now())
     ON CONFLICT (user_id, activity_date, platform) DO UPDATE SET
       active_ms = LEAST(86400000, app_presence_days.active_ms + EXCLUDED.active_ms),
       updated_at = now()`,
    [uid, todayShanghai(), bucket, credit],
  )
}
