import { Router } from 'express'
import { query } from './db.js'
import {
  adminRequired,
  clientIp,
  hashPassword,
  normalizePassword,
  normalizePhone,
  recordPasswordEvent,
  rotateSessionVersion,
  signAdminToken,
  verifyPassword,
} from './auth.js'
import { mapDeviceRow } from './device.js'
import { todayShanghai } from './checkin.js'
import { getRewardVideoPoints, setRewardVideoPoints, REWARD_VIDEO_DAILY_LIMIT } from './rewardVideo.js'
import { getLevelRules, setLevelRules, syncUserLevel } from './levels.js'
import {
  GIFT_CATEGORY_DEFS,
  mapGift,
  mapOrder,
  normalizeGiftStock,
  normalizeGiftCategories,
  normalizeGiftImages,
  getGiftMallCategories,
  setGiftMallCategories,
} from './gifts.js'
import {
  SHORT_CATEGORIES,
  listAdminShorts,
  listUserShortFavoritesAdmin,
  mapShortVideo,
  normalizeCategory,
  normalizeKeywords,
  validateKeywords,
} from './shorts.js'
import { refundImageCreditOrder } from './pointOrders.js'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import express from 'express'

export const adminRouter = Router()

function pageParams(req, max = 100) {
  const page = Math.max(1, Number(req.query.page) || 1)
  const pageSize = Math.min(max, Math.max(1, Number(req.query.pageSize) || 20))
  return { page, pageSize, offset: (page - 1) * pageSize }
}

function iso(value) {
  return value ? new Date(value).toISOString() : null
}

async function audit(req, action, targetType, targetId, detail = null) {
  await query(
    `INSERT INTO admin_audit (admin_id, action, target_type, target_id, detail, ip)
     VALUES ($1, $2, $3, $4, $5::jsonb, $6)`,
    [
      req.admin.id,
      action,
      targetType || null,
      targetId == null ? null : String(targetId),
      detail ? JSON.stringify(detail) : null,
      clientIp(req),
    ],
  )
}

function mapUser(row) {
  return {
    id: Number(row.id),
    phone: row.phone,
    avatarUrl: row.avatar_url || null,
    hasPassword: Boolean(row.has_password ?? row.password_hash),
    createdAt: iso(row.created_at),
    lastLoginAt: iso(row.last_login_at),
    lastLoginMethod: row.last_login_method || null,
    passwordChangedAt: iso(row.password_changed_at),
    loginCount: Number(row.login_count || 0),
    notebookCount: Number(row.notebook_count || 0),
    wordCount: Number(row.word_count || 0),
    lastDeviceLabel: row.last_device_label || null,
    lastDevicePlatform: row.last_device_platform || null,
    lastIp: row.last_ip || null,
    lastIpLocation: row.last_ip_location || null,
    deviceCount: Number(row.device_count || 0),
    level: Math.min(7, Math.max(0, Number.isFinite(Number(row.user_level)) ? Number(row.user_level) : 0)),
    validDays: row.valid_days == null ? null : Math.max(0, Number(row.valid_days) || 0),
    imageCreditsPurchased: Math.max(0, Number(row.image_credits_purchased || 0)),
    imageCreditsRemaining: Math.max(0, Number(row.image_credits || 0)),
    nickname: row.nickname || null,
    gender: row.gender || null,
    region: row.region || null,
    buddyId: row.buddy_id || null,
    signature: row.signature || null,
    email: row.email || null,
    deletionRequestedAt: iso(row.deletion_requested_at),
    deletionDueAt: iso(row.deletion_due_at),
    shippingName: row.shipping_name || null,
    shippingPhone: row.shipping_phone || null,
    shippingDetail: row.shipping_detail || null,
  }
}

function mapLogin(row) {
  return {
    id: Number(row.id),
    userId: row.user_id ? Number(row.user_id) : null,
    phone: row.phone || null,
    method: row.method,
    success: Boolean(row.success),
    ip: row.ip,
    ipLocation: row.ip_location || null,
    userAgent: row.user_agent,
    devicePlatform: row.device_platform || null,
    deviceBrand: row.device_brand || null,
    deviceModel: row.device_model || null,
    deviceLabel: row.device_label || null,
    createdAt: iso(row.created_at),
  }
}

function mapNotebook(row) {
  return {
    id: Number(row.id),
    kind: row.kind,
    slug: row.slug,
    name: row.name,
    published: Boolean(row.published),
    sortOrder: row.sort_order,
    ownerUserId: row.owner_user_id ? Number(row.owner_user_id) : null,
    ownerPhone: row.owner_phone || null,
    wordCount: Number(row.word_count || 0),
    createdAt: iso(row.created_at),
  }
}

function mapWord(row) {
  const defs = Array.isArray(row.definitions) ? row.definitions : []
  const examples = Array.isArray(row.examples) ? row.examples : []
  return {
    id: Number(row.id),
    notebookId: Number(row.notebook_id),
    text: row.word,
    isPhrase: Boolean(row.is_phrase),
    ipaUk: row.ipa_uk,
    ipaUs: row.ipa_us,
    definitions: defs,
    examples: examples.map((e) => ({
      english: e?.en ?? e?.english ?? '',
      chinese: e?.zh ?? e?.chinese ?? '',
    })),
    nearWords: Array.isArray(row.near_words) ? row.near_words : [],
    synonyms: Array.isArray(row.synonyms) ? row.synonyms : [],
    antonyms: Array.isArray(row.antonyms) ? row.antonyms : [],
    sortOrder: row.sort_order,
    addedAt: iso(row.added_at),
    images: [],
  }
}

function mnemonicWordKey(word) {
  return String(word || '').trim().toLowerCase().slice(0, 80)
}

function sniffImageType(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) return 'image/jpeg'
  if (buf[0] === 0xff && buf[1] === 0xd8) return 'image/jpeg'
  if (buf[0] === 0x89 && buf[1] === 0x50) return 'image/png'
  if (buf[0] === 0x47 && buf[1] === 0x49) return 'image/gif'
  if (buf[0] === 0x52 && buf[1] === 0x49 && buf[8] === 0x57) return 'image/webp'
  return 'image/jpeg'
}

async function attachMnemonicImages(items) {
  const keys = [...new Set(items.map((item) => mnemonicWordKey(item.text)).filter(Boolean))]
  if (!keys.length) return items
  const result = await query(
    `SELECT id, word_key, provider, meaning_key, created_at
     FROM mnemonic_images
     WHERE word_key = ANY($1::text[])
     ORDER BY created_at DESC, id DESC`,
    [keys],
  )
  const byKey = new Map()
  for (const row of result.rows) {
    const list = byKey.get(row.word_key) || []
    list.push({
      id: Number(row.id),
      provider: row.provider,
      meaningKey: row.meaning_key || '',
      createdAt: iso(row.created_at),
    })
    byKey.set(row.word_key, list)
  }
  for (const item of items) {
    item.images = byKey.get(mnemonicWordKey(item.text)) || []
  }
  return items
}

function acceptAdminQueryToken(req, _res, next) {
  if (!req.headers.authorization && req.query?.token) {
    req.headers.authorization = `Bearer ${String(req.query.token)}`
  }
  next()
}

adminRouter.post('/login', async (req, res) => {
  const username = String(req.body?.username || '').trim()
  const password = String(req.body?.password || '')
  if (!username || !password) {
    res.status(400).json({ error: '请输入管理员账号和密码' })
    return
  }
  const admin = (await query('SELECT id, username, password_hash FROM admins WHERE username = $1', [username]))
    .rows[0]
  if (!admin || !verifyPassword(password, admin.password_hash)) {
    res.status(400).json({ error: '账号或密码错误' })
    return
  }
  await query('UPDATE admins SET last_login_at = now() WHERE id = $1', [admin.id])
  res.json({
    token: signAdminToken(admin),
    admin: { id: Number(admin.id), username: admin.username },
  })
})

adminRouter.get('/me', adminRequired, async (req, res) => {
  const row = (await query('SELECT id, username, created_at, last_login_at FROM admins WHERE id = $1', [req.admin.id]))
    .rows[0]
  res.json({
    admin: {
      id: Number(row.id),
      username: row.username,
      createdAt: iso(row.created_at),
      lastLoginAt: iso(row.last_login_at),
    },
  })
})

