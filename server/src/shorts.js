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
    if (cleaned.length >= 30) break
  }
  return cleaned
}

export function validateKeywords(list) {
  if (list.length < 1) return '请至少填写 1 个关键词'
  if (list.length > 30) return '关键词最多 30 个'
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

const CATEGORY_ORDER = SHORT_CATEGORIES.map((c) => c.id)

/**
 * Watch-time share decides how often each category appears.
 * A few seconds is still a cold start (even mix). Longer 口语 time takes most slots,
 * with a floor so the other categories still show up.
 */
export function categoryWeightsFromWatch(watchMsByCategory, favoriteCountByCategory = {}) {
  const weights = Object.fromEntries(CATEGORY_ORDER.map((cat) => [cat, 1]))
  const total = CATEGORY_ORDER.reduce((sum, cat) => sum + Math.max(0, Number(watchMsByCategory[cat]) || 0), 0)
  if (total >= 3000) {
    for (const cat of CATEGORY_ORDER) {
      const share = Math.max(0, Number(watchMsByCategory[cat]) || 0) / total
      weights[cat] = 0.35 + share * 5.65
    }
  }
  for (const cat of CATEGORY_ORDER) {
    const fav = Math.max(0, Number(favoriteCountByCategory[cat]) || 0)
    if (fav > 0) weights[cat] += Math.min(1.2, fav * 0.3)
  }
  return weights
}

function allocateCategorySlots(weights, limit) {
  const total = CATEGORY_ORDER.reduce((sum, cat) => sum + (weights[cat] || 0), 0) || 1
  const counts = Object.fromEntries(CATEGORY_ORDER.map((cat) => [cat, 0]))
  const remainders = []
  let used = 0
  for (const cat of CATEGORY_ORDER) {
    const exact = ((weights[cat] || 0) / total) * limit
    const base = Math.floor(exact)
    counts[cat] = base
    used += base
    remainders.push({ cat, rem: exact - base })
  }
  remainders.sort((a, b) => b.rem - a.rem)
  let i = 0
  while (used < limit && remainders.length) {
    counts[remainders[i % remainders.length].cat] += 1
    used += 1
    i += 1
  }
  return counts
}

/** Spread minority categories through the page instead of dumping them at the end. */
function spreadCategoryPattern(counts, limit) {
  const seq = Array(limit).fill(null)
  const taken = new Set()
  const ordered = CATEGORY_ORDER
    .map((cat) => [cat, counts[cat] || 0])
    .filter(([, n]) => n > 0)
    .sort((a, b) => a[1] - b[1])
  for (const [cat, n] of ordered) {
    for (let i = 1; i <= n; i++) {
      let pos = Math.round((i * (limit + 1)) / (n + 1)) - 1
      if (pos < 0) pos = 0
      while (pos < limit && taken.has(pos)) pos += 1
      if (pos >= limit) pos = seq.findIndex((slot) => slot == null)
      if (pos < 0) break
      seq[pos] = cat
      taken.add(pos)
    }
  }
  const fallback = CATEGORY_ORDER.slice().sort((a, b) => (counts[b] || 0) - (counts[a] || 0))[0] || 'speaking'
  return seq.map((cat) => cat || fallback)
}

function videoFreshness(row, excludeSet, recentIds) {
  const id = Number(row.id)
  let score = 1 / (1 + Math.max(0, Number(row.sort_order) || 0) * 0.05)
  if (!excludeSet.has(id)) score += 100
  if (!recentIds.has(id)) score += 20
  score += Math.random() * 0.2
  return score
}

/**
 * One page of the feed. Unseen videos of the preferred category come first inside
 * that category. When the catalog is exhausted, the same pool is reused so the
 * client can keep paging in a loop.
 */
export function planShortFeed(rows, { limit = 20, excludeIds = [], weights, recentIds = new Set() } = {}) {
  const size = Math.min(50, Math.max(1, Number(limit) || 20))
  const excludeSet = new Set(
    (Array.isArray(excludeIds) ? excludeIds : [])
      .map((id) => Number(id))
      .filter((id) => Number.isFinite(id) && id > 0),
  )
  const buckets = Object.fromEntries(CATEGORY_ORDER.map((cat) => [cat, []]))
  for (const row of rows) {
    buckets[normalizeCategory(row.category)].push(row)
  }
  for (const cat of CATEGORY_ORDER) {
    buckets[cat].sort((a, b) => videoFreshness(b, excludeSet, recentIds) - videoFreshness(a, excludeSet, recentIds))
  }
  const availableWeights = { ...weights }
  for (const cat of CATEGORY_ORDER) {
    if (!buckets[cat].length) availableWeights[cat] = 0
  }
  if (CATEGORY_ORDER.every((cat) => !buckets[cat].length)) return []
  const counts = allocateCategorySlots(availableWeights, size)
  const pattern = spreadCategoryPattern(counts, size)
  const pointers = Object.fromEntries(CATEGORY_ORDER.map((cat) => [cat, 0]))
  const used = new Set()
  const picked = []
  const substitute = CATEGORY_ORDER.slice().sort(
    (a, b) => (availableWeights[b] || 0) - (availableWeights[a] || 0),
  )
  for (const slot of pattern) {
    let row = null
    if (buckets[slot]?.length) {
      // Repeat this category before giving the slot away. A 口语-heavy mix stays 口语
      // even when that category has fewer videos than its slot count.
      row = takeCategoryVideo(buckets[slot], pointers, slot, used, false)
        || takeCategoryVideo(buckets[slot], pointers, slot, used, true)
    }
    if (!row) {
      for (const cat of substitute) {
        if (cat === slot) continue
        row = takeCategoryVideo(buckets[cat], pointers, cat, used, false)
          || takeCategoryVideo(buckets[cat], pointers, cat, used, true)
        if (row) break
      }
    }
    if (row) picked.push(row)
    if (picked.length >= size) break
  }
  return picked
}

function takeCategoryVideo(list, pointers, cat, used, allowRepeat) {
  if (!list?.length) return null
  for (let n = 0; n < list.length; n++) {
    const index = (pointers[cat] + n) % list.length
    const row = list[index]
    if (!used.has(Number(row.id))) {
      pointers[cat] = (index + 1) % list.length
      used.add(Number(row.id))
      return row
    }
  }
  if (!allowRepeat) return null
  const row = list[pointers[cat] % list.length]
  pointers[cat] = (pointers[cat] + 1) % list.length
  return row
}

/**
 * Category mix follows recent watch time. The page stays full even after every
 * video has already been shown, so clients can loop instead of stopping.
 */
export async function recommendShorts({
  userId = null,
  deviceKey = null,
  limit = 20,
  excludeIds = [],
  absoluteBase = '',
} = {}) {
  const size = Math.min(50, Math.max(1, Number(limit) || 20))
  const identityClause =
    userId != null
      ? 'user_id = $1'
      : deviceKey
        ? 'device_key = $1'
        : null
  const watchMs = { speaking: 0, vocab: 0, listening: 0 }
  const recentIds = new Set()
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
      watchMs[cat] = Math.max(0, Number(row.ms) || 0)
    }
    const recent = (
      await query(
        `SELECT video_id
         FROM short_video_watches
         WHERE ${identityClause}
           AND created_at > now() - interval '2 days'
         ORDER BY created_at DESC
         LIMIT 40`,
        [identityValue],
      )
    ).rows
    for (const row of recent) recentIds.add(Number(row.video_id))
  }

  const favoriteIds = new Set()
  const favoriteCounts = { speaking: 0, vocab: 0, listening: 0 }
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
      favoriteCounts[normalizeCategory(row.category)] = Math.max(0, Number(row.n) || 0)
    }
    const favIds = (
      await query(`SELECT video_id FROM short_video_favorites WHERE user_id = $1`, [userId])
    ).rows
    for (const row of favIds) favoriteIds.add(Number(row.video_id))
  }

  const candidates = (
    await query(
      `SELECT * FROM short_videos
       WHERE published = TRUE
       ORDER BY sort_order ASC, id DESC
       LIMIT 1000`,
    )
  ).rows
  const weights = categoryWeightsFromWatch(watchMs, favoriteCounts)
  const page = planShortFeed(candidates, {
    limit: size,
    excludeIds,
    weights,
    recentIds,
  })
  return page.map((row) =>
    mapShortVideo(
      { ...row, favorited: favoriteIds.has(Number(row.id)) },
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
