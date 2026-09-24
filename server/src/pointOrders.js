import crypto from 'node:crypto'
import { pool, query } from './db.js'
import { adjustPoints, aiImagePointsCost } from './points.js'
import { alipayConfig, buildAppPayOrderInfo, verifyNotify } from './alipay.js'
import { createWechatAppPrepay, parseWechatPayNotify, wechatConfig } from './wechat.js'

function defaultPackages() {
  const cost = Math.max(1, aiImagePointsCost())
  const priceFen = Math.max(1, Number(process.env.POINT_TEST_PRICE_FEN) || 10)
  const yuan = (priceFen / 100).toFixed(2)
  return [
    {
      id: 'test',
      title: '积分充值',
      subtitle: `测试价 ¥${yuan}，可生成 1 次 AI 配图`,
      priceFen,
      points: cost,
      badge: '测试',
    },
  ]
}

export function listPointPackages() {
  const raw = String(process.env.POINT_PACKAGES_JSON || '').trim()
  if (raw) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length) {
        return parsed
          .map((item) => ({
            id: String(item.id || '').trim(),
            title: String(item.title || '积分包').trim().slice(0, 40),
            subtitle: String(item.subtitle || '').trim().slice(0, 80),
            priceFen: Math.max(1, Number(item.priceFen) || 0),
            points: Math.max(1, Number(item.points) || 0),
            badge: item.badge ? String(item.badge).slice(0, 12) : null,
          }))
          .filter((item) => item.id && item.priceFen > 0 && item.points > 0)
      }
    } catch {
      // fall through
    }
  }
  return defaultPackages()
}

export function pointPayConfig() {
  const ali = alipayConfig()
  const wx = wechatConfig()
  return {
    alipaySandbox: Boolean(ali.sandbox),
    alipayReady: Boolean(!ali.sandbox && ali.configured),
    wechatReady: Boolean(wx.payReady),
    channels: [
      { id: 'wechat', name: '微信支付', ready: Boolean(wx.payReady) },
      { id: 'alipay', name: '支付宝', ready: Boolean(ali.sandbox || ali.configured) },
    ],
  }
}

function mapOrder(row) {
  if (!row) return null
  const amountFen = Math.max(0, Number(row.amount_fen || 0))
  const channel = row.pay_channel || (row.alipay_trade_no ? 'alipay' : null)
  return {
    id: Number(row.id),
    packageId: row.package_id,
    outTradeNo: row.out_trade_no,
    points: Number(row.points || 0),
    amountFen,
    amountYuan: (amountFen / 100).toFixed(2),
    status: row.status,
    statusLabel:
      row.status === 'paid' ? '已支付' : row.status === 'closed' ? '已关闭' : '待支付',
    payChannel: channel,
    alipayTradeNo: row.alipay_trade_no || null,
    providerTradeNo: row.provider_trade_no || row.alipay_trade_no || null,
    createdAt: row.created_at ? new Date(row.created_at).toISOString() : null,
    paidAt: row.paid_at ? new Date(row.paid_at).toISOString() : null,
  }
}

function newOutTradeNo(userId) {
  const ts = Date.now().toString(36)
  const rand = crypto.randomBytes(3).toString('hex')
  // WeChat APP pay: 6-32 chars, [A-Za-z0-9_\-|*]
  return `p${userId}${ts}${rand}`.replace(/[^A-Za-z0-9_\-|]/g, '').slice(0, 32)
}

export async function createPointOrder({ userId, packageId, channel = 'alipay' }) {
  const pkg = listPointPackages().find((p) => p.id === packageId)
  if (!pkg) return { ok: false, error: '积分包不存在' }
  const ch = String(channel || 'alipay').trim().toLowerCase()
  if (ch !== 'alipay' && ch !== 'wechat') {
    return { ok: false, error: '请选择微信支付或支付宝' }
  }

  const ali = alipayConfig()
  const wx = wechatConfig()
  if (ch === 'wechat' && !wx.payReady) {
    return {
      ok: false,
      error: '微信支付尚未配置完成（需商户 API 证书、序列号与回调地址）',
    }
  }
  if (ch === 'alipay' && !ali.sandbox && !ali.configured) {
    return { ok: false, error: '支付宝支付尚未配置完成' }
  }

  const outTradeNo = newOutTradeNo(userId)
  const row = (
    await query(
      `INSERT INTO point_orders
         (user_id, package_id, out_trade_no, points, amount_fen, status, pay_channel)
       VALUES ($1, $2, $3, $4, $5, 'pending', $6)
       RETURNING *`,
      [userId, pkg.id, outTradeNo, pkg.points, pkg.priceFen, ch],
    )
  ).rows[0]

  if (ch === 'wechat') {
    const built = await createWechatAppPrepay({
      outTradeNo,
      description: `词搭子-${pkg.title}`,
      amountFen: pkg.priceFen,
      attach: String(row.id),
    })
    if (!built.ok) return { ok: false, error: built.error || '微信下单失败' }
    return {
      ok: true,
      order: mapOrder(row),
      channel: 'wechat',
      wechatPay: built.pay,
      orderInfo: null,
      sandbox: false,
      package: pkg,
    }
  }

  let orderInfo = null
  const sandbox = Boolean(ali.sandbox)
  if (!sandbox && ali.configured) {
    const built = buildAppPayOrderInfo({
      outTradeNo,
      subject: `词搭子-${pkg.title}`,
      totalAmountYuan: (pkg.priceFen / 100).toFixed(2),
      body: `${pkg.points}积分`,
    })
    if (!built.ok) return { ok: false, error: built.error }
    orderInfo = built.orderInfo
  }

  return {
    ok: true,
    order: mapOrder(row),
    channel: 'alipay',
    orderInfo,
    wechatPay: null,
    sandbox,
    package: pkg,
  }
}

