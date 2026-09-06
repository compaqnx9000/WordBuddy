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
  const [users, logins, words, catalogs, userBooks, sms] = await Promise.all([
    query('SELECT count(*)::int AS n FROM users'),
    query(`SELECT count(*)::int AS n FROM login_events WHERE created_at > now() - interval '7 days' AND success = TRUE`),
    query('SELECT count(*)::int AS n FROM words'),
    query(`SELECT count(*)::int AS n FROM notebooks WHERE kind = 'catalog'`),
    query(`SELECT count(*)::int AS n FROM notebooks WHERE kind = 'user'`),
    query(`SELECT count(*)::int AS n FROM sms_codes WHERE created_at > now() - interval '24 hours'`),
  ])
  const recentUsers = await query(
    `SELECT id, phone, avatar_url, created_at, last_login_at, login_count,
            (password_hash IS NOT NULL) AS has_password
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
           (u.password_hash IS NOT NULL) AS has_password,
           (SELECT count(*)::int FROM notebooks n WHERE n.owner_user_id = u.id) AS notebook_count,
           (SELECT count(*)::int FROM words w
             JOIN notebooks n ON n.id = w.notebook_id
            WHERE n.owner_user_id = u.id) AS word_count
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
              (password_hash IS NOT NULL) AS has_password
       FROM users WHERE id = $1`,
      [id],
    )
  ).rows[0]
  if (!user) {
    res.status(404).json({ error: '用户不存在' })
    return
  }
  const [notebooks, logins, passwords, sms] = await Promise.all([
    query(
      `SELECT n.id, n.kind, n.slug, n.name, n.published, n.sort_order, n.owner_user_id, n.created_at,
              (SELECT count(*)::int FROM words w WHERE w.notebook_id = n.id) AS word_count
       FROM notebooks n WHERE n.owner_user_id = $1
       ORDER BY n.sort_order ASC, n.id ASC`,
      [id],
    ),
    query(
      `SELECT id, method, success, ip, user_agent, created_at
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
  ])
  res.json({
    user: mapUser(user),
    notebooks: notebooks.rows.map(mapNotebook),
    logins: logins.rows.map((row) => ({
      id: Number(row.id),
      method: row.method,
      success: Boolean(row.success),
      ip: row.ip,
      userAgent: row.user_agent,
      createdAt: iso(row.created_at),
    })),
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
  if (req.body?.phone && !nextPhone) {
    res.status(400).json({ error: '手机号不正确' })
    return
  }
  if (req.body?.password && !nextPassword) {
    res.status(400).json({ error: '密码需要 6 到 32 位' })
    return
  }
  if (nextPhone && nextPhone !== user.phone) {
    await query('UPDATE users SET phone = $1 WHERE id = $2', [nextPhone, id])
  }
  if (nextPassword) {
    await query('UPDATE users SET password_hash = $1 WHERE id = $2', [hashPassword(nextPassword), id])
    await recordPasswordEvent(req, id, 'admin_reset')
  }
  await audit(req, 'update_user', 'user', id, {
    phone: Boolean(nextPhone),
    resetPassword: Boolean(nextPassword),
  })
  const fresh = (
    await query(
      `SELECT id, phone, avatar_url, created_at, last_login_at, last_login_method,
              password_changed_at, login_count,
              (password_hash IS NOT NULL) AS has_password
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
    where = '(e.phone ILIKE $1 OR e.method ILIKE $1 OR e.ip ILIKE $1)'
  }
  const total = (
    await query(`SELECT count(*)::int AS n FROM login_events e WHERE ${where}`, params)
  ).rows[0].n
  params.push(pageSize, offset)
  const result = await query(
    `
    SELECT e.id, e.user_id, e.phone, e.method, e.success, e.ip, e.user_agent, e.created_at
    FROM login_events e
    WHERE ${where}
    ORDER BY e.created_at DESC
    LIMIT $${params.length - 1} OFFSET $${params.length}
    `,
    params,
  )
  res.json({
    items: result.rows.map((row) => ({
      id: Number(row.id),
      userId: row.user_id ? Number(row.user_id) : null,
      phone: row.phone,
      method: row.method,
      success: Boolean(row.success),
      ip: row.ip,
      userAgent: row.user_agent,
      createdAt: iso(row.created_at),
    })),
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
