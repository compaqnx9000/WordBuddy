const OPEN_HOST = 'https://api.weixin.qq.com'

export function wechatLoginConfig() {
  const appId = String(process.env.WECHAT_APP_ID || '').trim()
  const appSecret = String(process.env.WECHAT_APP_SECRET || '').trim()
  return {
    appId,
    appSecret,
    loginReady: Boolean(appId && appSecret),
  }
}

/** Exchange a mobile SendAuth code for the user's OpenID. Secret stays on the server. */
export async function exchangeWechatCode(code) {
  const cfg = wechatLoginConfig()
  if (!cfg.loginReady) {
    return { ok: false, error: '微信登录尚未配置 AppId / AppSecret' }
  }
  const authCode = String(code || '').trim()
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(authCode)) {
    return { ok: false, error: '微信授权码无效' }
  }
  const url = new URL(`${OPEN_HOST}/sns/oauth2/access_token`)
  url.searchParams.set('appid', cfg.appId)
  url.searchParams.set('secret', cfg.appSecret)
  url.searchParams.set('code', authCode)
  url.searchParams.set('grant_type', 'authorization_code')
  let data
  try {
    const response = await fetch(url, { method: 'GET' })
    data = await response.json()
  } catch (error) {
    console.error('[wechat-login] token request failed', error)
    return { ok: false, error: '连接微信失败，请稍后重试' }
  }
  const openid = String(data?.openid || '').trim()
  if (!openid) {
    const message = String(data?.errmsg || '').trim()
    console.error('[wechat-login] exchange failed', data?.errcode, message)
    return { ok: false, error: wechatAuthError(data?.errcode, message) }
  }
  return {
    ok: true,
    openid,
    unionid: String(data?.unionid || '').trim() || null,
  }
}

function wechatAuthError(code, message) {
  const n = Number(code)
  if (n === 40029 || n === 40163) return '微信授权已失效，请重新登录'
  if (n === 40013 || n === 40125) return '微信登录配置有误，请联系管理员'
  if (message) return `微信登录失败（${message}）`
  return '微信登录失败'
}