adminRouter.get('/overview', adminRequired, async (_req, res) => {
  const today = todayShanghai()
  const [users, logins, words, catalogs, userBooks, sms, checkInToday, checkInUsers] = await Promise.all([
    query('SELECT count(*)::int AS n FROM users'),
    query(`SELECT count(*)::int AS n FROM login_events WHERE created_at > now() - interval '7 days' AND success = TRUE`),
    query('SELECT count(*)::int AS n FROM words'),
    query(`SELECT count(*)::int AS n FROM notebooks WHERE kind = 'catalog'`),
    query(`SELECT count(*)::int AS n FROM notebooks WHERE kind = 'user'`),
    query(`SELECT count(*)::int AS n FROM sms_codes WHERE created_at > now() - interval '24 hours'`),
    query(`SELECT count(*)::int AS n FROM user_checkin_logs WHERE checkin_date = $1::date`, [today]),
    query(`SELECT count(*)::int AS n FROM user_checkins WHERE total_points > 0`),
  ])
  const recentUsers = await query(
    `SELECT id, phone, avatar_url, created_at, last_login_at, login_count,
            last_device_label, last_device_platform, last_ip, last_ip_location, user_level,
            nickname, gender, region, buddy_id, signature, email,
            shipping_name, shipping_phone, shipping_detail,
            image_credits,
            COALESCE((
              SELECT SUM(o.points)::int FROM point_orders o
              WHERE o.user_id = users.id AND o.grant_kind = 'images' AND o.status = 'paid'
            ), 0) AS image_credits_purchased,
            (password_hash IS NOT NULL) AS has_password,
            (SELECT count(*)::int FROM user_devices d WHERE d.user_id = users.id) AS device_count
     FROM users ORDER BY created_at DESC LIMIT 8`,
  )
  res.json({
    stats: {
      userCount: users.rows[0].n,
      login7d: logins.rows[0].n,
      wordCount: words.rows[0].n,
      catalogCount: catalogs.rows[0].n,
      userNotebookCount: userBooks.rows[0].n,
      sms24h: sms.rows[0].n,
      checkInToday: checkInToday.rows[0].n,
      checkInUsers: checkInUsers.rows[0].n,
    },
    recentUsers: recentUsers.rows.map(mapUser),
  })
})

function addCalendarMonths(year, month, delta) {
  const total = year * 12 + (month - 1) + delta
  const y = Math.floor(total / 12)
  const m = (total % 12) + 1
  return { year: y, month: m }
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

function resolveCockpitPeriod(grainKey, rawAt) {
  const today = todayShanghai()
  const [ty, tm, td] = today.split('-').map(Number)
  const grain = ['day', 'month', 'year'].includes(grainKey) ? grainKey : 'day'
  if (grain === 'day') {
    const at = /^\d{4}-\d{2}-\d{2}$/.test(String(rawAt || '')) ? String(rawAt) : today
    const [y, m, d] = at.split('-').map(Number)
    const next = new Date(Date.UTC(y, m - 1, d + 1))
    const end = `${next.getUTCFullYear()}-${pad2(next.getUTCMonth() + 1)}-${pad2(next.getUTCDate())}`
    return {
      grain,
      at,
      label: `${y}年${m}月${d}日`,
      startLocal: `${at} 00:00:00`,
      endLocal: `${end} 00:00:00`,
      trunc: 'hour',
      step: '1 hour',
      fmt: 'HH24时',
      bucketLabel: '按小时',
    }
  }
  if (grain === 'month') {
    const at = /^\d{4}-\d{2}$/.test(String(rawAt || '')) ? String(rawAt) : `${ty}-${pad2(tm)}`
    const [y, m] = at.split('-').map(Number)
    const next = addCalendarMonths(y, m, 1)
    return {
      grain,
      at,
      label: `${y}年${m}月`,
      startLocal: `${y}-${pad2(m)}-01 00:00:00`,
      endLocal: `${next.year}-${pad2(next.month)}-01 00:00:00`,
      trunc: 'day',
      step: '1 day',
      fmt: 'DD日',
      bucketLabel: '按天',
    }
  }
  const year = /^\d{4}$/.test(String(rawAt || '')) ? Number(rawAt) : ty
  return {
    grain: 'year',
    at: String(year),
    label: `${year}年`,
    startLocal: `${year}-01-01 00:00:00`,
    endLocal: `${year + 1}-01-01 00:00:00`,
    trunc: 'month',
    step: '1 month',
    fmt: 'MM月',
    bucketLabel: '按月',
  }
}

adminRouter.get('/cockpit', adminRequired, async (req, res) => {
  const period = resolveCockpitPeriod(String(req.query.grain || ''), req.query.at)
  const localTs = (expr) => `timezone('Asia/Shanghai', ${expr})`
  const inWindow = (expr) =>
    `${localTs(expr)} >= $1::timestamp AND ${localTs(expr)} < $2::timestamp`
  const bucket = (expr) => `date_trunc('${period.trunc}', ${expr})`
  const bounds = [period.startLocal, period.endLocal]
  const series = await query(
    `
    WITH buckets AS (
      SELECT generate_series($1::timestamp, $2::timestamp - interval '${period.step}', interval '${period.step}') AS bucket
    ),
    signups AS (
      SELECT ${bucket(localTs('created_at'))} AS bucket, count(*)::int AS n
      FROM users
      WHERE ${inWindow('created_at')}
      GROUP BY 1
    ),
    deletions AS (
      SELECT ${bucket(localTs('completed_at'))} AS bucket, count(*)::int AS n
      FROM account_deletion_logs
      WHERE ${inWindow('completed_at')}
      GROUP BY 1
    ),
    actives AS (
      SELECT ${bucket(localTs('created_at'))} AS bucket,
             count(DISTINCT user_id)::int AS n
      FROM login_events
      WHERE success = TRUE AND user_id IS NOT NULL
        AND ${inWindow('created_at')}
      GROUP BY 1
    ),
    watches AS (
      SELECT ${bucket(localTs('created_at'))} AS bucket,
             COALESCE(SUM(watch_ms), 0)::bigint AS ms
      FROM short_video_watches
      WHERE ${inWindow('created_at')}
      GROUP BY 1
    ),
    checkins AS (
      SELECT ${bucket('checkin_date::timestamp')} AS bucket,
             count(*)::int AS n
      FROM user_checkin_logs
      WHERE checkin_date >= $1::date AND checkin_date < $2::date
      GROUP BY 1
    ),
    orders AS (
      SELECT ${bucket(localTs('created_at'))} AS bucket,
             count(*)::int AS n,
             COALESCE(SUM(points_spent), 0)::int AS points
      FROM gift_orders
      WHERE ${inWindow('created_at')}
      GROUP BY 1
    ),
    logins AS (
      SELECT ${bucket(localTs('created_at'))} AS bucket,
             count(*) FILTER (WHERE success)::int AS ok,
             count(*) FILTER (WHERE NOT success)::int AS fail
      FROM login_events
      WHERE ${inWindow('created_at')}
      GROUP BY 1
    )
    SELECT to_char(b.bucket, '${period.fmt}') AS label,
           COALESCE(s.n, 0)::int AS signups,
           COALESCE(d.n, 0)::int AS deletions,
           COALESCE(a.n, 0)::int AS active_users,
           COALESCE(w.ms, 0)::bigint AS watch_ms,
           COALESCE(c.n, 0)::int AS checkins,
           COALESCE(o.n, 0)::int AS gift_orders,
           COALESCE(o.points, 0)::int AS gift_points,
           COALESCE(l.ok, 0)::int AS login_ok,
           COALESCE(l.fail, 0)::int AS login_fail
    FROM buckets b
    LEFT JOIN signups s ON s.bucket = b.bucket
    LEFT JOIN deletions d ON d.bucket = b.bucket
    LEFT JOIN actives a ON a.bucket = b.bucket
    LEFT JOIN watches w ON w.bucket = b.bucket
    LEFT JOIN checkins c ON c.bucket = b.bucket
    LEFT JOIN orders o ON o.bucket = b.bucket
    LEFT JOIN logins l ON l.bucket = b.bucket
    ORDER BY b.bucket
    `,
    bounds,
  )
  const [summary, platforms] = await Promise.all([
    query(
      `
      SELECT
        (SELECT count(*)::int FROM users) AS users,
        (SELECT count(*)::int FROM users WHERE deletion_due_at IS NOT NULL) AS pending_deletions,
        (SELECT count(*)::int FROM account_deletion_logs) AS deleted_total,
        (SELECT count(DISTINCT user_id)::int FROM login_events
           WHERE success = TRUE AND user_id IS NOT NULL
             AND created_at >= (date_trunc('day', timezone('Asia/Shanghai', now())) AT TIME ZONE 'Asia/Shanghai')) AS dau,
        (SELECT count(DISTINCT user_id)::int FROM login_events
           WHERE success = TRUE AND user_id IS NOT NULL
             AND created_at >= now() - interval '7 days') AS wau,
        (SELECT count(DISTINCT user_id)::int FROM login_events
           WHERE success = TRUE AND user_id IS NOT NULL
             AND created_at >= now() - interval '30 days') AS mau,
        (SELECT COALESCE(SUM(total_points), 0)::bigint FROM user_checkins) AS points_outstanding,
        (SELECT COALESCE(SUM(watch_ms), 0)::bigint FROM short_video_watches) AS watch_ms_all,
        (SELECT count(*)::int FROM withdrawals WHERE status = 'pending') AS withdrawals_pending,
        (SELECT COALESCE(SUM(amount_fen), 0)::bigint FROM withdrawals WHERE status = 'pending') AS withdrawals_pending_fen,
        (SELECT count(DISTINCT user_id)::int FROM login_events
           WHERE success = TRUE AND user_id IS NOT NULL
             AND ${inWindow('created_at')}) AS period_active,
        (SELECT count(DISTINCT user_id)::int FROM short_video_watches
           WHERE user_id IS NOT NULL
             AND ${inWindow('created_at')}) AS watch_users_window,
        (SELECT COALESCE(SUM(watch_ms), 0)::bigint FROM short_video_watches
           WHERE ${inWindow('created_at')}) AS watch_ms_window
      `,
      bounds,
    ),
    query(
      `
      SELECT COALESCE(NULLIF(lower(device_platform), ''), 'unknown') AS platform,
             count(DISTINCT user_id)::int AS users
      FROM login_events
      WHERE success = TRUE AND user_id IS NOT NULL
        AND ${inWindow('created_at')}
      GROUP BY 1
      ORDER BY users DESC, platform
      `,
      bounds,
    ),
  ])
  const row = summary.rows[0] || {}
  res.json({
    grain: period.grain,
    at: period.at,
    label: period.label,
    bucketLabel: period.bucketLabel,
    today: todayShanghai(),
    points: series.rows.map((item) => ({
      label: item.label,
      signups: Number(item.signups || 0),
      deletions: Number(item.deletions || 0),
      activeUsers: Number(item.active_users || 0),
      watchMs: Number(item.watch_ms || 0),
      checkins: Number(item.checkins || 0),
      giftOrders: Number(item.gift_orders || 0),
      giftPoints: Number(item.gift_points || 0),
      loginOk: Number(item.login_ok || 0),
      loginFail: Number(item.login_fail || 0),
    })),
    summary: {
      users: Number(row.users || 0),
      pendingDeletions: Number(row.pending_deletions || 0),
      deletedTotal: Number(row.deleted_total || 0),
      dau: Number(row.dau || 0),
      wau: Number(row.wau || 0),
      mau: Number(row.mau || 0),
      periodActive: Number(row.period_active || 0),
      pointsOutstanding: Number(row.points_outstanding || 0),
      watchMsAll: Number(row.watch_ms_all || 0),
      withdrawalsPending: Number(row.withdrawals_pending || 0),
      withdrawalsPendingFen: Number(row.withdrawals_pending_fen || 0),
      watchUsersWindow: Number(row.watch_users_window || 0),
      watchMsWindow: Number(row.watch_ms_window || 0),
    },
    platforms: platforms.rows.map((item) => ({
      platform: item.platform,
      users: Number(item.users || 0),
    })),
  })
})

adminRouter.get('/users', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const params = []
  let where = 'TRUE'
  if (q) {
    params.push(`%${q}%`)
    where = `(u.phone ILIKE $1 OR u.nickname ILIKE $1 OR u.buddy_id ILIKE $1 OR u.email ILIKE $1 OR u.shipping_name ILIKE $1 OR u.shipping_phone ILIKE $1)`
  }
  const total = (
    await query(`SELECT count(*)::int AS n FROM users u WHERE ${where}`, params)
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `
    SELECT u.id, u.phone, u.avatar_url, u.created_at, u.last_login_at, u.last_login_method,
           u.password_changed_at, u.login_count,
           u.last_device_label, u.last_device_platform, u.last_ip, u.last_ip_location, u.user_level,
           u.nickname, u.gender, u.region, u.buddy_id, u.signature, u.email,
           u.shipping_name, u.shipping_phone, u.shipping_detail,
           u.deletion_requested_at, u.deletion_due_at,
           u.image_credits,
           COALESCE((
             SELECT SUM(o.points)::int FROM point_orders o
             WHERE o.user_id = u.id AND o.grant_kind = 'images' AND o.status = 'paid'
           ), 0) AS image_credits_purchased,
           (u.password_hash IS NOT NULL) AS has_password,
           (SELECT count(*)::int FROM notebooks n WHERE n.owner_user_id = u.id) AS notebook_count,
           (SELECT count(*)::int FROM words w
             JOIN notebooks n ON n.id = w.notebook_id
            WHERE n.owner_user_id = u.id) AS word_count,
           (SELECT count(*)::int FROM user_devices d WHERE d.user_id = u.id) AS device_count
    FROM users u
    WHERE ${where}
    ORDER BY u.created_at DESC
    LIMIT $${params.length - 1} OFFSET $${params.length}
    `,
    params,
  )
  res.json({ items: result.rows.map(mapUser), total, page, pageSize })
})

