import crypto, { X509Certificate } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const serverRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const certsDir = path.join(serverRoot, 'certs')

function wrapPrivateKey(raw) {
  let key = String(raw || '').trim().replace(/\\n/g, '\n')
  if (!key) return ''
  if (key.includes('BEGIN')) return key
  const body = key.replace(/\s+/g, '')
  if (!body) return ''
  const chunks = body.match(/.{1,64}/g)
  if (!chunks) return ''
  // Alipay key tool often exports PKCS#8 (MIIEv...) without PEM headers.
  const type = body.startsWith('MIIEv') ? 'PRIVATE KEY' : 'RSA PRIVATE KEY'
  return `-----BEGIN ${type}-----\n${chunks.join('\n')}\n-----END ${type}-----`
}

function normalizePem(raw, type) {
  let key = String(raw || '').trim().replace(/\\n/g, '\n')
  if (!key) return ''
  if (key.includes('BEGIN')) return key
  const body = key.replace(/\s+/g, '')
  if (!body) return ''
  const chunks = body.match(/.{1,64}/g)
  if (!chunks) return ''
  return `-----BEGIN ${type}-----\n${chunks.join('\n')}\n-----END ${type}-----`
}

function readPem(envKey, pathKey, fallbackPath = '') {
  const inline = String(process.env[envKey] || '').trim().replace(/\\n/g, '\n')
  if (inline.includes('BEGIN CERTIFICATE') || inline.includes('BEGIN ')) return inline
  const configured = String(process.env[pathKey] || '').trim()
  const candidates = [configured, fallbackPath].filter(Boolean)
  for (const candidate of candidates) {
    const abs = path.isAbsolute(candidate) ? candidate : path.resolve(serverRoot, candidate)
    try {
      if (fs.existsSync(abs)) return fs.readFileSync(abs, 'utf8')
    } catch {
      /* ignore missing certs */
    }
  }
  return ''
}

function findCertFile(prefix) {
  try {
    const names = fs
      .readdirSync(certsDir)
      .filter((name) => name.startsWith(prefix) && (name.endsWith('.crt') || name.endsWith('.pem')))
    if (names.length) return path.join(certsDir, names.sort()[0])
  } catch {
    /* certs dir may not exist */
  }
  return ''
}

function findPrivateKeyFile() {
  try {
    const names = fs.readdirSync(certsDir).filter((name) => {
      const lower = name.toLowerCase()
      if (name.includes('应用私钥')) return true
      if (!/private|私钥/.test(lower)) return false
      return ['.txt', '.pem', '.key'].some((ext) => lower.endsWith(ext))
    })
    if (names.length) return path.join(certsDir, names.sort()[0])
  } catch {
    /* certs dir may not exist */
  }
  return ''
}

