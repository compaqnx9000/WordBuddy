import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const certsDir = path.join(serverRoot, 'certs')
const HOST = 'https://api.mch.weixin.qq.com'

function readFileIfExists(relOrAbs) {
  const configured = String(relOrAbs || '').trim()
  if (!configured) return ''
  const abs = path.isAbsolute(configured) ? configured : path.resolve(serverRoot, configured)
  try {
    if (fs.existsSync(abs)) return fs.readFileSync(abs, 'utf8')
  } catch {
    /* ignore */
  }
  return ''
}

function findWechatKeyFile() {
  for (const name of ['wechat_apiclient_key.pem', 'apiclient_key.pem']) {
    const abs = path.join(certsDir, name)
    if (fs.existsSync(abs)) return abs
  }
  return ''
}

function wrapPrivateKey(raw) {
  let key = String(raw || '').trim().replace(/\\n/g, '\n')
  if (!key) return ''
  if (key.includes('BEGIN')) return key
  const body = key.replace(/\s+/g, '')
  if (!body) return ''
  const chunks = body.match(/.{1,64}/g)
  if (!chunks) return ''
  const type = body.startsWith('MIIEv') ? 'PRIVATE KEY' : 'RSA PRIVATE KEY'
  return `-----BEGIN ${type}-----\n${chunks.join('\n')}\n-----END ${type}-----`
}

export function isWechatOpenId(value) {
  return /^o[A-Za-z0-9_-]{16,63}$/.test(String(value || '').trim())
}

export function wechatConfig() {
  const mchId = String(process.env.WECHAT_MCH_ID || '').trim()
  const appId = String(process.env.WECHAT_APP_ID || '').trim()
  const serialNo = String(process.env.WECHAT_MCH_SERIAL_NO || '').trim()
  const privateKey = wrapPrivateKey(
    String(process.env.WECHAT_MCH_PRIVATE_KEY || '').trim() ||
      readFileIfExists(process.env.WECHAT_MCH_KEY_PATH || findWechatKeyFile()),
  )
  const apiV3Key = String(process.env.WECHAT_API_V3_KEY || '').trim()
  const notifyUrl = String(process.env.WECHAT_PAY_NOTIFY_URL || '').trim()
  const authNotifyUrl =
    String(process.env.WECHAT_AUTH_NOTIFY_URL || '').trim() ||
    (notifyUrl ? notifyUrl.replace(/\/wechat\/notify\/?$/i, '/wechat/auth-notify') : '')
  const sceneId = String(process.env.WECHAT_TRANSFER_SCENE_ID || '1000').trim() || '1000'
  const wechatpaySerial = String(process.env.WECHAT_PAY_SERIAL || process.env.WECHAT_PUB_KEY_ID || '').trim()
  const merchantReady = Boolean(mchId && appId && serialNo && privateKey)
  return {
    mchId,
    appId,
    serialNo,
    privateKey,
    apiV3Key,
    notifyUrl,
    authNotifyUrl,
    wechatpaySerial,
    sceneId,
    merchantReady,
    transferReady: merchantReady,
    payReady: Boolean(merchantReady && notifyUrl),
    notifyReady: Boolean(merchantReady && apiV3Key),
  }
}

export function wechatOutBillNo(withdrawalId) {
  // 微信商户单号只能是数字和字母，且至少 5 位。wd24 这种短号会被拒。
  const digits = String(withdrawalId ?? '').replace(/\D/g, '') || '0'
  return `WD${digits.padStart(8, '0')}`.slice(0, 32)
}

/** Stable merchant-side免确认授权单号 per user (digits/letters only, ≤32). */
export function wechatOutAuthorizationNo(userId, generation = 1) {
  const digits = String(userId ?? '').replace(/\D/g, '') || '0'
  const gen = Math.max(1, Number(generation) || 1)
  const base = `WXA${digits.padStart(8, '0')}`
  if (gen <= 1) return base.slice(0, 32)
  return `${base}G${gen}`.slice(0, 32)
}