adminRouter.get('/users/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const user = (
    await query(
      `SELECT id, phone, avatar_url, created_at, last_login_at, last_login_method,
              password_changed_at, login_count,
              last_device_label, last_device_platform, last_ip, last_ip_location, user_level,
              nickname, gender, region, buddy_id, signature, email,
              shipping_name, shipping_phone, shipping_detail,
              deletion_requested_at, deletion_due_at,
              image_credits,
              COALESCE((
                SELECT SUM(o.points)::int FROM point_orders o
                WHERE o.user_id = users.id AND o.grant_kind = 'images' AND o.status = 'paid'
              ), 0) AS image_credits_purchased,
              (password_hash IS NOT NULL) AS has_password,
              (SELECT count(*)::int FROM user_devices d WHERE d.user_id = users.id) AS device_count
       FROM users WHERE id = $1`,
      [id],
    )
  ).rows[0]
  if (!user) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  const perk = await syncUserLevel(id)
  user.user_level = perk.level
  user.valid_days = perk.validDays
  const [notebooks, logins, passwords, sms, devices, checkIn, checkInLogs, shortFavorites, withdrawals, withdrawalSummary, imageOrders] =
    await Promise.all([
    query(
      `SELECT n.id, n.kind, n.slug, n.name, n.published, n.sort_order, n.owner_user_id, n.created_at,
              (SELECT count(*)::int FROM words w WHERE w.notebook_id = n.id) AS word_count
       FROM notebooks n WHERE n.owner_user_id = $1
       ORDER BY n.sort_order ASC, n.id ASC`,
      [id],
    ),
    query(
      `SELECT id, user_id, phone, method, success, ip, user_agent,
              device_platform, device_brand, device_model, device_label, ip_location, created_at
       FROM login_events WHERE user_id = $1
       ORDER BY created_at DESC LIMIT 50`,
      [id],
    ),
    query(
      `SELECT id, reason, ip, created_at
       FROM password_events WHERE user_id = $1
       ORDER BY created_at DESC LIMIT 50`,
      [id],
    ),
    query(
      `SELECT id, created_at, expires_at, consumed_at,
              (consumed_at IS NOT NULL) AS consumed
       FROM sms_codes WHERE phone = $1
       ORDER BY created_at DESC LIMIT 30`,
      [user.phone],
    ),
    query(
      `SELECT id, platform, brand, model, label, os_version, app_version,
              last_ip, last_ip_location, first_seen_at, last_seen_at, login_count
       FROM user_devices WHERE user_id = $1
       ORDER BY last_seen_at DESC, id DESC`,
      [id],
    ),
    query(
      `SELECT total_points, streak_days, last_checkin_date, updated_at
       FROM user_checkins WHERE user_id = $1`,
      [id],
    ),
    query(
      `SELECT id, checkin_date, streak_days, points_earned, created_at
       FROM user_checkin_logs WHERE user_id = $1
       ORDER BY checkin_date DESC LIMIT 60`,
      [id],
    ),
    listUserShortFavoritesAdmin({ userId: id, limit: 80 }),
    query(
      `SELECT id, user_id, channel, account, amount_fen, points_spent, status,
              provider_trade_no, error_message, sandbox, created_at, updated_at
       FROM withdrawals WHERE user_id = $1
       ORDER BY created_at DESC LIMIT 80`,
      [id],
    ),
    query(
      `SELECT
         count(*)::int AS n,
         count(*) FILTER (WHERE status = 'success')::int AS success_n,
         count(*) FILTER (WHERE status = 'pending')::int AS pending_n,
         count(*) FILTER (WHERE status = 'failed')::int AS failed_n,
         COALESCE(SUM(amount_fen) FILTER (WHERE status = 'success'), 0)::bigint AS success_fen,
         COALESCE(SUM(points_spent) FILTER (WHERE status = 'success'), 0)::bigint AS success_points
       FROM withdrawals WHERE user_id = $1`,
      [id],
    ),
    query(
      `SELECT id, package_id, points, amount_fen, status, pay_channel, paid_at, created_at,
              refunded_at, refund_amount_fen, refunded_credits, refund_reason
       FROM point_orders
       WHERE user_id = $1 AND grant_kind = 'images'
       ORDER BY id DESC LIMIT 40`,
      [id],
    ),
  ])
  const checkInRow = checkIn.rows[0]
  res.json({
    user: mapUser(user),
    notebooks: notebooks.rows.map(mapNotebook),
    devices: devices.rows.map(mapDeviceRow),
    logins: logins.rows.map(mapLogin),
    passwordEvents: passwords.rows.map((row) => ({
      id: Number(row.id),
      reason: row.reason,
      ip: row.ip,
      createdAt: iso(row.created_at),
    })),
    sms: sms.rows.map((row) => ({
      id: Number(row.id),
      createdAt: iso(row.created_at),
      expiresAt: iso(row.expires_at),
      consumedAt: iso(row.consumed_at),
      consumed: Boolean(row.consumed),
    })),
    checkIn: checkInRow
      ? {
          totalPoints: Number(checkInRow.total_points || 0),
          streakDays: Number(checkInRow.streak_days || 0),
          lastCheckInDate: checkInRow.last_checkin_date
            ? String(checkInRow.last_checkin_date).slice(0, 10)
            : null,
          updatedAt: iso(checkInRow.updated_at),
        }
      : { totalPoints: 0, streakDays: 0, lastCheckInDate: null, updatedAt: null },
    checkInLogs: checkInLogs.rows.map((row) => ({
      id: Number(row.id),
      checkInDate: String(row.checkin_date).slice(0, 10),
      streakDays: Number(row.streak_days || 0),
      pointsEarned: Number(row.points_earned || 0),
      createdAt: iso(row.created_at),
    })),
    shortFavorites,
    withdrawals: withdrawals.rows.map(mapAdminWithdrawal),
    withdrawalSummary: {
      total: Number(withdrawalSummary.rows[0]?.n || 0),
      successCount: Number(withdrawalSummary.rows[0]?.success_n || 0),
      pendingCount: Number(withdrawalSummary.rows[0]?.pending_n || 0),
      failedCount: Number(withdrawalSummary.rows[0]?.failed_n || 0),
      successFen: Number(withdrawalSummary.rows[0]?.success_fen || 0),
      successPoints: Number(withdrawalSummary.rows[0]?.success_points || 0),
    },
    imageOrders: imageOrders.rows.map((row) => {
      const amountFen = Math.max(0, Number(row.amount_fen || 0))
      const status = row.status || 'pending'
      return {
        id: Number(row.id),
        packageId: row.package_id,
        title: { img10: '10 张', img100: '100 张', img500: '500 张' }[row.package_id] || `${Number(row.points || 0)} 张`,
        credits: Number(row.points || 0),
        amountFen,
        amountYuan: (amountFen / 100).toFixed(2),
        status,
        statusLabel:
          status === 'paid'
            ? '已支付'
            : status === 'refunded'
              ? '已退款'
              : status === 'closed'
                ? '已关闭'
                : '待支付',
        payChannel: row.pay_channel || null,
        paidAt: iso(row.paid_at),
        createdAt: iso(row.created_at),
        refundedAt: iso(row.refunded_at),
        refundedFen: row.refund_amount_fen == null ? null : Number(row.refund_amount_fen),
        refundedCredits: row.refunded_credits == null ? null : Number(row.refunded_credits),
        refundReason: row.refund_reason || null,
      }
    }),
  })
})

