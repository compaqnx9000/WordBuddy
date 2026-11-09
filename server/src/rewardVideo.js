import { pool, query } from './db.js'
import crypto from 'node:crypto'
import { todayShanghai } from './checkin.js'
import { adjustPoints, getPointsBalance } from './points.js'
import { getUserPerks } from './levels.js'

export const REWARD_VIDEO_DAILY_LIMIT = 20
/** Default minimum interval between two reward-video claims. Live value is in app_settings. */
export const REWARD_VIDEO_COOLDOWN_SEC = 5 * 60
const REWARD_VIDEO_COOLDOWN_MAX = 24 * 60 * 60
const REWARD_VIDEO_POINTS_MAX = 100000
const REWARD_VIDEO_POINTS_DEFAULT = 1500
const SETTING_KEY = 'reward_video_points'
const COOLDOWN_KEY = 'reward_video_cooldown_sec'
const GROMORE_KEY = 'gromore_reward_security_key'

function clampPoints(value) {
  const n = Math.trunc(Number(value))
  if (!Number.isFinite(n)) return null
  return Math.min(REWARD_VIDEO_POINTS_MAX, Math.max(1, n))
}

function clampCooldown(value) {
  const n = Math.trunc(Number(value))
  if (!Number.isFinite(n)) return null
  return Math.min(REWARD_VIDEO_COOLDOWN_MAX, Math.max(0, n))
}

async function readSetting(key, client = null) {
  const runner = client ? client.query.bind(client) : query
  const row = (await runner('SELECT value FROM app_settings WHERE key = $1', [key])).rows[0]
  return row?.value
}

async function writeSetting(key, value) {
  await query(
    `INSERT INTO app_settings (key, value, updated_at)
     VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET
       value = EXCLUDED.value,
       updated_at = now()`,
    [key, String(value)],
  )
}

export async function getRewardVideoPoints() {
  return clampPoints(await readSetting(SETTING_KEY)) ?? REWARD_VIDEO_POINTS_DEFAULT
}

export async function getRewardVideoCooldownSec(client = null) {
  return clampCooldown(await readSetting(COOLDOWN_KEY, client)) ?? REWARD_VIDEO_COOLDOWN_SEC
}

export async function setRewardVideoConfig({ pointsPerWatch, cooldownSec }) {
  const n = clampPoints(pointsPerWatch)
  if (n == null) return { ok: false, error: `搭币须为 1～${REWARD_VIDEO_POINTS_MAX} 的整数` }
  const hasCooldown = cooldownSec !== undefined && cooldownSec !== null && cooldownSec !== ''
  const cooldown = hasCooldown ? clampCooldown(cooldownSec) : await getRewardVideoCooldownSec()
  if (cooldown == null) {
    return { ok: false, error: `间隔须为 0～${REWARD_VIDEO_COOLDOWN_MAX} 秒的整数` }
  }
  await writeSetting(SETTING_KEY, n)
  if (hasCooldown) await writeSetting(COOLDOWN_KEY, cooldown)
  return {
    ok: true,
    pointsPerWatch: n,
    cooldownSec: cooldown,
    dailyLimit: REWARD_VIDEO_DAILY_LIMIT,
  }
}

function formatInterval(sec) {
  const n = Math.max(0, Math.trunc(Number(sec) || 0))
  if (n >= 60 && n % 60 === 0) return `${n / 60} 分钟`
  if (n >= 60) return `${Math.floor(n / 60)} 分 ${n % 60} 秒`
  return `${n} 秒`
}

async function usedToday(userId, today, client = null) {
  const runner = client ? client.query.bind(client) : query
  const row = (
    await runner(
      `SELECT count(*)::int AS n
       FROM reward_video_grants
       WHERE user_id = $1 AND grant_date = $2::date`,
      [userId, today],
    )
  ).rows[0]
  return Math.max(0, Number(row?.n || 0))
}