function signV3(privateKey, method, urlPath, timestamp, nonce, body) {
  const message = `${method}\n${urlPath}\n${timestamp}\n${nonce}\n${body}\n`
  const candidates = [privateKey, wrapPrivateKey(privateKey)]
  let lastError = null
  for (const key of candidates) {
    if (!key) continue
    try {
      const signer = crypto.createSign('RSA-SHA256')
      signer.update(message, 'utf8')
      signer.end()
      return signer.sign(key, 'base64')
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('微信商户私钥无效')
}

function authorization(cfg, method, urlPath, body) {
  const timestamp = String(Math.floor(Date.now() / 1000))
  const nonce = crypto.randomBytes(16).toString('hex')
  const signature = signV3(cfg.privateKey, method, urlPath, timestamp, nonce, body)
  return `WECHATPAY2-SHA256-RSA2048 mchid="${cfg.mchId}",nonce_str="${nonce}",signature="${signature}",timestamp="${timestamp}",serial_no="${cfg.serialNo}"`
}

async function callWechat(method, urlPath, bodyObj, { needWechatpaySerial = false } = {}) {
  const cfg = wechatConfig()
  if (!cfg.merchantReady) {
    return { ok: false, error: '微信商户号/API证书尚未配置完整（需 MCH_ID、证书序列号、apiclient_key）' }
  }
  const body = bodyObj == null ? '' : JSON.stringify(bodyObj)
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Authorization: authorization(cfg, method, urlPath, body),
  }
  if (needWechatpaySerial && cfg.wechatpaySerial) {
    headers['Wechatpay-Serial'] = cfg.wechatpaySerial
  }
  const response = await fetch(`${HOST}${urlPath}`, {
    method,
    headers,
    body: method === 'GET' ? undefined : body,
    signal: AbortSignal.timeout(25_000),
  })
  const text = await response.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : {}
  } catch {
    return { ok: false, error: '微信返回无法解析', raw: text.slice(0, 300) }
  }
  return { ok: true, httpStatus: response.status, data: json }
}

function sceneReportInfos(sceneId) {
  if (sceneId === '1005') {
    return [
      { info_type: '岗位类型', info_content: '平台用户' },
      { info_type: '报酬说明', info_content: '搭币提现' },
    ]
  }
  return [
    { info_type: '活动名称', info_content: '词搭子搭币提现' },
    { info_type: '奖励说明', info_content: '搭币兑换现金' },
  ]
}

export function interpretWechatTransfer(data = {}, httpStatus = 200) {
  const state = String(data.state || '').toUpperCase()
  const tradeNo = data.transfer_bill_no || data.out_bill_no || null
  if (httpStatus >= 200 && httpStatus < 300) {
    if (state === 'SUCCESS') {
      return { ok: true, pending: false, tradeNo, remark: '微信已到账' }
    }
    if (state === 'WAIT_USER_CONFIRM') {
      return {
        ok: true,
        pending: true,
        tradeNo,
        remark: '请在微信中确认收款',
        packageInfo: data.package_info || null,
      }
    }
    if (['ACCEPTED', 'PROCESSING', 'TRANSFERING'].includes(state)) {
      return { ok: true, pending: true, tradeNo, remark: '微信处理中' }
    }
    if (state === 'FAIL' || state === 'CANCELLED') {
      return {
        ok: false,
        error: data.fail_reason || data.message || '微信打款失败',
        tradeNo,
      }
    }
    if (state === 'CANCELING') {
      return { ok: true, pending: true, tradeNo, remark: '微信撤销中' }
    }
  }
  const code = String(data.code || '')
  const message = data.message || data.fail_reason || ''
  if (code === 'SYSTEM_ERROR' || httpStatus >= 500) {
    return { ok: true, pending: true, remark: '微信结果确认中，请稍后在记录中查看' }
  }
  if (code === 'NOT_FOUND' || /不存在/.test(message)) {
    return { ok: false, missing: true, error: message || '转账单不存在' }
  }
  return { ok: false, error: message || '微信打款失败', subCode: code || null }
}

