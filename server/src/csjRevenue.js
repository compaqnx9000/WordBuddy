import { query } from './db.js'
import { todayShanghai } from './checkin.js'

const SESSION_KEY = 'csj_console_cookie'
const SNAPSHOT_KEY = 'csj_revenue_snapshot'
const ORIGIN = 'https://www.csjplatform.com'
const LOGIN_URL = `${ORIGIN}/passport/web/user/login/?aid=1407&account_sdk_source=web&language=zh`
const LIST_URL = `${ORIGIN}/union_pangle/api/mediation/ad_unit/list`
const SITES = [
  { siteId: 5882819, platform: 'android', label: 'Android' },
  { siteId: 5891856, platform: 'ios', label: 'iOS' },
]
const UNIT_ORDER = [
  { type: 5, name: '激励视频' },
  { type: 3, name: '开屏' },
  { type: 7, name: 'draw信息流' },
]

function xorPassport(value) {
  return [...Buffer.from(String(value), 'utf8')].map((byte) => (byte ^ 5).toString(16)).join('')
}

function parseCookie(header) {
  const map = new Map()
  for (const part of String(header || '').split(';')) {
    const index = part.indexOf('=')
    if (index < 1) continue
    const name = part.slice(0, index).trim()
    const value = part.slice(index + 1).trim()
    if (name) map.set(name, value)
  }
  return map
}

function serializeCookie(map) {
  return [...map.entries()].map(([name, value]) => `${name}=${value}`).join('; ')
}

function applySetCookies(cookie, setCookies) {
  const map = parseCookie(cookie)
  for (const line of setCookies || []) {
    const pair = String(line).split(';')[0]
    const index = pair.indexOf('=')
    if (index < 1) continue
    const name = pair.slice(0, index).trim()
    const value = pair.slice(index + 1).trim()
    if (!name) continue
    if (!value) map.delete(name)
    else map.set(name, value)
  }
  return serializeCookie(map)
}

function cookieValue(cookie, name) {
  return parseCookie(cookie).get(name) || ''
}

async function readSession() {
  const row = (await query('SELECT value FROM app_settings WHERE key = $1', [SESSION_KEY])).rows[0]
  return String(row?.value || '').trim()
}

async function writeSession(cookie) {
  await query(
    `INSERT INTO app_settings (key, value, updated_at) VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()`,
    [SESSION_KEY, cookie],
  )
}

async function writeSnapshot(snapshot) {
  await query(
    `INSERT INTO app_settings (key, value, updated_at) VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()`,
    [SNAPSHOT_KEY, JSON.stringify(snapshot)],
  )
}

export async function loadCsjRevenueSnapshot() {
  const row = (await query('SELECT value FROM app_settings WHERE key = $1', [SNAPSHOT_KEY])).rows[0]
  const raw = String(row?.value || '').trim()
  if (raw) {
    try {
      const saved = JSON.parse(raw)
      if (saved && Array.isArray(saved.apps)) return { ...saved, empty: false }
    } catch {
      /* fall through and treat a broken snapshot as never pulled */
    }
  }
  const cookie = await readSession()
  return { ok: false, empty: true, needLogin: !cookie, date: todayShanghai(), apps: [] }
}

async function csjFetch(url, { method = 'GET', cookie = '', headers = {}, body, json } = {}) {
  const response = await fetch(url, {
    method,
    redirect: 'manual',
    headers: {
      'User-Agent':
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
      Accept: 'application/json, text/plain, */*',
      ...(cookie ? { Cookie: cookie } : {}),
      ...headers,
    },
    body: json != null ? JSON.stringify(json) : body,
    signal: AbortSignal.timeout(20_000),
  })
  const setCookies =
    typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : []
  const nextCookie = applySetCookies(cookie, setCookies)
  const text = await response.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  return { status: response.status, data, text, cookie: nextCookie }
}

function authFailed(status, data) {
  const code = String(data?.Code || data?.code || '')
  const message = String(data?.Message || data?.message || data?.description || '')
  if (status === 401 || status === 403) return true
  if (code === 'PG0001' || code.startsWith('403')) return true
  return /未登录|登录失效|请先登录|认证失败/.test(message)
}

function money(value) {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : null
}