async function lastGrantAt(userId, client = null) {
  const runner = client ? client.query.bind(client) : query
  const row = (
    await runner(
      `SELECT created_at
       FROM reward_video_grants
       WHERE user_id = $1
       ORDER BY created_at DESC
       LIMIT 1`,
      [userId],
    )
  ).rows[0]
  return row?.created_at ? new Date(row.created_at) : null
}

function cooldownRemainingSec(lastAt, cooldownSec) {
  if (!lastAt || cooldownSec <= 0) return 0
  const elapsedMs = Date.now() - lastAt.getTime()
  return Math.max(0, Math.ceil((cooldownSec * 1000 - elapsedMs) / 1000))
}

export async function rewardVideoStatus(userId) {
  const today = todayShanghai()
  const [pointsPerWatch, cooldownSec] = await Promise.all([
    getRewardVideoPoints(),
    getRewardVideoCooldownSec(),
  ])
  const perks = await getUserPerks(userId)
  const dailyLimit = perks.videoDailyLimit
  const used = await usedToday(userId, today)
  const totalPoints = await getPointsBalance(userId)
  const remaining = Math.max(0, dailyLimit - used)
  // 当天次数用完后不再倒计时，没有下一次可看的视频。
  const lastAt = remaining > 0 ? await lastGrantAt(userId) : null
  const cooldownLeft = cooldownRemainingSec(lastAt, cooldownSec)
  return {
    pointsPerWatch,
    dailyLimit,
    usedToday: used,
    remaining,
    totalPoints,
    today,
    level: perks.level,
    cooldownSec,
    cooldownRemainingSec: cooldownLeft,
  }
}

export async function claimRewardVideo(userId) {
  const today = todayShanghai()
  const client = await pool.connect()
  try {
    await client.query('BEGIN')
    await client.query('SELECT pg_advisory_xact_lock($1)', [Number(userId)])
    const used = await usedToday(userId, today, client)
    const perks = await getUserPerks(userId)
    const dailyLimit = perks.videoDailyLimit
    if (used >= dailyLimit) {
      await client.query('ROLLBACK')
      const status = await rewardVideoStatus(userId)
      return { ok: false, error: `今日奖励视频已达 ${dailyLimit} 次`, status }
    }
    const lastAt = await lastGrantAt(userId, client)
    const cooldownSec = await getRewardVideoCooldownSec(client)
    const cooldownLeft = cooldownRemainingSec(lastAt, cooldownSec)
    if (cooldownLeft > 0) {
      await client.query('ROLLBACK')
      const status = await rewardVideoStatus(userId)
      const waitLabel =
        cooldownLeft >= 60
          ? `${Math.ceil(cooldownLeft / 60)} 分钟`
          : `${cooldownLeft} 秒`
      return {
        ok: false,
        error: `看视频领搭币需间隔 ${formatInterval(cooldownSec)}，请再等 ${waitLabel}`,
        status,
      }
    }
    const keyRow = (
      await client.query('SELECT value FROM app_settings WHERE key = $1', [GROMORE_KEY])
    ).rows[0]
    if (String(keyRow?.value || '').trim()) {
      const seen = (
        await client.query(
          'SELECT 1 FROM reward_video_callbacks WHERE user_id = $1 LIMIT 1',
          [userId],
        )
      ).rows[0]
      if (seen) {
        const consumed = (
          await client.query(
            `UPDATE reward_video_callbacks
             SET consumed_at = now()
             WHERE id = (
               SELECT id FROM reward_video_callbacks
               WHERE user_id = $1 AND reward_allowed AND consumed_at IS NULL
                 AND created_at > now() - interval '30 minutes'
               ORDER BY id
               LIMIT 1
               FOR UPDATE SKIP LOCKED
             )
             RETURNING id`,
            [userId],
          )
        ).rows[0]
        if (!consumed) {
          await client.query('ROLLBACK')
          const status = await rewardVideoStatus(userId)
          return { ok: false, error: '还没有收到这次视频的奖励确认', status }
        }
      }
    }
    const pointsRow = (
      await client.query('SELECT value FROM app_settings WHERE key = $1', [SETTING_KEY])
    ).rows[0]
    const points = clampPoints(pointsRow?.value) ?? REWARD_VIDEO_POINTS_DEFAULT
    const credited = await adjustPoints({
      userId,
      delta: points,
      reason: 'reward_video',
      refType: 'reward_video',
      refId: `${today}-${used + 1}`,
      client,
    })
    if (!credited.ok) {
      await client.query('ROLLBACK')
      return { ok: false, error: credited.error || '搭币发放失败' }
    }
    await client.query(
      `INSERT INTO reward_video_grants (user_id, grant_date, points)
       VALUES ($1, $2::date, $3)`,
      [userId, today, points],
    )
    await client.query('COMMIT')
    const status = await rewardVideoStatus(userId)
    return { ok: true, pointsEarned: points, status }
  } catch (error) {
    await client.query('ROLLBACK')
    throw error
  } finally {
    client.release()
  }
}

