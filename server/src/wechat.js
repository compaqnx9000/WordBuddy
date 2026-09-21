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
  const sceneId = String(process.env.WECHAT_TRANSFER_SCENE_ID || '1000').trim() || '1000'
  return {
    mchId,
    appId,
    serialNo,
    privateKey,
    sceneId,
    transferReady: Boolean(mchId && appId && serialNo && privateKey),
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
  if (!cfg.transferReady) {
    return { ok: false, error: '微信商家转账未配置商户号/证书' }
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
  return { httpStatus: response.status, data: json }
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
  if (!result.httpStatus) return result
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
    return { ok: false, error: '请填写微信 OpenID（以 o 开头，不是微信号）' }
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
  if (!post.httpStatus) return post
  if (post.httpStatus >= 500 || post.data?.code === 'SYSTEM_ERROR') {
    const queried = await queryWechatTransfer(outBillNo)
    if (queried.ok || queried.missing === false) return queried
    return { ok: true, pending: true, remark: '微信结果确认中，请稍后在记录中查看' }
  }
  return interpretWechatTransfer(post.data || {}, post.httpStatus)
}
