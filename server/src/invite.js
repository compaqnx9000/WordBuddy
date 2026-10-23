import { pool, query } from './db.js'

/** Defaults when app_settings has no override. */
export const INVITE_REWARD_DEFAULT = 3000
const INVITE_REWARD_MAX = 100000
const KEY_INVITER = 'invite_reward_inviter'
const KEY_INVITEE = 'invite_reward_invitee'

function clampInvitePoints(value) {
  const n = Math.trunc(Number(value))
  if (!Number.isFinite(n)) return null
  return Math.min(INVITE_REWARD_MAX, Math.max(0, n))
}

async function readSettingPoints(key, fallback) {
  const row = (await query('SELECT value FROM app_settings WHERE key = $1', [key])).rows[0]
  return clampInvitePoints(row?.value) ?? fallback
}

export async function getInviteRewardPoints() {
  const [inviterPoints, inviteePoints] = await Promise.all([
    readSettingPoints(KEY_INVITER, INVITE_REWARD_DEFAULT),
    readSettingPoints(KEY_INVITEE, INVITE_REWARD_DEFAULT),
  ])
  return { inviterPoints, inviteePoints }
}

export async function setInviteRewardPoints({ inviterPoints, inviteePoints } = {}) {
  const inviter = clampInvitePoints(inviterPoints)
  const invitee = clampInvitePoints(inviteePoints)
  if (inviter == null) return { ok: false, error: `邀请人搭币须为 0～${INVITE_REWARD_MAX} 的整数` }
  if (invitee == null) return { ok: false, error: `被邀请人搭币须为 0～${INVITE_REWARD_MAX} 的整数` }
  await query(
    `INSERT INTO app_settings (key, value, updated_at)
     VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET
       value = EXCLUDED.value,
       updated_at = now()`,
    [KEY_INVITER, String(inviter)],
  )
  await query(
    `INSERT INTO app_settings (key, value, updated_at)
     VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET
       value = EXCLUDED.value,
       updated_at = now()`,
    [KEY_INVITEE, String(invitee)],
  )
  return { ok: true, inviterPoints: inviter, inviteePoints: invitee }
}

/** @deprecated Prefer getInviteRewardPoints(); kept for older imports. */
export const INVITE_REWARD_INVITER = INVITE_REWARD_DEFAULT
/** @deprecated Prefer getInviteRewardPoints(); kept for older imports. */
export const INVITE_REWARD_INVITEE = INVITE_REWARD_DEFAULT

export function normalizeInviteCode(raw) {
  const code = String(raw || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '')
  if (code.length < 4 || code.length > 16) return null
  return code
}

export async function findUserByBuddyId(buddyId) {
  const code = normalizeInviteCode(buddyId)
  if (!code) return null
  const row = (
    await query(
      `SELECT id, phone, buddy_id, nickname, avatar_url
       FROM users
       WHERE lower(btrim(buddy_id)) = $1
       LIMIT 1`,
      [code],
    )
  ).rows[0]
  return row || null
}

/**
 * Bind invite once (at register or later makeup). User must not already have invited_by_user_id.
 * @returns {{ ok: true, inviterId, inviteeReward, inviterReward } | { ok: false, skipped?: boolean, error?: string }}
 */