function clip(value, max) {
  const text = String(value ?? '').trim()
  return text ? text.slice(0, max) : null
}

function safeEqual(left, right) {
  const a = Buffer.from(String(left || ''), 'utf8')
  const b = Buffer.from(String(right || ''), 'utf8')
  if (a.length !== b.length || a.length === 0) return false
  return crypto.timingSafeEqual(a, b)
}

export async function getGromoreSecurityKey() {
  const row = (await query('SELECT value FROM app_settings WHERE key = $1', [GROMORE_KEY])).rows[0]
  return String(row?.value || '').trim()
}

function splitSecurityKeys(value) {
  return String(value ?? '')
    .split(/[\s,]+/)
    .map((part) => part.trim())
    .filter(Boolean)
}

export async function setGromoreSecurityKey(value) {
  const keys = splitSecurityKeys(value)
  if (keys.some((key) => key.length < 8 || key.length > 128)) {
    return { ok: false, error: '安全密钥格式不对' }
  }
  await query(
    `INSERT INTO app_settings (key, value, updated_at) VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()`,
    [GROMORE_KEY, keys.join(',')],
  )
  return { ok: true, configured: keys.length > 0, keyCount: keys.length }
}

/** GroMore placement callback. sign = sha256("m-key:trans_id"), reply within 1s. */
export async function verifyGromoreReward(params) {
  const transId = clip(params.trans_id, 128)
  const sign = String(params.sign || '').trim().toLowerCase()
  if (!transId || !/^[0-9a-f]{64}$/.test(sign)) {
    return { is_verify: false, reason: 40001 }
  }
  const keys = splitSecurityKeys(await getGromoreSecurityKey())
  if (!keys.length) return { is_verify: false, reason: 40002 }
  const matched = keys.some((key) =>
    safeEqual(crypto.createHash('sha256').update(`${key}:${transId}`).digest('hex'), sign),
  )
  if (!matched) return { is_verify: false, reason: 40001 }

  const existing = (
    await query(
      'SELECT reward_allowed FROM reward_video_callbacks WHERE trans_id = $1',
      [transId],
    )
  ).rows[0]
  if (existing) {
    return { is_verify: Boolean(existing.reward_allowed), reason: 0 }
  }

  const userRaw = String(params.user_id || '').trim()
  const userId = /^[1-9]\d{0,15}$/.test(userRaw) ? Number(userRaw) : 0
  const user = userId
    ? (await query('SELECT id FROM users WHERE id = $1', [userId])).rows[0]
    : null
  // 旧版客户端把 user_id 固定成 wordbuddy。签名有效就放行，但不记到某个用户名下。
  if (!user) return { is_verify: true, reason: 0 }

  const amount = Number(params.reward_amount)
  const status = await rewardVideoStatus(userId)
  const allow = status.remaining > 0 && status.cooldownRemainingSec <= 0
  await query(
    `INSERT INTO reward_video_callbacks
       (trans_id, user_id, reward_name, reward_amount, prime_rit, mediation_rit,
        adn_name, ecpm, extra, reward_allowed, watch_date)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::date)
     ON CONFLICT (trans_id) DO NOTHING`,
    [
      transId,
      userId,
      clip(params.reward_name, 64),
      Number.isFinite(amount) ? Math.round(amount) : null,
      clip(params.prime_rit, 64),
      clip(params.mediation_rit, 64),
      clip(params.adn_name, 64),
      clip(params.ecpm, 32),
      clip(params.extra, 500),
      allow,
      todayShanghai(),
    ],
  )
  return { is_verify: allow, reason: 0 }
}

