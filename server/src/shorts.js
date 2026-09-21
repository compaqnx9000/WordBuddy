import { query } from './db.js'

export const SHORT_CATEGORIES = [
  { id: 'speaking', name: '口语' },
  { id: 'vocab', name: '单词' },
  { id: 'listening', name: '听力' },
]

const CATEGORY_IDS = new Set(SHORT_CATEGORIES.map((c) => c.id))

export function normalizeCategory(raw) {
  const id = String(raw || '').trim()
  return CATEGORY_IDS.has(id) ? id : 'speaking'
}

export function normalizeKeywords(raw) {
  let list = []
  if (Array.isArray(raw)) list = raw
  else if (typeof raw === 'string') {
    list = raw
      .split(/[,，、\n]/)
      .map((s) => s.trim())
      .filter(Boolean)
  }
  const cleaned = []
  const seen = new Set()
  for (const item of list) {
    const word = String(item || '')
      .trim()
      .slice(0, 40)
    if (!word) continue
    const key = word.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    cleaned.push(word)
    if (cleaned.length >= 5) break
  }
  return cleaned
}

export function validateKeywords(list) {
  if (list.length < 3) return '请填写 3～5 个关键词'
  if (list.length > 5) return '关键词最多 5 个'
  return null
}

export function mapShortVideo(row, { absoluteBase } = {}) {
  const keywords = Array.isArray(row.keywords)
    ? row.keywords.map((k) => String(k))
    : typeof row.keywords === 'string'
      ? (() => {
          try {
            const parsed = JSON.parse(row.keywords)
            return Array.isArray(parsed) ? parsed.map((k) => String(k)) : []
          } catch {
            return []
          }
        })()
      : []
  let videoUrl = String(row.video_url || '').trim()
  let coverUrl = String(row.cover_url || '').trim()
  if (absoluteBase) {
    const base = absoluteBase.replace(/\/$/, '')
    if (videoUrl.startsWith('/')) videoUrl = `${base}${videoUrl}`
    if (coverUrl.startsWith('/')) coverUrl = `${base}${coverUrl}`
  }
  const category = normalizeCategory(row.category)
  const categoryMeta = SHORT_CATEGORIES.find((c) => c.id === category)
  return {
    id: String(row.id),
    title: row.title || '',
    author: row.author || '词搭子',
    caption: row.caption || '',
    videoUrl,
    coverUrl: coverUrl || null,
    category,
    categoryName: categoryMeta?.name || category,
    keywords,
    relatedWords: keywords,
    durationMs: row.duration_ms == null ? null : Number(row.duration_ms),
    sortOrder: Number(row.sort_order || 0),
    published: Boolean(row.published),
    favorited: Boolean(row.favorited),
    favoritedAt: row.favorited_at ? new Date(row.favorited_at).toISOString() : null,
    createdAt: row.created_at ? new Date(row.created_at).toISOString() : null,
  }
}

export function publicBaseFromReq(req) {
  const envBase = String(process.env.PUBLIC_BASE_URL || '').trim()
  if (envBase) return envBase.replace(/\/$/, '')
  const host = req.get('x-forwarded-host') || req.get('host')
  if (!host) return ''
  const proto = req.get('x-forwarded-proto') || req.protocol || 'http'
  return `${proto}://${host}`.replace(/\/$/, '')
}

