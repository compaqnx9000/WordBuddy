import { Router } from 'express'
import { query } from './db.js'
import {
  authRequired,
  optionalAuth,
  ensureUserNotebook,
  hashPassword,
  normalizePassword,
  normalizePhone,
  signToken,
} from './auth.js'
import { newLoginCode, sendCode, skipVerify } from './sms.js'

export const router = Router()

const PAGE_SIZE_MAX = 200

function wordInitialLetterSql(column = 'word') {
  return `
    CASE
      WHEN upper(substr(trim(${column}), 1, 1)) ~ '^[A-Z]$'
        THEN upper(substr(trim(${column}), 1, 1))
      ELSE '#'
    END`
}

function asArray(value) {
  return Array.isArray(value) ? value : []
}

function mapWord(row) {
  return {
    id: Number(row.id),
    notebookId: Number(row.notebook_id),
    text: row.word,
    isPhrase: Boolean(row.is_phrase),
    ipaUk: row.ipa_uk,
    ipaUs: row.ipa_us,
    definitions: asArray(row.definitions),
    examples: asArray(row.examples).map((e) => ({
      english: e?.en ?? e?.english ?? '',
      chinese: e?.zh ?? e?.chinese ?? '',
    })),
    nearWords: asArray(row.near_words),
    synonyms: asArray(row.synonyms),
    antonyms: asArray(row.antonyms),
    sortOrder: row.sort_order,
    addedAtMillis: row.added_at ? new Date(row.added_at).getTime() : Date.now(),
  }
}

function parseCursor(raw) {
  if (!raw) return null
  const [sort, id] = String(raw).split(':')
  const sortOrder = Number(sort)
  const wordId = Number(id)
  if (!Number.isFinite(sortOrder) || !Number.isFinite(wordId)) return null
  return { sortOrder, wordId }
}

router.get('/', (_req, res) => {
  res.type('html').send('<h1>hello</h1><p>HotWords API is reachable.</p>')
})

router.get('/hello', (_req, res) => {
  res.type('text').send('hello')
})

router.get('/health', (_req, res) => {
  res.json({ ok: true })
})

router.post('/auth/send-code', async (req, res) => {
  const phone = normalizePhone(req.body?.phone)
  if (!phone) {
    res.status(400).json({ error: '请输入正确的手机号' })
    return
  }
  const code = newLoginCode()
  await query(
    `INSERT INTO sms_codes (phone, code, expires_at)
     VALUES ($1, $2, now() + interval '10 minutes')`,
    [phone, code],
  )
  await sendCode(phone, code)
  const payload = { ok: true, expiresInSec: 600 }
  if (skipVerify()) payload.debugCode = code
  res.json(payload)
})

async function verifySmsCode(phone, code) {
  if (!/^\d{6}$/.test(code)) return { ok: false, error: '请输入6位验证码' }
  if (skipVerify()) return { ok: true }
  const found = await query(
    `SELECT id FROM sms_codes
     WHERE phone = $1 AND code = $2 AND consumed_at IS NULL AND expires_at > now()
     ORDER BY id DESC
     LIMIT 1`,
    [phone, code],
  )
  if (found.rowCount === 0) return { ok: false, error: '验证码无效或已过期' }
  return { ok: true, smsId: found.rows[0].id }
}

async function consumeSms(smsId) {
  if (smsId) await query('UPDATE sms_codes SET consumed_at = now() WHERE id = $1', [smsId])
}

async function finishLogin(res, user) {
  const vocabNotebookId = await ensureUserNotebook(user.id)
  res.json({
    isNewUser: false,
    token: signToken(user),
    user: { id: Number(user.id), phone: user.phone },
    vocabNotebookId: Number(vocabNotebookId),
  })
}

router.post('/auth/login', async (req, res) => {
  const phone = normalizePhone(req.body?.phone)
  const code = String(req.body?.code || '').trim()
  if (!phone) {
    res.status(400).json({ error: '请输入正确的手机号' })
    return
  }
  const checked = await verifySmsCode(phone, code)
  if (!checked.ok) {
    res.status(400).json({ error: checked.error })
    return
  }

  const user = (await query('SELECT id, phone FROM users WHERE phone = $1', [phone])).rows[0]
  if (!user) {
    res.json({ isNewUser: true })
    return
  }
  await consumeSms(checked.smsId)
  await finishLogin(res, user)
})

