import { pool, query } from './db.js'

export const GIFT_CATEGORIES = [
  { id: 'recommend', name: '推荐' },
  { id: 'points_only', name: '0元起兑' },
  { id: 'study', name: '学习好物' },
  { id: 'vip', name: '会员专享' },
  { id: 'physical', name: '实物礼品' },
]

export function mapGift(row) {
  const pointsCost = Math.max(0, Number(row.points_cost || 0))
  const cashFen = Math.max(0, Number(row.cash_fen || 0))
  const originalPriceFen = row.original_price_fen == null ? null : Math.max(0, Number(row.original_price_fen))
  const pointsOffsetFen =
    row.points_offset_fen == null
      ? Math.round(pointsCost * 2) // ~50 积分≈1 元 的展示用抵扣额
      : Math.max(0, Number(row.points_offset_fen))
  const stockRaw = Number(row.stock)
  const stock = !Number.isFinite(stockRaw) ? -1 : stockRaw < 0 ? -1 : Math.floor(stockRaw)
  return {
    id: Number(row.id),
    title: row.title,
    subtitle: row.subtitle || '',
    coverEmoji: row.cover_emoji || '🎁',
    coverColor: row.cover_color || '#1B6CA8',
    category: row.category || 'recommend',
    pointsCost,
    cashFen,
    cashYuan: (cashFen / 100).toFixed(2),
    originalPriceFen,
    originalPriceYuan: originalPriceFen == null ? null : (originalPriceFen / 100).toFixed(2),
    pointsOffsetFen,
    pointsOffsetYuan: (pointsOffsetFen / 100).toFixed(2),
    stock,
    stockLabel: stock < 0 ? '不限' : String(stock),
    redeemedCount: Math.max(0, Number(row.redeemed_count || 0)),
    sortOrder: Number(row.sort_order || 0),
    published: Boolean(row.published),
    needAddress: Boolean(row.need_address),
    description: row.description || '',
    createdAt: row.created_at ? new Date(row.created_at).toISOString() : null,
  }
}

/** Normalize admin stock input: -1 = unlimited, otherwise >= 0. */
export function normalizeGiftStock(raw) {
  if (raw == null || raw === '') return -1
  const n = Number(raw)
  if (!Number.isFinite(n)) return -1
  if (n < 0) return -1
  return Math.floor(n)
}

export function mapOrder(row) {
  return {
    id: Number(row.id),
    userId: Number(row.user_id),
    giftId: row.gift_id ? Number(row.gift_id) : null,
    giftTitle: row.gift_title,
    coverEmoji: row.cover_emoji || '🎁',
    coverColor: row.cover_color || '#1B6CA8',
    pointsSpent: Number(row.points_spent || 0),
    cashFen: Number(row.cash_fen || 0),
    cashYuan: (Number(row.cash_fen || 0) / 100).toFixed(2),
    status: row.status,
    addressName: row.address_name || null,
    addressPhone: row.address_phone || null,
    addressDetail: row.address_detail || null,
    remark: row.remark || null,
    createdAt: row.created_at ? new Date(row.created_at).toISOString() : null,
    updatedAt: row.updated_at ? new Date(row.updated_at).toISOString() : null,
    phone: row.phone || null,
  }
}

export function priceLabel(gift) {
  if (gift.cashFen > 0) return `${gift.pointsCost}积分 + ${gift.cashYuan}元`
  return `${gift.pointsCost}积分`
}

