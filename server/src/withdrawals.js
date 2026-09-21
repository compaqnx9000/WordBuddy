import { pool, query } from './db.js'
import {
  alipayConfig,
  identityType,
  interpretTransferData,
  queryAlipayTransfer,
  transferToAlipay,
  withdrawalOutBizNo,
} from './alipay.js'
import {
  isWechatOpenId,
  queryWechatTransfer,
  transferToWechat,
  wechatConfig,
  wechatOutBillNo,
} from './wechat.js'

/** Live Alipay TRANS_ACCOUNT_NO_PWD minimum is 0.10 yuan. */
const LIVE_MIN_FEN = 10

export function withdrawConfig() {
  const pay = alipayConfig()
  const sandboxEnv = process.env.WITHDRAW_SANDBOX
  const sandbox =
    sandboxEnv != null && sandboxEnv !== ''
      ? String(sandboxEnv).toLowerCase() !== 'false'
      : false
  const rawFen = Number(process.env.WITHDRAW_AMOUNT_FEN)
  const rawPoints = Number(process.env.WITHDRAW_POINTS_COST)
  const amountFen = sandbox
    ? Math.max(1, Number.isFinite(rawFen) ? rawFen : 1)
    : Math.max(LIVE_MIN_FEN, Number.isFinite(rawFen) && rawFen > 0 ? rawFen : 100)
  const pointsCost = sandbox
    ? Math.max(1, Number.isFinite(rawPoints) ? rawPoints : 1)
    : Math.max(1, Number.isFinite(rawPoints) ? rawPoints : 10)
  const wx = wechatConfig()
  const alipayReady = pay.transferReady && !sandbox
  const wechatReady = wx.transferReady && !sandbox
  const amountText = `单笔提现 ¥${(amountFen / 100).toFixed(2)}，消耗 ${pointsCost} 积分`
  let note
  if (sandbox) {
    note = '当前为沙箱模式：仅模拟打款成功，不会真实转账。'
  } else {
    const parts = [amountText]
    parts.push(
      wechatReady
        ? '微信需填写该商户 App 下的 OpenID，提交后可能要在微信里确认收款。'
        : '微信商家转账尚未配置商户号/证书，暂时无法打到微信。',
    )
    parts.push(
      alipayReady
        ? '支付宝需账号与实名一致。'
        : '支付宝应用还在审核/未上线，暂可能无法打款。',
    )
    note = parts.join('')
  }
  return {
    sandbox,
    amountFen,
    amountYuan: (amountFen / 100).toFixed(2),
    pointsCost,
    alipayReady,
    wechatReady,
    certMode: Boolean(pay.certMode),
    channels: [
      {
        id: 'wechat',
        name: '微信提现',
        accountLabel: '微信 OpenID',
        accountHint: '请填写微信 OpenID（以 o 开头），不能填微信号',
      },
      {
        id: 'alipay',
        name: '支付宝提现',
        accountLabel: '支付宝账号（手机号或邮箱）',
        accountHint: '请填写收款支付宝登录账号，并填写与账号一致的实名',
      },
    ],
    note,
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
  try {
    await reconcilePendingWithdrawals(userId)
  } catch (error) {
    console.error('[withdrawals/reconcile]', error)
  }
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
 * Debit points first, then call Alipay. Unknown results stay pending (no refund)
 * until query confirms success or failure.
 */
export async function createWithdrawal(userId, { channel, account, realName } = {}) {
  const cfg = withdrawConfig()
  const ch = String(channel || '').trim().toLowerCase()
  if (ch !== 'alipay' && ch !== 'wechat') {
    return { ok: false, error: '请选择支付宝或微信提现' }
  }
  if (!cfg.sandbox && ch === 'wechat' && !cfg.wechatReady) {
    return { ok: false, error: '微信商家转账尚未配置完成，暂时无法提现' }
  }
  if (!cfg.sandbox && ch === 'alipay' && !cfg.alipayReady) {
    return { ok: false, error: '支付宝商家转账尚未配置完成，暂时无法提现' }
  }
  const profile = (
    await query(
      'SELECT alipay_account, alipay_name, wechat_account FROM users WHERE id = $1',
      [userId],
    )
  ).rows[0]
  const saved =
    ch === 'wechat'
      ? String(profile?.wechat_account || '').trim()
      : String(profile?.alipay_account || '').trim()
  let acct = String(account || '').trim() || saved
  if (!acct || acct.length < 3 || acct.length > 128) {
    return {
      ok: false,
      error: ch === 'wechat' ? '请先在个人资料中设置微信收款账号' : '请先绑定支付宝收款账号',
    }
  }
  const payeeName = String(realName || profile?.alipay_name || '').trim()
  if (
    ch === 'alipay' &&
    !cfg.sandbox &&
    identityType(acct) === 'ALIPAY_LOGON_ID' &&
    payeeName.length < 2
  ) {
    return { ok: false, error: '请先绑定当前手机上的支付宝账号' }
  }
  if (ch === 'wechat' && !cfg.sandbox && !isWechatOpenId(acct)) {
    return { ok: false, error: '请填写微信 OpenID（以 o 开头，不是微信号）' }
  }

  const amountFen = cfg.amountFen
  const pointsCost = cfg.pointsCost
  const client = await pool.connect()
  let row
  let nextBalance
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

    nextBalance = balance - pointsCost
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

    row = (
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
          cfg.sandbox ? '沙箱模拟打款' : ch === 'wechat' ? '待微信打款' : '待支付宝打款',
        ],
      )
    ).rows[0]

    await client.query(
      `INSERT INTO points_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
       VALUES ($1, $2, $3, 'withdraw', 'withdrawal', $4)`,
      [userId, -pointsCost, nextBalance, String(row.id)],
    )
    await client.query('COMMIT')
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

  let payout
  try {
    payout = await performPayout({
      channel: ch,
      account: acct,
      realName: payeeName,
      amountFen,
      withdrawalId: row.id,
      sandbox: cfg.sandbox,
    })
  } catch (error) {
    console.error('[withdrawals/payout]', error)
    payout = {
      ok: true,
      pending: true,
      remark: ch === 'wechat' ? '微信结果确认中，请稍后在记录中查看' : '支付宝结果确认中，请稍后在记录中查看',
    }
  }

  return finalizeWithdrawal({
    withdrawalId: row.id,
    userId,
    pointsCost,
    payout,
    cfg,
    channel: ch,
  })
}