router.post('/auth/register', async (req, res) => {
  const phone = normalizePhone(req.body?.phone)
  const code = String(req.body?.code || '').trim()
  const password = normalizePassword(req.body?.password)
  if (!phone) {
    res.status(400).json({ error: '请输入正确的手机号' })
    return
  }
  if (!password) {
    res.status(400).json({ error: '密码需要 6 到 32 位' })
    return
  }
  const checked = await verifySmsCode(phone, code)
  if (!checked.ok) {
    res.status(400).json({ error: checked.error })
    return
  }

  const existing = (await query('SELECT id, phone FROM users WHERE phone = $1', [phone])).rows[0]
  if (existing) {
    await consumeSms(checked.smsId)
    await finishLogin(res, existing)
    return
  }
  const user = (
    await query('INSERT INTO users (phone, password_hash) VALUES ($1, $2) RETURNING id, phone', [
      phone,
      hashPassword(password),
    ])
  ).rows[0]
  await consumeSms(checked.smsId)
  await finishLogin(res, user)
})

router.get('/me', authRequired, async (req, res) => {
  const vocabNotebookId = await ensureUserNotebook(req.user.id)
  res.json({
    user: { id: req.user.id, phone: req.user.phone },
    vocabNotebookId: Number(vocabNotebookId),
  })
})

/** Public catalog list (中考 / 高考 / CET-4 / CET-6) — no login required. */
router.get('/catalogs', async (_req, res) => {
  const result = await query(
    `
    SELECT n.id, n.kind, n.slug, n.name, n.sort_order, n.created_at,
           (SELECT count(*)::int FROM words w WHERE w.notebook_id = n.id) AS word_count
    FROM notebooks n
    WHERE n.kind = 'catalog' AND n.published = TRUE
    ORDER BY n.sort_order ASC, n.id ASC
    `,
  )
  res.json({
    items: result.rows.map(mapNotebook),
  })
})

router.get('/notebooks', authRequired, async (req, res) => {
  await ensureUserNotebook(req.user.id)
  const result = await query(
    `
    SELECT n.id, n.kind, n.slug, n.name, n.sort_order, n.created_at,
           (SELECT count(*)::int FROM words w WHERE w.notebook_id = n.id) AS word_count
    FROM notebooks n
    WHERE
      (n.kind = 'catalog' AND n.published = TRUE)
      OR
      (n.kind = 'user' AND n.owner_user_id = $1)
    ORDER BY n.kind DESC, n.sort_order ASC, n.id ASC
    `,
    [req.user.id],
  )
  res.json({
    items: result.rows.map(mapNotebook),
  })
})

function mapNotebook(row) {
  return {
    id: Number(row.id),
    kind: row.kind,
    slug: row.slug,
    name: row.name,
    sortOrder: row.sort_order,
    wordCount: row.word_count ?? 0,
    createdAtMillis: new Date(row.created_at).getTime(),
    isSystem: row.kind === 'catalog',
  }
}

router.post('/notebooks', authRequired, async (req, res) => {
  const name = String(req.body?.name || '').trim()
  if (!name) {
    res.status(400).json({ error: '请输入生词本名称' })
    return
  }
  if (name.length > 20) {
    res.status(400).json({ error: '名称最多 20 个字' })
    return
  }
  const dup = await query(
    `SELECT id FROM notebooks
     WHERE kind = 'user' AND owner_user_id = $1 AND lower(name) = lower($2)`,
    [req.user.id, name],
  )
  if (dup.rowCount > 0) {
    res.status(400).json({ error: '已有同名生词本' })
    return
  }
  const maxSort = (
    await query(
      `SELECT coalesce(max(sort_order), 0) AS n
       FROM notebooks WHERE kind = 'user' AND owner_user_id = $1`,
      [req.user.id],
    )
  ).rows[0].n
  const inserted = await query(
    `INSERT INTO notebooks (kind, owner_user_id, name, sort_order)
     VALUES ('user', $1, $2, $3)
     RETURNING id, kind, slug, name, sort_order, created_at`,
    [req.user.id, name, maxSort + 1],
  )
  res.status(201).json({ item: mapNotebook({ ...inserted.rows[0], word_count: 0 }) })
})