export async function queryWechatTransfer(outBillNo) {
  const pathUrl = `/v3/fund-app/mch-transfer/transfer-bills/out-bill-no/${encodeURIComponent(outBillNo)}`
  const result = await callWechat('GET', pathUrl, null)
  if (!result.ok) return result
  return { ...interpretWechatTransfer(result.data || {}, result.httpStatus), raw: result.data }
}

export function interpretWechatAuthorization(data = {}, httpStatus = 200) {
  if (httpStatus === 404 || data.code === 'NOT_FOUND') {
    return { ok: false, missing: true, error: data.message || '授权记录不存在' }
  }
  if (httpStatus < 200 || httpStatus >= 300) {
    return { ok: false, error: data.message || data.code || '查询授权失败', subCode: data.code || null }
  }
  const state = String(data.state || '').toUpperCase()
  return {
    ok: true,
    state,
    takingEffect: state === 'TAKING_EFFECT',
    waitConfirm: state === 'WAIT_USER_CONFIRM',
    closed: state === 'CLOSED',
    authorizationId: data.authorization_id || null,
    outAuthorizationNo: data.out_authorization_no || null,
    openid: data.openid || null,
    raw: data,
  }
}

export async function queryWechatAuthorization(outAuthorizationNo) {
  const no = String(outAuthorizationNo || '').trim()
  if (!no) return { ok: false, missing: true, error: '缺少授权单号' }
  const pathUrl = `/v3/fund-app/mch-transfer/user-confirm-authorization/out-authorization-no/${encodeURIComponent(no)}`
  const result = await callWechat('GET', pathUrl, null)
  if (!result.ok) return result
  return interpretWechatAuthorization(result.data || {}, result.httpStatus)
}

function httpsNotify(url) {
  const u = String(url || '').trim()
  return /^https:\/\//i.test(u) ? u.slice(0, 255) : ''
}

function transferBodyBase({ outBillNo, amountFen, remark }) {
  const cfg = wechatConfig()
  const notify = httpsNotify(cfg.notifyUrl)
  return {
    appid: cfg.appId,
    out_bill_no: String(outBillNo).slice(0, 32),
    transfer_scene_id: cfg.sceneId,
    transfer_amount: Math.max(1, Number(amountFen) || 0),
    transfer_remark: String(remark || '搭币提现').slice(0, 32),
    user_recv_perception: cfg.sceneId === '1005' ? '劳务报酬' : '现金奖励',
    transfer_scene_report_infos: sceneReportInfos(cfg.sceneId),
    ...(notify ? { notify_url: notify } : {}),
  }
}

async function finishTransferPost(outBillNo, post) {
  if (!post.ok) return post
  if (post.httpStatus >= 500 || post.data?.code === 'SYSTEM_ERROR') {
    const queried = await queryWechatTransfer(outBillNo)
    if (queried.ok || queried.missing === false) return queried
    return { ok: true, pending: true, remark: '微信结果确认中，请稍后在记录中查看' }
  }
  if (post.httpStatus >= 400) {
    return {
      ok: false,
      error: post.data?.message || post.data?.code || '微信打款失败',
      subCode: post.data?.code || null,
      authInvalid: /授权|AUTHORIZ/i.test(String(post.data?.message || post.data?.code || '')),
      raw: post.data,
    }
  }
  return interpretWechatTransfer(post.data || {}, post.httpStatus)
}

/**
 * 用户已免确认授权后的直达零钱转账（无需拉起确认页）。
 * POST /v3/fund-app/mch-transfer/transfer-bills/transfer
 */
export async function transferToWechatAuthorized({
  outBillNo,
  amountFen,
  remark = '搭币提现',
  authorizationId,
  outAuthorizationNo,
} = {}) {
  const cfg = wechatConfig()
  if (!cfg.transferReady) {
    return { ok: false, error: '微信商家转账未配置商户号/证书' }
  }
  const authId = String(authorizationId || '').trim()
  const outAuth = String(outAuthorizationNo || '').trim()
  if (!authId && !outAuth) {
    return { ok: false, error: '缺少免确认收款授权' }
  }
  const body = {
    ...transferBodyBase({ outBillNo, amountFen, remark }),
    ...(authId ? { authorization_id: authId } : { out_authorization_no: outAuth }),
  }
  const post = await callWechat('POST', '/v3/fund-app/mch-transfer/transfer-bills/transfer', body, {
    needWechatpaySerial: true,
  })
  return finishTransferPost(outBillNo, post)
}