export async function listAdminShorts({ q, category, published, page = 1, pageSize = 20 } = {}) {
  const params = []
  const where = ['TRUE']
  if (q) {
    params.push(`%${q}%`)
    where.push(
      `(title ILIKE $${params.length} OR caption ILIKE $${params.length} OR author ILIKE $${params.length} OR keywords::text ILIKE $${params.length})`,
    )
  }
  if (category && CATEGORY_IDS.has(category)) {
    params.push(category)
    where.push(`category = $${params.length}`)
  }
  if (published === true || published === '1' || published === 'true') {
    where.push('published = TRUE')
  } else if (published === false || published === '0' || published === 'false') {
    where.push('published = FALSE')
  }
  const whereSql = where.join(' AND ')
  const total = (await query(`SELECT count(*)::int AS n FROM short_videos WHERE ${whereSql}`, params)).rows[0].n
  params.push(pageSize, (page - 1) * pageSize)
  const result = await query(
    `SELECT * FROM short_videos
     WHERE ${whereSql}
     ORDER BY sort_order ASC, id DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  )
  return { items: result.rows.map((row) => mapShortVideo(row)), total, page, pageSize }
}

/**
 * Category affinity from recent watch time, then rank unpublished-excluded videos.
 * Cold start: uniform category weights + sort_order.
 */
export async function recommendShorts({
  userId = null,
  deviceKey = null,
  limit = 20,
  excludeIds = [],
  absoluteBase = '',
} = {}) {
  const size = Math.min(50, Math.max(1, Number(limit) || 20))
  const exclude = (Array.isArray(excludeIds) ? excludeIds : [])
    .map((id) => Number(id))
    .filter((id) => Number.isFinite(id) && id > 0)

  const affinity = { speaking: 1, vocab: 1, listening: 1 }
  const identityClause =
    userId != null
      ? 'user_id = $1'
      : deviceKey
        ? 'device_key = $1'
        : null
  if (identityClause) {
    const identityValue = userId != null ? userId : deviceKey
    const rows = (
      await query(
        `SELECT v.category, COALESCE(SUM(w.watch_ms), 0)::bigint AS ms
         FROM short_video_watches w
         JOIN short_videos v ON v.id = w.video_id
         WHERE ${identityClause}
           AND w.created_at > now() - interval '30 days'
         GROUP BY v.category`,
        [identityValue],
      )
    ).rows
    for (const row of rows) {
      const cat = normalizeCategory(row.category)
      const ms = Math.max(0, Number(row.ms) || 0)
      // Soft floor so new categories still appear; boost by watch time (minutes).
      affinity[cat] = 1 + Math.log1p(ms / 1000) * 2
    }
  }

  const params = []
  const where = ['published = TRUE']
  if (exclude.length) {
    params.push(exclude)
    where.push(`id <> ALL($${params.length}::bigint[])`)
  }
  const candidates = (
    await query(
      `SELECT * FROM short_videos
       WHERE ${where.join(' AND ')}
       ORDER BY sort_order ASC, id DESC
       LIMIT 400`,
      params,
    )
  ).rows

  // Recently watched demotion
  const recentIds = new Set()
  if (identityClause) {
    const identityValue = userId != null ? userId : deviceKey
    const recent = (
      await query(
        `SELECT video_id
         FROM short_video_watches
         WHERE ${identityClause}
           AND created_at > now() - interval '7 days'
         ORDER BY created_at DESC
         LIMIT 80`,
        [identityValue],
      )
    ).rows
    for (const row of recent) recentIds.add(Number(row.video_id))
  }

  // Favorite category affinity (logged-in only) — helps push liked topics.
  const favoriteIds = new Set()
  if (userId != null) {
    const favCats = (
      await query(
        `SELECT v.category, count(*)::int AS n
         FROM short_video_favorites f
         JOIN short_videos v ON v.id = f.video_id
         WHERE f.user_id = $1
         GROUP BY v.category`,
        [userId],
      )
    ).rows
    for (const row of favCats) {
      const cat = normalizeCategory(row.category)
      affinity[cat] = (affinity[cat] || 1) + Math.max(0, Number(row.n) || 0) * 0.85
    }
    const favIds = (
      await query(`SELECT video_id FROM short_video_favorites WHERE user_id = $1`, [userId])
    ).rows
    for (const row of favIds) favoriteIds.add(Number(row.video_id))
  }

  const scored = candidates.map((row) => {
    const cat = normalizeCategory(row.category)
    const catScore = affinity[cat] || 1
    const watchedPenalty = recentIds.has(Number(row.id)) ? 0.25 : 1
    const sortBoost = 1 / (1 + Math.max(0, Number(row.sort_order) || 0) * 0.02)
    const jitter = 0.92 + Math.random() * 0.16
    return {
      row,
      score: catScore * watchedPenalty * sortBoost * jitter,
    }
  })
  scored.sort((a, b) => b.score - a.score)

  return scored.slice(0, size).map((item) =>
    mapShortVideo(
      { ...item.row, favorited: favoriteIds.has(Number(item.row.id)) },
      { absoluteBase },
    ),
  )
}

export async function setShortFavorite({ userId, videoId, favorited }) {
  const uid = Number(userId)
  const vid = Number(videoId)
  if (!Number.isFinite(uid) || uid <= 0) return { ok: false, error: '请先登录' }
  if (!Number.isFinite(vid) || vid <= 0) return { ok: false, error: '无效视频' }
  const exists = (await query('SELECT id FROM short_videos WHERE id = $1 AND published = TRUE', [vid]))
    .rows[0]
  if (!exists) return { ok: false, error: '视频不存在' }
  if (favorited) {
    await query(
      `INSERT INTO short_video_favorites (user_id, video_id)
       VALUES ($1, $2)
       ON CONFLICT (user_id, video_id) DO NOTHING`,
      [uid, vid],
    )
  } else {
    await query(`DELETE FROM short_video_favorites WHERE user_id = $1 AND video_id = $2`, [uid, vid])
  }
  return { ok: true, favorited: Boolean(favorited) }
}

export async function listShortFavorites({
  userId,
  page = 1,
  pageSize = 40,
  absoluteBase = '',
} = {}) {
  const uid = Number(userId)
  if (!Number.isFinite(uid) || uid <= 0) return { items: [], total: 0, page: 1, pageSize }
  const size = Math.min(60, Math.max(1, Number(pageSize) || 40))
  const pg = Math.max(1, Number(page) || 1)
  const total = (
    await query(`SELECT count(*)::int AS n FROM short_video_favorites WHERE user_id = $1`, [uid])
  ).rows[0].n
  const rows = (
    await query(
      `SELECT v.*, TRUE AS favorited, f.created_at AS favorited_at
       FROM short_video_favorites f
       JOIN short_videos v ON v.id = f.video_id
       WHERE f.user_id = $1 AND v.published = TRUE
       ORDER BY f.created_at DESC
       LIMIT $2 OFFSET $3`,
      [uid, size, (pg - 1) * size],
    )
  ).rows
  return {
    items: rows.map((row) => mapShortVideo(row, { absoluteBase })),
    total,
    page: pg,
    pageSize: size,
  }
}

export async function listUserShortFavoritesAdmin({ userId, limit = 50 } = {}) {
  const uid = Number(userId)
  if (!Number.isFinite(uid) || uid <= 0) return []
  const size = Math.min(100, Math.max(1, Number(limit) || 50))
  const rows = (
    await query(
      `SELECT v.id, v.title, v.author, v.category, v.cover_url, v.video_url, f.created_at AS favorited_at
       FROM short_video_favorites f
       JOIN short_videos v ON v.id = f.video_id
       WHERE f.user_id = $1
       ORDER BY f.created_at DESC
       LIMIT $2`,
      [uid, size],
    )
  ).rows
  return rows.map((row) => ({
    id: String(row.id),
    title: row.title || '',
    author: row.author || '词搭子',
    category: normalizeCategory(row.category),
    categoryName: SHORT_CATEGORIES.find((c) => c.id === normalizeCategory(row.category))?.name || '',
    coverUrl: row.cover_url || null,
    videoUrl: row.video_url || '',
    favoritedAt: row.favorited_at ? new Date(row.favorited_at).toISOString() : null,
  }))
}

export async function recordWatch({
  videoId,
  userId = null,
  deviceKey = null,
  watchMs = 0,
  completed = false,
}) {
  const id = Number(videoId)
  if (!Number.isFinite(id) || id <= 0) return { ok: false, error: '无效视频' }
  const exists = (await query('SELECT id, category FROM short_videos WHERE id = $1 AND published = TRUE', [id]))
    .rows[0]
  if (!exists) return { ok: false, error: '视频不存在' }
  const ms = Math.max(0, Math.min(600_000, Math.floor(Number(watchMs) || 0)))
  if (ms < 300 && !completed) return { ok: true, skipped: true }
  await query(
    `INSERT INTO short_video_watches (user_id, device_key, video_id, category, watch_ms, completed)
     VALUES ($1, $2, $3, $4, $5, $6)`,
    [userId, deviceKey || null, id, exists.category, ms, Boolean(completed)],
  )
  return { ok: true }
}
