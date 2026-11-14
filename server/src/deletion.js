import { createHash } from 'node:crypto'
import { query } from './db.js'

export const DELETION_COOLDOWN_DAYS = 7
export const ACCOUNT_DELETED_CODE = 'ACCOUNT_DELETED'
export const ACCOUNT_DELETED_MESSAGE = '账号已注销'

const SECURITY_DAYS = 30
const PENDING_ORDER_STATUSES = ['pending_ship', 'pending_cash']
const PENDING_WITHDRAW_STATUSES = ['pending']

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

export function phoneHash(phone) {
  return createHash('sha256').update(String(phone || '')).digest('hex')
}

function hashPhone(phone) {
  return phoneHash(phone)
}

/** True when this phone already finished 注销, including accounts removed before soft-close. */
export async function phoneHasClosedAccount(phone, exceptUserId = null) {
  const normalized = String(phone || '').trim()
  if (!normalized) return false
  const except = Number(exceptUserId)
  const exceptId = Number.isFinite(except) && except > 0 ? except : null
  const marked = await query(
    `SELECT 1 FROM users
     WHERE phone = $1 AND deleted_at IS NOT NULL
       AND ($2::bigint IS NULL OR id <> $2)
     LIMIT 1`,
    [normalized, exceptId],
  )
  if (marked.rowCount > 0) return true
  const logged = await query(
    `SELECT 1 FROM account_deletion_logs
     WHERE phone_hash = $1
       AND ($2::bigint IS NULL OR user_id IS DISTINCT FROM $2)
     LIMIT 1`,
    [hashPhone(normalized), exceptId],
  )
  return logged.rowCount > 0
}

/** Same person coming back by phone, WeChat, Apple, or Alipay is not a new user. */
export async function shouldSkipNewUserRewards({
  phone,
  wechatOpenid,
  appleUserId,
  alipayAccount,
} = {}) {
  if (await phoneHasClosedAccount(phone)) return true
  const identities = [
    ['wechat_openid', wechatOpenid],
    ['apple_user_id', appleUserId],
    ['alipay_account', alipayAccount],
  ]
  for (const [column, value] of identities) {
    const identity = String(value || '').trim()
    if (!identity) continue
    const hit = await query(
      `SELECT 1 FROM users WHERE ${column} = $1 AND deleted_at IS NOT NULL LIMIT 1`,
      [identity],
    )
    if (hit.rowCount > 0) return true
  }
  return false
}

function bucketPlatform(raw) {
  const value = String(raw || '').trim().toLowerCase()
  if (value === 'android') return 'android'
  if (value === 'ios') return 'ios'
  if (['web', 'windows', 'macos', 'mac os', 'linux', 'pc'].includes(value)) return 'pc'
  return 'unknown'
}

async function registerPlatformForUser(userId, fallback) {
  const row = (
    await query(
      `SELECT lower(trim(COALESCE(device_platform, ''))) AS platform
       FROM login_events
       WHERE success = TRUE AND user_id = $1
       ORDER BY created_at ASC, id ASC
       LIMIT 1`,
      [userId],
    )
  ).rows[0]
  return bucketPlatform(row?.platform || fallback)
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
      `SELECT id, phone, deletion_requested_at, deletion_due_at, deletion_reason, phone_changed_at, deleted_at
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0] || null
}

export async function evaluateDeletionConditions(userId) {
  const user = (
    await query(
      `SELECT id, phone, created_at, password_changed_at, phone_changed_at, image_credits
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
  const imageCredits = Math.max(0, Number(user.image_credits || 0))
  const settlementOk = orderCount === 0 && withdrawCount === 0
  const settlementBits = []
  if (orderCount > 0) settlementBits.push(`还有 ${orderCount} 笔礼品订单未完成`)
  if (withdrawCount > 0) settlementBits.push(`还有 ${withdrawCount} 笔提现处理中`)
  if (settlementOk) {
    const forfeits = []
    if (remainingPoints > 0) forfeits.push(`剩余 ${remainingPoints} 搭币将视为自愿放弃并清零`)
    if (imageCredits > 0) forfeits.push(`剩余 ${imageCredits} 张助记配图将视为自愿放弃并清零`)
    settlementBits.push(forfeits.length ? forfeits.join('；') : '没有未完成的兑换或提现')
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
  if (row.deleted_at || isDue(row.deletion_due_at)) {
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
  if (row.deleted_at || isDue(row.deletion_due_at)) {
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
  if (row.deleted_at || isDue(row.deletion_due_at)) {
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
  const platform = await registerPlatformForUser(row.id, row.last_device_platform)
  await query(
    `INSERT INTO account_deletion_logs
       (user_id, phone_masked, phone_hash, reason, requested_at, due_at, completed_at, completed_reason, platform)
     VALUES ($1, $2, $3, $4, $5, $6, now(), $7, $8)`,
    [
      row.id,
      maskPhone(row.phone),
      hashPhone(row.phone),
      row.deletion_reason || null,
      row.deletion_requested_at,
      row.deletion_due_at,
      completedReason,
      platform,
    ],
  )
}

export async function recordAccountDeletion(userId, completedReason) {
  const row = (
    await query(
      `SELECT id, phone, deletion_requested_at, deletion_due_at, deletion_reason, last_device_platform
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  if (!row) return
  await writeDeletionLog(row, completedReason)
}

async function markUserClosed(userId, reason = null) {
  await query(
    `UPDATE users
     SET deleted_at = COALESCE(deleted_at, now()),
         deletion_requested_at = COALESCE(deletion_requested_at, now()),
         deletion_due_at = COALESCE(deletion_due_at, now()),
         deletion_reason = COALESCE($2, deletion_reason),
         session_version = COALESCE(session_version, 0) + 1,
         session_desktop = COALESCE(session_desktop, 0) + 1,
         session_mobile = COALESCE(session_mobile, 0) + 1
     WHERE id = $1 AND deleted_at IS NULL`,
    [userId, reason],
  )
}

export async function finalizeDueAccountById(userId) {
  const row = (
    await query(
      `SELECT id, phone, deletion_requested_at, deletion_due_at, deletion_reason, last_device_platform, deleted_at
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  if (!row) return { deleted: false }
  if (row.deleted_at) return { deleted: true, already: true }
  if (!isDue(row.deletion_due_at)) return { deleted: false }
  await writeDeletionLog(row, 'cooldown_elapsed')
  await markUserClosed(row.id, row.deletion_reason)
  return { deleted: true, kept: true }
}

export async function finalizeForceAccountById(userId, reason = null) {
  const row = (
    await query(
      `SELECT id, phone, deletion_requested_at, deletion_due_at, deletion_reason, last_device_platform, deleted_at
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  if (!row) throw fail(404, '用户不存在')
  if (row.deleted_at) return { deleted: true, already: true, immediate: true }
  const stamped = {
    ...row,
    deletion_requested_at: row.deletion_requested_at || new Date(),
    deletion_due_at: new Date(),
    deletion_reason: reason || row.deletion_reason || '强行注销',
  }
  await writeDeletionLog(stamped, 'force_immediate')
  await markUserClosed(row.id, stamped.deletion_reason)
  return { deleted: true, kept: true, immediate: true }
}

export async function finalizeDueAccountByPhone(phone) {
  const row = (
    await query(
      `SELECT id FROM users
       WHERE phone = $1 AND deleted_at IS NULL
         AND deletion_due_at IS NOT NULL AND deletion_due_at <= now()
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
     WHERE deleted_at IS NULL
       AND deletion_due_at IS NOT NULL AND deletion_due_at <= now()
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