/**
 * 首次：发起转账并引导确认收款 + 免确认授权。
 * POST /v3/fund-app/mch-transfer/transfer-bills/pre-transfer-with-authorization
 * 无授权信息时回退旧的 transfer-bills（仅确认收款）。
 */
export async function transferToWechat({
  outBillNo,
  amountFen,
  openid,
  remark = '搭币提现',
  outAuthorizationNo,
  userDisplayName = '词搭子用户',
  authorizationId,
} = {}) {
  const cfg = wechatConfig()
  if (!cfg.transferReady) {
    return { ok: false, error: '微信商家转账未配置商户号/证书' }
  }
  const id = String(openid || '').trim()
  if (!isWechatOpenId(id)) {
    return { ok: false, error: '请先在个人资料中绑定微信账号' }
  }

  const authId = String(authorizationId || '').trim()
  if (authId) {
    const authorized = await transferToWechatAuthorized({
      outBillNo,
      amountFen,
      remark,
      authorizationId: authId,
      outAuthorizationNo,
    })
    if (authorized.ok || !authorized.authInvalid) return authorized
  }

  const amount = Math.max(1, Number(amountFen) || 0)
  const outAuth = String(outAuthorizationNo || '').trim()
  const authNotify = httpsNotify(cfg.authNotifyUrl)
  const canRequestAuth = Boolean(outAuth && authNotify)
  const base = {
    ...transferBodyBase({ outBillNo, amountFen: amount, remark }),
    openid: id,
  }

  if (canRequestAuth) {
    const body = {
      ...base,
      authorization_info: {
        user_display_name: String(userDisplayName || '词搭子用户').slice(0, 32),
        out_authorization_no: outAuth.slice(0, 32),
        authorization_notify_url: authNotify,
      },
    }
    const post = await callWechat(
      'POST',
      '/v3/fund-app/mch-transfer/transfer-bills/pre-transfer-with-authorization',
      body,
      { needWechatpaySerial: true },
    )
    const finished = await finishTransferPost(outBillNo, post)
    if (finished.ok || finished.pending) return { ...finished, requestedAuth: true }
    // 若免确认预下单失败，回退到普通确认收款，避免提现不可用
    console.error('[wechat-transfer] pre-transfer-with-auth failed', post.httpStatus, post.data)
  }

  const post = await callWechat('POST', '/v3/fund-app/mch-transfer/transfer-bills', base)
  return finishTransferPost(outBillNo, post)
}