router.delete('/notebooks/:id', authRequired, async (req, res) => {
  const notebook = await loadNotebook(Number(req.params.id))
  if (notebook?.kind === 'catalog') {
    res.status(403).json({ error: '系统词书不能删除' })
    return
  }
  if (!canWriteNotebook(notebook, req.user.id)) {
    res.status(403).json({ error: '不能删除该词本' })
    return
  }
  // Default「生词本」is a normal user notebook and may be deleted.
  // ensureUserNotebook recreates one when the user next needs a personal book.
  await query('DELETE FROM notebooks WHERE id = $1', [notebook.id])
  res.json({ ok: true })
})

async function loadNotebook(id) {
  const result = await query(
    `SELECT id, kind, slug, owner_user_id, name, published
     FROM notebooks WHERE id = $1`,
    [id],
  )
  return result.rows[0] || null
}

function canReadNotebook(notebook, userId) {
  if (!notebook) return false
  if (notebook.kind === 'catalog') return Boolean(notebook.published)
  if (userId == null) return false
  return Number(notebook.owner_user_id) === Number(userId)
}

function canWriteNotebook(notebook, userId) {
  return notebook?.kind === 'user' && Number(notebook.owner_user_id) === Number(userId)
}

/**
 * Precomputed A–Z / # → absolute list index (0-based) for the notebook.
 * Used by the client alphabet scrubber so seeks do not guess from a partial page.
 */
router.get('/notebooks/:id/letter-index', optionalAuth, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canReadNotebook(notebook, req.user?.id)) {
    res.status(notebook?.kind === 'catalog' ? 404 : 401).json({
      error: notebook?.kind === 'catalog' ? '词本不存在' : '未登录',
    })
    return
  }
  const total = (
    await query('SELECT count(*)::int AS n FROM words WHERE notebook_id = $1', [notebookId])
  ).rows[0].n
  const letterExpr = wordInitialLetterSql('word')
  const result = await query(
    `
    WITH ordered AS (
      SELECT word,
             (row_number() OVER (ORDER BY sort_order ASC, id ASC) - 1)::int AS idx
      FROM words
      WHERE notebook_id = $1
    )
    SELECT ${letterExpr} AS letter, MIN(idx)::int AS idx
    FROM ordered
    GROUP BY 1
    `,
    [notebookId],
  )
  const index = {}
  for (const row of result.rows) {
    index[row.letter] = row.idx
  }
  res.json({ index, total })
})

/**
 * Lightweight id+word list for in-memory alphabet / slider seeks.
 * Much smaller than full word pages (no definitions / examples).
 */
router.get('/notebooks/:id/heads', optionalAuth, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canReadNotebook(notebook, req.user?.id)) {
    res.status(notebook?.kind === 'catalog' ? 404 : 401).json({
      error: notebook?.kind === 'catalog' ? '词本不存在' : '未登录',
    })
    return
  }
  const result = await query(
    `SELECT id, word, is_phrase, ipa_uk, ipa_us, sort_order
     FROM words
     WHERE notebook_id = $1
     ORDER BY sort_order ASC, id ASC`,
    [notebookId],
  )
  const letterIndex = {}
  const items = result.rows.map((row, idx) => {
    const text = String(row.word || '')
    const ch = text.trim().charAt(0).toUpperCase()
    const letter = ch >= 'A' && ch <= 'Z' ? ch : '#'
    if (letterIndex[letter] === undefined) letterIndex[letter] = idx
    return {
      id: Number(row.id),
      text,
      isPhrase: Boolean(row.is_phrase),
      ipaUk: row.ipa_uk,
      ipaUs: row.ipa_us,
      sortOrder: row.sort_order,
    }
  })
  res.json({ items, total: items.length, index: letterIndex })
})

router.get('/notebooks/:id/words', optionalAuth, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canReadNotebook(notebook, req.user?.id)) {
    res.status(notebook?.kind === 'catalog' ? 404 : 401).json({
      error: notebook?.kind === 'catalog' ? '词本不存在' : '未登录',
    })
    return
  }
  const limit = Math.min(PAGE_SIZE_MAX, Math.max(1, Number(req.query.limit) || 50))
  const cursor = parseCursor(req.query.cursor)
  const fromIndexRaw = Number(req.query.fromIndex)
  const fromIndex =
    !cursor && Number.isFinite(fromIndexRaw) && fromIndexRaw > 0
      ? Math.floor(fromIndexRaw)
      : 0
  const total = cursor
    ? 0
    : (
        await query('SELECT count(*)::int AS n FROM words WHERE notebook_id = $1', [notebookId])
      ).rows[0].n

  let result
  if (fromIndex > 0) {
    result = await query(
      `SELECT * FROM words
       WHERE notebook_id = $1
       ORDER BY sort_order ASC, id ASC
       OFFSET $2 LIMIT $3`,
      [notebookId, fromIndex, limit + 1],
    )
  } else {
    const params = [notebookId]
    let where = 'notebook_id = $1'
    if (cursor) {
      params.push(cursor.sortOrder, cursor.wordId)
      where += ` AND (sort_order, id) > ($${params.length - 1}, $${params.length})`
    }
    params.push(limit + 1)
    result = await query(
      `SELECT * FROM words
       WHERE ${where}
       ORDER BY sort_order ASC, id ASC
       LIMIT $${params.length}`,
      params,
    )
  }
  const extra = result.rows.length > limit
  const rows = extra ? result.rows.slice(0, limit) : result.rows
  const last = rows[rows.length - 1]
  res.json({
    items: rows.map(mapWord),
    total,
    fromIndex,
    nextCursor: extra && last ? `${last.sort_order}:${last.id}` : null,
  })
})

