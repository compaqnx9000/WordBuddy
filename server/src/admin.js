import { Router } from 'express'
import { query } from './db.js'
import {
  adminRequired,
  clientIp,
  hashPassword,
  normalizePassword,
  normalizePhone,
  recordPasswordEvent,
  signAdminToken,
  verifyPassword,
} from './auth.js'
import { mapDeviceRow } from './device.js'
import { todayShanghai } from './checkin.js'
import { GIFT_CATEGORIES, mapGift, mapOrder } from './gifts.js'

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
  }
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

adminRouter.get('/users', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const params = []
  let where = 'TRUE'
  if (q) {
    params.push(`%${q.replace(/\D/g, q)}%`)
    params[params.length - 1] = `%${q}%`
    where = '(u.phone ILIKE $1)'
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
  const [notebooks, logins, passwords, sms, devices, checkIn, checkInLogs] = await Promise.all([
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
  })
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
  res.json({
    notebook: { id: Number(notebook.id), name: notebook.name },
    items: result.rows.map(mapWord),
    total,
    page,
    pageSize,
  })
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

adminRouter.get('/gifts', adminRequired, async (req, res) => {
  const { page, pageSize, offset } = pageParams(req)
  const q = String(req.query.q || '').trim()
  const params = []
  let where = 'TRUE'
  if (q) {
    params.push(`%${q}%`)
    where = '(title ILIKE $1 OR subtitle ILIKE $1)'
  }
  const total = (await query(`SELECT count(*)::int AS n FROM gifts WHERE ${where}`, params)).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `SELECT * FROM gifts WHERE ${where}
     ORDER BY sort_order ASC, id DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  )
  res.json({ items: result.rows.map(mapGift), total, page, pageSize, categories: GIFT_CATEGORIES })
})

adminRouter.post('/gifts', adminRequired, async (req, res) => {
  const title = String(req.body?.title || '').trim()
  if (!title) {
    res.status(400).json({ error: '请填写礼品名称' })
    return
  }
  const pointsCost = Math.max(0, Number(req.body?.pointsCost) || 0)
  const cashFen = Math.max(0, Math.round(Number(req.body?.cashYuan || 0) * 100) || Number(req.body?.cashFen) || 0)
  const row = (
    await query(
      `INSERT INTO gifts
        (title, subtitle, cover_emoji, cover_color, category, points_cost, cash_fen,
         original_price_fen, points_offset_fen, stock, sort_order, published, need_address, description)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
       RETURNING *`,
      [
        title,
        String(req.body?.subtitle || '').trim() || null,
        String(req.body?.coverEmoji || '🎁').trim() || '🎁',
        String(req.body?.coverColor || '#1B6CA8').trim() || '#1B6CA8',
        String(req.body?.category || 'recommend').trim() || 'recommend',
        pointsCost,
        cashFen,
        req.body?.originalPriceYuan != null
          ? Math.round(Number(req.body.originalPriceYuan) * 100)
          : req.body?.originalPriceFen ?? null,
        req.body?.pointsOffsetYuan != null
          ? Math.round(Number(req.body.pointsOffsetYuan) * 100)
          : req.body?.pointsOffsetFen ?? null,
        req.body?.stock == null || req.body?.stock === '' ? -1 : Number(req.body.stock),
        Number(req.body?.sortOrder) || 0,
        req.body?.published !== false,
        Boolean(req.body?.needAddress),
        String(req.body?.description || '').trim() || null,
      ],
    )
  ).rows[0]
  await audit(req, 'create_gift', 'gift', row.id, { title })
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
  const row = (
    await query(
      `UPDATE gifts SET
         title=$1, subtitle=$2, cover_emoji=$3, cover_color=$4, category=$5,
         points_cost=$6, cash_fen=$7, original_price_fen=$8, points_offset_fen=$9,
         stock=$10, sort_order=$11, published=$12, need_address=$13, description=$14
       WHERE id=$15 RETURNING *`,
      [
        title,
        req.body?.subtitle != null ? String(req.body.subtitle).trim() : existing.subtitle,
        req.body?.coverEmoji != null ? String(req.body.coverEmoji).trim() : existing.cover_emoji,
        req.body?.coverColor != null ? String(req.body.coverColor).trim() : existing.cover_color,
        req.body?.category != null ? String(req.body.category).trim() : existing.category,
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
        req.body?.stock != null ? Number(req.body.stock) : existing.stock,
        req.body?.sortOrder != null ? Number(req.body.sortOrder) : existing.sort_order,
        published,
        req.body?.needAddress != null ? Boolean(req.body.needAddress) : existing.need_address,
        req.body?.description != null ? String(req.body.description).trim() : existing.description,
        id,
      ],
    )
  ).rows[0]
  await audit(req, 'update_gift', 'gift', id, { title, published })
  res.json({ item: mapGift(row) })
})

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