export async function listAdWatches({ date, q = '', tail = '', page = 1, pageSize = 20 }) {
  const day = /^\d{4}-\d{2}-\d{2}$/.test(String(date || '')) ? String(date) : todayShanghai()
  const keyword = String(q || '').trim()
  const phoneTail = String(tail || '').trim().replace(/\D/g, '').slice(-4)
  const params = [day]
  const filters = []
  if (/^\d{4}$/.test(phoneTail)) {
    params.push(phoneTail)
    filters.push(`RIGHT(regexp_replace(COALESCE(u.phone, ''), '\\D', '', 'g'), 4) = $${params.length}`)
  } else if (/^\d{4}$/.test(keyword)) {
    params.push(keyword)
    filters.push(`RIGHT(regexp_replace(COALESCE(u.phone, ''), '\\D', '', 'g'), 4) = $${params.length}`)
  } else if (keyword) {
    params.push(`%${keyword}%`)
    const likeAt = params.length
    params.push(keyword)
    filters.push(`(u.phone ILIKE $${likeAt} OR u.nickname ILIKE $${likeAt} OR CAST(u.id AS TEXT) = $${params.length})`)
  }
  const filter = filters.length ? `AND ${filters.join(' AND ')}` : ''
  const where = `c.watch_date = $1::date AND c.user_id IS NOT NULL ${filter}`
  const totalRow = (
    await query(
      `SELECT COUNT(*)::int AS watches, COUNT(DISTINCT c.user_id)::int AS users
       FROM reward_video_callbacks c
       JOIN users u ON u.id = c.user_id
       WHERE ${where}`,
      params,
    )
  ).rows[0]
  const heavyRow = (
    await query(
      `SELECT COUNT(*)::int AS n
       FROM (
         SELECT c.user_id
         FROM reward_video_callbacks c
         JOIN users u ON u.id = c.user_id
         WHERE ${where}
         GROUP BY c.user_id
         HAVING COUNT(*) >= 20
       ) heavy`,
      params,
    )
  ).rows[0]
  const offset = (Math.max(1, page) - 1) * pageSize
  const listParams = [...params, pageSize, offset]
  const limitAt = params.length + 1
  const rows = (
    await query(
      `SELECT u.id AS "userId", u.phone, u.nickname, u.avatar_url AS "avatarUrl",
              COUNT(*)::int AS watches,
              COUNT(*) FILTER (WHERE c.reward_allowed)::int AS rewarded,
              MAX(c.created_at) AS "lastAt"
       FROM reward_video_callbacks c
       JOIN users u ON u.id = c.user_id
       WHERE ${where}
       GROUP BY u.id
       ORDER BY watches DESC, MAX(c.created_at) DESC
       LIMIT $${limitAt} OFFSET $${limitAt + 1}`,
      listParams,
    )
  ).rows
  return {
    date: day,
    watches: totalRow?.watches || 0,
    users: totalRow?.users || 0,
    usersWatched20Plus: heavyRow?.n || 0,
    total: totalRow?.users || 0,
    items: rows,
  }
}