adminRouter.post('/point-orders/:id/refund', adminRequired, async (req, res) => {
  try {
    const result = await refundImageCreditOrder({
      orderId: Number(req.params.id),
      reason: req.body?.reason,
    })
    if (!result.ok) {
      res.status(400).json({ error: result.error || '退款失败' })
      return
    }
    await audit(req, 'refund_image_order', 'point_order', req.params.id, {
      refundedFen: result.refundedFen,
      clawedCredits: result.clawedCredits,
      grantedCredits: result.grantedCredits,
      imageCredits: result.imageCredits,
    })
    res.json({
      ok: true,
      refundedFen: result.refundedFen,
      clawedCredits: result.clawedCredits,
      grantedCredits: result.grantedCredits,
      imageCredits: result.imageCredits,
    })
  } catch (error) {
    console.error('[admin/refund]', error)
    res.status(500).json({ error: '退款失败' })
  }
})

adminRouter.patch('/users/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const user = (await query('SELECT id, phone FROM users WHERE id = $1', [id])).rows[0]
  if (!user) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  const nextPhone = req.body?.phone != null ? normalizePhone(req.body.phone) : null
  const nextPassword = req.body?.password ? normalizePassword(req.body.password) : null
  const hasLevel = req.body?.level != null && req.body?.level !== ''
  const nextLevel = hasLevel ? Number(req.body.level) : null
  if (req.body?.phone && !nextPhone) {
    res.status(400).json({ error: '手机号不正确' })
    return
  }
  if (req.body?.password && !nextPassword) {
    res.status(400).json({ error: '密码需要 6 到 32 位' })
    return
  }
  if (hasLevel && (!Number.isFinite(nextLevel) || nextLevel < 0 || nextLevel > 7 || !Number.isInteger(nextLevel))) {
    res.status(400).json({ error: '用户等级需为 0–7' })
    return
  }
  if (nextPhone && nextPhone !== user.phone) {
    await query('UPDATE users SET phone = $1 WHERE id = $2', [nextPhone, id])
  }
  if (nextPassword) {
    await query('UPDATE users SET password_hash = $1 WHERE id = $2', [hashPassword(nextPassword), id])
    await recordPasswordEvent(req, id, 'admin_reset')
    // A reset is usually a response to a compromise: drop the old sessions too.
    await rotateSessionVersion(id)
  }
  if (hasLevel) {
    await query('UPDATE users SET user_level = $1 WHERE id = $2', [nextLevel, id])
  }
  await audit(req, 'update_user', 'user', id, {
    phone: Boolean(nextPhone),
    resetPassword: Boolean(nextPassword),
    level: hasLevel ? nextLevel : undefined,
  })
  const fresh = (
    await query(
      `SELECT id, phone, avatar_url, created_at, last_login_at, last_login_method,
              password_changed_at, login_count,
              last_device_label, last_device_platform, last_ip, last_ip_location, user_level,
              nickname, gender, region, buddy_id, signature, email,
              shipping_name, shipping_phone, shipping_detail,
              (password_hash IS NOT NULL) AS has_password,
              (SELECT count(*)::int FROM user_devices d WHERE d.user_id = users.id) AS device_count
       FROM users WHERE id = $1`,
      [id],
    )
  ).rows[0]
  res.json({ item: mapUser(fresh) })
})

adminRouter.delete('/users/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const user = (await query('SELECT id, phone FROM users WHERE id = $1', [id])).rows[0]
  if (!user) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  await query('DELETE FROM users WHERE id = $1', [id])
  await audit(req, 'delete_user', 'user', id, { phone: user.phone })
  res.json({ ok: true })
})

adminRouter.get('/logins', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const params = []
  let where = 'TRUE'
  if (q) {
    params.push(`%${q}%`)
    where =
      '(e.phone ILIKE $1 OR e.method ILIKE $1 OR e.ip ILIKE $1 OR e.device_label ILIKE $1 OR e.ip_location ILIKE $1 OR e.device_platform ILIKE $1)'
  }
  const total = (
    await query(`SELECT count(*)::int AS n FROM login_events e WHERE ${where}`, params)
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `
    SELECT e.id, e.user_id, e.phone, e.method, e.success, e.ip, e.user_agent,
           e.device_platform, e.device_brand, e.device_model, e.device_label, e.ip_location, e.created_at
    FROM login_events e
    WHERE ${where}
    ORDER BY e.created_at DESC
    LIMIT $${params.length - 1} OFFSET $${params.length}
    `,
    params,
  )
  res.json({
    items: result.rows.map(mapLogin),
    total,
    page,
    pageSize,
  })
})

adminRouter.get('/sms', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const params = []
  let where = 'TRUE'
  if (q) {
    params.push(`%${q}%`)
    where = 'phone ILIKE $1'
  }
  const total = (
    await query(`SELECT count(*)::int AS n FROM sms_codes WHERE ${where}`, params)
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `
    SELECT id, phone, created_at, expires_at, consumed_at
    FROM sms_codes
    WHERE ${where}
    ORDER BY created_at DESC
    LIMIT $${params.length - 1} OFFSET $${params.length}
    `,
    params,
  )
  res.json({
    items: result.rows.map((row) => ({
      id: Number(row.id),
      phone: row.phone,
      createdAt: iso(row.created_at),
      expiresAt: iso(row.expires_at),
      consumedAt: iso(row.consumed_at),
      consumed: Boolean(row.consumed_at),
    })),
    total,
    page,
    pageSize,
  })
})

adminRouter.get('/notebooks', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const kind = String(req.query.kind || '').trim()
  const q = String(req.query.q || '').trim()
  const params = []
  const clauses = []
  if (kind === 'catalog' || kind === 'user') {
    params.push(kind)
    clauses.push(`n.kind = $${params.length}`)
  }
  if (q) {
    params.push(`%${q}%`)
    clauses.push(`(n.name ILIKE $${params.length} OR n.slug ILIKE $${params.length} OR u.phone ILIKE $${params.length})`)
  }
  const where = clauses.length ? clauses.join(' AND ') : 'TRUE'
  const total = (
    await query(
      `SELECT count(*)::int AS n
       FROM notebooks n
       LEFT JOIN users u ON u.id = n.owner_user_id
       WHERE ${where}`,
      params,
    )
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `
    SELECT n.id, n.kind, n.slug, n.name, n.published, n.sort_order, n.owner_user_id, n.created_at,
           u.phone AS owner_phone,
           (SELECT count(*)::int FROM words w WHERE w.notebook_id = n.id) AS word_count
    FROM notebooks n
    LEFT JOIN users u ON u.id = n.owner_user_id
    WHERE ${where}
    ORDER BY n.kind ASC, n.sort_order ASC, n.id ASC
    LIMIT $${params.length - 1} OFFSET $${params.length}
    `,
    params,
  )
  res.json({ items: result.rows.map(mapNotebook), total, page, pageSize })
})