/** Alipay app_cert_sn / alipay_root_cert_sn (MD5 of issuer + decimal serial). */
function certSn(pem) {
  const cert = new X509Certificate(pem)
  const principal = String(cert.issuer || '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .reverse()
    .join(',')
  const serial = BigInt(`0x${cert.serialNumber}`).toString()
  return crypto.createHash('md5').update(principal + serial, 'utf8').digest('hex')
}

function rootCertSn(pem) {
  const blocks = String(pem).match(/-----BEGIN CERTIFICATE-----[\s\S]+?-----END CERTIFICATE-----/g) || []
  const sns = []
  for (const block of blocks) {
    try {
      const cert = new X509Certificate(block)
      // Official root SN only includes RSA-signed CAs (skip SM2/EC).
      if (cert.publicKey.asymmetricKeyType !== 'rsa') continue
      sns.push(certSn(block))
    } catch {
      /* skip unreadable chain entries */
    }
  }
  return sns.join('_')
}

function publicKeyFromCert(pem) {
  if (!pem) return ''
  try {
    return new X509Certificate(pem).publicKey.export({ type: 'spki', format: 'pem' })
  } catch {
    return ''
  }
}

export function alipayConfig() {
  const appId = String(process.env.ALIPAY_APP_ID || '').trim()
  const privateKey = wrapPrivateKey(
    String(process.env.ALIPAY_PRIVATE_KEY || '').trim() ||
      readPem('', 'ALIPAY_PRIVATE_KEY_PATH', findPrivateKeyFile()),
  )
  const appCertPem = readPem('ALIPAY_APP_CERT', 'ALIPAY_APP_CERT_PATH', findCertFile('appCertPublicKey'))
  const alipayCertPem = readPem(
    'ALIPAY_ALIPAY_CERT',
    'ALIPAY_PUBLIC_CERT_PATH',
    findCertFile('alipayCertPublicKey'),
  )
  const rootCertPem = readPem('ALIPAY_ROOT_CERT', 'ALIPAY_ROOT_CERT_PATH', findCertFile('alipayRootCert'))
  let appCertSn = ''
  let rootCertSnValue = ''
  try {
    if (appCertPem) appCertSn = certSn(appCertPem)
    if (rootCertPem) rootCertSnValue = rootCertSn(rootCertPem)
  } catch (error) {
    console.error('[alipay] cert sn', error)
  }
  const certMode = Boolean(appCertSn && rootCertSnValue && alipayCertPem)
  const envPublicKey = normalizePem(process.env.ALIPAY_PUBLIC_KEY || '', 'PUBLIC KEY')
  const alipayPublicKey = publicKeyFromCert(alipayCertPem) || envPublicKey
  const notifyUrl = String(process.env.ALIPAY_NOTIFY_URL || '').trim()
  const gateway =
    String(process.env.ALIPAY_GATEWAY || '').trim() ||
    'https://openapi.alipay.com/gateway.do'
  const keysReady = Boolean(appId && privateKey && alipayPublicKey)
  const configured = Boolean(keysReady && notifyUrl)
  const sandbox = String(process.env.ALIPAY_SANDBOX ?? (configured ? 'false' : 'true'))
    .toLowerCase() !== 'false'
  const pid =
    String(process.env.ALIPAY_PID || '').trim() || merchantPidFromCert(appCertPem)
  return {
    appId,
    privateKey,
    alipayPublicKey,
    notifyUrl,
    gateway,
    configured,
    certMode,
    appCertSn,
    rootCertSn: rootCertSnValue,
    /** Merchant transfer requires appId + private key; cert mode is strongly preferred. */
    transferReady: Boolean(appId && privateKey && (certMode || alipayPublicKey)),
    /** When true, App can simulate payment without opening Alipay. */
    sandbox: sandbox || !configured,
    pid,
  }
}

function merchantPidFromCert(pem) {
  if (!pem) return ''
  try {
    const subject = String(new X509Certificate(pem).subject || '')
    const match = subject.match(/CN\s*=\s*(2088\d{12,16})/)
    return match ? match[1] : ''
  } catch {
    return ''
  }
}

function sortedQuery(params) {
  return Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '' && k !== 'sign')
    .sort()
    .map((k) => `${k}=${params[k]}`)
    .join('&')
}

