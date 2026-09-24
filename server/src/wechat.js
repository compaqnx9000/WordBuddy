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
  const sceneId = String(process.env.WECHAT_TRANSFER_SCENE_ID || '1000').trim() || '1000'
  const merchantReady = Boolean(mchId && appId && serialNo && privateKey)
  return {
    mchId,
    appId,
    serialNo,
    privateKey,
    apiV3Key,
    notifyUrl,
    sceneId,
    merchantReady,
    transferReady: merchantReady,
    payReady: Boolean(merchantReady && notifyUrl),
    notifyReady: Boolean(merchantReady && apiV3Key),
  }
}

export function wechatOutBillNo(withdrawalId) {
  return `wd${withdrawalId}`
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

async function callWechat(method, urlPath, bodyObj) {
  const cfg = wechatConfig()
  if (!cfg.merchantReady) {
    return { ok: false, error: '微信商户号/API证书尚未配置完整（需 MCH_ID、证书序列号、apiclient_key）' }
  }
  const body = bodyObj == null ? '' : JSON.stringify(bodyObj)
  const response = await fetch(`${HOST}${urlPath}`, {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: authorization(cfg, method, urlPath, body),
    },
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
      { info_type: '报酬说明', info_content: '积分提现' },
    ]
  }
  return [
    { info_type: '活动名称', info_content: '词搭子积分提现' },
    { info_type: '奖励说明', info_content: '积分兑换现金' },
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
        remark: '请打开微信确认收款',
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

export async function transferToWechat({
  outBillNo,
  amountFen,
  openid,
  remark = '积分提现',
} = {}) {
  const cfg = wechatConfig()
  if (!cfg.transferReady) {
    return { ok: false, error: '微信商家转账未配置商户号/证书' }
  }
  const id = String(openid || '').trim()
  if (!isWechatOpenId(id)) {
    return { ok: false, error: '请先在个人资料中绑定微信账号' }
  }
  const amount = Math.max(1, Number(amountFen) || 0)
  const body = {
    appid: cfg.appId,
    out_bill_no: String(outBillNo).slice(0, 32),
    transfer_scene_id: cfg.sceneId,
    openid: id,
    transfer_amount: amount,
    transfer_remark: String(remark).slice(0, 32),
    user_recv_perception: cfg.sceneId === '1005' ? '劳务报酬' : '现金奖励',
    transfer_scene_report_infos: sceneReportInfos(cfg.sceneId),
  }
  const post = await callWechat('POST', '/v3/fund-app/mch-transfer/transfer-bills', body)
  if (!post.ok) return post
  if (post.httpStatus >= 500 || post.data?.code === 'SYSTEM_ERROR') {
    const queried = await queryWechatTransfer(outBillNo)
    if (queried.ok || queried.missing === false) return queried
    return { ok: true, pending: true, remark: '微信结果确认中，请稍后在记录中查看' }
  }
  return interpretWechatTransfer(post.data || {}, post.httpStatus)
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
  const message = `${cfg.appId}\n${timeStamp}\n${nonceStr}\nprepay_id=${id}\n`
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
    description: String(description || '词搭子积分').slice(0, 127),
    out_trade_no: tradeNo,
    notify_url: String(notifyUrl || cfg.notifyUrl).slice(0, 255),
    amount: { total, currency: 'CNY' },
  }
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
 * Decrypt WeChat Pay notify body (resource.ciphertext).
 * Returns { ok, outTradeNo, transactionId, tradeState, amountFen }.
 */
export function parseWechatPayNotify(body) {
  const cfg = wechatConfig()
  if (!cfg.apiV3Key) {
    return { ok: false, error: '缺少 WECHAT_API_V3_KEY，无法解密支付回调' }
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
    console.error('[wechat-pay] decrypt', error?.message || error)
    return { ok: false, error: '支付回调解密失败' }
  }
  let data
  try {
    data = JSON.parse(plain)
  } catch {
    return { ok: false, error: '支付回调明文无法解析' }
  }
  const tradeState = String(data.trade_state || '').toUpperCase()
  return {
    ok: true,
    eventType: String(body?.event_type || ''),
    outTradeNo: String(data.out_trade_no || '').trim(),
    transactionId: String(data.transaction_id || '').trim() || null,
    tradeState,
    amountFen: Number(data.amount?.total || 0) || 0,
    raw: data,
  }
}