export async function getPointOrderForUser(userId, orderId) {
  const row = (
    await query('SELECT * FROM point_orders WHERE id = $1 AND user_id = $2', [orderId, userId])
  ).rows[0]
  return mapOrder(row)
}

export async function fulfillPaidOrder({
  outTradeNo,
  providerTradeNo = null,
  alipayTradeNo = null,
} = {}) {
  const tradeNo = providerTradeNo || alipayTradeNo || null
  const client = await pool.connect()
  try {
    await client.query('BEGIN')
    const row = (
      await client.query(
        `SELECT * FROM point_orders WHERE out_trade_no = $1 FOR UPDATE`,
        [outTradeNo],
      )
    ).rows[0]
    if (!row) {
      await client.query('ROLLBACK')
      return { ok: false, error: '订单不存在' }
    }
    if (row.status === 'paid') {
      await client.query('COMMIT')
      return { ok: true, already: true, order: mapOrder(row) }
    }
    if (row.status !== 'pending') {
      await client.query('ROLLBACK')
      return { ok: false, error: '订单状态不可支付' }
    }
    const credited = await adjustPoints({
      userId: row.user_id,
      delta: Number(row.points),
      reason: 'purchase',
      refType: 'point_order',
      refId: row.id,
      client,
    })
    if (!credited.ok) {
      await client.query('ROLLBACK')
      return credited
    }
    const updated = (
      await client.query(
        `UPDATE point_orders
         SET status = 'paid',
             alipay_trade_no = COALESCE($2, alipay_trade_no),
             provider_trade_no = COALESCE($3, provider_trade_no),
             paid_at = now(),
             updated_at = now()
         WHERE id = $1
         RETURNING *`,
        [row.id, alipayTradeNo, tradeNo],
      )
    ).rows[0]
    await client.query('COMMIT')
    return { ok: true, order: mapOrder(updated), balance: credited.balance }
  } catch (error) {
    await client.query('ROLLBACK')
    throw error
  } finally {
    client.release()
  }
}

export async function simulatePayOrder({ userId, orderId }) {
  const cfg = alipayConfig()
  if (!cfg.sandbox) return { ok: false, error: '正式环境不支持模拟支付' }
  const row = (
    await query('SELECT * FROM point_orders WHERE id = $1 AND user_id = $2', [orderId, userId])
  ).rows[0]
  if (!row) return { ok: false, error: '订单不存在' }
  if (row.pay_channel && row.pay_channel !== 'alipay') {
    return { ok: false, error: '该订单不是支付宝沙箱订单' }
  }
  return fulfillPaidOrder({
    outTradeNo: row.out_trade_no,
    alipayTradeNo: `sandbox_${Date.now()}`,
    providerTradeNo: `sandbox_${Date.now()}`,
  })
}

export async function handleAlipayNotify(params) {
  const cfg = alipayConfig()
  if (!cfg.configured) return { ok: false, reply: 'failure' }
  if (!verifyNotify(params, cfg.alipayPublicKey)) {
    return { ok: false, reply: 'failure' }
  }
  const tradeStatus = String(params.trade_status || '')
  if (tradeStatus !== 'TRADE_SUCCESS' && tradeStatus !== 'TRADE_FINISHED') {
    return { ok: true, reply: 'success' }
  }
  const outTradeNo = String(params.out_trade_no || '')
  const tradeNo = String(params.trade_no || '')
  const result = await fulfillPaidOrder({
    outTradeNo,
    alipayTradeNo: tradeNo,
    providerTradeNo: tradeNo,
  })
  return { ok: result.ok, reply: result.ok ? 'success' : 'failure' }
}

export async function handleWechatPayNotify(body) {
  const parsed = parseWechatPayNotify(body)
  if (!parsed.ok) {
    return { ok: false, httpStatus: 400, reply: { code: 'FAIL', message: parsed.error } }
  }
  if (parsed.eventType && parsed.eventType !== 'TRANSACTION.SUCCESS') {
    return { ok: true, httpStatus: 200, reply: { code: 'SUCCESS', message: '成功' } }
  }
  if (parsed.tradeState && parsed.tradeState !== 'SUCCESS') {
    return { ok: true, httpStatus: 200, reply: { code: 'SUCCESS', message: '成功' } }
  }
  if (!parsed.outTradeNo) {
    return { ok: false, httpStatus: 400, reply: { code: 'FAIL', message: '缺少订单号' } }
  }
  const result = await fulfillPaidOrder({
    outTradeNo: parsed.outTradeNo,
    providerTradeNo: parsed.transactionId,
  })
  if (!result.ok) {
    return { ok: false, httpStatus: 500, reply: { code: 'FAIL', message: result.error || '入账失败' } }
  }
  return { ok: true, httpStatus: 200, reply: { code: 'SUCCESS', message: '成功' } }
}

export async function listUserPointOrders(userId, { page = 1, pageSize = 20 } = {}) {
  const p = Math.max(1, Number(page) || 1)
  const size = Math.min(50, Math.max(1, Number(pageSize) || 20))
  const total = (
    await query(`SELECT count(*)::int AS n FROM point_orders WHERE user_id = $1`, [userId])
  ).rows[0].n
  const rows = (
    await query(
      `SELECT * FROM point_orders WHERE user_id = $1
       ORDER BY id DESC LIMIT $2 OFFSET $3`,
      [userId, size, (p - 1) * size],
    )
  ).rows
  return { items: rows.map(mapOrder), total, page: p, pageSize: size }
}