adminRouter.patch('/notebooks/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = (await query('SELECT * FROM notebooks WHERE id = $1', [id])).rows[0]
  if (!current) {
    res.status(404).json({ error: '词本不存在' })
    return
  }
  const name = req.body?.name != null ? String(req.body.name).trim() : current.name
  const published = req.body?.published == null ? current.published : Boolean(req.body.published)
  const sortOrder =
    req.body?.sortOrder == null ? current.sort_order : Number(req.body.sortOrder) || 0
  if (!name) {
    res.status(400).json({ error: '名称不能为空' })
    return
  }
  const updated = (
    await query(
      `UPDATE notebooks SET name = $1, published = $2, sort_order = $3
       WHERE id = $4
       RETURNING id, kind, slug, name, published, sort_order, owner_user_id, created_at`,
      [name, published, sortOrder, id],
    )
  ).rows[0]
  await audit(req, 'update_notebook', 'notebook', id, { name, published, sortOrder })
  const count = (
    await query('SELECT count(*)::int AS n FROM words WHERE notebook_id = $1', [id])
  ).rows[0].n
  res.json({ item: mapNotebook({ ...updated, word_count: count }) })
})

adminRouter.delete('/notebooks/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = (await query('SELECT * FROM notebooks WHERE id = $1', [id])).rows[0]
  if (!current) {
    res.status(404).json({ error: '词本不存在' })
    return
  }
  if (current.kind === 'catalog') {
    res.status(403).json({ error: '系统词书请用「下架」而不是删除，以免 App 丢书' })
    return
  }
  await query('DELETE FROM notebooks WHERE id = $1', [id])
  await audit(req, 'delete_notebook', 'notebook', id, { name: current.name })
  res.json({ ok: true })
})

adminRouter.get('/notebooks/:id/words', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const notebook = (await query('SELECT id, name FROM notebooks WHERE id = $1', [id])).rows[0]
  if (!notebook) {
    res.status(404).json({ error: '词本不存在' })
    return
  }
  const { page, pageSize, offset } = pageParams(req, 200)
  const q = String(req.query.q || '').trim()
  const params = [id]
  let where = 'notebook_id = $1'
  if (q) {
    params.push(`%${q}%`)
    where += ` AND word ILIKE $${params.length}`
  }
  const total = (
    await query(`SELECT count(*)::int AS n FROM words WHERE ${where}`, params)
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `SELECT * FROM words WHERE ${where}
     ORDER BY sort_order ASC, id ASC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  )
  const items = await attachMnemonicImages(result.rows.map(mapWord))
  res.json({
    notebook: { id: Number(notebook.id), name: notebook.name },
    items,
    total,
    page,
    pageSize,
  })
})

adminRouter.get('/mnemonic-images/:id', acceptAdminQueryToken, adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  if (!Number.isFinite(id) || id <= 0) {
    res.status(400).json({ error: '图片不存在' })
    return
  }
  const row = (await query('SELECT image FROM mnemonic_images WHERE id = $1', [id])).rows[0]
  if (!row?.image) {
    res.status(404).json({ error: '图片不存在' })
    return
  }
  const buf = Buffer.isBuffer(row.image) ? row.image : Buffer.from(row.image)
  res.setHeader('Content-Type', sniffImageType(buf))
  res.setHeader('Cache-Control', 'private, max-age=3600')
  res.send(buf)
})

adminRouter.post('/notebooks/:id/words', adminRequired, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = (await query('SELECT id FROM notebooks WHERE id = $1', [notebookId])).rows[0]
  if (!notebook) {
    res.status(404).json({ error: '词本不存在' })
    return
  }
  const text = String(req.body?.text || '').trim()
  if (!text) {
    res.status(400).json({ error: '单词不能为空' })
    return
  }
  const maxSort = (
    await query('SELECT coalesce(max(sort_order), -1) + 1 AS n FROM words WHERE notebook_id = $1', [
      notebookId,
    ])
  ).rows[0].n
  const inserted = await query(
    `INSERT INTO words (
       notebook_id, word, is_phrase, ipa_uk, ipa_us,
       definitions, examples, near_words, synonyms, antonyms, sort_order
     ) VALUES (
       $1, $2, $3, $4, $5,
       $6::jsonb, $7::jsonb, $8::jsonb, $9::jsonb, $10::jsonb, $11
     ) RETURNING *`,
    [
      notebookId,
      text,
      Boolean(req.body?.isPhrase || text.includes(' ')),
      req.body?.ipaUk || null,
      req.body?.ipaUs || null,
      JSON.stringify(req.body?.definitions || []),
      JSON.stringify(
        (req.body?.examples || []).map((e) => ({
          en: e.english ?? e.en ?? '',
          zh: e.chinese ?? e.zh ?? '',
        })),
      ),
      JSON.stringify(req.body?.nearWords || []),
      JSON.stringify(req.body?.synonyms || []),
      JSON.stringify(req.body?.antonyms || []),
      maxSort,
    ],
  )
  await audit(req, 'create_word', 'word', inserted.rows[0].id, { notebookId, text })
  res.status(201).json({ item: mapWord(inserted.rows[0]) })
})

adminRouter.patch('/words/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = (await query('SELECT * FROM words WHERE id = $1', [id])).rows[0]
  if (!current) {
    res.status(404).json({ error: '词条不存在' })
    return
  }
  const body = req.body || {}
  const updated = await query(
    `UPDATE words SET
       word = COALESCE($2, word),
       is_phrase = COALESCE($3, is_phrase),
       ipa_uk = COALESCE($4, ipa_uk),
       ipa_us = COALESCE($5, ipa_us),
       definitions = COALESCE($6::jsonb, definitions),
       examples = COALESCE($7::jsonb, examples),
       near_words = COALESCE($8::jsonb, near_words),
       synonyms = COALESCE($9::jsonb, synonyms),
       antonyms = COALESCE($10::jsonb, antonyms)
     WHERE id = $1
     RETURNING *`,
    [
      id,
      body.text != null ? String(body.text).trim() : null,
      body.isPhrase == null ? null : Boolean(body.isPhrase),
      body.ipaUk ?? null,
      body.ipaUs ?? null,
      body.definitions ? JSON.stringify(body.definitions) : null,
      body.examples
        ? JSON.stringify(
            body.examples.map((e) => ({
              en: e.english ?? e.en ?? '',
              zh: e.chinese ?? e.zh ?? '',
            })),
          )
        : null,
      body.nearWords ? JSON.stringify(body.nearWords) : null,
      body.synonyms ? JSON.stringify(body.synonyms) : null,
      body.antonyms ? JSON.stringify(body.antonyms) : null,
    ],
  )
  await audit(req, 'update_word', 'word', id, { text: updated.rows[0].word })
  res.json({ item: mapWord(updated.rows[0]) })
})

adminRouter.delete('/words/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = (await query('SELECT id, word FROM words WHERE id = $1', [id])).rows[0]
  if (!current) {
    res.status(404).json({ error: '词条不存在' })
    return
  }
  await query('DELETE FROM words WHERE id = $1', [id])
  await audit(req, 'delete_word', 'word', id, { text: current.word })
  res.json({ ok: true })
})

adminRouter.get('/audit', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const total = (await query('SELECT count(*)::int AS n FROM admin_audit')).rows[0].n
  const result = await query(
    `SELECT a.id, a.action, a.target_type, a.target_id, a.detail, a.ip, a.created_at,
            ad.username
     FROM admin_audit a
     LEFT JOIN admins ad ON ad.id = a.admin_id
     ORDER BY a.created_at DESC
     LIMIT $1 OFFSET $2`,
    [pageSize, offset],
  )
  res.json({
    items: result.rows.map((row) => ({
      id: Number(row.id),
      action: row.action,
      targetType: row.target_type,
      targetId: row.target_id,
      detail: row.detail,
      ip: row.ip,
      username: row.username,
      createdAt: iso(row.created_at),
    })),
    total,
    page,
    pageSize,
  })
})

adminRouter.get('/reward-video', adminRequired, async (_req, res) => {
  const pointsPerWatch = await getRewardVideoPoints()
  res.json({ pointsPerWatch, dailyLimit: REWARD_VIDEO_DAILY_LIMIT })
})

adminRouter.put('/reward-video', adminRequired, async (req, res) => {
  const saved = await setRewardVideoPoints(req.body?.pointsPerWatch)
  if (!saved.ok) {
    res.status(400).json({ error: saved.error })
    return
  }
  res.json(saved)
})

adminRouter.get('/level-rules', adminRequired, async (_req, res) => {
  res.json(await getLevelRules())
})

adminRouter.put('/level-rules', adminRequired, async (req, res) => {
  const saved = await setLevelRules(req.body)
  if (!saved.ok) {
    res.status(400).json({ error: saved.error })
    return
  }
  res.json(saved)
})

adminRouter.get('/checkins', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const today = todayShanghai()
  const params = [today]
  let where = 'TRUE'
  if (q) {
    params.push(`%${q}%`)
    where = `(u.phone ILIKE $${params.length})`
  }
  const total = (
    await query(
      `SELECT count(*)::int AS n
       FROM user_checkins c
       JOIN users u ON u.id = c.user_id
       WHERE ${where}`,
      q ? [`%${q}%`] : [],
    )
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `
    SELECT c.user_id, c.total_points, c.streak_days, c.last_checkin_date, c.updated_at,
           u.phone, u.avatar_url, u.user_level,
           (c.last_checkin_date = $1::date) AS checked_today
    FROM user_checkins c
    JOIN users u ON u.id = c.user_id
    WHERE ${where}
    ORDER BY c.total_points DESC, c.updated_at DESC, c.user_id DESC
    LIMIT $${params.length - 1} OFFSET $${params.length}
    `,
    params,
  )
  const [todayCount, pointsSum] = await Promise.all([
    query(`SELECT count(*)::int AS n FROM user_checkin_logs WHERE checkin_date = $1::date`, [today]),
    query(`SELECT coalesce(sum(total_points), 0)::int AS n FROM user_checkins`),
  ])
  res.json({
    items: result.rows.map((row) => ({
      userId: Number(row.user_id),
      phone: row.phone,
      avatarUrl: row.avatar_url || null,
      level: Math.min(7, Math.max(0, Number(row.user_level || 0))),
      totalPoints: Number(row.total_points || 0),
      streakDays: Number(row.streak_days || 0),
      lastCheckInDate: row.last_checkin_date ? String(row.last_checkin_date).slice(0, 10) : null,
      checkedToday: Boolean(row.checked_today),
      updatedAt: iso(row.updated_at),
    })),
    stats: {
      today,
      checkInToday: todayCount.rows[0].n,
      totalPoints: pointsSum.rows[0].n,
      userCount: total,
    },
    total,
    page,
    pageSize,
  })
})

adminRouter.get('/checkins/logs', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const params = []
  let where = 'TRUE'
  if (q) {
    params.push(`%${q}%`)
    where = '(u.phone ILIKE $1)'
  }
  const total = (
    await query(
      `SELECT count(*)::int AS n
       FROM user_checkin_logs l
       JOIN users u ON u.id = l.user_id
       WHERE ${where}`,
      params,
    )
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `
    SELECT l.id, l.user_id, l.checkin_date, l.streak_days, l.points_earned, l.created_at,
           u.phone, u.avatar_url
    FROM user_checkin_logs l
    JOIN users u ON u.id = l.user_id
    WHERE ${where}
    ORDER BY l.checkin_date DESC, l.id DESC
    LIMIT $${params.length - 1} OFFSET $${params.length}
    `,
    params,
  )
  res.json({
    items: result.rows.map((row) => ({
      id: Number(row.id),
      userId: Number(row.user_id),
      phone: row.phone,
      avatarUrl: row.avatar_url || null,
      checkInDate: String(row.checkin_date).slice(0, 10),
      streakDays: Number(row.streak_days || 0),
      pointsEarned: Number(row.points_earned || 0),
      createdAt: iso(row.created_at),
    })),
    total,
    page,
    pageSize,
  })
})

adminRouter.get('/gift-categories', adminRequired, async (_req, res) => {
  const items = await getGiftMallCategories()
  res.json({ items, defs: GIFT_CATEGORY_DEFS })
})

adminRouter.put('/gift-categories', adminRequired, async (req, res) => {
  const saved = await setGiftMallCategories(req.body?.items)
  if (!saved.ok) {
    res.status(400).json({ error: saved.error || '保存失败' })
    return
  }
  await audit(req, 'update_gift_categories', 'gift_category', null, { count: saved.items.length })
  res.json({ items: saved.items })
})

adminRouter.get('/gifts', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const publishedFilter = String(req.query.published || '').trim()
  const params = []
  const where = []
  if (q) {
    params.push(`%${q}%`)
    where.push(`(title ILIKE $${params.length} OR subtitle ILIKE $${params.length})`)
  }
  if (publishedFilter === '1' || publishedFilter === 'true') {
    where.push('published = TRUE')
  } else if (publishedFilter === '0' || publishedFilter === 'false') {
    where.push('published = FALSE')
  }
  const whereSql = where.length ? where.join(' AND ') : 'TRUE'
  const total = (await query(`SELECT count(*)::int AS n FROM gifts WHERE ${whereSql}`, params)).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `SELECT * FROM gifts WHERE ${whereSql}
     ORDER BY sort_order ASC, id DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  )
  const categories = await getGiftMallCategories()
  res.json({ items: result.rows.map(mapGift), total, page, pageSize, categories })
})