async function finalizeWithdrawal({ withdrawalId, userId, pointsCost, payout, cfg, channel }) {
  const client = await pool.connect()
  try {
    await client.query('BEGIN')
    const locked = (
      await client.query(`SELECT * FROM withdrawals WHERE id = $1 FOR UPDATE`, [withdrawalId])
    ).rows[0]
    if (!locked) {
      await client.query('ROLLBACK')
      return { ok: false, error: '提现记录不存在' }
    }
    if (locked.status !== 'pending') {
      const checkIn = (
        await client.query('SELECT total_points FROM user_checkins WHERE user_id = $1', [userId])
      ).rows[0]
      await client.query('COMMIT')
      return {
        ok: locked.status === 'success',
        error: locked.status === 'failed' ? locked.error_message || '打款失败' : undefined,
        message:
          locked.status === 'success'
            ? `提现成功：¥${cfg.amountYuan} 已打款到${channel === 'wechat' ? '微信' : '支付宝'}`
            : locked.status === 'pending'
              ? `提现已提交：¥${cfg.amountYuan} ${channel === 'wechat' ? '微信' : '支付宝'}处理中`
              : locked.error_message,
        item: mapWithdrawal(locked),
        totalPoints: Number(checkIn?.total_points || 0),
        config: cfg,
      }
    }

    let status = 'failed'
    if (payout.ok && payout.pending) status = 'pending'
    else if (payout.ok) status = 'success'

    const updated = (
      await client.query(
        `UPDATE withdrawals SET
           status = $1,
           provider_trade_no = COALESCE($2, provider_trade_no),
           error_message = $3,
           remark = COALESCE($4, remark),
           updated_at = now()
         WHERE id = $5
         RETURNING *`,
        [
          status,
          payout.tradeNo || null,
          status === 'failed' ? payout.error || '打款失败' : null,
          payout.remark || null,
          withdrawalId,
        ],
      )
    ).rows[0]

    if (status === 'failed') {
      const already = (
        await client.query(
          `SELECT 1 FROM points_ledger
           WHERE user_id = $1 AND ref_type = 'withdrawal' AND ref_id = $2 AND reason = 'withdraw_refund'
           LIMIT 1`,
          [userId, String(withdrawalId)],
        )
      ).rows[0]
      let refundBalance
      if (!already) {
        const checkIn = (
          await client.query(
            `SELECT total_points FROM user_checkins WHERE user_id = $1 FOR UPDATE`,
            [userId],
          )
        ).rows[0]
        refundBalance = Math.max(0, Number(checkIn?.total_points || 0)) + pointsCost
        await client.query(
          `UPDATE user_checkins SET total_points = $1, updated_at = now() WHERE user_id = $2`,
          [refundBalance, userId],
        )
        await client.query(
          `INSERT INTO points_ledger (user_id, delta, balance_after, reason, ref_type, ref_id)
           VALUES ($1, $2, $3, 'withdraw_refund', 'withdrawal', $4)`,
          [userId, pointsCost, refundBalance, String(withdrawalId)],
        )
      } else {
        const checkIn = (
          await client.query('SELECT total_points FROM user_checkins WHERE user_id = $1', [userId])
        ).rows[0]
        refundBalance = Number(checkIn?.total_points || 0)
      }
      await client.query('COMMIT')
      return {
        ok: false,
        error: payout.error || '打款失败，积分已退回',
        item: mapWithdrawal(updated),
        totalPoints: refundBalance,
        config: cfg,
      }
    }

    const checkIn = (
      await client.query('SELECT total_points FROM user_checkins WHERE user_id = $1', [userId])
    ).rows[0]
    await client.query('COMMIT')
    const dest = channel === 'wechat' ? '微信' : '支付宝'
    const message =
      status === 'pending'
        ? payout.remark && /确认收款/.test(payout.remark)
          ? `提现已提交：¥${cfg.amountYuan}，请打开微信确认收款`
          : `提现已提交：¥${cfg.amountYuan} ${dest}处理中`
        : cfg.sandbox
          ? `沙箱提现成功：已模拟向${dest}打款 ¥${cfg.amountYuan}`
          : `提现成功：¥${cfg.amountYuan} 已打款到${dest}`
    return {
      ok: true,
      message,
      item: mapWithdrawal(updated),
      totalPoints: Number(checkIn?.total_points || 0),
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

async function performPayout({ channel, account, realName, amountFen, withdrawalId, sandbox }) {
  if (sandbox) {
    return {
      ok: true,
      pending: false,
      tradeNo: `SANDBOX-${channel.toUpperCase()}-${withdrawalId}-${Date.now()}`,
      remark: `沙箱模拟打款 ${amountFen} 分 → ${account}`,
    }
  }
  if (channel === 'wechat') {
    return transferToWechat({
      outBillNo: wechatOutBillNo(withdrawalId),
      amountFen,
      openid: account,
      remark: `积分提现#${withdrawalId}`,
    })
  }
  return transferToAlipay({
    outBizNo: withdrawalOutBizNo(withdrawalId),
    amountYuan: (amountFen / 100).toFixed(2),
    loginId: account,
    realName,
    title: '词搭子积分提现',
    remark: `积分提现#${withdrawalId}`,
  })
}

export async function reconcilePendingWithdrawals(userId) {
  const pending = await query(
    `SELECT w.*, u.alipay_name
     FROM withdrawals w
     JOIN users u ON u.id = w.user_id
     WHERE w.user_id = $1
       AND w.status = 'pending'
       AND w.sandbox = false
       AND w.channel IN ('alipay', 'wechat')
     ORDER BY w.id ASC
     LIMIT 8`,
    [userId],
  )
  const cfg = withdrawConfig()
  for (const row of pending.rows) {
    let payout = null
    try {
      if (row.channel === 'wechat') {
        const queried = await queryWechatTransfer(wechatOutBillNo(row.id))
        if (queried.ok || queried.missing === false) payout = queried
        else if (queried.missing) {
          payout = await transferToWechat({
            outBillNo: wechatOutBillNo(row.id),
            amountFen: Number(row.amount_fen || 0),
            openid: row.account,
            remark: `积分提现#${row.id}`,
          })
        } else if (!queried.ok && !queried.pending) {
          payout = queried
        }
      } else {
        const outBizNo = withdrawalOutBizNo(row.id)
        const queried = await queryAlipayTransfer(outBizNo)
        const interpreted = interpretTransferData(queried.data || {})
        if (interpreted.ok || (!interpreted.missing && queried.data?.status)) {
          payout = interpreted
        } else if (interpreted.missing) {
          payout = await transferToAlipay({
            outBizNo,
            amountYuan: (Number(row.amount_fen || 0) / 100).toFixed(2),
            loginId: row.account,
            realName: row.alipay_name,
            title: '词搭子积分提现',
            remark: `积分提现#${row.id}`,
          })
        } else if (!interpreted.ok && !interpreted.pending) {
          payout = interpreted
        }
      }
    } catch (error) {
      console.error('[withdrawals/query]', row.id, error)
      continue
    }
    if (!payout) continue
    if (payout.pending) continue
    await finalizeWithdrawal({
      withdrawalId: row.id,
      userId,
      pointsCost: Number(row.points_spent || 0),
      payout,
      cfg,
      channel: row.channel,
    })
  }
}
