import { pool, query } from './db.js'

export function aiImagePointsCost() {
  return Math.max(0, Number(process.env.AI_IMAGE_POINTS_COST) || 5)
}

/** Credited once when an account is created, before any invite bonus. */
export const SIGNUP_BONUS_POINTS = 10000

/**
 * Grant the new-user bonus at most once. Safe to call again after a retry.
 */
export async function grantSignupBonus(userId) {
  const uid = Number(userId)
  if (!Number.isFinite(uid) || uid <= 0) return { ok: false, error: '无效用户' }
  const paid = (
    await query(
      `SELECT 1 FROM points_ledger WHERE user_id = $1 AND reason = 'signup_bonus' LIMIT 1`,
      [uid],
    )
  ).rowCount > 0
  if (paid) return { ok: true, skipped: true, balance: await getPointsBalance(uid) }
  return adjustPoints({
    userId: uid,
    delta: SIGNUP_BONUS_POINTS,
    reason: 'signup_bonus',
    refType: 'signup',
    refId: String(uid),
  })
}

/**
 * Credit or debit points inside an existing client transaction, or open a short one.
 * delta > 0 credit, delta < 0 debit.
 */
export async function adjustPoints({
  userId,
  delta,
  reason,
  refType = null,
  refId = null,
  client = null,
} = {}) {
  const uid = Number(userId)
  const change = Math.trunc(Number(delta) || 0)
  if (!Number.isFinite(uid) || uid <= 0) return { ok: false, error: '无效用户' }
  if (!change) return { ok: false, error: '无效搭币变动' }

  const own = !client
  const c = client || (await pool.connect())
  try {
    if (own) await c.query('BEGIN')
    const checkIn = (
      await c.query(
        `SELECT total_points, streak_days, last_checkin_date
         FROM user_checkins WHERE user_id = $1 FOR UPDATE`,
        [uid],
      )
    ).rows[0]
    const balance = Math.max(0, Number(checkIn?.total_points || 0))
    if (change < 0 && balance < -change) {
      if (own) await c.query('ROLLBACK')
      return {
        ok: false,
        error: `搭币不足，还差 ${-change - balance} 搭币`,
        code: 'INSUFFICIENT_POINTS',
        balance,
        need: -change,
      }
    }
    const next = balance + change
    await c.query(
      `INSERT INTO user_checkins (user_id, total_points, streak_days, last_checkin_date, updated_at)
       VALUES ($1, $2, $3, $4, now())
       ON CONFLICT (user_id) DO UPDATE SET
         total_points = EXCLUDED.total_points,
         updated_at = now()`,
      [uid, next, Number(checkIn?.streak_days || 0), checkIn?.last_checkin_date || null],
    )
    await c.query(
      `INSERT INTO points_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [uid, change, next, String(reason || 'adjust').slice(0, 40), refType, refId == null ? null : String(refId)],
    )
    if (own) await c.query('COMMIT')
    return { ok: true, balance: next, delta: change }
  } catch (error) {
    if (own) await c.query('ROLLBACK')
    throw error
  } finally {
    if (own) c.release()
  }
}

export async function getPointsBalance(userId) {
  const row = (await query('SELECT total_points FROM user_checkins WHERE user_id = $1', [userId])).rows[0]
  return Math.max(0, Number(row?.total_points || 0))
}

const LEDGER_TITLES = {
  signup_bonus: '新用户奖励',
  invite_reward: '邀请好友',
  invite_bonus: '填写邀请码',
  reward_video: '看视频',
  withdraw: '提现',
  withdraw_refund: '提现退回',
  redeem: '兑礼',
  ai_image: '生成配图',
  ai_image_refund: '配图退回',
  purchase: '购买搭币',
  checkin: '每日签到',
  makeup: '补签',
  admin_adjust: '系统调整',
  admin_set: '系统调整',
}

export async function listPointsLedger(userId, { page = 1, pageSize = 30 } = {}) {
  const uid = Number(userId)
  const size = Math.min(50, Math.max(1, Number(pageSize) || 30))
  const current = Math.max(1, Number(page) || 1)
  const offset = (current - 1) * size
  const total = Number(
    (
      await query(
        `SELECT (
           (SELECT count(*) FROM points_ledger WHERE user_id = $1) +
           (SELECT count(*) FROM user_checkin_logs WHERE user_id = $1)
         )::int AS n`,
        [uid],
      )
    ).rows[0]?.n || 0,
  )
  const rows = (
    await query(
      `SELECT * FROM (
         SELECT
           'l' || l.id::text AS id,
           l.delta,
           l.balance_after,
           l.reason,
           l.created_at,
           CASE WHEN l.reason = 'redeem' THEN g.title ELSE NULL END AS detail
         FROM points_ledger l
         LEFT JOIN gifts g ON l.reason = 'redeem' AND g.id::text = l.ref_id
         WHERE l.user_id = $1
         UNION ALL
         SELECT
           'c' || c.id::text AS id,
           c.points_earned AS delta,
           NULL::integer AS balance_after,
           CASE
             WHEN c.checkin_date < (c.created_at AT TIME ZONE 'Asia/Shanghai')::date THEN 'makeup'
             ELSE 'checkin'
           END AS reason,
           c.created_at,
           ('连续 ' || c.streak_days || ' 天') AS detail
         FROM user_checkin_logs c
         WHERE c.user_id = $1
       ) entries
       ORDER BY created_at DESC, id DESC
       LIMIT $2 OFFSET $3`,
      [uid, size, offset],
    )
  ).rows
  return {
    balance: await getPointsBalance(uid),
    total,
    page: current,
    pageSize: size,
    items: rows.map((row) => ({
      id: String(row.id),
      delta: Number(row.delta || 0),
      balanceAfter: row.balance_after == null ? null : Number(row.balance_after),
      reason: row.reason,
      title: LEDGER_TITLES[row.reason] || '搭币变动',
      detail: row.detail || null,
      createdAt: row.created_at ? new Date(row.created_at).toISOString() : null,
    })),
  }
}