adminRouter.post('/gifts', adminRequired, async (req, res) => {
  const title = String(req.body?.title || '').trim()
  if (!title) {
    res.status(400).json({ error: '请填写礼品名称' })
    return
  }
  const pointsCost = Math.max(0, Number(req.body?.pointsCost) || 0)
  const cashFen = Math.max(0, Math.round(Number(req.body?.cashYuan || 0) * 100) || Number(req.body?.cashFen) || 0)
  const categories = normalizeGiftCategories(
    req.body?.categories != null ? req.body.categories : req.body?.category,
  )
  const category = categories[0]
  const images = normalizeGiftImages(req.body?.images)
  const row = (
    await query(
      `INSERT INTO gifts
        (title, subtitle, cover_emoji, cover_color, category, categories, images, points_cost, cash_fen,
         original_price_fen, points_offset_fen, stock, sort_order, published, need_address, description)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)
       RETURNING *`,
      [
        title,
        String(req.body?.subtitle || '').trim() || null,
        String(req.body?.coverEmoji || '🎁').trim() || '🎁',
        String(req.body?.coverColor || '#1B6CA8').trim() || '#1B6CA8',
        category,
        categories,
        images,
        pointsCost,
        cashFen,
        req.body?.originalPriceYuan != null
          ? Math.round(Number(req.body.originalPriceYuan) * 100)
          : req.body?.originalPriceFen ?? null,
        req.body?.pointsOffsetYuan != null
          ? Math.round(Number(req.body.pointsOffsetYuan) * 100)
          : req.body?.pointsOffsetFen ?? null,
        normalizeGiftStock(req.body?.stock),
        Number(req.body?.sortOrder) || 0,
        req.body?.published !== false,
        Boolean(req.body?.needAddress),
        String(req.body?.description || '').trim() || null,
      ],
    )
  ).rows[0]
  await audit(req, 'create_gift', 'gift', row.id, { title, categories, images: images.length })
  res.json({ item: mapGift(row) })
})

adminRouter.patch('/gifts/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const existing = (await query('SELECT * FROM gifts WHERE id = $1', [id])).rows[0]
  if (!existing) {
    res.status(404).json({ error: '礼品不存在' })
    return
  }
  const title = req.body?.title != null ? String(req.body.title).trim() : existing.title
  const pointsCost =
    req.body?.pointsCost != null ? Math.max(0, Number(req.body.pointsCost) || 0) : existing.points_cost
  let cashFen = existing.cash_fen
  if (req.body?.cashYuan != null) cashFen = Math.max(0, Math.round(Number(req.body.cashYuan) * 100))
  else if (req.body?.cashFen != null) cashFen = Math.max(0, Number(req.body.cashFen) || 0)
  const published = req.body?.published != null ? Boolean(req.body.published) : existing.published
  const categories =
    req.body?.categories != null || req.body?.category != null
      ? normalizeGiftCategories(
          req.body?.categories != null ? req.body.categories : req.body?.category,
          existing.categories || [existing.category],
        )
      : normalizeGiftCategories(existing.categories || existing.category)
  const category = categories[0]
  const images =
    req.body?.images != null
      ? normalizeGiftImages(req.body.images)
      : normalizeGiftImages(existing.images)
  const row = (
    await query(
      `UPDATE gifts SET
         title=$1, subtitle=$2, cover_emoji=$3, cover_color=$4, category=$5, categories=$6, images=$7,
         points_cost=$8, cash_fen=$9, original_price_fen=$10, points_offset_fen=$11,
         stock=$12, sort_order=$13, published=$14, need_address=$15, description=$16
       WHERE id=$17 RETURNING *`,
      [
        title,
        req.body?.subtitle != null ? String(req.body.subtitle).trim() : existing.subtitle,
        req.body?.coverEmoji != null ? String(req.body.coverEmoji).trim() : existing.cover_emoji,
        req.body?.coverColor != null ? String(req.body.coverColor).trim() : existing.cover_color,
        category,
        categories,
        images,
        pointsCost,
        cashFen,
        req.body?.originalPriceYuan != null
          ? Math.round(Number(req.body.originalPriceYuan) * 100)
          : req.body?.originalPriceFen != null
            ? Number(req.body.originalPriceFen)
            : existing.original_price_fen,
        req.body?.pointsOffsetYuan != null
          ? Math.round(Number(req.body.pointsOffsetYuan) * 100)
          : req.body?.pointsOffsetFen != null
            ? Number(req.body.pointsOffsetFen)
            : existing.points_offset_fen,
        req.body?.stock != null ? normalizeGiftStock(req.body.stock) : existing.stock,
        req.body?.sortOrder != null ? Number(req.body.sortOrder) : existing.sort_order,
        published,
        req.body?.needAddress != null ? Boolean(req.body.needAddress) : existing.need_address,
        req.body?.description != null ? String(req.body.description).trim() : existing.description,
        id,
      ],
    )
  ).rows[0]
  await audit(req, 'update_gift', 'gift', id, { title, published, categories, images: images.length })
  res.json({ item: mapGift(row) })
})

