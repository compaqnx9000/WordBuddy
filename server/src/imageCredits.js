import { pool, query } from './db.js'

/** Logged-in users get this many free AI mnemonic images per Shanghai calendar day. */
export const DAILY_FREE_AI_IMAGES = 2

/** Fixed shelves. Unit price falls as the pack gets larger; none of this balance is withdrawable. */
export function listImageCreditPackages() {
  return [
    {
      id: 'img10',
      title: '10 张',
      subtitle: '每张 ¥0.10',
      priceFen: 100,
      credits: 10,
      points: 10,
      badge: '省 ¥0',
    },
    {
      id: 'img100',
      title: '100 张',
      subtitle: '每张 ¥0.09',
      priceFen: 900,
      credits: 100,
      points: 100,
      badge: '省 ¥1',
    },
    {
      id: 'img500',
      title: '500 张',
      subtitle: '每张 ¥0.08',
      priceFen: 4000,
      credits: 500,
      points: 500,
      badge: '省 ¥10',
    },
  ]
}

export function findImageCreditPackage(packageId) {
  const id = String(packageId || '').trim()
  return listImageCreditPackages().find((item) => item.id === id) || null
}

export async function getImageCredits(userId) {
  const row = (await query('SELECT image_credits FROM users WHERE id = $1', [userId])).rows[0]
  return Math.max(0, Number(row?.image_credits || 0))
}

export async function getTodayDailyFreeAiUsed(userId) {
  const uid = Number(userId)
  if (!Number.isFinite(uid) || uid <= 0) return 0
  const row = (
    await query(
      `SELECT COUNT(*)::int AS n
       FROM image_credit_ledger
       WHERE user_id = $1
         AND reason = 'ai_image_daily_free'
         AND (created_at AT TIME ZONE 'Asia/Shanghai')::date
             = (now() AT TIME ZONE 'Asia/Shanghai')::date`,
      [uid],
    )
  ).rows[0]
  return Math.max(0, Number(row?.n || 0))
}

/** Daily free remaining + purchased credits. Clients may show this as "剩余次数". */
export async function getAiImageAvailability(userId) {
  const purchased = await getImageCredits(userId)
  const dailyUsed = await getTodayDailyFreeAiUsed(userId)
  const dailyRemaining = Math.max(0, DAILY_FREE_AI_IMAGES - dailyUsed)
  return {
    dailyLimit: DAILY_FREE_AI_IMAGES,
    dailyUsed,
    dailyRemaining,
    imageCredits: purchased,
    available: dailyRemaining + purchased,
  }
}

/**
 * Prefer today's free quota, then purchased image_credits.
 * Records daily free uses in the ledger without changing the purchased balance.
 */
export async function consumeAiImageQuota(userId, { refType = 'mnemonic', refId = null } = {}) {
  const uid = Number(userId)
  if (!Number.isFinite(uid) || uid <= 0) return { ok: false, error: '无效用户' }

  const c = await pool.connect()
  try {
    await c.query('BEGIN')
    const row = (
      await c.query('SELECT image_credits FROM users WHERE id = $1 FOR UPDATE', [uid])
    ).rows[0]
    if (!row) {
      await c.query('ROLLBACK')
      return { ok: false, error: '用户不存在' }
    }
    const purchased = Math.max(0, Number(row.image_credits || 0))
    const usedRow = (
      await c.query(
        `SELECT COUNT(*)::int AS n
         FROM image_credit_ledger
         WHERE user_id = $1
           AND reason = 'ai_image_daily_free'
           AND (created_at AT TIME ZONE 'Asia/Shanghai')::date
               = (now() AT TIME ZONE 'Asia/Shanghai')::date`,
        [uid],
      )
    ).rows[0]
    const dailyUsed = Math.max(0, Number(usedRow?.n || 0))
    const dailyRemaining = Math.max(0, DAILY_FREE_AI_IMAGES - dailyUsed)

    if (dailyRemaining > 0) {
      await c.query(
        `INSERT INTO image_credit_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
         VALUES ($1, 0, $2, 'ai_image_daily_free', $3, $4)`,
        [uid, purchased, refType, refId == null ? null : String(refId)],
      )
      await c.query('COMMIT')
      const nextDaily = dailyRemaining - 1
      return {
        ok: true,
        source: 'daily_free',
        balance: purchased,
        dailyRemaining: nextDaily,
        available: nextDaily + purchased,
        creditsSpent: 0,
      }
    }

    if (purchased < 1) {
      await c.query('ROLLBACK')
      return {
        ok: false,
        error: '今日免费 AI 生图已用完（每天 2 次），明天再来',
        code: 'DAILY_AI_IMAGE_LIMIT',
        balance: purchased,
        dailyRemaining: 0,
        available: 0,
        need: 1,
      }
    }

    const next = purchased - 1
    await c.query('UPDATE users SET image_credits = $2 WHERE id = $1', [uid, next])
    await c.query(
      `INSERT INTO image_credit_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
       VALUES ($1, -1, $2, 'ai_image', $3, $4)`,
      [uid, next, refType, refId == null ? null : String(refId)],
    )
    await c.query('COMMIT')
    return {
      ok: true,
      source: 'purchased',
      balance: next,
      dailyRemaining: 0,
      available: next,
      creditsSpent: 1,
    }
  } catch (error) {
    await c.query('ROLLBACK')
    throw error
  } finally {
    c.release()
  }
}

/** Free mnemonic-image uses granted once to a brand-new account. */
export const SIGNUP_IMAGE_CREDITS = 2

/** Grant signup image credits at most once. Not a purchase, and not granted again. */
export async function grantSignupImageCredit(userId) {
  const uid = Number(userId)
  if (!Number.isFinite(uid) || uid <= 0) return { ok: false, error: '无效用户' }
  const paid = (
    await query(
      `SELECT 1 FROM image_credit_ledger WHERE user_id = $1 AND reason = 'signup' LIMIT 1`,
      [uid],
    )
  ).rowCount > 0
  if (paid) return { ok: true, skipped: true, balance: await getImageCredits(uid) }
  return adjustImageCredits({
    userId: uid,
    delta: SIGNUP_IMAGE_CREDITS,
    reason: 'signup',
    refType: 'signup',
    refId: String(uid),
  })
}

/**
 * Credit or debit mnemonic-image uses. Separate from withdrawable points.
 * delta > 0 credit, delta < 0 debit.
 */
export async function adjustImageCredits({
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
  if (!change) return { ok: false, error: '无效配图次数变动' }

  const own = !client
  const c = client || (await pool.connect())
  try {
    if (own) await c.query('BEGIN')
    const row = (
      await c.query('SELECT image_credits FROM users WHERE id = $1 FOR UPDATE', [uid])
    ).rows[0]
    if (!row) {
      if (own) await c.query('ROLLBACK')
      return { ok: false, error: '用户不存在' }
    }
    const balance = Math.max(0, Number(row.image_credits || 0))
    if (change < 0 && balance < -change) {
      if (own) await c.query('ROLLBACK')
      const need = -change
      return {
        ok: false,
        error: `配图次数不足，还差 ${need - balance} 张`,
        code: 'INSUFFICIENT_IMAGE_CREDITS',
        balance,
        need,
      }
    }
    const next = balance + change
    await c.query('UPDATE users SET image_credits = $2 WHERE id = $1', [uid, next])
    await c.query(
      `INSERT INTO image_credit_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
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
