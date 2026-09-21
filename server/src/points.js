import { pool, query } from './db.js'

export function aiImagePointsCost() {
  return Math.max(0, Number(process.env.AI_IMAGE_POINTS_COST) || 5)
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
  if (!change) return { ok: false, error: '无效积分变动' }

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
        error: `积分不足，还差 ${-change - balance} 分`,
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