adminRouter.post(
  '/gifts/upload',
  adminRequired,
  express.raw({ type: () => true, limit: '12mb' }),
  async (req, res) => {
    try {
      const buf = Buffer.isBuffer(req.body) ? req.body : Buffer.from([])
      if (!buf.length) {
        res.status(400).json({ error: '空文件' })
        return
      }
      if (buf.length > 12 * 1024 * 1024) {
        res.status(400).json({ error: '图片过大（上限 12MB）' })
        return
      }
      const rawName = path.basename(String(req.query.filename || req.get('x-filename') || 'gift.jpg'))
      const lower = rawName.toLowerCase()
      const ext = ['.jpg', '.jpeg', '.png', '.webp', '.gif'].find((e) => lower.endsWith(e)) || '.jpg'
      const safeBase = sanitizeUploadBase(rawName, 60) || 'gift'
      const name = `${Date.now()}-${safeBase}${ext}`
      const dest = path.join(giftsUploadRoot, name)
      fs.writeFileSync(dest, buf)
      const url = `/uploads/gifts/${name}`
      await audit(req, 'upload_gift_image', 'gift', null, { url, bytes: buf.length })
      res.json({ url, bytes: buf.length })
    } catch (error) {
      console.error('[admin/gifts/upload]', error)
      res.status(500).json({ error: '上传失败' })
    }
  },
)

adminRouter.delete('/gifts/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const row = (await query('DELETE FROM gifts WHERE id = $1 RETURNING id, title', [id])).rows[0]
  if (!row) {
    res.status(404).json({ error: '礼品不存在' })
    return
  }
  await audit(req, 'delete_gift', 'gift', id, { title: row.title })
  res.json({ ok: true })
})

function mapAdminWithdrawal(row) {
  const amountFen = Math.max(0, Number(row.amount_fen || 0))
  const status = row.status || ''
  const statusLabel =
    status === 'pending' ? '处理中' : status === 'success' ? '已到账' : status === 'failed' ? '失败' : status
  return {
    id: Number(row.id),
    userId: Number(row.user_id),
    phone: row.phone || null,
    nickname: row.nickname || null,
    buddyId: row.buddy_id || null,
    channel: row.channel,
    channelLabel: row.channel === 'wechat' ? '微信' : row.channel === 'alipay' ? '支付宝' : row.channel,
    account: row.account,
    amountFen,
    amountYuan: (amountFen / 100).toFixed(2),
    pointsSpent: Number(row.points_spent || 0),
    status,
    statusLabel,
    providerTradeNo: row.provider_trade_no || null,
    errorMessage: row.error_message || null,
    sandbox: Boolean(row.sandbox),
    createdAt: iso(row.created_at),
    updatedAt: iso(row.updated_at),
  }
}

adminRouter.get('/withdrawals', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const status = String(req.query.status || '').trim()
  const params = []
  const where = ['TRUE']
  if (q) {
    params.push(`%${q}%`)
    where.push(`(
      u.phone ILIKE $${params.length}
      OR COALESCE(u.nickname, '') ILIKE $${params.length}
      OR COALESCE(u.buddy_id, '') ILIKE $${params.length}
      OR w.account ILIKE $${params.length}
      OR CAST(u.id AS TEXT) ILIKE $${params.length}
    )`)
  }
  if (status === 'pending' || status === 'success' || status === 'failed') {
    params.push(status)
    where.push(`w.status = $${params.length}`)
  }
  const total = (
    await query(
      `SELECT count(*)::int AS n
       FROM withdrawals w
       JOIN users u ON u.id = w.user_id
       WHERE ${where.join(' AND ')}`,
      params,
    )
  ).rows[0].n
  const summary = (
    await query(
      `SELECT
         count(*)::int AS n,
         count(*) FILTER (WHERE w.status = 'success')::int AS success_n,
         count(*) FILTER (WHERE w.status = 'pending')::int AS pending_n,
         count(*) FILTER (WHERE w.status = 'failed')::int AS failed_n,
         COALESCE(SUM(w.amount_fen) FILTER (WHERE w.status = 'success'), 0)::bigint AS success_fen,
         COALESCE(SUM(w.points_spent) FILTER (WHERE w.status = 'success'), 0)::bigint AS success_points
       FROM withdrawals w
       JOIN users u ON u.id = w.user_id
       WHERE ${where.join(' AND ')}`,
      params,
    )
  ).rows[0]
  params.push(pageSize, offset)
  const result = await query(
    `SELECT w.*, u.phone, u.nickname, u.buddy_id
     FROM withdrawals w
     JOIN users u ON u.id = w.user_id
     WHERE ${where.join(' AND ')}
     ORDER BY w.created_at DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  )
  res.json({
    items: result.rows.map(mapAdminWithdrawal),
    total,
    page,
    pageSize,
    summary: {
      total: Number(summary.n || 0),
      successCount: Number(summary.success_n || 0),
      pendingCount: Number(summary.pending_n || 0),
      failedCount: Number(summary.failed_n || 0),
      successFen: Number(summary.success_fen || 0),
      successPoints: Number(summary.success_points || 0),
    },
  })
})

adminRouter.get('/gift-orders', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const status = String(req.query.status || '').trim()
  const params = []
  const where = ['TRUE']
  if (q) {
    params.push(`%${q}%`)
    where.push(`(u.phone ILIKE $${params.length} OR o.gift_title ILIKE $${params.length})`)
  }
  if (status) {
    params.push(status)
    where.push(`o.status = $${params.length}`)
  }
  const total = (
    await query(
      `SELECT count(*)::int AS n FROM gift_orders o
       JOIN users u ON u.id = o.user_id
       WHERE ${where.join(' AND ')}`,
      params,
    )
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `SELECT o.*, u.phone FROM gift_orders o
     JOIN users u ON u.id = o.user_id
     WHERE ${where.join(' AND ')}
     ORDER BY o.created_at DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  )
  res.json({ items: result.rows.map(mapOrder), total, page, pageSize })
})

adminRouter.patch('/gift-orders/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const status = String(req.body?.status || '').trim()
  const allowed = new Set(['pending_cash', 'pending_ship', 'shipped', 'completed', 'cancelled'])
  if (!allowed.has(status)) {
    res.status(400).json({ error: '无效状态' })
    return
  }
  const row = (
    await query(
      `UPDATE gift_orders SET status = $1, updated_at = now(), remark = COALESCE($2, remark)
       WHERE id = $3 RETURNING *`,
      [status, req.body?.remark != null ? String(req.body.remark) : null, id],
    )
  ).rows[0]
  if (!row) {
    res.status(404).json({ error: '订单不存在' })
    return
  }
  await audit(req, 'update_gift_order', 'gift_order', id, { status })
  res.json({ item: mapOrder(row) })
})

const shortsUploadRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../uploads/videos')
const giftsUploadRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../uploads/gifts')
fs.mkdirSync(giftsUploadRoot, { recursive: true })
fs.mkdirSync(shortsUploadRoot, { recursive: true })

/** Strips the extension and anything that could escape the upload directory. */
function sanitizeUploadBase(rawName, maxLength) {
  return rawName
    .replace(/\.[^.]+$/, '')
    .replace(/[^\w\u4e00-\u9fff-]+/g, '_')
    .replace(/^[-_.]+/, '')
    .slice(0, maxLength)
}