function rate(value) {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function pickUnits(list) {
  const rows = []
  for (const spec of UNIT_ORDER) {
    const matched = (list || []).filter((item) => Number(item?.AdUnitType) === spec.type)
    if (!matched.length) {
      rows.push({ name: spec.name, type: spec.type, revenue: null, waterfallFillRate: null })
      continue
    }
    for (const item of matched) {
      const detail = item.Detail || {}
      rows.push({
        name: String(item.AdUnitName || spec.name),
        type: spec.type,
        revenue: money(detail.Revenue),
        waterfallFillRate: rate(detail.WaterfallSr),
      })
    }
  }
  return rows
}

async function loadSite(cookie, site, date) {
  const csrf = cookieValue(cookie, 'csrftoken')
  const result = await csjFetch(LIST_URL, {
    method: 'POST',
    cookie,
    headers: {
      'Content-Type': 'application/json',
      Referer: `${ORIGIN}/union/media/union/mediation/siteList?siteId=${site.siteId}`,
      'x-csrf-token': csrf,
      'X-CSRFToken': csrf,
    },
    json: {
      SiteId: site.siteId,
      StartDate: date,
      EndDate: date,
      Page: 1,
      PageSize: 50,
      NeedDetail: 1,
      rules_version: 'v2',
    },
  })
  if (authFailed(result.status, result.data)) {
    return { authFailed: true }
  }
  if (result.data?.Code !== 'PG0000') {
    return {
      ...site,
      units: [],
      error: result.data?.Message || `穿山甲返回 ${result.status}`,
    }
  }
  return {
    ...site,
    siteName: result.data?.Data?.AdUnitList?.[0]?.SiteName || '你的词搭子',
    units: pickUnits(result.data?.Data?.AdUnitList),
    error: null,
  }
}

export async function fetchCsjDailyRevenue() {
  const date = todayShanghai()
  const cookie = await readSession()
  if (!cookie) {
    return { ok: false, needLogin: true, date, apps: [] }
  }
  const apps = []
  for (const site of SITES) {
    try {
      const loaded = await loadSite(cookie, site, date)
      if (loaded.authFailed) {
        return { ok: false, needLogin: true, date, apps: [] }
      }
      apps.push(loaded)
    } catch (error) {
      apps.push({
        ...site,
        units: [],
        error: error?.name === 'TimeoutError' ? '穿山甲请求超时' : '拉取失败',
      })
    }
  }
  const snapshot = { ok: true, needLogin: false, empty: false, date, fetchedAt: new Date().toISOString(), apps }
  await writeSnapshot(snapshot)
  return snapshot
}

export async function saveCsjCookie(raw) {
  let cookie = String(raw || '').trim()
  if (/^cookie\s*:/i.test(cookie)) cookie = cookie.replace(/^cookie\s*:/i, '').trim()
  cookie = cookie.replace(/\s+/g, ' ')
  if (cookie.length < 20 || cookie.length > 12000 || !cookie.includes('=')) {
    return { ok: false, error: 'Cookie 格式不对' }
  }
  if (!/sessionid=|sid_tt=|sessionid_ss=/.test(cookie)) {
    return { ok: false, error: '这份 Cookie 里没有登录会话，请复制穿山甲请求头里的整段 Cookie' }
  }
  await writeSession(cookie)
  return { ok: true }
}

export async function loginCsjConsole(username, password) {
  let cookie = ''
  const home = await csjFetch(`${ORIGIN}/`, { cookie })
  cookie = home.cookie
  const csrf = cookieValue(cookie, 'passport_csrf_token')
  const body = new URLSearchParams({
    account: xorPassport(username),
    password: xorPassport(password),
    mix_mode: '1',
    fixed_mix_mode: '1',
  })
  const result = await csjFetch(LOGIN_URL, {
    method: 'POST',
    cookie,
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Origin: ORIGIN,
      Referer: `${ORIGIN}/?operation=login`,
      ...(csrf ? { 'x-tt-passport-csrf-token': csrf } : {}),
    },
    body: body.toString(),
  })
  cookie = result.cookie
  const errorCode = Number(result.data?.data?.error_code ?? result.data?.error_code)
  if (errorCode === 1105) {
    return {
      ok: false,
      needCaptcha: true,
      message: '穿山甲要求滑动验证，服务器没法替你滑。请在电脑登录穿山甲后，把请求头里的 Cookie 粘贴到下面。',
    }
  }
  if (errorCode) {
    const message = String(result.data?.data?.description || result.data?.description || '').trim()
    return { ok: false, message: message || '登录失败' }
  }
  if (!/sessionid=|sid_tt=|sessionid_ss=/.test(cookie)) {
    return { ok: false, message: '登录没有返回会话，请改用 Cookie' }
  }
  if (!cookieValue(cookie, 'csrftoken')) {
    const tokenPage = await csjFetch(`${ORIGIN}/union/media/api/get_csrf_token`, { cookie })
    cookie = tokenPage.cookie
    const token = tokenPage.data?.csrf_token || tokenPage.data?.data?.csrf_token
    if (token && !cookieValue(cookie, 'csrftoken')) {
      cookie = applySetCookies(cookie, [`csrftoken=${token}`])
    }
  }
  await writeSession(cookie)
  return { ok: true }
}
