import { pool, query } from './db.js'
import { todayShanghai } from './checkin.js'
import { adjustPoints, getPointsBalance } from './points.js'
import { getUserPerks } from './levels.js'

export const REWARD_VIDEO_DAILY_LIMIT = 20
const REWARD_VIDEO_POINTS_MAX = 100000
const REWARD_VIDEO_POINTS_DEFAULT = 1500
const SETTING_KEY = 'reward_video_points'

function clampPoints(value) {
  const n = Math.trunc(Number(value))
  if (!Number.isFinite(n)) return null
  return Math.min(REWARD_VIDEO_POINTS_MAX, Math.max(1, n))
}

export async function getRewardVideoPoints() {
  const row = (
    await query('SELECT value FROM app_settings WHERE key = $1', [SETTING_KEY])
  ).rows[0]
  return clampPoints(row?.value) ?? REWARD_VIDEO_POINTS_DEFAULT
}

export async function setRewardVideoPoints(points) {
  const n = clampPoints(points)
  if (n == null) return { ok: false, error: `搭币须为 1～${REWARD_VIDEO_POINTS_MAX} 的整数` }
  await query(
    `INSERT INTO app_settings (key, value, updated_at)
     VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET
       value = EXCLUDED.value,
       updated_at = now()`,
    [SETTING_KEY, String(n)],
  )
  return { ok: true, pointsPerWatch: n, dailyLimit: REWARD_VIDEO_DAILY_LIMIT }
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

export async function rewardVideoStatus(userId) {
  const today = todayShanghai()
  const pointsPerWatch = await getRewardVideoPoints()
  const perks = await getUserPerks(userId)
  const dailyLimit = perks.videoDailyLimit
  const used = await usedToday(userId, today)
  const totalPoints = await getPointsBalance(userId)
  return {
    pointsPerWatch,
    dailyLimit,
    usedToday: used,
    remaining: Math.max(0, dailyLimit - used),
    totalPoints,
    today,
    level: perks.level,
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