router.post('/notebooks/:id/words', authRequired, async (req, res) => {
  const notebookId = Number(req.params.id)
  const notebook = await loadNotebook(notebookId)
  if (!canWriteNotebook(notebook, req.user.id)) {
    res.status(403).json({ error: '系统词书不能修改' })
    return
  }
  const body = req.body || {}
  const text = String(body.text || '').trim()
  if (!text) {
    res.status(400).json({ error: '单词不能为空' })
    return
  }
  const existing = await query(
    'SELECT id FROM words WHERE notebook_id = $1 AND lower(word) = lower($2)',
    [notebookId, text],
  )
  if (existing.rowCount > 0) {
    const row = (
      await query('SELECT * FROM words WHERE id = $1', [existing.rows[0].id])
    ).rows[0]
    res.json({ item: mapWord(row), created: false })
    return
  }
  const maxSort = (
    await query('SELECT coalesce(min(sort_order), 0) - 1 AS n FROM words WHERE notebook_id = $1', [
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
      Boolean(body.isPhrase || text.includes(' ')),
      body.ipaUk || null,
      body.ipaUs || null,
      JSON.stringify(body.definitions || []),
      JSON.stringify(
        (body.examples || []).map((e) => ({
          en: e.english ?? e.en ?? '',
          zh: e.chinese ?? e.zh ?? '',
        })),
      ),
      JSON.stringify(body.nearWords || []),
      JSON.stringify(body.synonyms || []),
      JSON.stringify(body.antonyms || []),
      maxSort,
    ],
  )
  res.status(201).json({ item: mapWord(inserted.rows[0]), created: true })
})

router.patch('/words/:id', authRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = await query(
    `SELECT w.*, n.kind, n.owner_user_id
     FROM words w JOIN notebooks n ON n.id = w.notebook_id
     WHERE w.id = $1`,
    [id],
  )
  const row = current.rows[0]
  if (!row || row.kind !== 'user' || Number(row.owner_user_id) !== req.user.id) {
    res.status(404).json({ error: '词条不存在' })
    return
  }
  const body = req.body || {}
  const updated = await query(
    `UPDATE words SET
       definitions = COALESCE($2::jsonb, definitions),
       examples = COALESCE($3::jsonb, examples),
       near_words = COALESCE($4::jsonb, near_words),
       synonyms = COALESCE($5::jsonb, synonyms),
       antonyms = COALESCE($6::jsonb, antonyms),
       ipa_uk = COALESCE($7, ipa_uk),
       ipa_us = COALESCE($8, ipa_us)
     WHERE id = $1
     RETURNING *`,
    [
      id,
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
      body.ipaUk ?? null,
      body.ipaUs ?? null,
    ],
  )
  res.json({ item: mapWord(updated.rows[0]) })
})

router.delete('/words/:id', authRequired, async (req, res) => {
  const id = Number(req.params.id)
  const current = await query(
    `SELECT w.id, n.kind, n.owner_user_id
     FROM words w JOIN notebooks n ON n.id = w.notebook_id
     WHERE w.id = $1`,
    [id],
  )
  const row = current.rows[0]
  if (row?.kind === 'catalog') {
    res.status(403).json({ error: '系统词书不能删除词条' })
    return
  }
  if (!row || row.kind !== 'user' || Number(row.owner_user_id) !== req.user.id) {
    res.status(404).json({ error: '词条不存在' })
    return
  }
  await query('DELETE FROM words WHERE id = $1', [id])
  res.json({ ok: true })
})