function signAppPayMessage(privateKey, message) {
  const candidates = [privateKey, wrapPrivateKey(privateKey)]
  let lastError = null
  for (const key of candidates) {
    if (!key) continue
    try {
      const signer = crypto.createSign('RSA-SHA256')
      signer.update(message, 'utf8')
      signer.end()
      return signer.sign(key, 'base64')
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('微信商户私钥无效')
}

/** WeChat time_expire: yyyy-MM-DDTHH:mm:ss+08:00 */
export function wechatExpireAt(epochMs) {
  const d = new Date(Number(epochMs) || Date.now())
  const pad = (n) => String(n).padStart(2, '0')
  // Asia/Shanghai offset fixed +08:00 for merchant APIs
  const utc = d.getTime() + 8 * 60 * 60 * 1000
  const t = new Date(utc)
  return `${t.getUTCFullYear()}-${pad(t.getUTCMonth() + 1)}-${pad(t.getUTCDate())}T${pad(t.getUTCHours())}:${pad(t.getUTCMinutes())}:${pad(t.getUTCSeconds())}+08:00`
}

export async function queryWechatPayByOutTradeNo(outTradeNo) {
  const cfg = wechatConfig()
  const tradeNo = String(outTradeNo || '').trim()
  if (!tradeNo) return { ok: false, error: '缺少商户订单号' }
  const pathUrl = `/v3/pay/transactions/out-trade-no/${encodeURIComponent(tradeNo)}?mchid=${encodeURIComponent(cfg.mchId)}`
  const result = await callWechat('GET', pathUrl, null)
  if (!result.ok) return result
  if (result.httpStatus === 404 || result.data?.code === 'ORDER_NOT_EXIST') {
    return { ok: false, missing: true, error: result.data?.message || '订单不存在' }
  }
  if (result.httpStatus < 200 || result.httpStatus >= 300) {
    return { ok: false, error: result.data?.message || result.data?.code || '查单失败', raw: result.data }
  }
  const data = result.data || {}
  return {
    ok: true,
    tradeState: String(data.trade_state || '').toUpperCase(),
    transactionId: data.transaction_id || null,
    outTradeNo: data.out_trade_no || tradeNo,
    amountFen: Number(data.amount?.total || 0) || 0,
    raw: data,
  }
}

export async function closeWechatPayByOutTradeNo(outTradeNo) {
  const cfg = wechatConfig()
  const tradeNo = String(outTradeNo || '').trim()
  if (!tradeNo) return { ok: false, error: '缺少商户订单号' }
  const pathUrl = `/v3/pay/transactions/out-trade-no/${encodeURIComponent(tradeNo)}/close`
  const post = await callWechat('POST', pathUrl, { mchid: cfg.mchId })
  if (!post.ok) return post
  // 204 No Content = closed; ORDERPAID means already paid
  if (post.httpStatus === 204 || (post.httpStatus >= 200 && post.httpStatus < 300)) {
    return { ok: true, closed: true }
  }
  const code = String(post.data?.code || '')
  if (code === 'ORDERPAID') {
    return { ok: true, paid: true }
  }
  if (code === 'ORDERCLOSED') {
    return { ok: true, closed: true }
  }
  return { ok: false, error: post.data?.message || code || '关单失败', raw: post.data }
}

/** Build Android PayReq fields from prepay_id (RSA2 sign). */
export function buildWechatAppPayParams(prepayId) {
  const cfg = wechatConfig()
  if (!cfg.merchantReady) {
    return { ok: false, error: '微信商户号/API证书尚未配置完整' }
  }
  const id = String(prepayId || '').trim()
  if (!id) return { ok: false, error: '缺少微信预支付单号' }
  const timeStamp = String(Math.floor(Date.now() / 1000))
  const nonceStr = crypto.randomBytes(16).toString('hex')
  const packageValue = 'Sign=WXPay'
  // APP 调起支付的第四行是 prepay_id 本身，不能写成 prepay_id=xxx。
  const message = `${cfg.appId}\n${timeStamp}\n${nonceStr}\n${id}\n`
  const sign = signAppPayMessage(cfg.privateKey, message)
  return {
    ok: true,
    pay: {
      appId: cfg.appId,
      partnerId: cfg.mchId,
      prepayId: id,
      packageValue,
      nonceStr,
      timeStamp,
      sign,
    },
  }
}

/**
 * APP 支付下单：POST /v3/pay/transactions/app
 * @see https://pay.weixin.qq.com/doc/v3/merchant/4012791857
 */
export async function createWechatAppPrepay({
  outTradeNo,
  description,
  amountFen,
  attach = '',
  notifyUrl = '',
  timeExpire = '',
} = {}) {
  const cfg = wechatConfig()
  if (!cfg.payReady) {
    return {
      ok: false,
      error: cfg.merchantReady
        ? '缺少 WECHAT_PAY_NOTIFY_URL'
        : '微信商户号/API证书尚未配置完整（需 MCH_ID、证书序列号、apiclient_key）',
    }
  }
  const tradeNo = String(outTradeNo || '')
    .replace(/[^A-Za-z0-9_\-|*=]/g, '')
    .slice(0, 32)
  if (tradeNo.length < 6) return { ok: false, error: '商户订单号无效' }
  const total = Math.max(1, Number(amountFen) || 0)
  const body = {
    appid: cfg.appId,
    mchid: cfg.mchId,
    description: String(description || '词搭子搭币').slice(0, 127),
    out_trade_no: tradeNo,
    notify_url: String(notifyUrl || cfg.notifyUrl).slice(0, 255),
    amount: { total, currency: 'CNY' },
  }
  const expire = String(timeExpire || '').trim()
  if (expire) body.time_expire = expire
  const attachText = String(attach || '').trim().slice(0, 128)
  if (attachText) body.attach = attachText

  const post = await callWechat('POST', '/v3/pay/transactions/app', body)
  if (!post.ok) return post
  if (post.httpStatus < 200 || post.httpStatus >= 300) {
    const message = post.data?.message || post.data?.code || '微信下单失败'
    console.error('[wechat-pay] prepay', post.httpStatus, post.data)
    return { ok: false, error: message, raw: post.data }
  }
  const prepayId = String(post.data?.prepay_id || '').trim()
  if (!prepayId) {
    return { ok: false, error: '微信未返回 prepay_id', raw: post.data }
  }
  const built = buildWechatAppPayParams(prepayId)
  if (!built.ok) return built
  return { ok: true, prepayId, pay: built.pay, outTradeNo: tradeNo }
}

function decryptAesGcm(apiV3Key, associatedData, nonce, ciphertext) {
  const key = Buffer.from(String(apiV3Key || ''), 'utf8')
  if (key.length !== 32) {
    throw new Error('WECHAT_API_V3_KEY 必须是 32 字节')
  }
  const buf = Buffer.from(String(ciphertext || ''), 'base64')
  if (buf.length <= 16) throw new Error('回调密文无效')
  const authTag = buf.subarray(buf.length - 16)
  const data = buf.subarray(0, buf.length - 16)
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(String(nonce || ''), 'utf8'))
  decipher.setAuthTag(authTag)
  if (associatedData) decipher.setAAD(Buffer.from(String(associatedData), 'utf8'))
  return Buffer.concat([decipher.update(data), decipher.final()]).toString('utf8')
}

/**
 * Decrypt WeChat notify body (resource.ciphertext).
 */
export function decryptWechatNotify(body) {
  const cfg = wechatConfig()
  if (!cfg.apiV3Key) {
    return { ok: false, error: '缺少 WECHAT_API_V3_KEY，无法解密回调' }
  }
  const resource = body?.resource
  if (!resource?.ciphertext) {
    return { ok: false, error: '回调缺少加密资源' }
  }
  let plain
  try {
    plain = decryptAesGcm(
      cfg.apiV3Key,
      resource.associated_data || '',
      resource.nonce || '',
      resource.ciphertext,
    )
  } catch (error) {
    console.error('[wechat] decrypt', error?.message || error)
    return { ok: false, error: '回调解密失败' }
  }
  let data
  try {
    data = JSON.parse(plain)
  } catch {
    return { ok: false, error: '回调明文无法解析' }
  }
  return {
    ok: true,
    eventType: String(body?.event_type || ''),
    data,
  }
}

/**
 * Decrypt WeChat Pay notify body (resource.ciphertext).
 * Returns { ok, outTradeNo, transactionId, tradeState, amountFen }.
 */
export function parseWechatPayNotify(body) {
  const decrypted = decryptWechatNotify(body)
  if (!decrypted.ok) return decrypted
  const data = decrypted.data || {}
  const tradeState = String(data.trade_state || '').toUpperCase()
  return {
    ok: true,
    eventType: decrypted.eventType,
    outTradeNo: String(data.out_trade_no || '').trim(),
    transactionId: String(data.transaction_id || '').trim() || null,
    tradeState,
    amountFen: Number(data.amount?.total || 0) || 0,
    raw: data,
  }
}

/** Parse 免确认收款授权结果通知. */
export function parseWechatAuthNotify(body) {
  const decrypted = decryptWechatNotify(body)
  if (!decrypted.ok) return decrypted
  const data = decrypted.data || {}
  return {
    ok: true,
    eventType: decrypted.eventType,
    outAuthorizationNo: String(data.out_authorization_no || '').trim(),
    authorizationId: String(data.authorization_id || '').trim() || null,
    openid: String(data.openid || '').trim() || null,
    state: String(data.state || '').toUpperCase(),
    raw: data,
  }
}
