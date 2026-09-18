import { pool, query } from './db.js'

/** Sandbox cash-out: fixed 0.01 yuan until real Alipay/WeChat is wired. */
export function withdrawConfig() {
  const sandbox = String(process.env.WITHDRAW_SANDBOX ?? 'true').toLowerCase() !== 'false'
  const amountFen = Math.max(1, Number(process.env.WITHDRAW_AMOUNT_FEN) || 1)
  const pointsCost = Math.max(1, Number(process.env.WITHDRAW_POINTS_COST) || 1)
  return {
    sandbox,
    amountFen,
    amountYuan: (amountFen / 100).toFixed(2),
    pointsCost,
    channels: [
      {
        id: 'alipay',
        name: '支付宝提现',
        accountLabel: '支付宝账号（手机号或邮箱）',
        accountHint: '请填写收款支付宝登录账号',
      },
      {
        id: 'wechat',
        name: '微信提现',
        accountLabel: '微信收款标识',
        accountHint: sandbox
          ? '沙箱测试可填任意标识，例如微信号'
          : '请填写已绑定的微信 OpenID / 商户收款账号',
      },
    ],
    note: sandbox
      ? '当前为沙箱模式：仅模拟打款成功，不会真实转账。单笔固定 0.01 元。'
      : '正式打款将调用支付宝/微信企业付款接口。',
  }
}

function mapWithdrawal(row) {
  if (!row) return null
  const amountFen = Math.max(0, Number(row.amount_fen || 0))
  return {
    id: Number(row.id),
    channel: row.channel,
    channelLabel: row.channel === 'wechat' ? '微信' : '支付宝',
    account: row.account,
    amountFen,
    amountYuan: (amountFen / 100).toFixed(2),
    pointsSpent: Number(row.points_spent || 0),
    status: row.status,
    statusLabel: statusLabel(row.status),
    providerTradeNo: row.provider_trade_no || null,
    errorMessage: row.error_message || null,
    remark: row.remark || null,
    sandbox: Boolean(row.sandbox),
    createdAt: row.created_at ? new Date(row.created_at).toISOString() : null,
    updatedAt: row.updated_at ? new Date(row.updated_at).toISOString() : null,
    paidAt:
      row.status === 'success' && row.updated_at
        ? new Date(row.updated_at).toISOString()
        : null,
  }
}

function statusLabel(status) {
  switch (status) {
    case 'pending':
      return '处理中'
    case 'success':
      return '已到账'
    case 'failed':
      return '失败'
    default:
      return status
  }
}

export async function listWithdrawals(userId, { page = 1, pageSize = 20 } = {}) {
  const p = Math.max(1, Number(page) || 1)
  const size = Math.min(50, Math.max(1, Number(pageSize) || 20))
  const offset = (p - 1) * size
  const total = (
    await query('SELECT count(*)::int AS n FROM withdrawals WHERE user_id = $1', [userId])
  ).rows[0].n
  const result = await query(
    `SELECT * FROM withdrawals WHERE user_id = $1
     ORDER BY created_at DESC LIMIT $2 OFFSET $3`,
    [userId, size, offset],
  )
  return {
    items: result.rows.map(mapWithdrawal),
    total,
    page: p,
    pageSize: size,
  }
}

/**
 * Debit points and (in sandbox) mark payout success immediately.
 * Real Alipay/WeChat transfer hooks can replace simulatePayout later.
 */