export async function listPublishedGifts({ category, q, page = 1, pageSize = 40 } = {}) {
  const params = []
  const where = ['published = TRUE']
  if (category && category !== 'recommend') {
    if (category === 'points_only') {
      where.push('cash_fen = 0')
    } else {
      params.push(category)
      where.push(`category = $${params.length}`)
    }
  }
  if (q) {
    params.push(`%${q}%`)
    where.push(`(title ILIKE $${params.length} OR subtitle ILIKE $${params.length})`)
  }
  const total = (
    await query(`SELECT count(*)::int AS n FROM gifts WHERE ${where.join(' AND ')}`, params)
  ).rows[0].n
  params.push(pageSize, (page - 1) * pageSize)
  const result = await query(
    `SELECT * FROM gifts
     WHERE ${where.join(' AND ')}
     ORDER BY sort_order ASC, id DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  )
  return { items: result.rows.map(mapGift), total, page, pageSize }
}

export async function getGift(id) {
  const row = (await query('SELECT * FROM gifts WHERE id = $1', [id])).rows[0]
  return row ? mapGift(row) : null
}

/**
 * Redeem a gift with points (cash portion is recorded, not charged online).
 */
export async function redeemGift(userId, giftId, address = {}) {
  const client = await pool.connect()
  try {
    await client.query('BEGIN')
    const gift = (
      await client.query('SELECT * FROM gifts WHERE id = $1 FOR UPDATE', [giftId])
    ).rows[0]
    if (!gift || !gift.published) {
      await client.query('ROLLBACK')
      return { ok: false, error: '礼品不存在或已下架' }
    }
    const stock = Number(gift.stock ?? -1)
    // stock < 0 means unlimited; stock === 0 (or NaN treated as 0) is sold out.
    if (!Number.isFinite(stock) || stock === 0) {
      await client.query('ROLLBACK')
      return { ok: false, error: '库存不足' }
    }
    const pointsCost = Math.max(0, Number(gift.points_cost || 0))
    const cashFen = Math.max(0, Number(gift.cash_fen || 0))
    const needAddress = Boolean(gift.need_address)
    const name = String(address.name || '').trim()
    const phone = String(address.phone || '').trim()
    const detail = String(address.detail || '').trim()
    if (needAddress && (!name || !phone || !detail)) {
      await client.query('ROLLBACK')
      return { ok: false, error: '请填写收货姓名、手机号和地址' }
    }

    const checkIn = (
      await client.query(
        `SELECT total_points, streak_days, last_checkin_date
         FROM user_checkins WHERE user_id = $1 FOR UPDATE`,
        [userId],
      )
    ).rows[0]
    const balance = Math.max(0, Number(checkIn?.total_points || 0))
    if (balance < pointsCost) {
      await client.query('ROLLBACK')
      return { ok: false, error: `积分不足，还差 ${pointsCost - balance} 分` }
    }
    const nextBalance = balance - pointsCost
    await client.query(
      `INSERT INTO user_checkins (user_id, total_points, streak_days, last_checkin_date, updated_at)
       VALUES ($1, $2, $3, $4, now())
       ON CONFLICT (user_id) DO UPDATE SET
         total_points = EXCLUDED.total_points,
         updated_at = now()`,
      [
        userId,
        nextBalance,
        Number(checkIn?.streak_days || 0),
        checkIn?.last_checkin_date || null,
      ],
    )
    await client.query(
      `INSERT INTO points_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
       VALUES ($1, $2, $3, 'redeem', 'gift', $4)`,
      [userId, -pointsCost, nextBalance, String(giftId)],
    )

    let status = 'completed'
    if (cashFen > 0) status = 'pending_cash'
    else if (needAddress) status = 'pending_ship'

    const order = (
      await client.query(
        `INSERT INTO gift_orders
           (user_id, gift_id, gift_title, cover_emoji, cover_color, points_spent, cash_fen, status,
            address_name, address_phone, address_detail, remark)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
         RETURNING *`,
        [
          userId,
          gift.id,
          gift.title,
          gift.cover_emoji,
          gift.cover_color,
          pointsCost,
          cashFen,
          status,
          name || null,
          phone || null,
          detail || null,
          cashFen > 0 ? '现金部分需线下支付/客服确认' : null,
        ],
      )
    ).rows[0]

    if (stock > 0) {
      // Defense in depth: conditional decrement so stock never goes below 0
      // even if another path bypassed FOR UPDATE.
      const dec = await client.query(
        `UPDATE gifts
         SET stock = stock - 1, redeemed_count = redeemed_count + 1
         WHERE id = $1 AND stock > 0
         RETURNING stock`,
        [gift.id],
      )
      if (dec.rowCount === 0) {
        await client.query('ROLLBACK')
        return { ok: false, error: '库存不足' }
      }
    } else {
      await client.query('UPDATE gifts SET redeemed_count = redeemed_count + 1 WHERE id = $1', [
        gift.id,
      ])
    }

    await client.query('COMMIT')
    return {
      ok: true,
      order: mapOrder(order),
      totalPoints: nextBalance,
      message:
        status === 'pending_cash'
          ? '积分已扣除，现金部分请等待客服确认'
          : status === 'pending_ship'
            ? '兑换成功，等待发货'
            : '兑换成功',
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

export async function ensureGiftSeed() {
  const count = (await query('SELECT count(*)::int AS n FROM gifts')).rows[0].n
  if (count > 0) return
  const seeds = [
    {
      title: '学习打卡纪念徽章（虚拟）',
      subtitle: '坚持的证明',
      emoji: '🏅',
      color: '#C9A227',
      category: 'study',
      points: 7,
      cash: 0,
      original: 990,
      offset: 14,
      needAddress: false,
      description: '连续学习纪念虚拟徽章，兑换后在账号中留存记录。',
    },
    {
      title: '词搭子主题书签套装',
      subtitle: '实体礼品 · 包邮',
      emoji: '🔖',
      color: '#2A9D8F',
      category: 'physical',
      points: 50,
      cash: 0,
      original: 1990,
      offset: 100,
      needAddress: true,
      description: '金属书签 ×2，需填写收货地址，人工发货。',
    },
    {
      title: '精美笔记本（A5）',
      subtitle: '积分 + 现金',
      emoji: '📓',
      color: '#E76F51',
      category: 'physical',
      points: 100,
      cash: 990,
      original: 2990,
      offset: 200,
      needAddress: true,
      description: '硬壳笔记本，积分抵扣后补差价 9.90 元（现金暂记待确认）。',
    },
    {
      title: '7 天专注学习卡',
      subtitle: '虚拟权益',
      emoji: '⏰',
      color: '#457B9D',
      category: 'vip',
      points: 21,
      cash: 0,
      original: 1500,
      offset: 42,
      needAddress: false,
      description: '兑换记录可作为专注打卡凭证（功能展示）。',
    },
    {
      title: '1 元红包体验券',
      subtitle: '0元起兑',
      emoji: '🧧',
      color: '#D62828',
      category: 'points_only',
      points: 30,
      cash: 0,
      original: 100,
      offset: 60,
      needAddress: false,
      description: '演示礼品：纯积分兑换。',
    },
    {
      title: '白象经典桶面整箱 12 桶',
      subtitle: '积分 + 现金示例',
      emoji: '🍜',
      color: '#9B2226',
      category: 'recommend',
      points: 217,
      cash: 2889,
      original: 3323,
      offset: 434,
      needAddress: true,
      description: '仿照积分商城「积分+现金」兑换示例；现金部分需客服确认后发货。',
    },
    {
      title: '记忆棉 U 型枕',
      subtitle: '出行伴侣',
      emoji: '🛏️',
      color: '#264653',
      category: 'recommend',
      points: 4699,
      cash: 990,
      original: 9990,
      offset: 9398,
      needAddress: true,
      description: '高分示例商品，展示大额积分 + 少量现金。',
    },
    {
      title: '词搭子贴纸包',
      subtitle: '可爱周边',
      emoji: '✨',
      color: '#9B5DE5',
      category: 'study',
      points: 15,
      cash: 0,
      original: 800,
      offset: 30,
      needAddress: false,
      description: '电子贴纸包兑换凭证。',
    },
  ]
  for (let i = 0; i < seeds.length; i++) {
    const s = seeds[i]
    await query(
      `INSERT INTO gifts
        (title, subtitle, cover_emoji, cover_color, category, points_cost, cash_fen,
         original_price_fen, points_offset_fen, stock, redeemed_count, sort_order,
         published, need_address, description)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,-1,$10,$11,TRUE,$12,$13)`,
      [
        s.title,
        s.subtitle,
        s.emoji,
        s.color,
        s.category,
        s.points,
        s.cash,
        s.original,
        s.offset,
        Math.floor(Math.random() * 8000) + 20,
        i,
        s.needAddress,
        s.description,
      ],
    )
  }
  console.log(`[gifts] seeded ${seeds.length} demo gifts`)
}