export async function bindInviteCode(userId, inviteCodeRaw) {
  const code = normalizeInviteCode(inviteCodeRaw)
  if (!code) return { ok: false, error: '请输入邀请码' }

  const client = await pool.connect()
  try {
    await client.query('BEGIN')
    const me = (
      await client.query(
        `SELECT id, invited_by_user_id FROM users WHERE id = $1 FOR UPDATE`,
        [userId],
      )
    ).rows[0]
    if (!me) {
      await client.query('ROLLBACK')
      return { ok: false, error: '用户不存在' }
    }
    if (me.invited_by_user_id) {
      await client.query('ROLLBACK')
      return { ok: false, error: '已绑定过邀请人，无法再填写' }
    }

    const inviter = (
      await client.query(
        `SELECT id, buddy_id FROM users WHERE lower(btrim(buddy_id)) = $1 FOR UPDATE`,
        [code],
      )
    ).rows[0]
    if (!inviter) {
      await client.query('ROLLBACK')
      return { ok: false, error: '邀请码无效' }
    }
    if (Number(inviter.id) === Number(userId)) {
      await client.query('ROLLBACK')
      return { ok: false, error: '不能填写自己的邀请码' }
    }

    const updated = await client.query(
      `UPDATE users
       SET invited_by_user_id = $2, invite_bound_at = now()
       WHERE id = $1 AND invited_by_user_id IS NULL
       RETURNING id`,
      [userId, inviter.id],
    )
    if (updated.rowCount === 0) {
      await client.query('ROLLBACK')
      return { ok: false, error: '已绑定过邀请人，无法再填写' }
    }

    // Defense: never double-pay invite_bonus for the same invitee.
    const alreadyPaid = (
      await client.query(
        `SELECT 1 FROM points_ledger
         WHERE user_id = $1 AND reason = 'invite_bonus'
         LIMIT 1`,
        [userId],
      )
    ).rowCount > 0
    if (alreadyPaid) {
      await client.query('ROLLBACK')
      return { ok: false, error: '已绑定过邀请人，无法再填写' }
    }

    async function grantPoints(uid, delta, reason) {
      const row = (
        await client.query(
          `SELECT total_points, streak_days, last_checkin_date
           FROM user_checkins WHERE user_id = $1 FOR UPDATE`,
          [uid],
        )
      ).rows[0]
      const prev = Math.max(0, Number(row?.total_points || 0))
      const next = prev + delta
      await client.query(
        `INSERT INTO user_checkins (user_id, total_points, streak_days, last_checkin_date, updated_at)
         VALUES ($1, $2, $3, $4, now())
         ON CONFLICT (user_id) DO UPDATE SET
           total_points = EXCLUDED.total_points,
           updated_at = now()`,
        [uid, next, Number(row?.streak_days || 0), row?.last_checkin_date || null],
      )
      await client.query(
        `INSERT INTO points_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
         VALUES ($1, $2, $3, $4, 'invite', $5)`,
        [uid, delta, next, reason, String(userId === uid ? inviter.id : userId)],
      )
      return next
    }

    const rewards = await getInviteRewardPoints()
    if (rewards.inviterPoints > 0) {
      await grantPoints(inviter.id, rewards.inviterPoints, 'invite_reward')
    }
    if (rewards.inviteePoints > 0) {
      await grantPoints(userId, rewards.inviteePoints, 'invite_bonus')
    }

    await client.query('COMMIT')
    return {
      ok: true,
      inviterId: Number(inviter.id),
      inviterBuddyId: inviter.buddy_id,
      inviterReward: rewards.inviterPoints,
      inviteeReward: rewards.inviteePoints,
    }
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
}

export async function getInviteStats(userId) {
  const invitedCount = (
    await query(`SELECT count(*)::int AS n FROM users WHERE invited_by_user_id = $1`, [userId])
  ).rows[0].n
  const earned = (
    await query(
      `SELECT COALESCE(sum(delta), 0)::int AS n
       FROM points_ledger
       WHERE user_id = $1 AND reason = 'invite_reward'`,
      [userId],
    )
  ).rows[0].n
  const me = (
    await query(
      `SELECT u.invited_by_user_id, inv.buddy_id AS invited_by_buddy_id
       FROM users u
       LEFT JOIN users inv ON inv.id = u.invited_by_user_id
       WHERE u.id = $1`,
      [userId],
    )
  ).rows[0]
  return {
    invitedCount,
    invitePointsEarned: earned,
    canBindInvite: !me?.invited_by_user_id,
    invitedByBuddyId: me?.invited_by_buddy_id ? String(me.invited_by_buddy_id) : null,
  }
}

/** @deprecated use bindInviteCode */
export const bindInviteOnRegister = bindInviteCode