export async function createWithdrawal(userId, { channel, account } = {}) {
  const cfg = withdrawConfig()
  const ch = String(channel || '').trim().toLowerCase()
  if (ch !== 'alipay' && ch !== 'wechat') {
    return { ok: false, error: '请选择支付宝或微信提现' }
  }
  const profile = (
    await query('SELECT alipay_account, wechat_account FROM users WHERE id = $1', [userId])
  ).rows[0]
  const saved =
    ch === 'wechat'
      ? String(profile?.wechat_account || '').trim()
      : String(profile?.alipay_account || '').trim()
  let acct = String(account || '').trim() || saved
  if (!acct || acct.length < 3 || acct.length > 64) {
    return {
      ok: false,
      error: ch === 'wechat' ? '请先在个人资料中设置微信收款账号' : '请先在个人资料中设置支付宝收款账号',
    }
  }

  const amountFen = cfg.amountFen
  const pointsCost = cfg.pointsCost
  const client = await pool.connect()
  try {
    await client.query('BEGIN')
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

    const todayCount = (
      await client.query(
        `SELECT count(*)::int AS n FROM withdrawals
         WHERE user_id = $1 AND created_at >= date_trunc('day', now())
           AND status IN ('pending', 'success')`,
        [userId],
      )
    ).rows[0].n
    if (todayCount >= 20) {
      await client.query('ROLLBACK')
      return { ok: false, error: '今日提现次数已达上限，请明天再试' }
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

    const row = (
      await client.query(
        `INSERT INTO withdrawals
           (user_id, channel, account, amount_fen, points_spent, status, sandbox, remark)
         VALUES ($1,$2,$3,$4,$5,'pending',$6,$7)
         RETURNING *`,
        [
          userId,
          ch,
          acct,
          amountFen,
          pointsCost,
          cfg.sandbox,
          cfg.sandbox ? '沙箱模拟打款' : null,
        ],
      )
    ).rows[0]

    await client.query(
      `INSERT INTO points_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
       VALUES ($1, $2, $3, 'withdraw', 'withdrawal', $4)`,
      [userId, -pointsCost, nextBalance, String(row.id)],
    )

    const payout = await simulatePayout({
      channel: ch,
      account: acct,
      amountFen,
      withdrawalId: row.id,
      sandbox: cfg.sandbox,
    })

    const updated = (
      await client.query(
        `UPDATE withdrawals SET
           status = $1,
           provider_trade_no = $2,
           error_message = $3,
           remark = COALESCE($4, remark),
           updated_at = now()
         WHERE id = $5
         RETURNING *`,
        [
          payout.ok ? 'success' : 'failed',
          payout.tradeNo || null,
          payout.ok ? null : payout.error || '打款失败',
          payout.remark || null,
          row.id,
        ],
      )
    ).rows[0]

    if (!payout.ok) {
      // Refund points on failed payout
      await client.query(
        `UPDATE user_checkins SET total_points = total_points + $1, updated_at = now()
         WHERE user_id = $2`,
        [pointsCost, userId],
      )
      const refundBalance = nextBalance + pointsCost
      await client.query(
        `INSERT INTO points_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
         VALUES ($1, $2, $3, 'withdraw_refund', 'withdrawal', $4)`,
        [userId, pointsCost, refundBalance, String(row.id)],
      )
      await client.query('COMMIT')
      return {
        ok: false,
        error: payout.error || '打款失败，积分已退回',
        item: mapWithdrawal(updated),
        totalPoints: refundBalance,
      }
    }

    await client.query('COMMIT')
    return {
      ok: true,
      message: cfg.sandbox
        ? `沙箱提现成功：已模拟向${ch === 'wechat' ? '微信' : '支付宝'}打款 ¥${cfg.amountYuan}`
        : `提现成功：¥${cfg.amountYuan} 已提交打款`,
      item: mapWithdrawal(updated),
      totalPoints: nextBalance,
      config: cfg,
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

async function simulatePayout({ channel, account, amountFen, withdrawalId, sandbox }) {
  if (sandbox) {
    return {
      ok: true,
      tradeNo: `SANDBOX-${channel.toUpperCase()}-${withdrawalId}-${Date.now()}`,
      remark: `沙箱模拟打款 ${amountFen} 分 → ${account}`,
    }
  }
  // Real providers not wired yet — keep points safe by failing closed.
  return {
    ok: false,
    error:
      channel === 'wechat'
        ? '微信企业付款尚未开通，请先使用沙箱（WITHDRAW_SANDBOX=true）'
        : '支付宝商家转账尚未开通或未配置证书，请先使用沙箱',
  }
}