function formatWatchMs(ms) {
  const n = Math.max(0, Number(ms) || 0)
  const totalSec = Math.floor(n / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  if (h > 0) return `${h}小时${m}分${s}秒`
  if (m > 0) return `${m}分${s}秒`
  return `${s}秒`
}

adminRouter.get('/shorts/stats', adminRequired, async (req, res) => {
  const days = Math.min(365, Math.max(1, Number(req.query.days) || 30))
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()

  const categoryTotals = (
    await query(
      `SELECT category,
              COALESCE(SUM(watch_ms), 0)::bigint AS watch_ms,
              count(*)::int AS events,
              count(DISTINCT COALESCE(user_id::text, 'd:' || COALESCE(device_key, '')))::int AS viewers
       FROM short_video_watches
       WHERE created_at > now() - ($1 || ' days')::interval
       GROUP BY category
       ORDER BY watch_ms DESC`,
      [String(days)],
    )
  ).rows.map((row) => {
    const meta = SHORT_CATEGORIES.find((c) => c.id === row.category)
    return {
      category: row.category,
      categoryName: meta?.name || row.category,
      watchMs: Number(row.watch_ms || 0),
      watchLabel: formatWatchMs(row.watch_ms),
      events: Number(row.events || 0),
      viewers: Number(row.viewers || 0),
    }
  })

  const params = [String(days)]
  let userFilter = ''
  if (q) {
    params.push(`%${q}%`)
    userFilter = ` AND (u.phone ILIKE $${params.length} OR u.nickname ILIKE $${params.length} OR w.device_key ILIKE $${params.length})`
  }

  const total = (
    await query(
      `SELECT count(*)::int AS n FROM (
         SELECT COALESCE(w.user_id::text, 'd:' || COALESCE(w.device_key, 'anon')) AS viewer_key
         FROM short_video_watches w
         LEFT JOIN users u ON u.id = w.user_id
         WHERE w.created_at > now() - ($1 || ' days')::interval
         ${userFilter}
         GROUP BY viewer_key
       ) t`,
      params,
    )
  ).rows[0].n

  params.push(pageSize, offset)
  const rows = (
    await query(
      `SELECT
         w.user_id,
         max(u.phone) AS phone,
         max(u.nickname) AS nickname,
         max(u.avatar_url) AS avatar_url,
         max(w.device_key) AS device_key,
         COALESCE(SUM(w.watch_ms) FILTER (WHERE w.category = 'speaking'), 0)::bigint AS speaking_ms,
         COALESCE(SUM(w.watch_ms) FILTER (WHERE w.category = 'vocab'), 0)::bigint AS vocab_ms,
         COALESCE(SUM(w.watch_ms) FILTER (WHERE w.category = 'listening'), 0)::bigint AS listening_ms,
         COALESCE(SUM(w.watch_ms), 0)::bigint AS total_ms,
         count(*)::int AS events,
         max(w.created_at) AS last_watched_at
       FROM short_video_watches w
       LEFT JOIN users u ON u.id = w.user_id
       WHERE w.created_at > now() - ($1 || ' days')::interval
       ${userFilter}
       GROUP BY w.user_id, CASE WHEN w.user_id IS NULL THEN w.device_key ELSE NULL END
       ORDER BY total_ms DESC
       LIMIT $${params.length - 1} OFFSET $${params.length}`,
      params,
    )
  ).rows

  res.json({
    days,
    categories: SHORT_CATEGORIES,
    categoryTotals,
    items: rows.map((row) => ({
      userId: row.user_id == null ? null : Number(row.user_id),
      phone: row.phone || null,
      nickname: row.nickname || null,
      avatarUrl: row.avatar_url || null,
      deviceKey: row.user_id == null ? row.device_key || null : null,
      speakingMs: Number(row.speaking_ms || 0),
      vocabMs: Number(row.vocab_ms || 0),
      listeningMs: Number(row.listening_ms || 0),
      totalMs: Number(row.total_ms || 0),
      speakingLabel: formatWatchMs(row.speaking_ms),
      vocabLabel: formatWatchMs(row.vocab_ms),
      listeningLabel: formatWatchMs(row.listening_ms),
      totalLabel: formatWatchMs(row.total_ms),
      events: Number(row.events || 0),
      lastWatchedAt: iso(row.last_watched_at),
      preferredCategory:
        [
          { id: 'speaking', ms: Number(row.speaking_ms || 0) },
          { id: 'vocab', ms: Number(row.vocab_ms || 0) },
          { id: 'listening', ms: Number(row.listening_ms || 0) },
        ].sort((a, b) => b.ms - a.ms)[0]?.id || null,
    })),
    total,
    page,
    pageSize,
  })
})

adminRouter.get('/shorts', adminRequired, async (req, res) => {
  const { page, pageSize } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const category = String(req.query.category || '').trim()
  const published = String(req.query.published || '').trim()
  const data = await listAdminShorts({ q, category, published, page, pageSize })
  res.json({ ...data, categories: SHORT_CATEGORIES })
})

adminRouter.post(
  '/shorts/upload',
  adminRequired,
  express.raw({ type: () => true, limit: '40mb' }),
  async (req, res) => {
    try {
      const buf = Buffer.isBuffer(req.body) ? req.body : Buffer.from([])
      if (!buf.length) {
        res.status(400).json({ error: '空文件' })
        return
      }
      if (buf.length > 40 * 1024 * 1024) {
        res.status(400).json({ error: '文件过大（上限 40MB）' })
        return
      }
      const rawName = path.basename(String(req.query.filename || req.get('x-filename') || 'video.mp4'))
      const safe = sanitizeUploadBase(rawName, 80) || 'video'
      const name = `${Date.now()}-${safe}.mp4`
      const dest = path.join(shortsUploadRoot, name)
      fs.writeFileSync(dest, buf)
      const url = `/uploads/videos/${name}`
      await audit(req, 'upload_short_video', 'short_video', null, { url, bytes: buf.length })
      res.json({ url, bytes: buf.length })
    } catch (error) {
      console.error('[admin/shorts/upload]', error)
      res.status(500).json({ error: '上传失败' })
    }
  },
)

adminRouter.post('/shorts', adminRequired, async (req, res) => {
  const title = String(req.body?.title || '').trim()
  const videoUrl = String(req.body?.videoUrl || '').trim()
  if (!title) {
    res.status(400).json({ error: '请填写标题' })
    return
  }
  if (!videoUrl) {
    res.status(400).json({ error: '请上传或填写视频地址' })
    return
  }
  const keywords = normalizeKeywords(req.body?.keywords)
  const kwErr = validateKeywords(keywords)
  if (kwErr) {
    res.status(400).json({ error: kwErr })
    return
  }
  const category = normalizeCategory(req.body?.category)
  const row = (
    await query(
      `INSERT INTO short_videos
        (title, author, caption, video_url, cover_url, category, keywords, duration_ms, sort_order, published)
       VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb,$8,$9,$10)
       RETURNING *`,
      [
        title,
        String(req.body?.author || '词搭子').trim() || '词搭子',
        String(req.body?.caption || '').trim() || null,
        videoUrl,
        String(req.body?.coverUrl || '').trim() || null,
        category,
        JSON.stringify(keywords),
        req.body?.durationMs != null ? Math.max(0, Number(req.body.durationMs) || 0) : null,
        Number(req.body?.sortOrder) || 0,
        req.body?.published !== false,
      ],
    )
  ).rows[0]
  await audit(req, 'create_short_video', 'short_video', row.id, { title, category })
  res.json({ item: mapShortVideo(row) })
})

adminRouter.patch('/shorts/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const existing = (await query('SELECT * FROM short_videos WHERE id = $1', [id])).rows[0]
  if (!existing) {
    res.status(404).json({ error: '视频不存在' })
    return
  }
  const title = req.body?.title != null ? String(req.body.title).trim() : existing.title
  if (!title) {
    res.status(400).json({ error: '请填写标题' })
    return
  }
  let keywords = Array.isArray(existing.keywords) ? existing.keywords : []
  if (req.body?.keywords != null) {
    keywords = normalizeKeywords(req.body.keywords)
    const kwErr = validateKeywords(keywords)
    if (kwErr) {
      res.status(400).json({ error: kwErr })
      return
    }
  }
  const videoUrl =
    req.body?.videoUrl != null ? String(req.body.videoUrl).trim() : existing.video_url
  if (!videoUrl) {
    res.status(400).json({ error: '请填写视频地址' })
    return
  }
  const category =
    req.body?.category != null ? normalizeCategory(req.body.category) : existing.category
  const published =
    req.body?.published != null ? Boolean(req.body.published) : existing.published
  const row = (
    await query(
      `UPDATE short_videos SET
         title=$1, author=$2, caption=$3, video_url=$4, cover_url=$5,
         category=$6, keywords=$7::jsonb, duration_ms=$8, sort_order=$9, published=$10
       WHERE id=$11 RETURNING *`,
      [
        title,
        req.body?.author != null ? String(req.body.author).trim() || '词搭子' : existing.author,
        req.body?.caption != null ? String(req.body.caption).trim() : existing.caption,
        videoUrl,
        req.body?.coverUrl != null ? String(req.body.coverUrl).trim() || null : existing.cover_url,
        category,
        JSON.stringify(keywords),
        req.body?.durationMs != null
          ? Math.max(0, Number(req.body.durationMs) || 0)
          : existing.duration_ms,
        req.body?.sortOrder != null ? Number(req.body.sortOrder) || 0 : existing.sort_order,
        published,
        id,
      ],
    )
  ).rows[0]
  await audit(req, 'update_short_video', 'short_video', id, { title, category, published })
  res.json({ item: mapShortVideo(row) })
})

adminRouter.delete('/shorts/:id', adminRequired, async (req, res) => {
  const id = Number(req.params.id)
  const row = (await query('DELETE FROM short_videos WHERE id = $1 RETURNING id, title, video_url', [id]))
    .rows[0]
  if (!row) {
    res.status(404).json({ error: '视频不存在' })
    return
  }
  await audit(req, 'delete_short_video', 'short_video', id, { title: row.title })
  res.json({ ok: true })
})
