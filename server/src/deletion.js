import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { query } from './db.js'

export const DELETION_COOLDOWN_DAYS = 7
export const ACCOUNT_DELETED_CODE = 'ACCOUNT_DELETED'
export const ACCOUNT_DELETED_MESSAGE = '账号已注销'

const SECURITY_DAYS = 30
const PENDING_ORDER_STATUSES = ['pending_ship', 'pending_cash']
const PENDING_WITHDRAW_STATUSES = ['pending']
const uploadsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../uploads')

function fail(status, message, code = null) {
  const error = new Error(message)
  error.status = status
  if (code) error.code = code
  return error
}

function iso(value) {
  return value ? new Date(value).toISOString() : null
}

export function maskPhone(phone) {
  const digits = String(phone || '').replace(/\D/g, '')
  if (digits.length < 7) return digits || ''
  return `${digits.slice(0, 3)}****${digits.slice(-4)}`
}

function hashPhone(phone) {
  return createHash('sha256').update(String(phone || '')).digest('hex')
}

export function formatShanghai(value) {
  if (!value) return ''
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(new Date(value))
  const pick = (type) => parts.find((part) => part.type === type)?.value || ''
  return `${pick('year')}年${pick('month')}月${pick('day')}日 ${pick('hour')}:${pick('minute')}`
}

function isDue(dueAt) {
  return Boolean(dueAt) && new Date(dueAt).getTime() <= Date.now()
}