export function signParams(params, privateKey) {
  const content = sortedQuery(params)
  const candidates = [
    privateKey,
    normalizePem(privateKey, 'PRIVATE KEY'),
    normalizePem(privateKey, 'RSA PRIVATE KEY'),
  ]
  let lastError = null
  for (const key of candidates) {
    if (!key) continue
    try {
      const signer = crypto.createSign('RSA-SHA256')
      signer.update(content, 'utf8')
      signer.end()
      return signer.sign(key, 'base64')
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('支付宝私钥无效')
}

export function verifyNotify(params, alipayPublicKey) {
  const sign = String(params.sign || '')
  const key = alipayPublicKey || alipayConfig().alipayPublicKey
  if (!sign || !key) return false
  const content = sortedQuery(params)
  const verifier = crypto.createVerify('RSA-SHA256')
  verifier.update(content, 'utf8')
  verifier.end()
  try {
    return verifier.verify(key, sign, 'base64')
  } catch {
    return false
  }
}

function attachCertParams(params, cfg) {
  if (cfg.certMode) {
    params.app_cert_sn = cfg.appCertSn
    params.alipay_root_cert_sn = cfg.rootCertSn
  }
  return params
}

/**
 * Build orderInfo string for Alipay Android PayTask.payV2.
 */
export function buildAppPayOrderInfo({
  outTradeNo,
  subject,
  totalAmountYuan,
  body = '',
}) {
  const cfg = alipayConfig()
  if (!cfg.configured) {
    return { ok: false, error: '支付宝未配置应用私钥/公钥/回调地址' }
  }
  const bizContent = JSON.stringify({
    out_trade_no: outTradeNo,
    total_amount: totalAmountYuan,
    subject: String(subject || '词搭子积分').slice(0, 128),
    product_code: 'QUICK_MSECURITY_PAY',
    body: String(body || '').slice(0, 128),
  })
  const params = attachCertParams(
    {
      app_id: cfg.appId,
      method: 'alipay.trade.app.pay',
      charset: 'utf-8',
      sign_type: 'RSA2',
      timestamp: formatAlipayTimestamp(new Date()),
      version: '1.0',
      notify_url: cfg.notifyUrl,
      biz_content: bizContent,
    },
    cfg,
  )
  const sign = signParams(params, cfg.privateKey)
  const orderInfo = `${sortedQuery(params)}&sign=${encodeURIComponent(sign)}`
  return { ok: true, orderInfo }
}

function formatAlipayTimestamp(date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date)
  const get = (type) => parts.find((p) => p.type === type)?.value
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}:${get('second')}`
}

function responseKey(method) {
  return `${String(method).replaceAll('.', '_')}_response`
}

export async function callAlipay(method, bizContent) {
  const cfg = alipayConfig()
  if (!cfg.appId || !cfg.privateKey) {
    return { ok: false, error: '支付宝未配置 APPID 或应用私钥' }
  }
  const params = attachCertParams(
    {
      app_id: cfg.appId,
      method,
      format: 'JSON',
      charset: 'utf-8',
      sign_type: 'RSA2',
      timestamp: formatAlipayTimestamp(new Date()),
      version: '1.0',
      biz_content: JSON.stringify(bizContent),
    },
    cfg,
  )
  return postAlipay(cfg, method, params)
}

async function postAlipay(cfg, method, params) {
  params.sign = signParams(params, cfg.privateKey)
  const body = Object.keys(params)
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const response = await fetch(cfg.gateway, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8' },
    body,
    signal: AbortSignal.timeout(25_000),
  })
  const text = await response.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    return { ok: false, error: '支付宝返回无法解析', raw: text.slice(0, 300) }
  }
  const data = json[responseKey(method)] || json.error_response || null
  return { ok: true, data, raw: json }
}

/**
 * Signed auth string for Alipay AuthTask.authV2.
 * Opens the installed Alipay app so the user can authorize this account.
 */
export function buildAppAuthInfo() {
  const cfg = alipayConfig()
  if (!cfg.appId || !cfg.privateKey) {
    return { ok: false, error: '支付宝未配置 APPID 或应用私钥' }
  }
  if (!/^2088\d{12,16}$/.test(cfg.pid)) {
    return { ok: false, error: '缺少支付宝商户 PID' }
  }
  const targetId = `wb${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`.slice(0, 32)
  const params = {
    apiname: 'com.alipay.account.auth',
    app_id: cfg.appId,
    app_name: 'mc',
    auth_type: 'AUTHACCOUNT',
    biz_type: 'openservice',
    method: 'alipay.open.auth.sdk.code.get',
    pid: cfg.pid,
    product_id: 'APP_FAST_LOGIN',
    // auth_user：可调 alipay.user.info.share 获取昵称/头像；kuaijie 仅快捷登录标识。
    scope: 'auth_user',
    sign_type: 'RSA2',
    target_id: targetId,
  }
  const sign = signParams(params, cfg.privateKey)
  return { ok: true, authInfo: `${sortedQuery(params)}&sign=${encodeURIComponent(sign)}` }
}

export async function exchangeAlipayAuthCode(code) {
  const cfg = alipayConfig()
  if (!cfg.appId || !cfg.privateKey) {
    return { ok: false, error: '支付宝未配置 APPID 或应用私钥' }
  }
  const authCode = String(code || '').trim()
  if (!authCode || authCode.length > 512) {
    return { ok: false, error: '缺少支付宝授权码' }
  }
  const params = attachCertParams(
    {
      app_id: cfg.appId,
      method: 'alipay.system.oauth.token',
      format: 'JSON',
      charset: 'utf-8',
      sign_type: 'RSA2',
      timestamp: formatAlipayTimestamp(new Date()),
      version: '1.0',
      grant_type: 'authorization_code',
      code: authCode,
    },
    cfg,
  )
  const post = await postAlipay(cfg, 'alipay.system.oauth.token', params)
  if (!post.ok) return post
  const data = post.data || {}
  if (data.sub_code || data.sub_msg || (data.code && data.code !== '10000')) {
    return { ok: false, error: data.sub_msg || data.msg || '支付宝授权失败' }
  }
  const userId = String(data.user_id || data.alipay_user_id || '').trim()
  const openId = String(data.open_id || '').trim()
  const identity = (userId || openId).slice(0, 128)
  if (!identity) return { ok: false, error: '支付宝未返回用户标识' }
  const accessToken = String(data.access_token || '').trim() || null
  return { ok: true, identity, accessToken, userId: userId || null, openId: openId || null }
}

/** Fetch nickname / avatar after oauth (requires auth_user scope). */
export async function fetchAlipayUserProfile(accessToken) {
  const token = String(accessToken || '').trim()
  if (!token) return { ok: false, error: '缺少支付宝授权令牌' }
  const cfg = alipayConfig()
  if (!cfg.appId || !cfg.privateKey) {
    return { ok: false, error: '支付宝未配置 APPID 或应用私钥' }
  }
  const params = attachCertParams(
    {
      app_id: cfg.appId,
      method: 'alipay.user.info.share',
      format: 'JSON',
      charset: 'utf-8',
      sign_type: 'RSA2',
      timestamp: formatAlipayTimestamp(new Date()),
      version: '1.0',
      auth_token: token,
      biz_content: '{}',
    },
    cfg,
  )
  const post = await postAlipay(cfg, 'alipay.user.info.share', params)
  if (!post.ok) return post
  const data = post.data || {}
  if (data.sub_code || data.sub_msg || (data.code && data.code !== '10000')) {
    console.error('[alipay] userinfo', data.sub_code || data.code, data.sub_msg || data.msg)
    return { ok: false, error: data.sub_msg || data.msg || '获取支付宝资料失败' }
  }
  const genderRaw = String(data.gender || '').trim().toUpperCase()
  const sex = genderRaw === 'M' ? 1 : genderRaw === 'F' ? 2 : 0
  return {
    ok: true,
    nickname: String(data.nick_name || data.user_name || '').trim().slice(0, 32) || null,
    headimgurl: String(data.avatar || '').trim() || null,
    sex,
  }
}

export function identityType(loginId) {
  const id = String(loginId || '').trim()
  if (/^2088\d{12,16}$/.test(id)) return 'ALIPAY_USER_ID'
  if (id.includes('@') || /^1\d{10}$/.test(id)) return 'ALIPAY_LOGON_ID'
  if (/^[A-Za-z0-9_-]{16,128}$/.test(id)) return 'ALIPAY_OPEN_ID'
  return 'ALIPAY_LOGON_ID'
}

export function withdrawalOutBizNo(withdrawalId) {
  return `wd${withdrawalId}`
}

function transferSceneReports(scene, remark) {
  const detail = String(remark || '积分提现').trim().slice(0, 64) || '积分提现'
  // 现金营销必须同时传「活动名称」和「奖励说明」，缺一条会被拒。
  if (scene === '现金营销') {
    return [
      { info_type: '活动名称', info_content: '词搭子积分提现' },
      { info_type: '奖励说明', info_content: detail },
    ]
  }
  const infoType = {
    企业退款: '退款原因',
    佣金报酬: '佣金报酬说明',
    业务结算: '结算款项名称',
    二手回收: '回收商品名称',
  }[scene] || `${scene}说明`
  return [{ info_type: infoType, info_content: detail }]
}

export async function queryAlipayTransfer(outBizNo) {
  return callAlipay('alipay.fund.trans.common.query', {
    product_code: 'TRANS_ACCOUNT_NO_PWD',
    biz_scene: 'DIRECT_TRANSFER',
    out_biz_no: String(outBizNo),
  })
}

/**
 * Merchant transfer to an Alipay login id / uid.
 * TRANS_ACCOUNT_NO_PWD minimum is 0.10 yuan.
 * 商家转账要求公钥证书模式（app_cert_sn + alipay_root_cert_sn）。
 */
export async function transferToAlipay({
  outBizNo,
  amountYuan,
  loginId,
  realName,
  title = '词搭子积分提现',
  remark = '积分提现',
} = {}) {
  const cfg = alipayConfig()
  if (!cfg.transferReady) {
    return { ok: false, error: '支付宝商家转账未配置应用私钥/公钥' }
  }
  const identity = String(loginId || '').trim()
  const name = String(realName || '').trim()
  if (!identity) return { ok: false, error: '缺少收款支付宝账号' }
  const type = identityType(identity)
  if (type === 'ALIPAY_LOGON_ID' && name.length < 2) {
    return { ok: false, error: '请填写支付宝实名（与账号一致）' }
  }
  if (!cfg.certMode) {
    console.warn('[alipay] transfer without cert mode; Alipay may reject uni.transfer')
  }
  const scene = String(process.env.ALIPAY_TRANSFER_SCENE || '现金营销').trim() || '现金营销'
  const biz = {
    out_biz_no: String(outBizNo).slice(0, 64),
    trans_amount: String(amountYuan),
    product_code: 'TRANS_ACCOUNT_NO_PWD',
    biz_scene: 'DIRECT_TRANSFER',
    order_title: String(title).slice(0, 64),
    remark: String(remark).slice(0, 200),
    payee_info: {
      identity,
      identity_type: type,
      ...(name ? { name } : {}),
    },
    transfer_scene_name: scene,
    transfer_scene_report_infos: transferSceneReports(scene, remark),
  }

  const post = await callAlipay('alipay.fund.trans.uni.transfer', biz)
  if (!post.ok) return post
  let data = post.data || {}

  const code = String(data.code || '')
  if (code === '20000' || code === 'SYSTEM_ERROR' || !code) {
    const queried = await queryAlipayTransfer(outBizNo)
    if (queried.data?.code === '10000' || queried.data?.status) data = queried.data
  }

  return interpretTransferData(data)
}

export function interpretTransferData(data = {}) {
  const finalCode = String(data.code || '')
  const status = String(data.status || '').toUpperCase()
  const subMsg = data.sub_msg || data.msg || data.fail_reason || data.sub_code || ''
  const tradeNo = data.order_id || data.pay_fund_order_id || null
  if (finalCode === '10000' && status === 'SUCCESS') {
    return { ok: true, pending: false, tradeNo, remark: '支付宝已到账' }
  }
  if (status === 'DEALING' || status === 'WAIT_PAY') {
    return { ok: true, pending: true, tradeNo, remark: '支付宝处理中' }
  }
  if (finalCode === '20000' || finalCode === 'SYSTEM_ERROR' || status === 'DEALING') {
    return {
      ok: true,
      pending: true,
      tradeNo,
      remark: '支付宝结果确认中，请稍后在记录中查看',
    }
  }
  const subCode = String(data.sub_code || '')
  if (
    subCode === 'ORDER_NOT_EXIST' ||
    (finalCode === '40004' && /not exist|不存在/i.test(String(subMsg)))
  ) {
    return { ok: false, missing: true, error: subMsg || '转账单不存在', subCode }
  }
  return {
    ok: false,
    error: subMsg || '支付宝打款失败',
    subCode: subCode || null,
  }
}
