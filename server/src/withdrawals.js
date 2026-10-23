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
  queryWechatAuthorization,
  queryWechatTransfer,
  transferToWechat,
  wechatConfig,
  wechatOutAuthorizationNo,
  wechatOutBillNo,
} from './wechat.js'

/** Live Alipay TRANS_ACCOUNT_NO_PWD minimum is 0.10 yuan. */
const LIVE_MIN_FEN = 10

/** Alipay cash choices. Points are fixed, not derived from the old 1-point rate. */
const ALIPAY_TIERS = [
  { amountFen: 20, amountYuan: '0.2', label: '0.2元', pointsCost: 2000 },
  { amountFen: 50, amountYuan: '0.5', label: '0.5元', pointsCost: 5000 },
  { amountFen: 100, amountYuan: '1.0', label: '1.0元', pointsCost: 10000 },
]

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
  const tiers = ALIPAY_TIERS.map((tier) => ({ ...tier }))
  const amountText = '支付宝可选 0.2元、0.5元、1.0元，每天只能提现一次。看完激励广告后才能提现。'
  let note
  if (sandbox) {
    note = '当前为沙箱模式：仅模拟打款成功，不会真实转账。支付宝每天只能提现一次。'
  } else {
    const parts = [amountText]
    parts.push(
      wechatReady
        ? '微信请先在个人资料中绑定微信。首次提现需在微信确认收款并授权；授权后再次提现将直接到账零钱。'
        : '微信商家转账尚未配置商户 API 证书，暂时无法打到微信。',
    )
    parts.push(
      alipayReady
        ? '支付宝请先在个人资料中绑定支付宝。每天只能提现一次。'
        : '支付宝应用还在审核/未上线，暂可能无法打款。每天只能提现一次。',
    )
    note = parts.join('')
  }
  return {
    sandbox,
    amountFen,
    amountYuan: (amountFen / 100).toFixed(2),
    pointsCost,
    tiers,
    alipayReady,
    wechatReady,
    certMode: Boolean(pay.certMode),
    channels: [
      {
        id: 'wechat',
        name: '微信提现',
        accountLabel: '微信账号',
        accountHint: '请在个人资料中调起微信绑定',
        ready: sandbox || wechatReady,
      },
      {
        id: 'alipay',
        name: '支付宝提现',
        accountLabel: '支付宝账号',
        accountHint: '请在个人资料中调起支付宝绑定',
        ready: sandbox || alipayReady,
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
    wechatConfirm: wechatConfirmPayload(row),
  }
}

function wechatConfirmPayload(row) {
  if (!row || row.channel !== 'wechat' || row.status !== 'pending') return null
  const packageInfo = String(row.wechat_package || '').trim()
  if (!packageInfo) return null
  const cfg = wechatConfig()
  if (!cfg.mchId || !cfg.appId) return null
  return { mchId: cfg.mchId, appId: cfg.appId, packageInfo }
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
  try {
    await syncWechatAuthorizationForUser(userId)
  } catch (error) {
    console.error('[withdrawals/sync-auth]', error)
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
export async function createWithdrawal(userId, { channel, account, realName, amountFen: requestedFen } = {}) {
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
      `SELECT alipay_account, alipay_name, wechat_account, wechat_openid, nickname,
              wechat_out_authorization_no, wechat_authorization_id, wechat_auth_generation
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  const saved =
    ch === 'wechat'
      ? String(profile?.wechat_openid || profile?.wechat_account || '').trim()
      : String(profile?.alipay_account || '').trim()
  let acct = String(account || '').trim() || saved
  if (!acct || acct.length < 3 || acct.length > 128) {
    return {
      ok: false,
      error: ch === 'wechat' ? '请先在个人资料中绑定微信账号' : '请先绑定支付宝收款账号',
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
    return { ok: false, error: '请先在个人资料中绑定微信账号' }
  }

  let amountFen = cfg.amountFen
  let pointsCost = cfg.pointsCost
  if ((ch === 'alipay' || ch === 'wechat') && requestedFen != null && requestedFen !== '') {
    const tier = cfg.tiers.find((item) => item.amountFen === Number(requestedFen))
    if (!tier) {
      return { ok: false, error: '请选择 0.2元、0.5元或 1.0元' }
    }
    amountFen = tier.amountFen
    pointsCost = tier.pointsCost
  }
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
      return { ok: false, error: '搭币不足' }
    }

    const todayCount = (
      await client.query(
        `SELECT count(*)::int AS n FROM withdrawals
         WHERE user_id = $1
           AND status IN ('pending', 'success')
           AND (created_at AT TIME ZONE 'Asia/Shanghai')::date
             = (now() AT TIME ZONE 'Asia/Shanghai')::date`,
        [userId],
      )
    ).rows[0].n
    if (todayCount >= 1) {
      await client.query('ROLLBACK')
      return { ok: false, error: '今天已经提现过一次，请明天再试' }
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
      userId,
      profile,
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
           wechat_package = CASE
             WHEN $1 = 'pending' THEN COALESCE($6, wechat_package)
             ELSE NULL
           END,
           updated_at = now()
         WHERE id = $5
         RETURNING *`,
        [
          status,
          payout.tradeNo || null,
          status === 'failed' ? payout.error || '打款失败' : null,
          payout.remark || null,
          withdrawalId,
          payout.packageInfo || null,
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
        error: payout.error || '打款失败，搭币已退回',
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
        ? payout.packageInfo
          ? `提现已提交：¥${cfg.amountYuan}，请在微信中确认收款`
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

async function ensureWechatAuthNos(userId, profile = {}) {
  let outNo = String(profile.wechat_out_authorization_no || '').trim()
  let authId = String(profile.wechat_authorization_id || '').trim()
  let generation = Math.max(1, Number(profile.wechat_auth_generation) || 1)
  if (!outNo) {
    outNo = wechatOutAuthorizationNo(userId, generation)
    await query(
      `UPDATE users
         SET wechat_out_authorization_no = $2, wechat_auth_generation = $3
       WHERE id = $1`,
      [userId, outNo, generation],
    )
  }
  if (authId) {
    const checked = await queryWechatAuthorization(outNo)
    if (checked.takingEffect && checked.authorizationId) {
      if (checked.authorizationId !== authId) {
        await query(`UPDATE users SET wechat_authorization_id = $2 WHERE id = $1`, [
          userId,
          checked.authorizationId,
        ])
        authId = checked.authorizationId
      }
      return { outAuthorizationNo: outNo, authorizationId: authId }
    }
    if (checked.closed || checked.missing) {
      generation += 1
      outNo = wechatOutAuthorizationNo(userId, generation)
      authId = ''
      await query(
        `UPDATE users
           SET wechat_out_authorization_no = $2,
               wechat_authorization_id = NULL,
               wechat_auth_generation = $3
         WHERE id = $1`,
        [userId, outNo, generation],
      )
    }
  }
  return { outAuthorizationNo: outNo, authorizationId: authId || null }
}

export async function syncWechatAuthorizationForUser(userId) {
  const row = (
    await query(
      `SELECT wechat_out_authorization_no, wechat_authorization_id, wechat_auth_generation
       FROM users WHERE id = $1`,
      [userId],
    )
  ).rows[0]
  const outNo = String(row?.wechat_out_authorization_no || '').trim()
  if (!outNo) return null
  const checked = await queryWechatAuthorization(outNo)
  if (checked.takingEffect && checked.authorizationId) {
    await query(`UPDATE users SET wechat_authorization_id = $2 WHERE id = $1`, [
      userId,
      checked.authorizationId,
    ])
    return checked
  }
  if (checked.closed) {
    await query(`UPDATE users SET wechat_authorization_id = NULL WHERE id = $1`, [userId])
  }
  return checked
}

export async function applyWechatAuthorizationNotify({
  outAuthorizationNo,
  authorizationId,
  openid,
  state,
}) {
  const outNo = String(outAuthorizationNo || '').trim()
  if (!outNo) return { ok: false, error: '缺少授权单号' }
  const st = String(state || '').toUpperCase()
  if (st === 'TAKING_EFFECT' && authorizationId) {
    const updated = await query(
      `UPDATE users
         SET wechat_authorization_id = $2,
             wechat_out_authorization_no = COALESCE(wechat_out_authorization_no, $1)
       WHERE wechat_out_authorization_no = $1
          OR ($3::text IS NOT NULL AND wechat_openid = $3)
       RETURNING id`,
      [outNo, String(authorizationId).trim(), openid || null],
    )
    return { ok: true, matched: updated.rowCount > 0 }
  }
  if (st === 'CLOSED') {
    await query(
      `UPDATE users
         SET wechat_authorization_id = NULL
       WHERE wechat_out_authorization_no = $1
          OR ($2::text IS NOT NULL AND wechat_openid = $2)`,
      [outNo, openid || null],
    )
    return { ok: true, closed: true }
  }
  return { ok: true, ignored: true }
}

async function performPayout({
  channel,
  account,
  realName,
  amountFen,
  withdrawalId,
  sandbox,
  userId,
  profile,
}) {
  if (sandbox) {
    return {
      ok: true,
      pending: false,
      tradeNo: `SANDBOX-${channel.toUpperCase()}-${withdrawalId}-${Date.now()}`,
      remark: `沙箱模拟打款 ${amountFen} 分 → ${account}`,
    }
  }
  if (channel === 'wechat') {
    const auth = await ensureWechatAuthNos(userId, profile || {})
    const display =
      String(profile?.nickname || '').trim() ||
      `用户${String(userId).slice(-6)}` ||
      '词搭子用户'
    return transferToWechat({
      outBillNo: wechatOutBillNo(withdrawalId),
      amountFen,
      openid: account,
      remark: `搭币提现#${withdrawalId}`,
      outAuthorizationNo: auth.outAuthorizationNo,
      authorizationId: auth.authorizationId,
      userDisplayName: display.slice(0, 32),
    })
  }
  return transferToAlipay({
    outBizNo: withdrawalOutBizNo(withdrawalId),
    amountYuan: (amountFen / 100).toFixed(2),
    loginId: account,
    realName,
    title: '词搭子搭币提现',
    remark: `搭币提现#${withdrawalId}`,
  })
}

export async function reconcilePendingWithdrawals(userId) {
  const pending = await query(
    `SELECT w.*, u.alipay_name, u.nickname, u.wechat_out_authorization_no,
            u.wechat_authorization_id, u.wechat_auth_generation
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
        const auth = await ensureWechatAuthNos(userId, row)
        const display =
          String(row.nickname || '').trim() || `用户${String(userId).slice(-6)}` || '词搭子用户'
        const wxArgs = {
          outBillNo: wechatOutBillNo(row.id),
          amountFen: Number(row.amount_fen || 0),
          openid: row.account,
          remark: `搭币提现#${row.id}`,
          outAuthorizationNo: auth.outAuthorizationNo,
          authorizationId: auth.authorizationId,
          userDisplayName: display.slice(0, 32),
        }
        const queried = await queryWechatTransfer(wechatOutBillNo(row.id))
        if (!row.wechat_package && queried.pending) {
          // 已有待确认单：不要走授权后转账，只重放预下单以取回 package_info
          const replay = await transferToWechat({ ...wxArgs, authorizationId: null })
          if (replay.packageInfo) {
            await query(
              `UPDATE withdrawals
                 SET wechat_package = $1, updated_at = now()
               WHERE id = $2 AND status = 'pending' AND wechat_package IS NULL`,
              [replay.packageInfo, row.id],
            )
            row.wechat_package = replay.packageInfo
          }
        }
        if (queried.ok || queried.missing === false) payout = queried
        else if (queried.missing) {
          payout = await transferToWechat(wxArgs)
        } else if (!queried.ok && !queried.pending) {
          payout = queried
        }
        if (payout && payout.ok && !payout.pending) {
          try {
            await syncWechatAuthorizationForUser(userId)
          } catch (error) {
            console.error('[withdrawals/sync-auth]', row.id, error)
          }
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
            title: '词搭子搭币提现',
            remark: `搭币提现#${row.id}`,
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