export async function getDeletionRow(userId) {
  return (
    await query(
      `SELECT id, phone, deletion_requested_at, deletion_due_at, deletion_reason, phone_changed_at
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0] || null
}

export async function evaluateDeletionConditions(userId) {
  const user = (
    await query(
      `SELECT id, phone, created_at, password_changed_at, phone_changed_at
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  if (!user) throw fail(404, '用户不存在')

  const [passwordEvents, pendingOrders, pendingWithdrawals, checkIn] = await Promise.all([
    query(
      `SELECT created_at FROM password_events
       WHERE user_id = $1 AND reason IN ('change', 'admin_reset')
         AND created_at > now() - ($2 || ' days')::interval
       ORDER BY created_at DESC LIMIT 1`,
      [userId, String(SECURITY_DAYS)],
    ),
    query(
      `SELECT count(*)::int AS n FROM gift_orders
       WHERE user_id = $1 AND status = ANY($2::text[])`,
      [userId, PENDING_ORDER_STATUSES],
    ),
    query(
      `SELECT count(*)::int AS n FROM withdrawals
       WHERE user_id = $1 AND status = ANY($2::text[])`,
      [userId, PENDING_WITHDRAW_STATUSES],
    ),
    query('SELECT total_points FROM user_checkins WHERE user_id = $1', [userId]),
  ])

  const recentPassword = passwordEvents.rows[0]?.created_at
  const phoneChangedAt = user.phone_changed_at
  const recentPhone =
    phoneChangedAt && Date.now() - new Date(phoneChangedAt).getTime() < SECURITY_DAYS * 86400000
      ? phoneChangedAt
      : null
  const securityOk = !recentPassword && !recentPhone
  const securityBits = []
  if (recentPassword) securityBits.push(`近${SECURITY_DAYS}天内修改过密码`)
  if (recentPhone) securityBits.push(`近${SECURITY_DAYS}天内更换过手机号`)

  const orderCount = Number(pendingOrders.rows[0]?.n || 0)
  const withdrawCount = Number(pendingWithdrawals.rows[0]?.n || 0)
  const remainingPoints = Number(checkIn.rows[0]?.total_points || 0)
  const settlementOk = orderCount === 0 && withdrawCount === 0
  const settlementBits = []
  if (orderCount > 0) settlementBits.push(`还有 ${orderCount} 笔礼品订单未完成`)
  if (withdrawCount > 0) settlementBits.push(`还有 ${withdrawCount} 笔提现处理中`)
  if (settlementOk) {
    settlementBits.push(
      remainingPoints > 0
        ? `剩余 ${remainingPoints} 积分将视为自愿放弃并清零`
        : '没有未完成的兑换或提现',
    )
  }

  const conditions = [
    {
      key: 'security',
      title: '账号处于安全状态',
      ok: securityOk,
      detail: securityOk
        ? `近${SECURITY_DAYS}天未改密、未换绑手机号`
        : `${securityBits.join('；')}，请期满后再申请`,
    },
    {
      key: 'settlement',
      title: '账号财产已结清',
      ok: settlementOk,
      detail: settlementBits.join('；'),
    },
    {
      key: 'bindings',
      title: '账号权限已解除',
      ok: true,
      detail: '当前没有第三方授权登录需要解绑',
    },
    {
      key: 'disputes',
      title: '无未结争议纠纷',
      ok: true,
      detail: '当前没有投诉、举报或其他争议',
    },
  ]

  return {
    conditions,
    allPassed: conditions.every((item) => item.ok),
    remainingPoints,
    phoneMasked: maskPhone(user.phone),
  }
}

export async function getDeletionStatus(userId) {
  const row = await getDeletionRow(userId)
  if (!row) throw fail(404, '用户不存在')
  if (isDue(row.deletion_due_at)) {
    await finalizeDueAccountById(userId)
    throw fail(401, ACCOUNT_DELETED_MESSAGE, ACCOUNT_DELETED_CODE)
  }
  const evaled = await evaluateDeletionConditions(userId)
  const pending = Boolean(row.deletion_requested_at && row.deletion_due_at)
  return {
    pending,
    cooldownDays: DELETION_COOLDOWN_DAYS,
    requestedAt: iso(row.deletion_requested_at),
    dueAt: iso(row.deletion_due_at),
    dueAtLabel: pending ? formatShanghai(row.deletion_due_at) : '',
    reason: row.deletion_reason || '',
    ...evaled,
  }
}

export async function assertNotDeleting(userId) {
  const row = await getDeletionRow(userId)
  if (!row) throw fail(404, '用户不存在')
  if (isDue(row.deletion_due_at)) {
    await finalizeDueAccountById(userId)
    throw fail(401, ACCOUNT_DELETED_MESSAGE, ACCOUNT_DELETED_CODE)
  }
  if (row.deletion_requested_at && row.deletion_due_at) {
    throw fail(409, '账号正在注销中，请先撤销注销后再操作')
  }
}

export async function requestAccountDeletion(userId, { reason, force = false } = {}) {
  const status = await getDeletionStatus(userId)
  if (status.pending) throw fail(400, '已提交注销申请，可在冷静期内撤销')
  const forceDelete = force === true
  if (!forceDelete && !status.allPassed) {
    throw fail(400, '暂不满足注销条件，请先处理未完成事项，或勾选强行注销')
  }
  const note = String(reason || '').trim().slice(0, 40)
  const reasonText = forceDelete
    ? [note, '强行注销'].filter(Boolean).join(' · ').slice(0, 80)
    : note || null

  if (forceDelete) {
    const deleted = await finalizeForceAccountById(userId, reasonText)
    return {
      pending: false,
      immediate: true,
      deleted: true,
      cooldownDays: 0,
      requestedAt: null,
      dueAt: null,
      dueAtLabel: '',
      reason: reasonText || '',
      phoneMasked: status.phoneMasked,
      remainingPoints: status.remainingPoints,
      conditions: status.conditions,
      allPassed: status.allPassed,
      ...deleted,
    }
  }

  const updated = (
    await query(
      `UPDATE users
       SET deletion_requested_at = now(),
           deletion_due_at = now() + ($2 || ' days')::interval,
           deletion_reason = $3
       WHERE id = $1
       RETURNING deletion_requested_at, deletion_due_at, deletion_reason`,
      [userId, String(DELETION_COOLDOWN_DAYS), reasonText],
    )
  ).rows[0]
  return {
    pending: true,
    immediate: false,
    deleted: false,
    cooldownDays: DELETION_COOLDOWN_DAYS,
    requestedAt: iso(updated.deletion_requested_at),
    dueAt: iso(updated.deletion_due_at),
    dueAtLabel: formatShanghai(updated.deletion_due_at),
    reason: updated.deletion_reason || '',
    phoneMasked: status.phoneMasked,
    remainingPoints: status.remainingPoints,
    conditions: status.conditions,
    allPassed: status.allPassed,
  }
}

export async function cancelAccountDeletion(userId) {
  const row = await getDeletionRow(userId)
  if (!row) throw fail(404, '用户不存在')
  if (isDue(row.deletion_due_at)) {
    await finalizeDueAccountById(userId)
    throw fail(401, ACCOUNT_DELETED_MESSAGE, ACCOUNT_DELETED_CODE)
  }
  if (!row.deletion_requested_at) throw fail(400, '当前没有注销申请')
  await query(
    `UPDATE users
     SET deletion_requested_at = NULL, deletion_due_at = NULL, deletion_reason = NULL
     WHERE id = $1`,
    [userId],
  )
  return getDeletionStatus(userId)
}

async function writeDeletionLog(row, completedReason) {
  await query(
    `INSERT INTO account_deletion_logs
       (user_id, phone_masked, phone_hash, reason, requested_at, due_at, completed_at, completed_reason)
     VALUES ($1, $2, $3, $4, $5, $6, now(), $7)`,
    [
      row.id,
      maskPhone(row.phone),
      hashPhone(row.phone),
      row.deletion_reason || null,
      row.deletion_requested_at,
      row.deletion_due_at,
      completedReason,
    ],
  )
}

async function removeAvatarFile(userId) {
  const file = path.join(uploadsRoot, 'avatars', `${userId}.jpg`)
  try {
    fs.unlinkSync(file)
  } catch {
    // already gone
  }
}

export async function finalizeDueAccountById(userId) {
  const row = (
    await query(
      `SELECT id, phone, deletion_requested_at, deletion_due_at, deletion_reason
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  if (!row) return { deleted: false }
  if (!isDue(row.deletion_due_at)) return { deleted: false }
  await writeDeletionLog(row, 'cooldown_elapsed')
  await removeAvatarFile(row.id)
  await query('DELETE FROM users WHERE id = $1', [row.id])
  return { deleted: true }
}

export async function finalizeForceAccountById(userId, reason = null) {
  const row = (
    await query(
      `SELECT id, phone, deletion_requested_at, deletion_due_at, deletion_reason
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  if (!row) throw fail(404, '用户不存在')
  const stamped = {
    ...row,
    deletion_requested_at: row.deletion_requested_at || new Date(),
    deletion_due_at: new Date(),
    deletion_reason: reason || row.deletion_reason || '强行注销',
  }
  await writeDeletionLog(stamped, 'force_immediate')
  await removeAvatarFile(row.id)
  await query('DELETE FROM users WHERE id = $1', [row.id])
  return { deleted: true, immediate: true }
}

export async function finalizeDueAccountByPhone(phone) {
  const row = (
    await query(
      `SELECT id FROM users
       WHERE phone = $1 AND deletion_due_at IS NOT NULL AND deletion_due_at <= now()
       LIMIT 1`,
      [phone],
    )
  ).rows[0]
  if (!row) return { deleted: false }
  return finalizeDueAccountById(row.id)
}

export async function purgeDueDeletions(limit = 50) {
  const due = await query(
    `SELECT id FROM users
     WHERE deletion_due_at IS NOT NULL AND deletion_due_at <= now()
     ORDER BY deletion_due_at ASC
     LIMIT $1`,
    [limit],
  )
  let deleted = 0
  for (const row of due.rows) {
    const result = await finalizeDueAccountById(row.id)
    if (result.deleted) deleted += 1
  }
  return { scanned: due.rowCount, deleted }
}
